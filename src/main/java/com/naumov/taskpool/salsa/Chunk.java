package com.naumov.taskpool.salsa;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public class Chunk implements Cloneable {
    static final VarHandle AVH = MethodHandles.arrayElementVarHandle(Runnable[].class); // to perform CAS

    private final AtomicInteger owner; // to perform CAS
    private final int chunkSize;
    private final Runnable[] tasks;

    public Chunk(int chunkSize, int owner) {
        this.owner = new AtomicInteger(owner);
        this.chunkSize = chunkSize;
        this.tasks = new Runnable[chunkSize];
    }

    // only for cloning
    private Chunk(int chunkSize, int owner, Runnable[] tasks) {
        this.owner = new AtomicInteger(owner);
        this.chunkSize = chunkSize;
        this.tasks = tasks;
    }

    public AtomicInteger getOwner() {
        return owner;
    }

    public Runnable[] getTasks() {
        return tasks;
    }

    // todo needs synchronization?
    public void clear() {
        Arrays.fill(tasks, null);
    }

    @Override
    public Chunk clone() throws CloneNotSupportedException {
        // todo implement wisely
        super.clone();
        return new Chunk(chunkSize, owner.get(), tasks);
    }

    @Override
    public String toString() {
        return "Chunk{" +
                "owner=" + owner.get() +
                ", chunkSize=" + chunkSize +
                ", tasks=" + Arrays.toString(tasks) +
                '}';
    }
}
