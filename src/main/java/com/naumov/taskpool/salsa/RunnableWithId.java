package com.naumov.taskpool.salsa;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link Runnable} wrapper with an unique id
 */
public class RunnableWithId implements Runnable {
    public static final RunnableWithId TAKEN = new RunnableWithId();
    private static final AtomicLong idCounter = new AtomicLong(Long.MIN_VALUE + 1); // todo deal with possible overflow
    private final long id;
    private final Runnable task;
    private volatile boolean isCompleted = false; // for correctness testing purposes only

    private RunnableWithId() {
        // for the TAKEN only
        this.id = Long.MIN_VALUE;
        this.task = null;
    }

    public RunnableWithId(Runnable task) {
        this.id = idCounter.getAndIncrement();
        this.task = task;
    }

    @Override
    public void run() {
        if (isCompleted) {
            throw new IllegalStateException("Thread [" + Thread.currentThread() + "] + is trying to execute " + this + ", which has already been executed!");
        }

        System.out.println("Thread [" + Thread.currentThread() + "] + is executing " + this);
        this.task.run();
        this.isCompleted = true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RunnableWithId runnableWithId = (RunnableWithId) o;
        return id == runnableWithId.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "RunnableWithId{" +
                "id=" + id +
                '}';
    }
}
