package com.naumov;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class NaiveTest {

    public static class MathLogTask implements Callable<Double> {
        int a = 2 + ThreadLocalRandom.current().nextInt(100000);

        @Override
        public Double call() {
            return Math.log(a);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        final int N_PRODUCERS = 4;
        final int N_CONSUMERS = 4;
        final long RUNTIME_MS = 600000L;

        ExecutorService executorService = MyExecutors.newSalsaThreadPool(N_PRODUCERS,
                N_CONSUMERS,
                1000,
                Integer.MAX_VALUE,
                0);

        // init producers
        List<Thread> producers = new ArrayList<>();
        for (int i = 0; i < N_PRODUCERS; i++) {
            Thread producer = new Thread(() -> {
                ThreadUtil.logMajorAction("started");
                outer: while (!Thread.currentThread().isInterrupted()) {
                    ThreadUtil.logAction("submit task");
                    try {
                        MathLogTask task = new MathLogTask();
                        Future<Double> doubleFuture = executorService.submit(new MathLogTask());

                        int c = 0;
                        while(c < 100000) {
                            c++;
                            if (doubleFuture.isDone()) {
                                System.out.println("a = " + task.a + ", res = " + doubleFuture.get());
                                continue outer;
                            }
                        }

                        Thread.sleep(3000); // sleep 1s to wait for task completion
                        if (!doubleFuture.isDone()) {
                            throw new RuntimeException();
                        }

                        System.out.println("a = " + task.a + ", res = " + doubleFuture.get());
                    } catch (InterruptedException | ExecutionException e) {
                        ThreadUtil.logMajorAction("interrupted");
                        return;
                    }
                }
                ThreadUtil.logMajorAction("stopped");
            }, "Producer-" + i);
            producers.add(producer);
        }

        // start producing
        producers.forEach(Thread::start);
        Thread.sleep(RUNTIME_MS);
        producers.forEach(Thread::interrupt);

        executorService.shutdownNow();
        System.out.println("--------------------------------------------------------------------------------");
    }
}
