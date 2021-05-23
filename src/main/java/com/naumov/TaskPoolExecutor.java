package com.naumov;

import com.naumov.taskpool.TaskPool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class TaskPoolExecutor extends AbstractExecutorService {
    private final TaskPool taskPool;
    private final int nConsumers;
    private final List<Worker> consumers;

    public TaskPoolExecutor(TaskPool taskPool, int nConsumers) {
        this.taskPool = taskPool;
        this.nConsumers = nConsumers;

        // init consumers
        List<Worker> workers = new ArrayList<>();
        for (int i = 0; i < nConsumers; i++) {
            Worker consumer = new Worker("TaskPool-consumer-" + i);
            workers.add(consumer);
        }
        this.consumers =  workers;

        consumers.forEach(Thread::start);
    }

    /**
     * Consumer
     */
    class Worker extends Thread {

        public Worker(String name) {
            super(name);
        }

        @Override
        public void run() {
            while (!this.isInterrupted()) {
                Runnable task = taskPool.get();
                ThreadUtil.logAction("extracted task: " + task);
                if (task != null) task.run(); // todo introduce exponential backoff here? (when pool is empty)
            }
        }
    }

    @Override
    public void execute(Runnable task) {
        taskPool.put(task);
    }

    @Override
    public void shutdown() {
        // TBD
    }

    @Override
    public List<Runnable> shutdownNow() {
        // TBD
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
