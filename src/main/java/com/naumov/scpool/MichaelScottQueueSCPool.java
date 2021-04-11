package com.naumov.scpool;

import java.util.concurrent.ConcurrentLinkedQueue;

// todo check if needs sync
public class MichaelScottQueueSCPool implements SCPool {
    private final ConcurrentLinkedQueue<Object> container = new ConcurrentLinkedQueue<>();


    @Override
    public boolean produce(Object item) {
        return false;
    }

    @Override
    public void produceForce(Object item) {

    }

    @Override
    public Object consume() {
        return container.poll();
    }

    @Override
    public Object steal(SCPool from) {
        return from.consume();
    }

    @Override
    public boolean isEmpty() {
        return container.isEmpty();
    }

    @Override
    public void setIndicator(int consumerId) {

    }

    @Override
    public boolean checkIndicator(int consumerId) {
        return false;
    }
}
