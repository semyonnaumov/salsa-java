package com.naumov.taskpool.ms;

import com.naumov.taskpool.AbstractTaskPool;
import com.naumov.taskpool.SCPool;

public class MSQTaskPool extends AbstractTaskPool {

    public MSQTaskPool(int nProducers, int nConsumers, int chunkSize) {
        super(nProducers, nConsumers, chunkSize);
    }

    @Override
    protected SCPool newSCPool(int consumerId, int chunkSize, int nProducers, int nConsumers) {
        return new MSQSCPool(nConsumers);
    }

    @Override
    protected void registerProducerOnSCPool(SCPool scPool, int producerId) {
        // intentionally left blank
    }

    @Override
    protected void registerOwnerOnSCPool(SCPool scPool, int consumerId) {
        // intentionally left blank
    }
}
