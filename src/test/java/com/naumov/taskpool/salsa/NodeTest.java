package com.naumov.taskpool.salsa;

import org.junit.Test;

import static org.junit.Assert.*;

// simple sequential test
public class NodeTest {
    private final Chunk chunk = new Chunk(5,5);

    @Test
    public void testConstructor0() {
        assertThrows(IllegalArgumentException.class , () -> new Chunk(0, 5));
    }

    @Test
    public void testCopyingConstructor0() {
        Node node = new Node();
        node.setChunk(chunk);

        Node other = new Node(node);

        assertNotSame(node.getChunk(), other.getChunk());
        assertEquals(node, other);
        assertEquals(node.hashCode(), other.hashCode());
    }

    @Test
    public void testCopyingConstructor1() {
        assertThrows(IllegalArgumentException.class , () -> new Node(null));
    }
}