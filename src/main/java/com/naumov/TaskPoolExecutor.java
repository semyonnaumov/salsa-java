package com.naumov;

import com.naumov.taskpool.TaskPool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

public class TaskPoolExecutor implements ExecutorService {
    private final TaskPool taskPool;
    private final int nConsumers;
    private final List<Worker> consumers;

    public TaskPoolExecutor(TaskPool taskPool, int nConsumers) {
        this.taskPool = taskPool;
        this.nConsumers = nConsumers;

        // init consumers
        List<Worker> workers = new ArrayList<>();
        for (int i = 0; i < nConsumers; i++) {
            Worker consumer = new Worker();
            workers.add(consumer);
        }
        this.consumers =  workers;

        consumers.forEach(Thread::start);
    }

    /**
     * Consumer
     */
    class Worker extends Thread {
        @Override
        public void run() {
            while (!this.isInterrupted()) {
                Runnable task = taskPool.get();
                task.run();
            }
        }
    }

    @Override
    public void execute(Runnable task) {
        taskPool.put(task);
    }

    @Override
    public Future<?> submit(Runnable task) {
        taskPool.put(task);
        return null; // TBD
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

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        // TBD
        return null;
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        // TBD
        return null;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        // TBD
        return null;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        // TBD
        return null;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        // TBD
        return null;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        // TBD
        return null;
    }
}
