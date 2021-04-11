package com.naumov.scpool;

public interface SCPool {
    boolean produce(Object item);
    void produceForce(Object item);
    Object consume();
    Object steal(SCPool from);
    boolean isEmpty();
    void setIndicator(int consumerId); // todo check this method
    boolean checkIndicator(int consumerId); // todo check this method
}
