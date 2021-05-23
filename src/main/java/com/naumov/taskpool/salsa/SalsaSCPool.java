package com.naumov.taskpool.salsa;

import com.naumov.taskpool.SCPool;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

import static com.naumov.taskpool.salsa.RunnableWithId.TAKEN;
import static com.naumov.taskpool.salsa.VHUtil.RUNNABLE_ARRAY_VH;

public class SalsaSCPool implements SCPool {
    private final int consumerId;
    private final int chunkSize;
    private final int maxNProducers;
    private final int nConsumers;
    private final CopyOnWriteArrayList<SomeSingleWriterMultiReaderList<Node>> chunkLists; // some sync-free on get() structure
    private final ThreadLocal<ProducerContext> pContextThreadLocal = ThreadLocal.withInitial(() -> null);
    private final Queue<Chunk> chunkPool = new ConcurrentLinkedQueue<>(); // M-S queue for spare chunks, initially empty
    private volatile Node currentNode = null; // current node to work with, initially null // todo volatile?
    private final boolean[] emptyIndicator;

    public SalsaSCPool(int consumerId, int chunkSize, int maxNProducers, int nConsumers) {
        this.consumerId = consumerId;
        this.chunkSize = chunkSize;
        this.maxNProducers = maxNProducers;
        this.nConsumers = nConsumers;
        this.emptyIndicator = new boolean[nConsumers];
        this.chunkLists = initChunkLists(maxNProducers);
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

            newChunk = new Chunk(chunkSize, consumerId);
        }

