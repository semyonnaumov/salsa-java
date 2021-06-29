package com.naumov;

import com.naumov.taskpool.TaskPool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class TaskPoolExecutor extends AbstractExecutorService {
    private final TaskPool taskPool;
    private final List<Worker> consumers;

    public TaskPoolExecutor(TaskPool taskPool, int nConsumers, int backoffStartTimeout) {
        this.taskPool = taskPool;

        // init consumers
        List<Worker> workers = new ArrayList<>();
        for (int i = 0; i < nConsumers; i++) {
            Worker consumer = new Worker(i, backoffStartTimeout);
            workers.add(consumer);
        }

        consumers = Collections.unmodifiableList(workers);
        consumers.forEach(Thread::start);
    }

    /**
     * Consumer thread
     */
    class Worker extends Thread {
        private final int backoffStartTimeout;

        public Worker(int id, int backoffStartTimeout) {
            super("TaskPool-consumer-" + id);
            this.backoffStartTimeout = backoffStartTimeout;
        }

        @Override
        public void run() {
            if (backoffStartTimeout > 0) {
                // back-offed run
                Backoff backoff = new Backoff(backoffStartTimeout, backoffStartTimeout * 3, backoffStartTimeout * 2000);

                while (!this.isInterrupted()) {
                    Runnable task = taskPool.get();
                    if (task != null) {
                        backoff.flush();
                        task.run();
                    } else {
                        backoff.backoff();
                    }
                }
            } else {
                // without backoff
                while (!this.isInterrupted()) {
                    Runnable task = taskPool.get();
                    if (task != null) task.run();
                }
            }
        }
    }

    @Override
    public void execute(Runnable task) {
        if (task == null) throw new NullPointerException();
        taskPool.put(task);
    }

    @Override
    public void shutdown() {
        // todo implement correctly
        shutdownNow();
    }

    @Override
    public List<Runnable> shutdownNow() {
        // todo implement correctly
        ThreadUtil.logMajorAction("shutting down workers: " + consumers.stream().map(Thread::getName).collect(Collectors.toList()));
        consumers.forEach(Thread::interrupt);
        return null;
    }

    @Override
    public boolean isShutdown() {
        // TBD
        return false;
    }

    @Override
    public boolean isTerminated() {
        // TBD
        return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        // TBD
        return false;
    }

    private static class Backoff {
        private static final int SMALL_PRIME = 7;
        private final int minStartTimeoutNs;
        private final int maxStartTimeoutNs;
        private final int maxTimeoutNs;
        private int currentTimeoutNs; // backoff value
        private int i; // backoff exponent

        private Backoff(int minStartTimeoutNs, int maxStartTimeoutNs, int maxTimeoutNs) {
            this.minStartTimeoutNs = minStartTimeoutNs;
            this.maxStartTimeoutNs = maxStartTimeoutNs;
            this.maxTimeoutNs = maxTimeoutNs;
            currentTimeoutNs = minStartTimeoutNs + ThreadLocalRandom.current().nextInt(maxStartTimeoutNs);
            i = 1;
        }

        private void backoff() {
            if (currentTimeoutNs < maxTimeoutNs) {
                currentTimeoutNs = Math.min((int) Math.pow(SMALL_PRIME + currentTimeoutNs, i), maxTimeoutNs);
            }

            long startTime = System.nanoTime();
            long elapsedNanos = System.nanoTime() - startTime;
            // busy wait on backoff
            while (elapsedNanos < currentTimeoutNs && !Thread.currentThread().isInterrupted()) {
                elapsedNanos = System.nanoTime() - startTime;
            }
        }

        private void flush() {
            currentTimeoutNs = ThreadLocalRandom.current().nextInt(minStartTimeoutNs, maxStartTimeoutNs);
            i = 1;
        }
    }
}
