package com.naumov.taskpool.salsa;

import com.naumov.taskpool.SCPool;
import com.naumov.taskpool.salsa.annot.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicIntegerArray;

public class SalsaSCPool implements SCPool {

    // unmodifiable shared state
    private final int consumerId;
    private final int chunkSize;
    private final int nProducers;
    private final int nConsumers;

    // shared state
    private final CopyOnWriteArrayList<SWMRList<Node>> chunkLists; // shared among all actors
    private final AtomicIntegerArray emptyIndicators; // shared among all consumers
    private final Queue<Chunk> chunkPool = new ConcurrentLinkedQueue<>(); // M-S queue for spare chunks,
                                                                          // shared among owner and producers

    // ThreadLocals
    private final ThreadLocal<ProducerContext> pContextThreadLocal = ThreadLocal.withInitial(() -> null);
    private final ThreadLocal<OwnerContext> ownerContextThreadLocal = ThreadLocal.withInitial(() -> null);

    public SalsaSCPool(int consumerId, int chunkSize, int nProducers, int nConsumers) {
        this.consumerId = consumerId;
        this.chunkSize = chunkSize;
        this.nProducers = nProducers;
        this.nConsumers = nConsumers;
        this.chunkLists = initChunkLists(nProducers);
        this.emptyIndicators = new AtomicIntegerArray(nConsumers);
    }

    // todo check
    private CopyOnWriteArrayList<SWMRList<Node>> initChunkLists(int producersCount) {

        // one-time used template for chunkLists
        final List<SWMRList<Node>> chunkListsTemplate = new ArrayList<>(producersCount + 1);

        for (int i = 0; i < producersCount; i++) {
            chunkListsTemplate.add(new SWMRList<>());
        }

        SWMRList<Node> stealList = new SWMRList<>();
        chunkListsTemplate.add(stealList);

        return new CopyOnWriteArrayList<>(chunkListsTemplate);
    }

    /**
     * Init ThreadLocal for the new producer.
     */
    @PermitAll
    void registerProducer(int pId) {
        if (pContextThreadLocal.get() != null) {
            throw new UnsupportedOperationException("Trying to register producer " + pId +
                    " that is already registered in this " + SalsaSCPool.class.getSimpleName());
        }

        pContextThreadLocal.set(new ProducerContext(pId));
    }

    /**
     * Init ThreadLocal for the owner consumer.
     */
    @PermitAll
    void registerOwner() {
        if (ownerContextThreadLocal.get() != null) {
            throw new UnsupportedOperationException("Trying to register owner for already owned " +
                    SalsaSCPool.class.getSimpleName());
        }

        ownerContextThreadLocal.set(new OwnerContext());
    }

    @Override
    @PermitProducers
    public boolean produce(Runnable task) {
        checkProducerRegistration();

        return insert(task, false);
    }

    @Override
    @PermitProducers
    public void produceForce(Runnable task) {
        checkProducerRegistration();

        insert(task, true);
    }

    private void checkProducerRegistration() {
        if (pContextThreadLocal.get() == null) {
            throw new IllegalStateException("Calling thread wasn't registered as producer.");
        }
    }

    private void checkOwnerRegistration() {
        if (ownerContextThreadLocal.get() == null) {
            throw new IllegalStateException("Calling thread wasn't registered as owner consumer.");
        }
    }

    private boolean insert(Runnable task, boolean force) {
        task = new SalsaTask(task); // wrap original task to introduce uniqueness at every insertion

        ProducerContext producerContext = pContextThreadLocal.get();

        if (producerContext.getChunk() == null) {
            // allocate new chunk, put it into producer context
            if (!getChunk(force)) return false;
        }

        producerContext.getChunk().getTasks().set(producerContext.getProdIdx(), task);
        producerContext.setProdIdx(producerContext.getProdIdx() + 1);

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

        final Node node = new Node();
        node.setChunk(newChunk);
        // todo check how cleanup's working
        chunkLists.get(producerContext.getProducerId()).addWithTotalCleanup(node, aNode -> aNode.getChunk() == null); // add new node to producer's own chunk list
        producerContext.chunk = newChunk;
        producerContext.prodIdx = 0;
        return true;
    }

