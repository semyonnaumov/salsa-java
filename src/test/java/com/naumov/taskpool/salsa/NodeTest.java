package com.naumov.taskpool.salsa;

import org.junit.Test;

import static org.junit.Assert.*;

public class NodeTest {

    @Test
    public void testClone() {
        Node node = new Node();
        node.setChunk(new Chunk(5, 10));
        System.out.println(node);
        Node clone = new Node(node);
        System.out.println(clone);
        System.out.println(node == clone); // false => copied
    }
}