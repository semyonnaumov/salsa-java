package com.naumov.taskpool;

public interface SCPool {
    boolean produce(Runnable task);
    void produceForce(Runnable task);
    Runnable consume();
    Runnable steal(SCPool from);
    boolean isEmpty();
    void setIndicator(int consumerId);
    boolean checkIndicator(int consumerId);
}
