package com.naumov.taskpool.salsa;

import com.naumov.ThreadUtil;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link Runnable} wrapper, needed to make sure all {@code Runnable}s, inserted to the task pool are unique.
 * Uniqueness are provided by creation of a new object each time a producer calls {@link SalsaTaskPool#put(Runnable)}.
 * For this reason, there's no need to override {@link Object#equals(Object)} and {@link Object#hashCode()} methods.
 */
public class SalsaTask implements Runnable {
    public static final SalsaTask TAKEN = new SalsaTask(); // taken task sentinel
    private final Runnable task;
    private final AtomicBoolean isCompleted = new AtomicBoolean(); // todo test code, delete

    // for the TAKEN only
    private SalsaTask() {
        this.task = null;
    }

    public SalsaTask(Runnable task) {
        if (task == null) throw new IllegalArgumentException("Creating " + getClass().getSimpleName() +
                " with null runnable");
        this.task = task;
    }

    @Override
    public void run() {
        if (this.equals(TAKEN)) {
            throw new IllegalStateException("Thread [" + Thread.currentThread() +
                    "] is trying to execute TAKEN sentinel");
        }

        if (isCompleted.get()) {
            throw new IllegalStateException("Thread [" + Thread.currentThread() + "] is trying to execute " + this +
                    ", which has already been executed");
        }

        ThreadUtil.logAction("starting to execute " + this);
        this.task.run();
        ThreadUtil.logAction("finished executing " + this);

        // todo test code, delete
        if (!isCompleted.compareAndSet(false, true)) {
            throw new IllegalStateException("Thread [" + Thread.currentThread() + "] executed " + this +
                    ", which has already been executed");
        }
    }
}
