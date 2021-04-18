package com.naumov.taskpool.salsa;

public class Node {
    private Chunk chunk;
    private int idx = -1;
    private Node next = null; // todo delete after using SomeSingleWriterMultiReaderList

    public Node(Chunk chunk) {
        this.chunk = chunk;
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

    public Node getNext() {
        return next;
    }

    public void setNext(Node next) {
        this.next = next;
    }
}
