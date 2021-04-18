package com.naumov.taskpool;

public interface TaskPool {
    void put(Runnable task);
    Runnable get();
    boolean isEmpty();
}