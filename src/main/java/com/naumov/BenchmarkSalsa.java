package com.naumov;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.concurrent.*;

// $ java -jar target/benchmarks.jar BenchmarkSalsa -w 15s -wi 5 -r 15s -i 10 -t 8 -si true -f 1
// -w = warmup time
// -wi = warmup iterations
// -r = measurement time
// -i = measurement iterations
// -t = threads
// -si = synchronize iterations
// -f = JVM forks
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class BenchmarkSalsa {

    // only for runs from IDE
    private static final int nProducers = 8;

    @State(Scope.Benchmark)
    public static class ExecutorWrapper {
        @Param({"SALSA", "FJP", "TPE"})
        public String type;

        @Param({"1", "2", "4", "6", "8"})
        public int nConsumers;

        ExecutorService service;

        @Setup(Level.Trial)
        public void up() {
            switch (type) {
                case "SALSA":
                    service = MyExecutors.newSalsaThreadPool(nProducers, nConsumers, 100, 1, 0);
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

//    @Benchmark
//    @BenchmarkMode(Mode.Throughput) // submission throughput
//    public Future<Double> submit(ExecutorWrapper e, final Scratch s) throws InterruptedException {
//        return e.service.submit(new Task(s));
//    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput) // throughput
    public double submitAndGetResult(ExecutorWrapper e, final Scratch s) throws InterruptedException, ExecutionException {
        return e.service.submit(new Task(s)).get();
    }

    @State(Scope.Thread) // other benchmark threads can't see this object (although consumers threads can)
    public static class Scratch {
        public double doWork() {
            double p = ThreadLocalRandom.current().nextDouble(1.21232342, 13257687.3234234);
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
                .warmupIterations(5)
                .measurementIterations(10)
                .warmupTime(TimeValue.seconds(15))
                .measurementTime(TimeValue.seconds(15))
                .threads(nProducers)
                .syncIterations(true)
                .forks(1)
                .build();

        new Runner(opt).run();
    }
}