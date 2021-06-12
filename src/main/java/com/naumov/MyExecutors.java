package com.naumov;

import com.naumov.taskpool.ms.MSQTaskPool;
import com.naumov.taskpool.salsa.SalsaTaskPool;

import java.util.concurrent.*;

public final class MyExecutors {
    private MyExecutors() {
    }

    public static ExecutorService newSalsaThreadPool(int maxNProducers, int nConsumers) {
        return new TaskPoolExecutor(new SalsaTaskPool(maxNProducers, nConsumers, 1000), nConsumers);
    }

    // for performance comparison
    public static ExecutorService newMichealScottThreadPool(int maxNProducers, int nConsumers) {
        return new TaskPoolExecutor(new MSQTaskPool(maxNProducers, nConsumers, 1000), nConsumers);
    }
}
