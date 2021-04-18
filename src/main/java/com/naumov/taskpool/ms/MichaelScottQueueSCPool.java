package com.naumov.taskpool.ms;

import com.naumov.taskpool.SCPool;

import java.util.concurrent.ConcurrentLinkedQueue;

// todo
public class MichaelScottQueueSCPool implements SCPool {
    private final ConcurrentLinkedQueue<Object> container = new ConcurrentLinkedQueue<>();

    @Override
    public boolean produce(Runnable task) {
        return false;
    }

    @Override
    public void produceForce(Runnable task) {

    }

    @Override
    public Runnable consume() {
        return null;
    }

    @Override
    public Runnable steal(SCPool from) {
        return null;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public void setIndicator(int consumerId) {

    }

    @Override
    public boolean checkIndicator(int consumerId) {
        return false;
    }
}
