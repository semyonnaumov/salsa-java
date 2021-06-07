package com.naumov.taskpool.salsa;

import org.junit.Test;

import static org.junit.Assert.*;

// simple sequential test
public class NodeTest {

    @Test
    public void testCopyingConstructor0() {
        Node node = new Node();
        node.setChunk(new Chunk(5,5));

        Node other = new Node(node);

        assertEquals(node.getIdx(), other.getIdx());
        assertNotSame(node.getChunk(), other.getChunk());
    }

    @Test
    public void testCopyingConstructor1() {
        assertThrows(IllegalArgumentException.class , () -> new Node(null));
    }
}