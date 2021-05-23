package com.naumov.taskpool.salsa;

public class Node implements Cloneable {
    private volatile Chunk chunk;
    private volatile int idx = -1;

    public Node(Chunk chunk) {
        this.chunk = chunk;
    }

    // only for cloning
    private Node(Chunk chunk, int idx) {
        this.chunk = chunk;
        this.idx = idx;
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
    public Node clone() throws CloneNotSupportedException {
        // todo implement wisely
        super.clone();
        return new Node(chunk.clone(), idx);
    }

    @Override
    public String toString() {
        return "Node{" +
                "chunk=" + chunk +
                ", idx=" + idx +
                '}';
    }
}