        Node node = new Node(newChunk);
        chunkLists.get(producerContext.getProducerId()).add(node);
        producerContext.chunk = newChunk;
        producerContext.prodIdx = 0;
        return true;
    }

    @Override
    public Runnable consume() {
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
        Chunk chunk = node.getChunk();
        if (chunk == null) return null; // this chunk has been stolen

        Runnable task = chunk.getTasks()[node.getIdx() + 1];
        if (task == null) return null; // no inserted tasks

        if (chunk.getOwner().get() != consumerId) return null;

        node.setIdx(node.getIdx() + 1); // tell the world you're going to take a task from idx // todo atomicity?
        if (chunk.getOwner().get() == consumerId) { // common case
            int currentIndex = node.getIdx();
            Runnable next = currentIndex + 1 < chunkSize ? chunk.getTasks()[currentIndex + 1] : null; // for isEmpty()
            chunk.getTasks()[currentIndex] = TAKEN;
            checkLast(node, next);
            return task;
        }

        // the chunk has been stolen, CAS the last task and go away
        int currentIndex = node.getIdx();
        Runnable next = currentIndex + 1 < chunkSize ? chunk.getTasks()[currentIndex + 1] : null; // for isEmpty()
        boolean success = !TAKEN.equals(task) && RUNNABLE_ARRAY_VH.compareAndSet(chunk.getTasks(), currentIndex, task, TAKEN);

        if (success) checkLast(node, next);
        currentNode = null;
        return success ? task : null;
    }

    private void checkLast(Node node, Runnable next) {
        if (node.getIdx() + 1 == chunkSize) { // finished the chunk
            Chunk usedChunk = node.getChunk();

            // recycle chunk and return to the chunk pool
            usedChunk.clear();
            chunkPool.add(usedChunk);
            node.setChunk(null);
            currentNode = null;
            clearIndicator();
        }

        if (next == null) clearIndicator();
    }

    /**
     * Called by pool owner to steal a task (and a chunk, holding it) from another consumer
     *
     * @param otherSCPool other's consumer pool
     * @return stolen task or {@code null}
     */
    @Override
    public Runnable steal(SCPool otherSCPool) {

        Node prevNode = getNode(otherSCPool);
        if (prevNode == null) return null; // no chunks found

        Chunk chunk = prevNode.getChunk();
        if (chunk == null) return null;

        int prevIdx = prevNode.getIdx();
        if (prevIdx + 1 == chunkSize || chunk.getTasks()[prevIdx + 1] == null) return null;

        SomeSingleWriterMultiReaderList<Node> myStealList = chunkLists.get(maxNProducers + 1);

        myStealList.add(prevNode); // make it stealable from my list
        if (!chunk.getOwner().compareAndSet(((SalsaSCPool) otherSCPool).consumerId, consumerId)) {
            myStealList.remove(prevNode); // failed to steal, remove last
            return null;
        }

        clearIndicator(); // for isEmpty()

        int idx = prevNode.getIdx();
        if (idx + 1 == chunkSize) { // chunk is empty
            myStealList.remove(prevNode);
            return null;
        }

        Runnable task = chunk.getTasks()[idx + 1];
        if (task != null) { // found the task
            if (chunk.getOwner().get() != consumerId && idx != prevIdx) {
                myStealList.remove(prevNode);
                return null;
            }
            idx++;
        }

        Node newNode = null; // make snapshot copy
        try {
            newNode = prevNode.clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        newNode.setIdx(idx);

        myStealList.remove(prevNode);
        myStealList.add(newNode);

        prevNode.setChunk(null); // remove chunk from consumer's list

        // done stealing chunk, take one task from it
        if (task == null) return null; // still no task at idx
        Runnable next = idx + 1 < chunkSize ? chunk.getTasks()[idx + 1] : null; // for isEmpty()
        if (TAKEN.equals(task) || !RUNNABLE_ARRAY_VH.compareAndSet(chunk.getTasks(), idx, task, TAKEN)) task = null;

        checkLast(newNode, next);

        if (chunk.getOwner().get() == consumerId) currentNode = newNode;
        return task;
    }

    /**
     * Called to teal a chunk from the scPool, other than that of current consumer thread.
     * todo: This method to be subjected to performance tuning via best traversal search
     *
     * @param otherSCPool other consumer's scPool
     * @return found node
     */
    private Node getNode(SCPool otherSCPool) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int startId = random.nextInt(maxNProducers + 1);

        // traverse all entries from a random start circularly to find not empty node
        for (int i = startId; i <= maxNProducers; i++) {
            Node res = scanChunkListAtIndex(otherSCPool, i);
            if (res != null) return res;
        }

        for (int i = 0; i < startId; i++) {
            Node res = scanChunkListAtIndex(otherSCPool, i);
            if (res != null) return res;
        }

        return null;
    }

    /**
     * For using only in {@link SalsaSCPool#getNode(SCPool)}
     */
    private Node scanChunkListAtIndex(SCPool scPool, int i) {
        SomeSingleWriterMultiReaderList<Node> nodes = ((SalsaSCPool) scPool).chunkLists.get(i);
        if (!nodes.isEmpty()) {
            // make a snapshot to iterate through
            CopyOnWriteArrayList<Node> snapshot = new CopyOnWriteArrayList<>(nodes);
            for (Node node : snapshot) {
                Chunk chunk = node.getChunk();
                if (node.getIdx() != -1 && node.getIdx() + 1 != chunkSize && chunk != null) return node; // found not empty chunk
            }
        }

        return null;
    }

    @Override
    public boolean isEmpty() {
        for (SomeSingleWriterMultiReaderList<Node> chunkList : chunkLists) {
            for (Node node : chunkList) {
                int idx = node.getIdx();
                for (int i = idx + 1; i < chunkSize; i++) {
                    Runnable task = node.getChunk().getTasks()[i];
                    if (task != TAKEN && task != null) return false;
                }
            }
        }
        return true;
    }

    @Override
    public void setIndicator(int consumerId) {
        VHUtil.BOOLEAN_ARRAY_VH.compareAndSet(this.emptyIndicator, consumerId, false, true);
    }

    @Override
    public boolean checkIndicator(int consumerId) {
        return this.emptyIndicator[consumerId]; // todo need atomic/volatile?
    }

    private void clearIndicator() {
        for (int i = 0; i < nConsumers; i++) {
            this.emptyIndicator[i] = false; // todo need volatile?
        }
    }

    private static class ProducerContext {
        private final int producerId;
        private Chunk chunk;
        private int prodIdx;

        public ProducerContext(int producerId) {
            this.producerId = producerId;
            this.chunk = null;
            this.prodIdx = 0;
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
}
