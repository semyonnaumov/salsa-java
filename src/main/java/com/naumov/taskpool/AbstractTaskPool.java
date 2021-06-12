package com.naumov.taskpool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractTaskPool implements TaskPool {
    private static final int MAX_N_PRODUCERS = 32768;
    private static final int MAX_N_CONSUMERS = 32768;

    // unmodifiable shared pool state
    private final int nProducers;
    private final int nConsumers;
    private final CopyOnWriteArrayList<SCPool> allSCPools;

    // shared pool state
    private final AtomicInteger pCount = new AtomicInteger(0); // registered producers count
    private final AtomicInteger cCount = new AtomicInteger(0); // registered consumers count

    // ThreadLocals
    private final ThreadLocal<Integer> pIdThreadLocal = ThreadLocal.withInitial(() -> null); // producer's id in the pool: [0 .. nProducers)
    private final ThreadLocal<Integer> cIdThreadLocal = ThreadLocal.withInitial(() -> null); // consumer's id in the pool: [0 .. nConsumers)
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

        final List<SCPool> allSCPools = new ArrayList<>();
        for (int cId = 0; cId < nConsumers; cId++) {
            final SCPool scPool = newSCPool(cId, chunkSize, nProducers, nConsumers);
            allSCPools.add(scPool); // create sc pools, but not bind to consumers yet
        }

        this.allSCPools = new CopyOnWriteArrayList<>(allSCPools);
    }

    protected abstract SCPool newSCPool(int consumerId, int chunkSize, int nProducers, int nConsumers);

    @Override
    public void put(Runnable task) {
        checkThreadRegistered(true);

        // produce to the pool by the order of the access list
        for (SCPool scPool: pAccessListThreadLocal.get()) {
            if (scPool.produce(task)) return;
        }

        // if all pools are full, expand the closest pool
        SCPool firstSCPool = pAccessListThreadLocal.get().get(0);
        firstSCPool.produceForce(task);
    }

    @Override
    public Runnable get() {
        checkThreadRegistered(false);

        SCPool myPool = cSCPoolThreadLocal.get();
        while (!Thread.currentThread().isInterrupted()) { // todo if more than 4 consumers, they can't quit this method!
            // first try to get a task from the local pool
            Runnable task = myPool.consume();
            if (task != null) return task;

            // failed to get an task from a local pool - steal
            for (SCPool otherSCPool : cAccessListThreadLocal.get()) {
                task = myPool.steal(otherSCPool);
                if (task != null) return task;
            }

            // no tasks found - validate emptiness
            if (isEmpty()) return null;
        }

        return null;
    }

    @Override
    public boolean isEmpty() {
        // todo currently cannot be called by a producer - fix
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
     * @throws UnsupportedOperationException when called by a registered producer with {@code fromProducerContext == false}
     * or vice versa
     * @param fromProducerContext flag to check for specific context
     */
    private void checkThreadRegistered(boolean fromProducerContext) {
        if (pIdThreadLocal.get() == null && cIdThreadLocal.get() == null) {
            // new thread, need to register
            if (fromProducerContext) {
                // register as producer
                int id = tryInitId(pCount, nProducers, true);
                pIdThreadLocal.set(id);

                // init access list and bind producer
                pAccessListThreadLocal.set(new CopyOnWriteArrayList<>(allSCPools)); // todo introduce affinity-based sorting here
                pAccessListThreadLocal.get().forEach(scPool -> registerProducerOnSCPool(scPool,id));
            } else {
                // register as consumer
                int id = tryInitId(cCount, nConsumers, false);
                cIdThreadLocal.set(id);

                // init access list and bind owner
                cAccessListThreadLocal.set(new CopyOnWriteArrayList<>(allSCPools));
                SCPool myPool = cAccessListThreadLocal.get().remove(id);
                registerOwnerOnSCPool(myPool, id);
                cSCPoolThreadLocal.set(myPool);
            }
        } else if (pIdThreadLocal.get() != null && !fromProducerContext) {
            // asked to register already registered producer as consumer
            throw new UnsupportedOperationException("Already registered producer called from consumer context");
        } else if (cIdThreadLocal.get() != null && fromProducerContext) {
            // asked to register already registered consumer as producer
            throw new UnsupportedOperationException("Already registered consumer called from producer context");
        }
    }

    private int tryInitId(AtomicInteger count, int max, boolean isProducer) {
        // trying to increment a counter for itself
        int currentCount;
        do {
            currentCount = count.get();
            if (currentCount >= max) { // too many producers
                throw new UnsupportedOperationException("Too many " + (isProducer ? "producers" : "consumers"));
            }
        } while (!count.compareAndSet(currentCount, currentCount + 1) && !Thread.currentThread().isInterrupted());

        return currentCount;
    }
}
