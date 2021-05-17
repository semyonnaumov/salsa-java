package com.naumov;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.*;

// $ java -jar target/benchmarks.jar BenchmarkSalsa -f 1
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class BenchmarkSalsa {

    /*
     * Fixtures have different Levels to control when they are about to run.
     * Level.Invocation is useful sometimes to do some per-invocation work,
     * which should not count as payload. PLEASE NOTE the timestamping and
     * synchronization for Level.Invocation helpers might significantly offset
     * the measurement, use with care. See Level.Invocation javadoc for further
     * discussion.
     *
     * Consider this sample:
     */

    /*
     * This state handles the executor.
     * Note we create and shutdown executor with Level.Trial, so
     * it is kept around the same across all iterations.
     */

    @State(Scope.Benchmark)
    public static class ExecutorWrapper {
        @Param({"1", "2"})
        public int parallelism;

        @Param({"SALSA", "FJP"})
        public String type;

        ExecutorService service;

        @Setup(Level.Trial)
        public void up() {
            if ("SALSA".equals(type)) {
                service = MyExecutors.newSalsaThreadPool(10, parallelism);
            } else {
                service = Executors.newWorkStealingPool(parallelism);
            }
        }

        @TearDown(Level.Trial)
        public void down() {
            service.shutdown();
        }
    }

    /*
     * This allows us to formulate the task: measure the task turnaround in
     * "hot" mode when we are not sleeping between the submits, and "cold" mode,
     * when we are sleeping.
     */

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public Object measure(ExecutorWrapper e, final Scratch s) throws InterruptedException {
        return e.service.submit(new Task(s));
    }

    @State(Scope.Thread) // thread-local state
    public static class Scratch {
        private double p;

        public double doWork() {
            p = Math.log(p);
            return p;
        }
    }

    public static class Task implements Callable<Double> {
        private Scratch s;

        public Task(Scratch s) {
            this.s = s;
        }

        @Override
        public Double call() {
            return s.doWork();
        }
    }
}