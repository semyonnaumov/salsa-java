package com.naumov.taskpool.salsa;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public class Chunk {
    static final VarHandle AVH = MethodHandles.arrayElementVarHandle(Runnable[].class); // to perform CAS
    private final AtomicInteger owner; // to perform CAS
    private final int chunkSize;
    private final Runnable[] tasks;

    public Chunk(int chunkSize, int owner) {
        this.owner = new AtomicInteger(owner);
        this.chunkSize = chunkSize;
        this.tasks = new Runnable[chunkSize];
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
}
