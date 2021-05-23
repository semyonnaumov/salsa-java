package com.naumov.taskpool.salsa;

import com.naumov.taskpool.SCPool;
import com.naumov.taskpool.salsa.annot.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

import static com.naumov.taskpool.salsa.RunnableWithId.TAKEN;
import static com.naumov.taskpool.salsa.VHUtil.RUNNABLE_ARRAY_VH;

public class SalsaSCPool implements SCPool {

    // unshared owner-local state
    private volatile Node currentNode = null; // node for owner to work with

    // unmodifiable shared state
    private final int consumerId;
    private final int chunkSize;
    private final int maxNProducers;
    private final int nConsumers;

    // shared state
    private final CopyOnWriteArrayList<SomeSingleWriterMultiReaderList<Node>> chunkLists; // todo introduce some sync-free on get() thread-safe structure
    private final boolean[] emptyIndicator; // shared among all consumers
    private final Queue<Chunk> chunkPool = new ConcurrentLinkedQueue<>(); // M-S queue for spare chunks, initially empty
                                                                          // shared among owner and producers

    // ThreadLocals
    private final ThreadLocal<ProducerContext> pContextThreadLocal = ThreadLocal.withInitial(() -> null);

    public SalsaSCPool(int consumerId, int chunkSize, int maxNProducers, int nConsumers) {
        this.consumerId = consumerId;
        this.chunkSize = chunkSize;
        this.maxNProducers = maxNProducers;
        this.nConsumers = nConsumers;
        this.chunkLists = initChunkLists(maxNProducers);
        this.emptyIndicator = new boolean[nConsumers];
    }

    // todo check
    private CopyOnWriteArrayList<SomeSingleWriterMultiReaderList<Node>> initChunkLists(int producersCount) {

        // one-time used template for chunkLists
        final List<SomeSingleWriterMultiReaderList<Node>> chunkListsTemplate = new ArrayList<>(producersCount + 1);

        for (int i = 0; i < producersCount; i++) {
            chunkListsTemplate.add(new SomeSingleWriterMultiReaderList<>());
        }

        SomeSingleWriterMultiReaderList<Node> stealList = new SomeSingleWriterMultiReaderList<>();
        chunkListsTemplate.add(stealList);

        return new CopyOnWriteArrayList<>(chunkListsTemplate);
    }

    /**
     * Init ThreadLocal for the new producer.
     */
    @PermitProducers
    void bindProducer(int pId) {
        if (pContextThreadLocal.get() != null) {
            throw new UnsupportedOperationException("Trying to bind producer " + pId + " that is already bound");
        }

        pContextThreadLocal.set(new ProducerContext(pId));
    }

    @Override
    @PermitProducers
    public boolean produce(Runnable task) {
        checkProducerRegistration("produce");
        return insert(new RunnableWithId(task), false); // producing only as RunnableWithId wrapper
    }

    @Override
    @PermitProducers
    public void produceForce(Runnable task) {
        checkProducerRegistration("produceForce");
        insert(new RunnableWithId(task), true); // producing only as RunnableWithId wrapper
    }

    @PermitProducers
    private void checkProducerRegistration(String from) {
        if (pContextThreadLocal.get() == null) {
            throw new IllegalStateException("Calling " + from + " method with null pContextThreadLocal.");
        }
    }

