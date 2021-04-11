package com.naumov;

import com.naumov.scpool.Chunk;
import com.naumov.scpool.SCPool;
import com.naumov.scpool.SalsaSCPool;

import java.util.concurrent.CopyOnWriteArrayList;

public final class ActorData {
    private final boolean isProducer;
    private final CopyOnWriteArrayList<SCPool> accessList = new CopyOnWriteArrayList<>(); // first naive implementation
    private final ProducerContext producerContext;
    private final ConsumerContext consumerContext;

    public ActorData(boolean isProducer, int id) {
        this.isProducer = isProducer;
        this.producerContext = initProducer(id);
        this.consumerContext = initConsumer(id);
        // todo init accessList
    }

    private ProducerContext initProducer(int producerId) {
        return new ProducerContext(producerId);
    }

    private ConsumerContext initConsumer(int consumerId) {
        return new ConsumerContext(consumerId, )
    }

    class ProducerContext {
        private final int producerId;
        private Chunk chunk; // initially null
        private int idx; // initially 0

        public ProducerContext(int producerId) {
            this.producerId = producerId;
        }

        public int getProducerId() {
            return producerId;
        }

        public Chunk getChunk() {
            return chunk;
        }

        public void setChunk(Chunk chunk) {
            this.chunk = chunk;
        }

        public int getIdx() {
            return idx;
        }

        public void setIdx(int idx) {
            this.idx = idx;
        }
    }

    class ConsumerContext {
        private final int consumerId;
        private final SCPool myPool;

        public ConsumerContext(int consumerId, CopyOnWriteArrayList<Integer> producerIds) {
            this.consumerId = consumerId;
            this.myPool = new SalsaSCPool(consumerId, producerIds);
        }

        public int getConsumerId() {
            return consumerId;
        }

        public SCPool getMyPool() {
            return myPool;
        }
    }

    public boolean isProducer() {
        return isProducer;
    }

    public CopyOnWriteArrayList<SCPool> getAccessList() {
        return accessList;
    }

    public CopyOnWriteArrayList<SCPool> updateAccessList(SalsaSCPool scPool) {
        return accessList; // todo
    }

    public ProducerContext getProducerContext() {
        return producerContext;
    }

    public ConsumerContext getConsumerContext() {
        return consumerContext;
    }
}
