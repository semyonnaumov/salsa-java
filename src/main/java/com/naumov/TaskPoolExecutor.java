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

    public TaskPoolExecutor(TaskPool taskPool, int nConsumers) {
        this.taskPool = taskPool;

        // init consumers
        List<Worker> workers = new ArrayList<>();
        for (int i = 0; i < nConsumers; i++) {
            Worker consumer = new Worker(i);
            workers.add(consumer);
        }
        consumers = Collections.unmodifiableList(workers);

        ThreadUtil.logMajorAction("starting workers: " + consumers.stream().map(Thread::getName).collect(Collectors.toList()));
        consumers.forEach(Thread::start);
    }

    /**
     * Consumer thread
     */
    class Worker extends Thread {

        public Worker(int id) {
            super("TaskPool-consumer-" + id);
        }

        @Override
        public void run() {
            ThreadUtil.logMajorAction("started");
            while (!this.isInterrupted()) {
                Runnable task = taskPool.get();

                if (task != null) {
                    ThreadUtil.logAction("extracted task: " + task);
                    task.run(); // todo introduce exponential backoff here? (when pool is empty)
                }
            }

            ThreadUtil.logMajorAction("interrupted");
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
}
