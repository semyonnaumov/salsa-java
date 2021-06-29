package com.naumov.taskpool.salsa;

/**
 * Represents a {@link Chunk} wrapper with field {@code idx}, which points to the last taken (or about to be taken)
 * {@link Runnable} task in a {@code chunk}. Must not override {@link Object#equals(Object)} and
 * {@link Object#hashCode()} methods since reference comparison is used for deletion from its containers.
 */
public class Node {
    /*
     * Index of the last taken task in the chunk. Needed to sync consumers during stealing. Initialized by the thread
     * that created the node (producer/stealer), modified only by the owner of the containing SCPool (consumer)
     */
    private volatile int idx = -1;
    private volatile Chunk chunk;

    public Node(Chunk chunk) {
        this.chunk = chunk;
    }

    /**
     * Copying constructor. Copying is not atomic.
     *
     * @param other node to copy
     */
    public Node(Node other) {
        if (other == null) throw new IllegalArgumentException(getClass().getSimpleName() +
                " copying constructor called with null argument");

        this.chunk = other.chunk; // Copy reference, otherwise chunk can be lost during stealing
        this.idx = other.idx;
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

    @Override
    public String toString() {
        return "Node{" +
                "chunk=" + chunk +
                ", idx=" + idx +
                '}';
    }
}
