package com.naumov.taskpool.salsa;

public class Node {
    private volatile int idx = -1;
    private volatile Chunk chunk;

    public Node() {
    }

    /**
     * Copying constructor
     *
     * @param other node to copy
     */
    public Node(Node other) {
        if (other == null) throw new IllegalArgumentException(getClass().getSimpleName() +
                " copying constructor called with null argument");

        idx = other.idx;
        Chunk otherChunk = other.chunk;
        chunk = otherChunk != null ? new Chunk(otherChunk) : null;
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
