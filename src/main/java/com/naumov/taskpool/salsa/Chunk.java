package com.naumov.taskpool.salsa;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.AtomicStampedReference;

/**
 * Wrapper for an array of tasks, which is a minimal unit of task stealing. Field {@code owner} represents
 * the consumer, owning this chunk, and is used for synchronization during stealing. Chunks are created only by
 * producers when they call {@link com.naumov.taskpool.SCPool#produce(Runnable)} on empty pool.
 */
public class Chunk {
    private final int chunkSize;
    private final AtomicStampedReference<Integer> owner; // stamped to prevent ABA during steal-back
                                                         // allows only values from constant pool: [-128, 127]
                                                         // since boxed Integers are compared by reference
    private final AtomicReferenceArray<Runnable> tasks;

    public Chunk(int chunkSize, int owner) {
        if (chunkSize <= 0) throw new IllegalArgumentException("chunkSize must be a positive number");
        if (owner < Byte.MIN_VALUE || owner > Byte.MAX_VALUE)
            throw new IllegalArgumentException("Only byte values are allowed for owner field: [-128, 127].");

        this.chunkSize = chunkSize;
        this.owner = new AtomicStampedReference<>(owner, 0);
        this.tasks = new AtomicReferenceArray<>(chunkSize);
    }

    public AtomicStampedReference<Integer> getOwner() {
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

            int[] thisStampHolder = new int[1];
            int thisOwnerVal = this.owner.get(thisStampHolder);

            int[] otherStampHolder = new int[1];
            int otherOwnerVal = this.owner.get(otherStampHolder);

            if (other.chunkSize != this.chunkSize ||
                    thisOwnerVal != otherOwnerVal ||
                    thisStampHolder[0] != otherStampHolder[0]) return false;
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

        int[] stampHolder = new int[1];
        int ownerVal = this.owner.get(stampHolder);

        return Integer.hashCode(chunkSize) + Integer.hashCode(ownerVal) + Integer.hashCode(stampHolder[0]) + accumulatedHash;
    }

    @Override
    public String toString() {
        return "Chunk{" +
                "owner=" + owner +
                ", chunkSize=" + chunkSize +
                ", tasks=" + tasks +
                '}';
    }
}
