package com.naumov.taskpool.salsa;

/**
 * Represents a {@link Chunk} wrapper with field {@code idx}, which points to the last taken (or about to be taken)
 * {@link Runnable} task in a {@code chunk}. Must not override {@link Object#equals(Object)} and {@link Object#hashCode()}
 * methods since reference comparison is used for deletion from its containers.
 */
public class Node {
    private volatile Chunk chunk;
    private volatile int idx = -1; // index of the last taken task in the chunk
                                   // needed to sync consumers during stealing
                                   // initialized by the thread that created the node (producer/stealer),
                                   // modified only by the owner of the containing SCPool (consumer)

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

        this.chunk = other.chunk; // copy reference, otherwise chunk could become lost during stealing
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
