package com.naumov.taskpool.salsa;

import com.naumov.taskpool.SCPool;
import com.naumov.taskpool.salsa.annot.*;

import java.lang.invoke.VarHandle;
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
    private final CopyOnWriteArrayList<CopyOnWriteArrayList<Node>> chunkLists; // shared among all actors
    private final AtomicIntegerArray emptyIndicators; // shared among all consumers
    private final Queue<Chunk> chunkPool; // M-S queue for spare chunks, shared among owner and producers

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
        this.chunkPool = new ConcurrentLinkedQueue<>();
    }

    private CopyOnWriteArrayList<CopyOnWriteArrayList<Node>> initChunkLists(int producersCount) {
        // one-time used template for chunkLists
        final List<CopyOnWriteArrayList<Node>> chunkListsTemplate = new ArrayList<>(producersCount + 1);
        for (int i = 0; i < producersCount; i++) {
            chunkListsTemplate.add(new CopyOnWriteArrayList<>());
        }

        // add steal list
        chunkListsTemplate.add(new CopyOnWriteArrayList<>());

        return new CopyOnWriteArrayList<>(chunkListsTemplate);
    }

    /**
     * Init ThreadLocal for the new producer.
     */
    @PermitAll
    void registerProducer(int pId) {
        if (pContextThreadLocal.get() != null) {
            throw new IllegalCallerException("Trying to register producer " + pId +
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
            throw new IllegalCallerException("Trying to register owner for already owned " +
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

        if (producerContext.chunk == null) {
            // allocate new chunk, put it into producer context
            if (!getChunk(force)) return false;
        }

        producerContext.chunk.getTasks().set(producerContext.prodIdx, task);
        producerContext.prodIdx++;

        if (producerContext.prodIdx == chunkSize) {
            // the chunk is full
            producerContext.chunk = null;
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
        // add new node to producer's own chunk list
        // todo check how cleanup's working
        chunkLists.get(producerContext.producerId).add(node);
        producerContext.chunk = newChunk;
        producerContext.prodIdx = 0;
        return true;
    }

    @Override
    @PermitOwner
    public Runnable consume() {
        checkOwnerRegistration();
        OwnerContext ownerContext = ownerContextThreadLocal.get();

        if (ownerContext.currentNode != null) {
            // common case
            Runnable task = takeTask(ownerContext.currentNode);
            if (task != null) return ((SalsaTask) task).getTask();
        }

        // wasn't able to get a task from the currentNode (null/empty/stolen), traverse chunkLists
        for (CopyOnWriteArrayList<Node> chunkList : chunkLists) {
            for (Node node : chunkList) {
                Chunk chunk = node.getChunk();
                if (chunk != null && chunk.getOwner().getReference() == consumerId) {
                    // found owned chunk
                    Runnable task = takeTask(node);
                    if (task != null) {
                        ownerContext.currentNode = node;
                        return ((SalsaTask) task).getTask();
                    }
                }
            }
        }

        // failed to take a task from owned SCPool
        ownerContext.currentNode = null;
        return null;
    }

    /**
     * Tries to extract a task from the given node. Can be called only by scPool owner.
     * Owner competes with the stealer for the found task, if not {@code null}.
     * @param node some node to retrieve a task from
     * @return retrieved task or {@code null}
     */
    private Runnable takeTask(Node node) {
        Chunk chunk = node.getChunk();
        if (chunk == null) return null; // chunk has been stolen or recycled by owner consumer

        Runnable task = getTaskAt(chunk, node.getIdx() + 1);
        if (task == null) return null; // no tasks in this chunk

        // todo эта проверка пропала в короткой статье!
        if (chunk.getOwner().getReference() != consumerId) return null; // chunk is stolen

        node.setIdx(node.getIdx() + 1); // tell the world you're going to take a task from idx + 1
                                        // atomicity is not needed since only the owner of the SCPool can update idx

        // todo add mfence here? or volatile semantics is a mfence?
//        VarHandle.fullFence();

        if (chunk.getOwner().getReference() == consumerId) {
            // common case
            Runnable next = getTaskAt(chunk, node.getIdx() + 1); // get next task for checkLast()
            chunk.getTasks().set(node.getIdx(), SalsaTask.TAKEN);
            checkLast(node, next);
            return task;
        }

        // owner changed, the chunk has been stolen, CAS the last task and go away
        Runnable next = getTaskAt(chunk, node.getIdx() + 1); // get next task for checkLast()
        boolean success = !SalsaTask.TAKEN.equals(task) && chunk.getTasks().compareAndSet(node.getIdx(), task, SalsaTask.TAKEN);

        if (success) checkLast(node, next);
        ownerContextThreadLocal.get().currentNode = null; // chunk from this node was stolen

        return success ? task : null;
    }

    private Runnable getTaskAt(Chunk chunk, int idx){
        return idx < chunkSize ? chunk.getTasks().get(idx) : null;
    }

    /**
     * If the {@code taskNextToCurrent} is the last one in the {@code node.getChunk()}, the caller (pool owner)
     * will recycle this chunk and flush it's {@code currentNode} field.
     * Only owner of the current pool can execute this method.
     * @param node node to check for being completely used up
     * @param taskNextToCurrent task to check
     */
    private void checkLast(Node node, Runnable taskNextToCurrent) {
        if (node.getIdx() + 1 == chunkSize) {
            // finished the chunk
            node.setChunk(null);
            chunkPool.add(new Chunk(chunkSize, consumerId));
            ownerContextThreadLocal.get().currentNode = null;
            clearIndicators();
        }

        if (taskNextToCurrent == null) clearIndicators(); // pool could have become empty, tell others to check this
    }

    // todo есть разница между новой и старой статьями!
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

        StolenNodeWrapper prevNodeWrapper = getNode((SalsaSCPool) otherSCPool);
        Node prevNode = prevNodeWrapper != null ? prevNodeWrapper.node : null;
        if (prevNode == null) return null; // no chunks found

        Chunk chunk = prevNode.getChunk();
        if (chunk == null) return null;

        int prevIdx = prevNode.getIdx();
        if (prevIdx + 1 == chunkSize || chunk.getTasks().get(prevIdx + 1) == null) return null; // no tasks in the chunk

        CopyOnWriteArrayList<Node> myStealList = chunkLists.get(nProducers);

        // todo check how cleanup's working
        myStealList.add(prevNode); // make it stealable from my list
        if (!chunk.getOwner().compareAndSet(prevNodeWrapper.inspectedChunkOwner, consumerId,
                prevNodeWrapper.inspectedChunkStamp, prevNodeWrapper.inspectedChunkStamp + 1)) {
            myStealList.remove(prevNode); // failed to steal (somebody else stole it), remove it
            return null;
        }

        clearIndicators(); // for isEmpty() todo check

        int idx = prevNode.getIdx();
        if (idx + 1 == chunkSize) {
            // stole used chunk
            myStealList.remove(prevNode);
            return null;
        }

        Runnable task = chunk.getTasks().get(idx + 1);
        if (task != null) {
            // found the task
            if (chunk.getOwner().getReference() != consumerId && idx != prevIdx) {
                myStealList.remove(prevNode);
                return null;
            }
            idx++;
        }

        Node newNode = new Node(prevNode); // make snapshot copy
        newNode.setIdx(idx);

        // todo delete this debugging check
        // todo нужно именно в то же место его воткнуть?
        if (!myStealList.remove(prevNode)) throw new IllegalStateException("couldn't delete added Node form steal list");
        myStealList.add(newNode);

        prevNode.setChunk(null); // remove chunk from consumer's list

        // done stealing chunk, take one task from it
        if (task == null) return null; // still no task at idx
        Runnable next = idx + 1 < chunkSize ? chunk.getTasks().get(idx + 1) : null; // for isEmpty()
        if (SalsaTask.TAKEN.equals(task) || !chunk.getTasks().compareAndSet(idx, task, SalsaTask.TAKEN)) task = null;

        checkLast(newNode, next);

        if (chunk.getOwner().getReference() == consumerId) ownerContextThreadLocal.get().currentNode = newNode;
        return task != null ? ((SalsaTask) task).getTask() : null;
    }

    /**
     * Called to steal a chunk from the scPool, other than that of current consumer thread.
     * todo: This method to be subjected to performance tuning via best traversal search
     *
     * @param otherSCPool other consumer's {@link SalsaSCPool}
     * @return found node
     */
    private StolenNodeWrapper getNode(SalsaSCPool otherSCPool) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int startId = random.nextInt(nProducers + 1); // [0, nProducers + 1) accounts for steal-list

        // todo collapse all in one loop with %
        // traverse all entries from a random start circularly to find not empty node
        for (int i = startId; i <= nProducers; i++) {
            StolenNodeWrapper nodeWrapper = scanChunkList(otherSCPool.chunkLists.get(i), otherSCPool.consumerId);
            if (nodeWrapper != null) return nodeWrapper;
        }

        for (int i = 0; i < startId; i++) {
            StolenNodeWrapper nodeWrapper = scanChunkList(otherSCPool.chunkLists.get(i), otherSCPool.consumerId);
            if (nodeWrapper != null) return nodeWrapper;
        }

        return null;
    }

    // именно тут владелец текущего пула лезет в чужой пул
    /**
     * Used to search for a node with possibly not empty {@link Chunk}, belonging to the specified consumer.
     * For using only in {@link SalsaSCPool#getNode(SalsaSCPool)}.
     *
     * @param chunkList other consumer's chunkList
     * @param soughtOwner consumer, expected to be the owner of this chunk
     */
    private StolenNodeWrapper scanChunkList(CopyOnWriteArrayList<Node> chunkList, int soughtOwner) {

        for (Node node : chunkList) {
            Chunk chunk = node.getChunk();
            if (chunk != null && node.getIdx() + 1 < chunkSize) {
                // atomically get chunk owner and stamp for further CAS
                int[] stampHolder = new int[1];
                int ownerValue = chunk.getOwner().get(stampHolder);

                if (ownerValue == soughtOwner) {
                    // the chunk belongs to the pool owner, we can try to steal it
                    return new StolenNodeWrapper(node, ownerValue, stampHolder[0]);
                }
            }
        }

        // appropriate chunk not found
        return null;
    }

    @Override
    @PermitAll
    public boolean isEmpty() {
        for (CopyOnWriteArrayList<Node> chunkList : chunkLists) {
            for (Node node : chunkList) {
                Chunk chunk = node.getChunk();
                if (chunk == null) continue;
                int idx = node.getIdx();
                for (int i = idx + 1; i < chunkSize; i++) {
                    Runnable task = chunk.getTasks().get(i);
                    // found non empty task
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
        private Chunk chunk; // current chunk to work with
        private int prodIdx; // where to add next task

        public ProducerContext(int producerId) {
            this.producerId = producerId;
            this.chunk = null;
            this.prodIdx = 0;
        }
    }

    private static class OwnerContext {
        private Node currentNode = null;
    }

    private static class StolenNodeWrapper {
        private final Node node;
        private final int inspectedChunkOwner;
        private final int inspectedChunkStamp;

        private StolenNodeWrapper(Node node, int inspectedChunkOwner, int inspectedChunkStamp) {
            if (node == null) throw new NullPointerException("Node has to be not null");

            this.node = node;
            this.inspectedChunkOwner = inspectedChunkOwner;
            this.inspectedChunkStamp = inspectedChunkStamp;
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
