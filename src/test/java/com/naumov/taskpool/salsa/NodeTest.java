package com.naumov.taskpool.salsa;

import org.junit.Test;

import static org.junit.Assert.*;

// simple sequential test
public class NodeTest {

    @Test
    public void testCopyingConstructor0() {
        Node node = new Node(new Chunk(5,5));
        Node other = new Node(node);

        assertEquals(node.getIdx(), other.getIdx());
        assertSame(node.getChunk(), other.getChunk()); // copying is shallow by design!
    }
}