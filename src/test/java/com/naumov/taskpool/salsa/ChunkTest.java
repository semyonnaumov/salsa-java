package com.naumov.taskpool.salsa;

import org.junit.Test;

import static org.junit.Assert.*;

// simple sequential test
public class ChunkTest {

    @Test
    public void testConstructor0() {
        int chunkSize = 5;
        int owner = 5;
        Chunk chunk = new Chunk(chunkSize, owner);

        assertEquals(chunk.getOwner().get(), owner);
        assertEquals(chunk.getTasks().length(), chunkSize);
    }

    @Test
    public void testConstructor1() {
        assertThrows(IllegalArgumentException.class , () -> new Chunk(0, 5));
    }

    @Test
    public void testCopyingConstructor0() {
        int chunkSize = 5;
        int owner = 5;
        Chunk chunk = new Chunk(chunkSize, owner);
        Chunk other = new Chunk(chunk);

        assertNotSame(chunk.getOwner(), other.getOwner());
        assertNotSame(chunk.getTasks(), other.getTasks());
        assertEquals(chunk, other);
        assertEquals(chunk.hashCode(), other.hashCode());
    }

    @Test
    public void testCopyingConstructor1() {
        assertThrows(IllegalArgumentException.class , () -> new Chunk(null));
    }
}