package com.naumov.taskpool;

/**
 * Producer-consumer task pool abstraction.
 */
public interface TaskPool {
    /**
     * Adds new task to the pool.
     * @param task a task, that can be executed
     */
    void put(Runnable task);

    /**
     * Extracts and returns a task (without any order) from the pool.
     * @return a task or {@code null} if the pool was empty during some point in this method execution
     */
    Runnable get();

    /**
     * For the system to be linearizable this method must return {@code true} only
     * when there's no tasks in the pool at some point during this method execution.
     */
    boolean isEmpty();
}