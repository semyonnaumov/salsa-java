package com.naumov;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.*;

// $ java -jar target/benchmarks.jar BenchmarkSalsa -w 1s -r 1s -f 1 -si true
// -w = warmup time
// -r = measurement time
// -f = JVM forks
// -t = threads
// -si = synchronize iterations
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class BenchmarkSalsa {

    @State(Scope.Benchmark)
    public static class ExecutorWrapper {
//        @Param({"1", "2", "4", "6", "8", "10", "12", "14", "16"})
        @Param({"1", "2", "4"})
        public int parallelism;

        @Param({"SALSA", "FJP", "TPE"})
        public String type;

        ExecutorService service;

        @Setup(Level.Trial)
        public void up() {
            if ("SALSA".equals(type)) {
                service = MyExecutors.newSalsaThreadPool(10, parallelism);
            } else if ("FJP".equals(type)) {
                service = Executors.newWorkStealingPool(parallelism);
            } else {
                service = Executors.newFixedThreadPool(parallelism);
            }
        }

        @TearDown(Level.Trial)
        public void down() {
            service.shutdown();
        }
    }


    @Benchmark
    @Group("producers")
    @GroupThreads(1) // 1 producer for start
    @BenchmarkMode(Mode.Throughput)
    public double measure(ExecutorWrapper e, final Scratch s) throws InterruptedException, ExecutionException {
        return e.service.submit(new Task(s)).get();
    }

    @State(Scope.Thread) // other benchmark threads can't see this object (although consumers threads can)
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