    @PermitProducers
    private boolean insert(Runnable task, boolean force) {
        ProducerContext producerContext = pContextThreadLocal.get();

        if (producerContext.getChunk() == null) {
            // allocate new chunk, put it into producer context
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

    @PermitProducers
    private boolean getChunk(boolean force) {
        ProducerContext producerContext = pContextThreadLocal.get();

        Chunk newChunk = chunkPool.poll();

        // no available chunks in the pool
        if (newChunk == null) {
            if (!force) return false;

            newChunk = new Chunk(chunkSize, consumerId);
        }

        final Node node = new Node(newChunk);
        chunkLists.get(producerContext.getProducerId()).add(node); // add new node to producer's own chunk list
        producerContext.chunk = newChunk;
        producerContext.prodIdx = 0;
        return true;
    }

    @Override
    @PermitOwner
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

    /**
     * Called only by scPool owner
     * @param node some node to retrieve a task from
     * @return retrieved task or {@code null}
     */
    @PermitOwner
    private Runnable takeTask(Node node) {
        Chunk chunk = node.getChunk();
        if (chunk == null) return null; // chunk has been stolen from this node

        Runnable task = chunk.getTasks()[node.getIdx() + 1];
        if (task == null) return null; // no tasks in this chunk

        if (chunk.getOwner().get() != consumerId) return null;

        node.setIdx(node.getIdx() + 1); // tell the world you're going to take a task from idx
                                        // atomicity is not needed since only the owner of the SCPool can update idx
        if (chunk.getOwner().get() == consumerId) { // common case
            int idx = node.getIdx();
            Runnable next = idx + 1 < chunkSize ? chunk.getTasks()[idx + 1] : null; // get next task for isEmpty()
            chunk.getTasks()[idx] = TAKEN;
            checkLast(node, next);
            return task;
        }

        // the chunk has been stolen, CAS the last task and go away
        int idx = node.getIdx();
        Runnable next = idx + 1 < chunkSize ? chunk.getTasks()[idx + 1] : null; // get next task for isEmpty()
        boolean success = !TAKEN.equals(task) && RUNNABLE_ARRAY_VH.compareAndSet(chunk.getTasks(), idx, task, TAKEN);

        if (success) checkLast(node, next);
        currentNode = null;
        return success ? task : null;
    }

    /**
     * Only owner of the current pool can call this method.
     * @param node node to check for being completely used up
     * @param runnable task to
     */
    @PermitOwner
    private void checkLast(Node node, Runnable runnable) {
        if (node.getIdx() + 1 == chunkSize) { // finished the chunk
            Chunk usedChunk = node.getChunk();

            // recycle chunk and return to the chunk pool
            usedChunk.clear(); // todo check this
            chunkPool.add(usedChunk);
            node.setChunk(null);
            currentNode = null;
            clearIndicator();
        }

        if (runnable == null) clearIndicator();
    }

    /**
     * Called by pool owner to steal a task (and a chunk, holding it) from another consumer
     *
     * @param otherSCPool other's consumer pool
     * @return stolen task or {@code null}
     */
    @Override
    @PermitOwner
    public Runnable steal(SCPool otherSCPool) {

        Node prevNode = getNode((SalsaSCPool) otherSCPool);
        if (prevNode == null) return null; // no chunks found

        Chunk chunk = prevNode.getChunk();
        if (chunk == null) return null;

        int prevIdx = prevNode.getIdx();
        if (prevIdx + 1 == chunkSize || chunk.getTasks()[prevIdx + 1] == null) return null;

        SomeSingleWriterMultiReaderList<Node> myStealList = chunkLists.get(maxNProducers);

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
     * Called to steal a chunk from the scPool, other than that of current consumer thread.
     * todo: This method to be subjected to performance tuning via best traversal search
     *
     * @param otherSCPool other consumer's {@link SalsaSCPool}
     * @return found node
     */
    @PermitOwner
    private Node getNode(SalsaSCPool otherSCPool) {
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
     * Used to search for a node with possibly not empty {@link Chunk}.
     * For using only in {@link SalsaSCPool#getNode(SalsaSCPool)}.
     *
     * @param otherSCPool other consumer's {@link SalsaSCPool}
     * @param i chunk list index to look for a chunk
     */
    @PermitProducers
    private Node scanChunkListAtIndex(SalsaSCPool otherSCPool, int i) {
        SomeSingleWriterMultiReaderList<Node> nodes = otherSCPool.chunkLists.get(i);
        if (!nodes.isEmpty()) { // todo consider performance issues
            // make a snapshot to iterate through
            CopyOnWriteArrayList<Node> snapshot = new CopyOnWriteArrayList<>(nodes);
            for (Node node : snapshot) {
                Chunk chunk = node.getChunk();
                if (chunk != null && node.getIdx() + 1 != chunkSize) return node; // found chunk possibly with tasks
            }
        }

        return null;
    }

    @Override
    @PermitAll
    public boolean isEmpty() {
        for (SomeSingleWriterMultiReaderList<Node> chunkList : chunkLists) {
            for (Node node : chunkList) {
                if (node.getChunk() == null) continue;
                int idx = node.getIdx();
                for (int i = idx + 1; i < chunkSize; i++) {
                    Runnable task = node.getChunk().getTasks()[i];
                    if (task != null && !TAKEN.equals(task)) return false;
                }
            }
        }
        return true;
    }

    @Override
    @PermitAll
    public void setIndicator(int consumerId) {
        VHUtil.BOOLEAN_ARRAY_VH.compareAndSet(this.emptyIndicator, consumerId, false, true);
    }

    @Override
    @PermitAll
    public boolean checkIndicator(int consumerId) {
        return this.emptyIndicator[consumerId]; // todo need atomic/volatile?
    }

    @PermitOwner
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