    @Override
    @PermitOwner
    public Runnable consume() {
        checkOwnerRegistration();
        OwnerContext ownerContext = ownerContextThreadLocal.get();

        if (ownerContext.getCurrentNode() != null) { // common case
            Runnable task = takeTask(ownerContext.getCurrentNode());
            if (task != null) return ((SalsaTask) task).getTask();
        }

        // traverse chunkLists
        for (SWMRList<Node> chunkList : chunkLists) {
            for (Node node : chunkList) {
                Chunk chunk = node.getChunk();
                if (chunk != null && chunk.getOwner().get() == consumerId) {
                    Runnable task = takeTask(node);
                    if (task != null) {
                        ownerContext.setCurrentNode(node);
                        return ((SalsaTask) task).getTask();
                    }
                }
            }
        }

        ownerContext.setCurrentNode(null);
        return null;
    }

    /**
     * Called only by scPool owner
     * @param node some node to retrieve a task from
     * @return retrieved task or {@code null}
     */
    private Runnable takeTask(Node node) {
        Chunk chunk = node.getChunk();
        if (chunk == null) return null; // chunk has been stolen from this node

        Runnable task = chunk.getTasks().get(node.getIdx() + 1);
        if (task == null) return null; // no tasks in this chunk

        if (chunk.getOwner().get() != consumerId) return null;

        node.setIdx(node.getIdx() + 1); // tell the world you're going to take a task from idx
                                        // atomicity is not needed since only the owner of the SCPool can update idx
        // todo add mfence here? or volatile semantics is a mfence?
        if (chunk.getOwner().get() == consumerId) { // common case
            int idx = node.getIdx();
            Runnable next = idx + 1 < chunkSize ? chunk.getTasks().get(idx + 1) : null; // get next task for isEmpty()
            chunk.getTasks().set(idx, SalsaTask.TAKEN);
            checkLast(node, next);
            return task;
        }

        // the chunk has been stolen, CAS the last task and go away
        int idx = node.getIdx();
        Runnable next = idx + 1 < chunkSize ? chunk.getTasks().get(idx + 1) : null; // get next task for isEmpty()
        boolean success = !SalsaTask.TAKEN.equals(task) && chunk.getTasks().compareAndSet(idx, task, SalsaTask.TAKEN);

        if (success) checkLast(node, next);
        ownerContextThreadLocal.get().setCurrentNode(null);
        return success ? task : null;
    }

    /**
     * If the {@code task} is the last one in the {@code node.getChunk()}, the caller (pool owner)
     * will recycle this chunk and flush it's {@code currentNode} field.
     * Only owner of the current pool can execute this method.
     * @param node node to check for being completely used up
     * @param task task to check
     */
    private void checkLast(Node node, Runnable task) {
        if (node.getIdx() + 1 == chunkSize) { // finished the chunk
            //  Chunk usedChunk = node.getChunk();
            node.setChunk(null);

            // recycle used chunk and return to the chunk pool
            //  usedChunk.clear(); // todo maybe just create a new chunk? how much pressure on GC?
            chunkPool.add(new Chunk(chunkSize, consumerId));

            ownerContextThreadLocal.get().setCurrentNode(null);
            clearIndicators();
        }

        if (task == null) clearIndicators();
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
        checkOwnerRegistration();
        if (otherSCPool == this) throw new IllegalArgumentException("Stealing from yourself is not supported");

        Node prevNode = getNode((SalsaSCPool) otherSCPool);
        if (prevNode == null) return null; // no chunks found

        Chunk chunk = prevNode.getChunk();
        if (chunk == null) return null;

        int prevIdx = prevNode.getIdx();
        if (prevIdx + 1 == chunkSize || chunk.getTasks().get(prevIdx + 1) == null) return null;

        SWMRList<Node> myStealList = chunkLists.get(nProducers);

        // todo check how cleanup's working
        myStealList.addWithTotalCleanup(prevNode, aNode -> aNode.getChunk() == null); // make it stealable from my list
        if (!chunk.getOwner().compareAndSet(((SalsaSCPool) otherSCPool).consumerId, consumerId)) {
            myStealList.remove(prevNode); // failed to steal, remove last
            return null;
        }

        clearIndicators(); // for isEmpty()

        int idx = prevNode.getIdx();
        if (idx + 1 == chunkSize) { // chunk is empty
            myStealList.remove(prevNode);
            return null;
        }

        Runnable task = chunk.getTasks().get(idx + 1);
        if (task != null) { // found the task
            if (chunk.getOwner().get() != consumerId && idx != prevIdx) {
                myStealList.remove(prevNode);
                return null;
            }
            idx++;
        }

        Node newNode = new Node(prevNode); // make snapshot copy
        newNode.setIdx(idx);

        // todo delete this debugging check
        if (!myStealList.remove(prevNode)) {
            throw new IllegalStateException("couldn't delete added Node form steal list");
        };
        myStealList.add(newNode);

        prevNode.setChunk(null); // remove chunk from consumer's list

        // done stealing chunk, take one task from it
        if (task == null) return null; // still no task at idx
        Runnable next = idx + 1 < chunkSize ? chunk.getTasks().get(idx + 1) : null; // for isEmpty()
        if (SalsaTask.TAKEN.equals(task) || !chunk.getTasks().compareAndSet(idx, task, SalsaTask.TAKEN)) task = null;

        checkLast(newNode, next);

        if (chunk.getOwner().get() == consumerId) ownerContextThreadLocal.get().setCurrentNode(newNode);
        return task != null ? ((SalsaTask) task).getTask() : null;
    }

