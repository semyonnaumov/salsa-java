package com.naumov;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class Test {

    public static void main(String[] args) {
        final int NUMBER_OF_PRODUCERS = 2;
        final int NUMBER_OF_CONSUMERS = 2;

        ExecutorService executorService = MyExecutors.newSalsaThreadPool(NUMBER_OF_PRODUCERS, NUMBER_OF_CONSUMERS);
        // since here executorService is fully initialized

        Runnable standardTask = () -> {
            for (int i = 0; i < 1000; i++) {
                System.out.printf("Task, worker = %s, iter = %i\n" + i, Thread.currentThread().getName(), i);
            }
        };

        // init producers
        List<Thread> producers = new ArrayList<>();
        for (int i = 0; i < NUMBER_OF_PRODUCERS; i++) {
            Thread producer = new Thread(() -> {
                while (true) {
                    executorService.submit(standardTask);
                }
            });
            producers.add(producer);
        }

        // start producing
        producers.forEach(Thread::start);
    }
}
