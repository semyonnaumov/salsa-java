package com.naumov;

import com.naumov.taskpool.TaskPool;
import com.naumov.taskpool.salsa.SalsaTaskPool;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public final class ChecksumTest {

    private static final ExecutorService pool = Executors.newCachedThreadPool();
    private final AtomicInteger putSum = new AtomicInteger(0);
    private final AtomicInteger getSum = new AtomicInteger(0);
    private final CyclicBarrier barrier;
    private final TaskPool taskPool;
    private final int nTrials, nPairs;

    // sometimes livelocks since stealing is allowed only from lists, other than chunkLists[steal]
    public static void main(String[] args) {
        new ChecksumTest(32, 100000).test();
        pool.shutdown();
    }

    ChecksumTest(int nPairs, int nTrials) {
        this.taskPool = new SalsaTaskPool(nPairs, nPairs, 100, 1);
        this.nTrials = nTrials;
        this.nPairs = nPairs;
        this.barrier = new CyclicBarrier(nPairs * 2 + 1);
    }

    void test() {
        try {
            for (int i = 0; i < nPairs; i++) {
                pool.execute(new Producer());
                pool.execute(new Consumer());
            }
            barrier.await(); // ждать, когда все потоки будут готовы
            barrier.await(); // ждать, когда все потоки завершатся
            assertEquals(putSum.get(), getSum.get());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static int xorShift(int x) {
        x ^= (x << 6);
        x ^= (x >>> 21);
        x ^= (x << 7);
        return x;
    }

    class Producer implements Runnable {
        public void run() {
            try {
                int seed = (this.hashCode() ^ (int)System.nanoTime());
                int sum = 0;
                barrier.await();
                for (int i = nTrials; i > 0; --i) {
                    taskPool.put(new StampedRunnable(seed));
                    sum += seed;
                    seed = xorShift(seed);
                }
                putSum.getAndAdd(sum);
                barrier.await();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    class Consumer implements Runnable {
        public void run() {
            try {
                barrier.await();
                int sum = 0;
                int caughtItems = 0;
                while (caughtItems < nTrials && !Thread.currentThread().isInterrupted()) {
                    StampedRunnable stampedRunnable = (StampedRunnable) taskPool.get();
                    if (stampedRunnable != null) {
                        sum += stampedRunnable.stamp;
                        caughtItems++;
                    }
                }
                getSum.getAndAdd(sum);
                barrier.await();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class StampedRunnable implements Runnable {
        private final int stamp;

        public StampedRunnable(int stamp) {
            this.stamp = stamp;
        }

        @Override
        public void run() {
            // do nothing
        }
    }
}
