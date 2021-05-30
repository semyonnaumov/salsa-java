package com.naumov.taskpool.salsa;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class Chunk {
    private final int chunkSize;
    private final AtomicInteger owner;
    private volatile AtomicReferenceArray<Runnable> tasks; // todo change to final and not use clear()?

    public Chunk(int chunkSize, int owner) {
        this.chunkSize = chunkSize;
        this.owner = new AtomicInteger(owner);
        this.tasks = new AtomicReferenceArray<>(chunkSize);
    }

    /**
     * Copying constructor
     * @param other chunk to copy
     */
    public Chunk(Chunk other) {
        if (other == null) throw new IllegalArgumentException(getClass().getSimpleName() +
                " copying constructor called with null argument");

        chunkSize = other.chunkSize;
        owner = new AtomicInteger(other.owner.get());

        Runnable[] copy = new Runnable[chunkSize];
        for (int i = 0; i < copy.length; i++) {
            copy[i] = other.tasks.get(i);
        }

        tasks = new AtomicReferenceArray<>(copy);
    }

    public AtomicInteger getOwner() {
        return owner;
    }

    public AtomicReferenceArray<Runnable> getTasks() {
        return tasks;
    }

    // todo consider deleting this method
    public void clear() {
        this.tasks = new AtomicReferenceArray<>(chunkSize);
    }

    @Override
    public String toString() {
        return "Chunk{" +
                "owner=" + owner.get() +
                ", chunkSize=" + chunkSize +
                ", tasks=" + tasks +
                '}';
    }
}
