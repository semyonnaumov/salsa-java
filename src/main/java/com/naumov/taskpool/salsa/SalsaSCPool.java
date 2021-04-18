package com.naumov.taskpool.salsa;

import com.naumov.taskpool.SCPool;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.naumov.taskpool.salsa.RunnableWithId.TAKEN;

public class SalsaSCPool implements SCPool {
    private final int chunkSize;
    private final int scPoolOwnerId;
    private final CopyOnWriteArrayList<SomeSingleWriterMultiReaderList<Node>> chunkLists; // some sync-free on get() structure
    private final ThreadLocal<ProducerContext> pContextThreadLocal = ThreadLocal.withInitial(() -> null);
    private final ThreadLocal<ConsumerContext> cContextThreadLocal = ThreadLocal.withInitial(() -> null);
    private final Queue<Chunk> chunkPool = new ConcurrentLinkedQueue<>(); // M-S queue for spare chunks, initially empty
    private Node currentNode = null; // current node to work with, initially null // todo volatile?

    public SalsaSCPool(int scPoolOwnerId, int chunkSize, int maxPCount) {
        this.scPoolOwnerId = scPoolOwnerId;
        this.chunkSize = chunkSize;
        this.chunkLists = initChunkLists(maxPCount);
    }

    /**
     * Init thread local for the new producer
     */
    void bindProducer(int pId) {
        if (pContextThreadLocal.get() != null) {
            throw new UnsupportedOperationException("Trying to bind producer " + pId + " that is already bound");
        }

        pContextThreadLocal.set(new ProducerContext(pId));
    }

    void bindConsumer(int cId) {
        if (cContextThreadLocal.get() != null) {
            throw new UnsupportedOperationException("Trying to bind consumer " + cId + " that is already bound");
        }

        cContextThreadLocal.set(new ConsumerContext(cId));
    }

    // todo check
    private CopyOnWriteArrayList<SomeSingleWriterMultiReaderList<Node>> initChunkLists(int producersCount) {

        // one-time used template for chunkLists
        List<SomeSingleWriterMultiReaderList<Node>> chunkListsTemplate = new ArrayList<>(producersCount + 1);

        for (int i = 0; i < producersCount; i++) {
            chunkListsTemplate.add(new SomeSingleWriterMultiReaderList<>());
        }

        SomeSingleWriterMultiReaderList<Node> stealList = new SomeSingleWriterMultiReaderList<>();
        chunkListsTemplate.add(stealList);

        return new CopyOnWriteArrayList<>(chunkListsTemplate);
    }

    private void checkProducerRegistration(String from) {
        if (pContextThreadLocal.get() == null) {
            throw new IllegalStateException("Calling " + from + " method with null pContextThreadLocal.");
        }
    }

    private void checkConsumerRegistration(String from) {
        if (cContextThreadLocal.get() == null) {
            throw new IllegalStateException("Calling " + from + " method with null cContextThreadLocal.");
        }
    }

    @Override
    public boolean produce(Runnable task) {
        checkProducerRegistration("produce");
        return insert(new RunnableWithId(task), false); // producing only as RunnableWithId wrapper
    }

    @Override
    public void produceForce(Runnable task) {
        checkProducerRegistration("produceForce");
        insert(new RunnableWithId(task), true); // producing only as RunnableWithId wrapper
    }

    private boolean insert(Runnable task, boolean force) {
        ProducerContext producerContext = pContextThreadLocal.get();

        if (producerContext.getChunk() == null) {
            // allocate new chunk
            if (!getChunk(force)) return false;
        }

        producerContext.getChunk().getTasks()[producerContext.getProdIdx()] = task;
        producerContext.setProdIdx(producerContext.getProdIdx() + 1); // todo do we need atomic increment here?

        if (producerContext.getProdIdx() == chunkSize) {
            // the chunk is full
            producerContext.setChunk(null);
        }

        return true;
    }

    private boolean getChunk(boolean force) {
        ProducerContext producerContext = pContextThreadLocal.get();

        Chunk newChunk = chunkPool.poll();

        // no available chunks in the pool
        if (newChunk == null) {
            if (!force) return false;

            newChunk = new Chunk(chunkSize, scPoolOwnerId);
        }

        Node node = new Node(newChunk);
        chunkLists.get(producerContext.getProducerId()).add(node);
        producerContext.chunk = newChunk;
        producerContext.prodIdx = 0;
        return true;
    }

    @Override
    public Runnable consume() {
        checkConsumerRegistration("consume");
        int consumerId = cContextThreadLocal.get().getConsumerId();

        if (currentNode != null) { // common case
            Runnable task = takeTask(currentNode);
            if (task != null) return task;
        }

        // traverse chunkLists
        for (SomeSingleWriterMultiReaderList<Node> chunkList : chunkLists) {
            for (Node node : chunkList) {
                if (node.getChunk() != null && node.getChunk().getOwner().get() == consumerId) {
                    Runnable task = takeTask(node);
                    if (task != null) {
                        currentNode = node;
                        return task;
                    }
                }
            }
        }

        currentNode = null;
        return null;
    }

    private Runnable takeTask(Node node) {
        int consumerId = cContextThreadLocal.get().getConsumerId();

        Chunk chunk = node.getChunk();
        if (chunk == null) return null; // this chunk has been stolen

        Runnable task = chunk.getTasks()[node.getIdx() + 1];
        if (task == null) return null; // no inserted tasks

        if (chunk.getOwner().get() != consumerId) return null;

        node.setIdx(node.getIdx()); // tell the world you're going to take a task from idx // todo atomicity?
        if (chunk.getOwner().get() == consumerId) { // common case
            chunk.getTasks()[node.getIdx()] = TAKEN;
            checkLast(node);
            return task;
        }

        // the chunk has been stolen, CAS the last task and go away
        boolean success = !TAKEN.equals(task) && Chunk.AVH.compareAndSet(chunk.getTasks(), node.getIdx(), task, TAKEN);

        if (success) checkLast(node);
        currentNode = null;
        return success ? task : null;
    }

    // todo check implementation
    private void checkLast(Node node) {
        if (node.getIdx() + 1 == chunkSize) { // finished the chunk
            Chunk usedChunk = node.getChunk();

            // recycle chunk and return to the chunk pool
            usedChunk.clear();
            chunkPool.add(usedChunk);
            node.setChunk(null);
            currentNode = null;
        }
    }

    @Override
    public Runnable steal(SCPool from) {
        checkConsumerRegistration("consume");
        // todo
        return null;
    }

    @Override
    public boolean isEmpty() {
//        checkConsumerRegistration("consume"); // ??
        // todo implement later
        return false;
    }

    @Override
    public void setIndicator(int consumerId) {
        // todo
    }

    @Override
    public boolean checkIndicator(int consumerId) {
        // todo
        return false;
    }

    private static class ProducerContext {
        private final int producerId;
        private Chunk chunk;
        private int prodIdx;

        public ProducerContext(int producerId) {
            this.chunk = null;
            this.prodIdx = 0;
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

        public int getProdIdx() {
            return prodIdx;
        }

        public void setProdIdx(int prodIdx) {
            this.prodIdx = prodIdx;
        }
    }

    private static class ConsumerContext {
        private final int consumerId;

        private ConsumerContext(int consumerId) {
            this.consumerId = consumerId;
        }

        public int getConsumerId() {
            return consumerId;
        }
    }
}
