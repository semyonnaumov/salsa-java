package com.naumov.taskpool.salsa;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Wrapper for an array of tasks, which is a minimal unit of task stealing. Field {@code owner} represents
 * the consumer, owning this chunk, and is used for synchronization during stealing.
 */
public class Chunk {
    private final int chunkSize;
    private final AtomicInteger owner;
    private final AtomicReferenceArray<Runnable> tasks;

    public Chunk(int chunkSize, int owner) {
        if (chunkSize <= 0) throw new IllegalArgumentException("chunkSize must be a positive number");

        this.chunkSize = chunkSize;
        this.owner = new AtomicInteger(owner);
        this.tasks = new AtomicReferenceArray<>(chunkSize);
    }

    /**
     * Copying constructor.
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

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj instanceof Chunk) {
            Chunk other = (Chunk) obj;

            if (other.chunkSize != this.chunkSize || other.owner.get() != this.owner.get()) return false;
            for (int i = 0; i < chunkSize; i++) {
                if (other.tasks.get(i) != this.tasks.get(i)) return false;
            }
            return true;
        }

        return false;
    }

    @Override
    public int hashCode() {
        int accumulatedHash = 0;
        for (int i = 0; i < chunkSize; i++) {
            accumulatedHash += Objects.hashCode(this.tasks.get(0));
        }

        return Integer.hashCode(chunkSize) + Integer.hashCode(this.owner.get()) + accumulatedHash;
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
