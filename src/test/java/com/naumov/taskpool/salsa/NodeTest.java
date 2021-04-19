package com.naumov.taskpool.salsa;

import org.junit.Test;

import static org.junit.Assert.*;

public class NodeTest {

    @Test
    public void testClone() throws CloneNotSupportedException {
        Node node = new Node(new Chunk(5, 10));
        System.out.println(node);
        Node clone = node.clone();
        System.out.println(clone);
        System.out.println(node == clone); // false => copied
    }
}