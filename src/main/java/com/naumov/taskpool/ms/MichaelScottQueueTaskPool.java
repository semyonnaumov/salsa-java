package com.naumov.taskpool.ms;

import com.naumov.taskpool.TaskPool;

import java.util.concurrent.ConcurrentLinkedQueue;

public class MichaelScottQueueTaskPool implements TaskPool {
    // todo add cons threads init
    private final int maxNProducers; // for compatibility with SALSA
    private final int nConsumers; // for compatibility with SALSA
    private final ConcurrentLinkedQueue<Runnable> queue;

    public MichaelScottQueueTaskPool(int maxNProducers, int nConsumers) {
        this.maxNProducers = maxNProducers;
        this.nConsumers = nConsumers;
        queue = new ConcurrentLinkedQueue<>();
    }

    @Override
    public void put(Runnable task) {
        // todo add checking
        queue.add(task);
    }

    @Override
    public Runnable get() {
        // todo check called internally
        return queue.poll();
    }

    @Override
    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
