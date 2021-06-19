package com.naumov;

import com.naumov.taskpool.ms.MSQTaskPool;
import com.naumov.taskpool.salsa.SalsaTaskPool;

import java.util.concurrent.*;

public final class MyExecutors {
    private MyExecutors() {
    }

    public static ExecutorService newSalsaThreadPool(int nProducers,
                                                     int nConsumers,
                                                     int chunkSize,
                                                     int cleanupCycles,
                                                     int backoffStartTimeout) {
        return new TaskPoolExecutor(new SalsaTaskPool(nProducers, nConsumers, chunkSize, cleanupCycles), nConsumers, backoffStartTimeout);
    }

    // for performance comparison
    public static ExecutorService newMichealScottThreadPool(int maxNProducers, int nConsumers) {
        return new TaskPoolExecutor(new MSQTaskPool(maxNProducers, nConsumers), nConsumers, 0);
    }
}
