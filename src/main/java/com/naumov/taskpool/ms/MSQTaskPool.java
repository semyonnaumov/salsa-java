package com.naumov.taskpool.ms;

import com.naumov.taskpool.AbstractTaskPool;
import com.naumov.taskpool.SCPool;

public class MSQTaskPool extends AbstractTaskPool {

    public MSQTaskPool(int nProducers, int nConsumers) {
        super(nProducers, nConsumers, 0, 0);
    }

    @Override
    protected SCPool newSCPool(int consumerId, int nProducers, int nConsumers, int chunkSize, int cleanupCycles) {
        return new MSQSCPool(nConsumers);
    }

    @Override
    protected void regCurrentThreadAsProducer(SCPool scPool, int producerId) {
        // intentionally left blank
    }

    @Override
    protected void regCurrentThreadAsOwner(SCPool scPool, int consumerId) {
        // intentionally left blank
    }
}
