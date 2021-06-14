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
        assertEquals(chunk.getOwner().getStamp(), Integer.MIN_VALUE);
        assertEquals(chunk.getTasks().length(), chunkSize);
    }

    @Test
    public void testConstructorFailure() {
        assertThrows(IllegalArgumentException.class, () -> new Chunk(5, -200));
        assertThrows(IllegalArgumentException.class, () -> new Chunk(5, 200));
        assertThrows(IllegalArgumentException.class, () -> new Chunk(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Chunk(-5, 0));
    }

//    @Test
//    public void testCopyingConstructorSuccess() {
//        int chunkSize = 5;
//        int owner = 5;
//        Chunk chunk = new Chunk(chunkSize, owner);
//
//        chunk.getTasks().set(0, () -> {});
//        chunk.getTasks().set(1, () -> {});
//        chunk.getTasks().set(2, () -> {});
//
//        Chunk other = new Chunk(chunk);
//
//        assertNotSame(chunk.getOwner(), other.getOwner());
//        assertNotSame(chunk.getTasks(), other.getTasks());
//        assertEquals(chunk.getOwner().getReference(), other.getOwner().getReference());
//        assertEquals(chunk.getOwner().getStamp(), other.getOwner().getStamp());
//
//        for (int i = 0; i < chunkSize; i++) {
//             assertEquals(chunk.getTasks().get(i), other.getTasks().get(i));
//        }
//
//        assertEquals(chunk.hashCode(), other.hashCode());
//        assertEquals(chunk, other);
//    }
//
//    @Test
//    public void testCopyingConstructorFailure() {
//        assertThrows(IllegalArgumentException.class , () -> new Chunk(null));
//    }
}