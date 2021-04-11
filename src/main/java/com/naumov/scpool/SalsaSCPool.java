package com.naumov.scpool;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class SalsaSCPool implements SCPool {
    private final ThreadLocal<>

    /**
     *  Initialized in constructor at first arrival of a consuming thread
     */
    private final int consumerId;

    /**
     * CopyOnWriteArrayList since we neew sunc-free get() operation,
     * and writes occur very rarely (new producer registration)
     */
    private final CopyOnWriteArrayList<SomeSingleWriterMultiReaderList<Node>> chunkLists;

    /**
     * Micheal-Scott queue of spare chunks, initially empty
     */
    private final Queue<Node> chunkPool = new ConcurrentLinkedQueue<>();
    private Node currentNode = null;

    // todo add locking since new producer ?
    public SalsaSCPool(int consumerId, AtomicInteger producersCount) {
        this.consumerId = consumerId;
        this.chunkLists = initChunkLists(producersCount.get());
    }

    private CopyOnWriteArrayList<SomeSingleWriterMultiReaderList<Node>> initChunkLists(int producersCount) {
        SomeSingleWriterMultiReaderList<Node> stealList = new SomeSingleWriterMultiReaderList<>();

        // one-time used template for chunkLists
        List<SomeSingleWriterMultiReaderList<Node>> chunkListsTemplate = new ArrayList<>(producersCount + 1);
        chunkListsTemplate.add(stealList);

        for (int i = 0; i < producersCount; i++) {
            chunkListsTemplate.add(new SomeSingleWriterMultiReaderList<>());
        }

        return new CopyOnWriteArrayList<>(chunkListsTemplate);
    }

    // todo sync???
    private void addNewProducer(int producerId) {
        // todo add producer entry to chunkLists
    }

    @Override
    public boolean produce(Object item) {
        return false;
    }

    @Override
    public void produceForce(Object item) {

    }

    @Override
    public Object consume() {
        return null;
    }

    @Override
    public Object steal(SCPool from) {
        return null;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public void setIndicator(int consumerId) {

    }

    @Override
    public boolean checkIndicator(int consumerId) {
        return false;
    }

    private static class Node {

    }
}
