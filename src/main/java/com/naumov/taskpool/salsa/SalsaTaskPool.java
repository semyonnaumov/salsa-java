package com.naumov.taskpool.salsa;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.naumov.taskpool.TaskPool;
import com.naumov.taskpool.salsa.annot.PermitAll;

public class SalsaTaskPool implements TaskPool {
    private static final int MAX_PRODUCERS = 32768;
    private static final int MAX_CONSUMERS = 32768;

    // unmodifiable shared pool state
    private final int maxNProducers;
    private final int maxNConsumers;
    private final CopyOnWriteArrayList<SalsaSCPool> allSCPools;

    // shared pool state
    private final AtomicInteger pCount = new AtomicInteger(0); // registered producers count
    private final AtomicInteger cCount = new AtomicInteger(0); // registered consumers count

    // ThreadLocals
    private final ThreadLocal<Integer> pIdThreadLocal = ThreadLocal.withInitial(() -> null); // producer's id in the pool: [0 .. maxNProducers)
    private final ThreadLocal<Integer> cIdThreadLocal = ThreadLocal.withInitial(() -> null); // consumer's id in the pool: [0 .. nConsumers)
    private final ThreadLocal<List<SalsaSCPool>> pAccessListThreadLocal = ThreadLocal.withInitial(() -> null); // producer's access list
    private final ThreadLocal<List<SalsaSCPool>> cAccessListThreadLocal = ThreadLocal.withInitial(() -> null); // consumer's access list
    private final ThreadLocal<SalsaSCPool> cSCPoolThreadLocal = ThreadLocal.withInitial(() -> null); // consumer's SCPool

    public SalsaTaskPool(int maxNProducers, int maxNConsumers, int chunkSize) {
        if (maxNProducers < 1 || maxNProducers > MAX_PRODUCERS) {
            throw new IllegalArgumentException("maxNProducers cannot be less than 1 and greater than " + MAX_PRODUCERS
                    + ", got " + maxNProducers);
        }

        if (maxNConsumers < 1 || maxNConsumers > MAX_CONSUMERS) {
            throw new IllegalArgumentException("nConsumers cannot be less than 1 and greater than " + MAX_CONSUMERS
                    + ", got " + maxNConsumers);
        }

        this.maxNProducers = maxNProducers;
        this.maxNConsumers = maxNConsumers;

        final List<SalsaSCPool> allSCPools = new ArrayList<>();
        for (int cId = 0; cId < maxNConsumers; cId++) {
            final SalsaSCPool scPool = new SalsaSCPool(cId, chunkSize, maxNProducers, maxNConsumers);
            allSCPools.add(scPool); // create sc pools, but not bind to consumers yet
        }

        this.allSCPools = new CopyOnWriteArrayList<>(allSCPools);
    }

    @Override
    public void put(Runnable task) {
        checkThreadRegistered(true);

        // produce to the pool by the order of the access list
        for (SalsaSCPool scPool: pAccessListThreadLocal.get()) {
            if (scPool.produce(new SalsaTask(task))) return;
        }

        // if all pools are full, expand the closest pool
        SalsaSCPool firstSCPool = pAccessListThreadLocal.get().get(0);
        firstSCPool.produceForce(new SalsaTask(task));
    }

    @Override
    public Runnable get() {
        checkThreadRegistered(false);

        SalsaSCPool myPool = cSCPoolThreadLocal.get();
        while (!Thread.currentThread().isInterrupted()) { // todo if more than 4 consumers, they can't quit this method!
            // first try to get a task from the local pool
            Runnable task = myPool.consume();
            if (task != null) return task;

            // failed to get an task from a local pool - steal
            for (SalsaSCPool otherSCPool : cAccessListThreadLocal.get()) {
                task = myPool.steal(otherSCPool);
                if (task != null) return task;
            }

            // no tasks found - validate emptiness
            if (isEmpty()) return null;
        }

        return null;
    }

    @Override
    @PermitAll
    public boolean isEmpty() {
        // todo currently cannot be called by a producer - fix
        checkThreadRegistered(false);

        for (int i = 0; i < maxNConsumers; i++) {
            for (SalsaSCPool scPool : allSCPools) {
                if (i == 0) scPool.setIndicator(cIdThreadLocal.get());
                if (!scPool.isEmpty()) return false;
                if (!scPool.checkIndicator(cIdThreadLocal.get())) return false;
            }
        }
        return true;
    }

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
                int id = tryInitId(pCount, maxNProducers, true);
                pIdThreadLocal.set(id);

                // init access list and bind
                pAccessListThreadLocal.set(new CopyOnWriteArrayList<>(allSCPools)); // todo introduce affinity-based sorting here
                pAccessListThreadLocal.get().forEach(scPool -> scPool.bindProducer(id));
            } else {
                // register as consumer
                int id = tryInitId(cCount, maxNConsumers, false);
                cIdThreadLocal.set(id);

                // init access list and bind
                cAccessListThreadLocal.set(new CopyOnWriteArrayList<>(allSCPools));
                SalsaSCPool myPool = cAccessListThreadLocal.get().remove(id);
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