    /**
     * Called to steal a chunk from the scPool, other than that of current consumer thread.
     * todo: This method to be subjected to performance tuning via best traversal search
     *
     * @param otherSCPool other consumer's {@link SalsaSCPool}
     * @return found node
     */
    private Node getNode(SalsaSCPool otherSCPool) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int startId = random.nextInt(nProducers + 1);

        // my feature
        // traverse all entries from a random start circularly to find not empty node
        for (int i = startId; i <= nProducers; i++) {
            Node res = scanChunkListAtIndex(otherSCPool, i);
            if (res != null) return res;
        }

        for (int i = 0; i < startId; i++) {
            Node res = scanChunkListAtIndex(otherSCPool, i);
            if (res != null) return res;
        }

        return null;
    }

    // именно тут владелец текущего пула лезет в чужой пул
    /**
     * Used to search for a node with possibly not empty {@link Chunk}.
     * For using only in {@link SalsaSCPool#getNode(SalsaSCPool)}.
     *
     * @param otherSCPool other consumer's {@link SalsaSCPool}
     * @param i chunk list index to look for a chunk
     */
    private Node scanChunkListAtIndex(SalsaSCPool otherSCPool, int i) {
        SWMRList<Node> chunkList = otherSCPool.chunkLists.get(i);

        // todo iterate over a snapshot as before?
        for (Node node : chunkList) {
            Chunk chunk = node.getChunk();
            if (chunk != null && node.getIdx() + 1 != chunkSize) return node; // found chunk possibly with tasks
        }

        return null;
    }

    @Override
    @PermitAll
    public boolean isEmpty() {
        for (SWMRList<Node> chunkList : chunkLists) {
            for (Node node : chunkList) {
                int idx = node.getIdx();
                Chunk chunk = node.getChunk();
                if (chunk == null) continue;
                for (int i = idx + 1; i < chunkSize; i++) {
                    Runnable task = chunk.getTasks().get(i);
                    if (task != null && !SalsaTask.TAKEN.equals(task)) return false;
                }
            }
        }
        return true;
    }

    @Override
    @PermitConsumers
    public void setIndicator(int consumerId) {
        emptyIndicators.set(consumerId, 1);
    }

    @Override
    @PermitConsumers
    public boolean checkIndicator(int consumerId) {
        return emptyIndicators.get(consumerId) == 1;
    }

    private void clearIndicators() {
        for (int i = 0; i < nConsumers; i++) {
            emptyIndicators.set(i, 0);
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

    private static class OwnerContext {
        private Node currentNode = null; // todo need volatile???

        public Node getCurrentNode() {
            return currentNode;
        }

        public void setCurrentNode(Node currentNode) {
            this.currentNode = currentNode;
        }
    }

    /**
     * A {@link Runnable} wrapper, needed to make sure all {@code Runnable}s, inserted to the {@code SalsaSCPool}s are unique.
     * Uniqueness is provided by creation of a new object each time a producer calls {@link SalsaSCPool#insert(Runnable, boolean)}.
     * For this reason, there's no need to override {@link Object#equals(Object)} and {@link Object#hashCode()} methods.
     */
    private static class SalsaTask implements Runnable {
        public static final SalsaTask TAKEN = new SalsaTask(); // taken sentinel
        private final Runnable task;

        // for the TAKEN only
        private SalsaTask() {
            this.task = null;
        }

        public SalsaTask(Runnable task) {
            if (task == null) throw new IllegalArgumentException("Creating " + SalsaTask.class.getSimpleName() +
                    " with null runnable");
            this.task = task;
        }

        public Runnable getTask() {
            return this.task;
        }

        @Override
        public void run() {
            this.task.run();
        }

        @Override
        public String toString() {
            return this == TAKEN
                    ? "SalsaTask.TAKEN"
                    : "SalsaTask{" +
                    "task=" + task +
                    '}';
        }
    }
}
