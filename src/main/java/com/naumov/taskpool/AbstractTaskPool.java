package com.naumov.taskpool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractTaskPool implements TaskPool {
    private static final int MAX_N_PRODUCERS = 32768;
    private static final int MAX_N_CONSUMERS = 32; // can be increased, currently limited due to the emptyIndicator implementation

    // unmodifiable shared pool state
    private final int nProducers;
    private final int nConsumers;
    private final CopyOnWriteArrayList<SCPool> allSCPools;

    // shared pool state
    private final AtomicInteger pCount = new AtomicInteger(0); // registered producers count, for issuing id
    private final AtomicInteger cCount = new AtomicInteger(0); // registered consumers count, for issuing id

    // ThreadLocals
    private final ThreadLocal<Integer> pIdThreadLocal = ThreadLocal.withInitial(() -> -1); // producer's id in the pool: [0 .. nProducers)
    private final ThreadLocal<Integer> cIdThreadLocal = ThreadLocal.withInitial(() -> -1); // consumer's id in the pool: [0 .. nConsumers)
    private final ThreadLocal<List<SCPool>> pAccessListThreadLocal = ThreadLocal.withInitial(() -> null); // producer's access list
    private final ThreadLocal<List<SCPool>> cAccessListThreadLocal = ThreadLocal.withInitial(() -> null); // consumer's access list
    private final ThreadLocal<SCPool> cSCPoolThreadLocal = ThreadLocal.withInitial(() -> null); // consumer's SCPool

    public AbstractTaskPool(int nProducers, int nConsumers, int chunkSize) {
        if (nProducers < 1 || nProducers > MAX_N_PRODUCERS) {
            throw new IllegalArgumentException("nProducers cannot be less than 1 and greater than " + MAX_N_PRODUCERS
                    + ", got " + nProducers);
        }

        if (nConsumers < 1 || nConsumers > MAX_N_CONSUMERS) {
            throw new IllegalArgumentException("nConsumers cannot be less than 1 and greater than " + MAX_N_CONSUMERS
                    + ", got " + nConsumers);
        }

        this.nProducers = nProducers;
        this.nConsumers = nConsumers;

        final List<SCPool> allSCPools = new ArrayList<>(nConsumers);
        for (int cId = 0; cId < nConsumers; cId++) {
            final SCPool scPool = newSCPool(cId, chunkSize, nProducers, nConsumers);
            allSCPools.add(scPool); // create sc pools, but not bind to consumers yet
        }

        this.allSCPools = new CopyOnWriteArrayList<>(allSCPools);
    }

    /**
     * Successors of this class must implement this method to provide used {@code SCPool} implementations.
     * @param consumerId id of the owner of a created pool
     * @param chunkSize size of chunks in this pool
     * @param nProducers max number of producers this SCPool allows
     * @param nConsumers max number of consumer this SCPool allows
     * @return newly created SCPool successor
     */
    protected abstract SCPool newSCPool(int consumerId, int chunkSize, int nProducers, int nConsumers);

    @Override
    public void put(Runnable task) {
        checkThreadRegistered(true);

        List<SCPool> accessList = pAccessListThreadLocal.get();
        int accessListSize = accessList.size();
        int startIdx = ThreadLocalRandom.current().nextInt(accessListSize); // [0, accessListSize)

        // try produce to all pools, traversing from a random start
        for (int i = startIdx; i < accessListSize + startIdx; i++) {
            SCPool scPool = accessList.get(i % accessListSize);
            if (scPool.tryProduce(task)) return;
        }

        // all pools are full, expand the first pool
        SCPool firstSCPool = accessList.get(startIdx);
        firstSCPool.produce(task);
    }

    @Override
    public Runnable get() {
        checkThreadRegistered(false);

        SCPool myPool = cSCPoolThreadLocal.get();
        while (!Thread.currentThread().isInterrupted()) {
            // first try to get a task from the local pool
            Runnable task = myPool.consume();
            if (task != null) return task;

            // failed to get a task from the local pool - steal
            List<SCPool> accessList = cAccessListThreadLocal.get();
            int accessListSize = accessList.size();
            if (accessListSize > 0) {
                int startIdx = ThreadLocalRandom.current().nextInt(accessListSize); // [0, accessListSize)
                for (int i = startIdx; i < accessListSize + startIdx; i++) {
                    SCPool scPool = accessList.get(i % accessListSize);
                    task = myPool.steal(scPool);
                    if (task != null) return task;
                }
            }

            // no tasks found - validate emptiness
            if (isEmpty()) return null;
        }

        return null;
    }

    @Override
    public boolean isEmpty() {
        checkThreadRegistered(false);

        for (int i = 0; i < nConsumers; i++) {
            for (SCPool scPool : allSCPools) {
                if (i == 0) scPool.setIndicator(cIdThreadLocal.get());
                if (!scPool.isEmpty()) return false;
                if (!scPool.checkIndicator(cIdThreadLocal.get())) return false;
            }
        }
        return true;
    }

    protected abstract void registerProducerOnSCPool(SCPool scPool, int producerId);
    protected abstract void registerOwnerOnSCPool(SCPool scPool, int consumerId);

    /**
     * Checks whether a calling thread (producer/consumer) is registered in the task pool and register it if necessary.
     * Thread is registered only once, at the first arrival at this method.
     * @throws IllegalCallerException when called by a registered producer with {@code fromProducerContext == false}
     * or vice versa
     * @param fromProducerContext flag to check for specific context
     */
    private void checkThreadRegistered(boolean fromProducerContext) {
        if (pIdThreadLocal.get() == -1 && cIdThreadLocal.get() == -1) {
            // new thread, need to register
            if (fromProducerContext) {
                // register as producer
                int id = tryInitId(true);
                pIdThreadLocal.set(id);

                // init access list and bind producer
                List<SCPool> accessListTemplate = new ArrayList<>(allSCPools);
                Collections.shuffle(accessListTemplate); // shuffle for better workload distribution
                pAccessListThreadLocal.set(new CopyOnWriteArrayList<>(accessListTemplate));
                pAccessListThreadLocal.get().forEach(scPool -> registerProducerOnSCPool(scPool, id));
            } else {
                // register as consumer
                int id = tryInitId(false);
                cIdThreadLocal.set(id);

                // init access list and bind owner
                List<SCPool> accessListTemplate = new ArrayList<>(allSCPools);
                SCPool myPool = accessListTemplate.remove(id);
                registerOwnerOnSCPool(myPool, id);
                cSCPoolThreadLocal.set(myPool);
                Collections.shuffle(accessListTemplate);
                cAccessListThreadLocal.set(new CopyOnWriteArrayList<>(accessListTemplate));
            }
        } else if (pIdThreadLocal.get() != -1 && !fromProducerContext) {
            // registered producer appeared in a consumer context
            throw new IllegalCallerException("Already registered producer called from consumer context");
        } else if (cIdThreadLocal.get() != -1 && fromProducerContext) {
            // registered consumer appeared in a producer context
            throw new IllegalCallerException("Already registered consumer called from producer context");
        }
        // everything alright, actor is registered
    }

    /**
     * Inits a unique id for the current thread.
     * @param isProducer producer/consumer flag
     * @return unique id for producer/consumer
     */
    private int tryInitId(boolean isProducer) {
        AtomicInteger count = isProducer ? pCount : cCount;
        int max = isProducer ? nProducers : nConsumers;

        // init id in a CAS loop
        int currentCount;
        do {
            currentCount = count.get();
            if (currentCount >= max) {
                throw new IllegalStateException("Too many " + (isProducer ? "producers" : "consumers"));
            }
        } while (!count.compareAndSet(currentCount, currentCount + 1) && !Thread.currentThread().isInterrupted());

        return currentCount;
    }
}
