package com.naumov.taskpool;

/**
 * Single-consumer pool abstraction. Only owner of the pool normally can retrieve tasks
 */
public interface SCPool {
    /**
     * Lets a producer thread try to insert a task to the pool: returns {@code false} if no space is available.
     * When it is called by a consumer, some kind of a {@link UnsupportedOperationException} is thrown
     * (depends on the implementation).
     *
     * @param task task to be inserted
     * @return result of the insertion: it is {@code false} when the pool is full
     */
    boolean produce(Runnable task);

    /**
     * Lets a producer thread insert a task to the pool, expanding it when necessary. When it is called by a consumer,
     * some kind of a {@link UnsupportedOperationException} is thrown (depends on the implementation).
     *
     * @param task task to be inserted
     */
    void produceForce(Runnable task);

    /**
     * Lets the consumer thread, that owns the pool, retrieve a task from it. When it is called by another consumer
     * or a producer some kind of a {@link UnsupportedOperationException} is thrown (depends on the implementation).
     *
     * @return a task or {@code null} when no tasks are detected
     */
    Runnable consume();

    /**
     * Lets a consumer thread, that owns the pool, try to steal a number of tasks form the given pool
     * moving them move them to the current pool. When it is called by another consumer or a producer
     * some kind of a {@link UnsupportedOperationException} is thrown (depends on the implementation).
     *
     * @param from the pool to steal from
     * @return one of stolen tasks, {@code null} if stealing was unsuccessful
     */
    Runnable steal(SCPool from);

    /**
     * Lets producers and consumers check the emptiness of the pool.
     *
     * @return {@code true} when the pool contains any tasks
     */
    boolean isEmpty();

    /**
     * Sets the empty indicator in the current pool for the consumer {@code consumerId}.
     *
     * @param consumerId consumer to set indicator for
     */
    void setIndicator(int consumerId);

    /**
     * Checks the empty indicator in the current pool for the consumer {@code consumerId}.
     *
     * @param consumerId consumer to check indicator for
     * @return the empty indicator value
     */
    boolean checkIndicator(int consumerId);
}
