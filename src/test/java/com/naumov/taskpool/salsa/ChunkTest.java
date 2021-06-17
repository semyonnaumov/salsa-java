package com.naumov.taskpool.salsa;

import org.junit.Test;

import static org.junit.Assert.*;

// simple sequential test
public class ChunkTest {

    @Test
    public void testConstructorSuccess() {
        int chunkSize = 5;
        int ownerValue = 5;
        Chunk chunk = new Chunk(chunkSize, ownerValue);

        assertEquals(chunk.getOwner().getReference().intValue(), ownerValue);
        assertEquals(chunk.getOwner().getStamp(), 0);
        assertEquals(chunk.getTasks().length(), chunkSize);
    }

    @Test
    public void testConstructorFailure() {
        assertThrows(IllegalArgumentException.class, () -> new Chunk(5, -200));
        assertThrows(IllegalArgumentException.class, () -> new Chunk(5, 200));
        assertThrows(IllegalArgumentException.class, () -> new Chunk(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Chunk(-5, 0));
    }
}