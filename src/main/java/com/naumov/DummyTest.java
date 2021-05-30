package com.naumov;

import java.util.concurrent.ExecutorService;

public class DummyTest {
    public static void main(String[] args) {
        final int NUMBER_OF_PRODUCERS = 4;
        final int NUMBER_OF_CONSUMERS = 4;

        ExecutorService executorService = MyExecutors.newSalsaThreadPool(NUMBER_OF_PRODUCERS, NUMBER_OF_CONSUMERS);
        // since here executorService is fully initialized

        executorService.shutdown();
    }
}
