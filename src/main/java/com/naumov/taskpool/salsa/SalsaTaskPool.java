package com.naumov.taskpool.salsa;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.naumov.taskpool.TaskPool;

public class SalsaTaskPool implements TaskPool {
    private final int maxNProducers;
    private final int nConsumers;
    private final AtomicInteger pCount = new AtomicInteger(0); // registered producers count
    private final AtomicInteger cCount = new AtomicInteger(0); // registered consumers count
    private final ThreadLocal<Integer> pIdThreadLocal = ThreadLocal.withInitial(() -> null); // producer id in pool: [0 .. maxNProducers)
    private final ThreadLocal<Integer> cIdThreadLocal = ThreadLocal.withInitial(() -> null); // consumer id in pool: [0 .. nConsumers)
    private final ThreadLocal<CopyOnWriteArrayList<SalsaSCPool>> pAccessListThreadLocal = ThreadLocal.withInitial(() -> null); // producer access list
    private final ThreadLocal<CopyOnWriteArrayList<SalsaSCPool>> cAccessListThreadLocal = ThreadLocal.withInitial(() -> null); // consumer access list
    private final ThreadLocal<SalsaSCPool> cSCPoolThreadLocal = ThreadLocal.withInitial(() -> null); // consumer's own SCPool
    private final CopyOnWriteArrayList<SalsaSCPool> allSCPools = new CopyOnWriteArrayList<>();

    public SalsaTaskPool(int maxNProducers, int nConsumers, int chunkSize) {
        this.maxNProducers = maxNProducers;
        this.nConsumers = nConsumers;

        for (int cId = 0; cId < nConsumers; cId++) {
            SalsaSCPool scPool = new SalsaSCPool(cId, chunkSize, maxNProducers, nConsumers);
            allSCPools.add(scPool); // create sc pools, but not bind to consumers yet
        }
    }

    @Override
    public void put(Runnable task) {
        if (!isProducer() && !isConsumer()) {
            // need to register a new producer
            initProducer();
        } else if (isConsumer()) {
            // it is a consumer
            throw new UnsupportedOperationException("Method put(...) was called by a thread, already registered as consumer");
        }
        // registered producer

        // produce to the pool by the order of the access list
        for (SalsaSCPool scPool: pAccessListThreadLocal.get()) {
            if (scPool.produce(task)) return;
        }

        // if all pools are full, expand the closest pool
        SalsaSCPool firstSCPool = pAccessListThreadLocal.get().get(0);
        firstSCPool.produceForce(task);
    }

    @Override
    public Runnable get() {
        if (!isConsumer() && !isProducer()) {
            // need to register a new consumer
            initConsumer();
        } else if (isProducer()) {
            // it is a producer
            throw new UnsupportedOperationException("Method get() was called by a thread, already registered as producer");
        }

        // registered consumer
        SalsaSCPool myPool = cSCPoolThreadLocal.get();

        while (true) {
            // first try to get an task from a local pool
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
    }

    private boolean isProducer() {
        return pIdThreadLocal.get() != null;
    }

    private boolean isConsumer() {
        return cIdThreadLocal.get() != null;
    }

    private void initProducer() {
        int id = tryInitId(pCount, maxNProducers, true);
        pIdThreadLocal.set(id);

        // all sc pools must be created up until this moment
        pAccessListThreadLocal.set(new CopyOnWriteArrayList<>(allSCPools)); // todo introduce affinity-based sorting here
        pAccessListThreadLocal.get().forEach(scPool -> scPool.bindProducer(id)); // bind producer to all SCPools here
    }

    private void initConsumer() {
        int id = tryInitId(cCount, nConsumers, false);
        cIdThreadLocal.set(id);

        // all sc pools must be created up until this moment
        SalsaSCPool myPool = allSCPools.get(id);
        // bind consumer to his SCPool
        cSCPoolThreadLocal.set(myPool);

        cAccessListThreadLocal.set(new CopyOnWriteArrayList<>(allSCPools));
        cAccessListThreadLocal.get().remove(id); // remove consumer's own SCPool
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

    @Override
    public boolean isEmpty() {
        // todo currently cannot be called by a producer

        for (int i = 0; i < nConsumers; i++) {
            for (SalsaSCPool scPool : allSCPools) {
                if (i == 0) scPool.setIndicator(cIdThreadLocal.get());
                if (!scPool.isEmpty()) return false;
                if (!scPool.checkIndicator(cIdThreadLocal.get())) return false;
            }
        }
        return true;
    }
}
