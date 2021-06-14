package com.naumov.taskpool.salsa;

import com.naumov.taskpool.AbstractTaskPool;
import com.naumov.taskpool.InitializationException;
import com.naumov.taskpool.SCPool;

public class SalsaTaskPool extends AbstractTaskPool {

    public SalsaTaskPool(int nProducers, int nConsumers, int chunkSize) {
        super(nProducers, nConsumers, chunkSize);
    }

    @Override
    protected SCPool newSCPool(int consumerId, int chunkSize, int nProducers, int nConsumers) {
        return new SalsaSCPool(consumerId, chunkSize, nProducers, nConsumers);
    }

    @Override
    protected void registerCurrentThreadAsProducer(SCPool scPool, int producerId) {
        SalsaSCPool salsaSCPool;
        try {
            salsaSCPool = (SalsaSCPool) scPool;
        } catch (ClassCastException ex) {
            throw new InitializationException("Couldn't register producer on SCPool since it's not an instance of" +
                    SalsaSCPool.class.getSimpleName());
        }
        salsaSCPool.registerCurrentThreadAsProducer(producerId);
    }

    @Override
    protected void registerCurrentThreadAsOwner(SCPool scPool, int consumerId) {
        SalsaSCPool salsaSCPool;
        try {
            salsaSCPool = (SalsaSCPool) scPool;
        } catch (ClassCastException ex) {
            throw new InitializationException("Couldn't register producer on SCPool since it's not an instance of" +
                    SalsaSCPool.class.getSimpleName());
        }
        salsaSCPool.registerCurrentThreadAsOwner();
    }
}
