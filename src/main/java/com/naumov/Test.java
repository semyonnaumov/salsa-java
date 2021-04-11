package com.naumov;

public class Test {

    public static void main(String[] args) {
        Pool pool = new SalsaPool();

        Thread producer1 = new Thread(() -> {
            // do some producing
            while (true) {
                Object item = new Object();
                pool.put(item);
            }
        });

        Thread consumer1 = new Thread(() -> {
            // do some consuming
//            org.openjdk.jmh.infra.Blackhole blackHole = new org.openjdk.jmh.infra.Blackhole(); // todo add jmh
            while (true) {
                Object task = pool.get();
//                blackHole.consume(task);
            }
        });

// правильно стартовать потоки
        consumer1.start();
        producer1.start();
    }
}
