package com.naumov;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

public class NaiveTest {

    public static void main(String[] args) {
        final int NUMBER_OF_PRODUCERS = 2;
        final int NUMBER_OF_CONSUMERS = 2;

//        ExecutorService executorService = Executors.newWorkStealingPool(NUMBER_OF_CONSUMERS);
//        ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_CONSUMERS);
        ExecutorService executorService = MyExecutors.newSalsaThreadPool(NUMBER_OF_PRODUCERS, NUMBER_OF_CONSUMERS);

        Runnable standardTask = () -> {
            int a = ThreadLocalRandom.current().nextInt(1000);
            for (int i = 0; i < 1000; i++) {
                a += i;
            }
            ThreadUtil.logAction("exec task, res = " + a);
        };

        // init producers
        List<Thread> producers = new ArrayList<>();
        for (int i = 0; i < NUMBER_OF_PRODUCERS; i++) {
            Thread producer = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    ThreadUtil.logAction("submit task");
                    executorService.submit(standardTask);
                }
            }, "Producer-" + i);
            producers.add(producer);
        }

        // start producing
        producers.forEach(Thread::start);
    }
}
