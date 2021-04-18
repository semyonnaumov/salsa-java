package com.naumov;

import com.naumov.taskpool.ms.MichaelScottQueueTaskPool;
import com.naumov.taskpool.salsa.SalsaTaskPool;

import java.util.concurrent.*;

public final class MyExecutors {
    private MyExecutors() {
    }

    public static ExecutorService newSalsaThreadPool() {
        return newSalsaThreadPool(16, 16);
    }

    public static ExecutorService newSalsaThreadPool(int maxNProducers, int nConsumers) {
        return new TaskPoolExecutor(new SalsaTaskPool(maxNProducers, nConsumers, 100), nConsumers);
    }

    public static ExecutorService newMichealScottThreadPool() {
        return newMichealScottThreadPool(16, 16);
    }

    public static ExecutorService newMichealScottThreadPool(int maxNProducers, int nConsumers) {
        return new TaskPoolExecutor(new MichaelScottQueueTaskPool(maxNProducers, nConsumers), nConsumers);
    }
}
