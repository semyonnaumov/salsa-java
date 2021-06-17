package com.naumov;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.concurrent.*;

// $ java -jar target/benchmarks.jar BenchmarkSalsa -w 10s -r 10s -f 1 -si true
// -w = warmup time
// -r = run (measurement) time
// -f = JVM forks
// -t = threads
// -si = synchronize iterations
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class BenchmarkSalsa {

    private static final int nProducers = 2;

    @State(Scope.Benchmark)
    public static class ExecutorWrapper {
        @Param({"1", "2", "4", "6", "8"})
        public int nConsumers;

        @Param({"SALSA", "MSQ", "FJP", "TPE"})
//        @Param({"SALSA"})
        public String type;

        ExecutorService service;

        @Setup(Level.Trial)
        public void up() {
            switch (type) {
                case "SALSA":
                    service = MyExecutors.newSalsaThreadPool(nProducers, nConsumers);
                    break;
                case "MSQ":
                    service = MyExecutors.newMichealScottThreadPool(nProducers, nConsumers);
                    break;
                case "FJP":
                    service = Executors.newWorkStealingPool(nConsumers);
                    break;
                case "TPE":
                    service = Executors.newFixedThreadPool(nConsumers);
                    break;
            }
        }

        @TearDown(Level.Trial)
        public void down() {
            service.shutdown();
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput) // submission throughput
    public Future<Double> submit(ExecutorWrapper e, final Scratch s) throws InterruptedException, ExecutionException {
        return e.service.submit(new Task(s));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput) // throughput
    public double submitAndGetResult(ExecutorWrapper e, final Scratch s) throws InterruptedException, ExecutionException {
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

    // to run from IDEA
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(BenchmarkSalsa.class.getSimpleName())
                .warmupTime(TimeValue.seconds(1))
                .measurementTime(TimeValue.seconds(1))
                .threads(nProducers)
                .syncIterations(true)
                .forks(1)
                .build();

        new Runner(opt).run();
    }
}