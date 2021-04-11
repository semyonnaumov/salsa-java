package com.naumov;

import com.naumov.scpool.SCPool;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SalsaPool implements Pool {
    private static final int DEFAULT_CHUNK_SIZE = 1000;
    private final int chunkSize;
    /**
     * Первоначальная идея - взаимно исключить операции инициализации потока
     */
    private final Lock newActorLock = new ReentrantLock(); // to put mutex on
    /**
     * Пока что продьюсеры и консьюмеры могут только добавляться, достаточно счетчиков
     */
    private final AtomicInteger producerCounter = new AtomicInteger(0);
    private final AtomicInteger consumerCounter = new AtomicInteger(0);

    private final ThreadLocal<ActorData> actorDataThreadLocal = ThreadLocal.withInitial(() -> null);

    /**
     * Used only when calling {@link SalsaPool#isEmpty()}
     * todo Maybe use it when initializing access lists ???
     */
    private final CopyOnWriteArrayList<SCPool> allSCPools = new CopyOnWriteArrayList<>();

    public SalsaPool() {
        this(DEFAULT_CHUNK_SIZE);
    }

    public SalsaPool(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    @Override
    public void put(Object item) {
        // actor checks and registration stuff
        ActorData actorData = getOrCreateActorData(true, producerCounter);
        ActorData.ProducerContext producerContext = actorData.getProducerContext();

        // produce to the pool by the order of the access lists
        for (SCPool scPool: actorData.getAccessList()) {
            if (scPool.produce(item)) return;
        }

        // if all pools are full, expand the closest pool
        SCPool firstSCPool = actorData.getAccessList().get(0);
        firstSCPool.produceForce(item);
    }

    @Override
    public Object get() {
        // actor checks and registration stuff
        ActorData actorData = getOrCreateActorData(false, consumerCounter);
        ActorData.ConsumerContext consumerContext = actorData.getConsumerContext();
        // todo finish with registration part

        while (true) {
            SCPool myPool = consumerContext.getMyPool();

            // first try to get an item from a local pool
            Object item = myPool.consume();
            if (item != null) return item;

            // failed to get an item from a local pool - steal
            for (SCPool otherSCPool : actorData.getAccessList()) {
                item = myPool.steal(otherSCPool);
                if (item != null) return item;
            }

            // no tasks found - validate emptiness
            if (isEmpty()) return null;
        }
    }

    @Override
    public boolean isEmpty() {
        return false; // todo implement later
    }


    /**
     * Allows to sustain actor's (producer/consumer) thread-local context.
     * Implements thread registration as producer/consumer, checks roles conformity.
     *
     * @param isProducer register or check the thread is producer/consumer
     * @return thread-local context
     */
    // todo sync!
    private ActorData getOrCreateActorData(boolean isProducer, AtomicInteger idCounter) {
        ActorData actorData = actorDataThreadLocal.get();

        if (actorData == null) {
            // register new thread
            ActorData newActorData = new ActorData(isProducer, idCounter.getAndIncrement());
            actorDataThreadLocal.set(newActorData);
            actorData = newActorData;

            // todo add propagating this actor to all accessLists if consumer or all SCPools if producer
        } else if (!actorData.isProducer() == isProducer) {
            throw new UnsupportedOperationException("Same thread acts as producer and consumer. Current version of "
                    + SalsaPool.class.getSimpleName() + " doesn't support such behaviour.");
        }

        return actorData;
    }
}
