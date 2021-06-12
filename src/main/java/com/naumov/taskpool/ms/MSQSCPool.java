package com.naumov.taskpool.ms;

import com.naumov.taskpool.SCPool;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicIntegerArray;

public class MSQSCPool implements SCPool {

    // unmodifiable shared state
    private final ConcurrentLinkedQueue<Runnable> container = new ConcurrentLinkedQueue<>();
    private final int nConsumers;

    // shared state
    private final AtomicIntegerArray emptyIndicators; // shared among all consumers

    public MSQSCPool(int nConsumers) {
        this.nConsumers = nConsumers;
        this.emptyIndicators = new AtomicIntegerArray(nConsumers);
    }

    @Override
    public boolean produce(Runnable task) {
        return container.add(task);
    }

    @Override
    public void produceForce(Runnable task) {
        container.add(task);
    }

    @Override
    public Runnable consume() {
        Runnable r = container.poll();
        if (container.isEmpty()) clearIndicators();
        return r;
    }

    @Override
    public Runnable steal(SCPool from) {
        return from.consume();
    }

    @Override
    public boolean isEmpty() {
        return container.isEmpty();
    }

    @Override
    public void setIndicator(int consumerId) {
        emptyIndicators.set(consumerId, 1);
    }

    @Override
    public boolean checkIndicator(int consumerId) {
        return emptyIndicators.get(consumerId) == 1;
    }

    private void clearIndicators() {
        for (int i = 0; i < nConsumers; i++) {
            emptyIndicators.set(i, 0);
        }
    }
}
