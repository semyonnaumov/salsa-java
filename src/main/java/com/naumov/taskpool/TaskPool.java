package com.naumov.taskpool;

/**
 * Producer-consumer task pool abstraction. Represents an unordered collection of {@link Runnable} items.
 */
public interface TaskPool {
    /**
     * Adds new task to the pool. The thread that calls this method is considered a <b>producer</b>.
     * Implementations may throw any kind of {@link RuntimeException} if needed (i.e. for pool management purposes).
     * @param task a task, that can be executed
     */
    void put(Runnable task);

    /**
     * Extracts and returns a task (without any order) from the pool, if it contains any or {@code null} if it is empty.
     * The thread that calls this method is considered a <b>consumer</b>. Implementations may throw any kind of
     * {@link RuntimeException} if needed (i.e. for pool management purposes).
     * @return a task or {@code null} if the pool was empty during some point in this method execution
     */
    Runnable get();

    /**
     * Emptiness check that returns {@code true} only when there's no tasks in the pool at some point during this method
     * execution. Such behavior is needed for the task pool to be <b>linearizable</b> concurrent object.
     * Implementations may throw any kind of {@link RuntimeException} if needed (i.e. for pool management purposes).
     * @return {@code false} result of emptiness check
     */
    boolean isEmpty();
}