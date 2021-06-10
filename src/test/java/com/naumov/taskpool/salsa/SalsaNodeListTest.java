package com.naumov.taskpool.salsa;

import org.junit.Test;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static org.junit.Assert.*;

// simple sequential test
public class SalsaNodeListTest {

    @Test
    public void test0() {
        Set<Node> reference = new HashSet<>();
        SalsaNodeList list = new SalsaNodeList();

        Node node0 = new Node();
        Node node1 = new Node();
        Node node2 = new Node();

        list.add(node0);
        list.add(node1);
        list.add(node2);
        reference.add(node0);
        reference.add(node1);
        reference.add(node2);

        int c0 = 0;
        Iterator<Node> iterator0 = list.iterator();
        while (iterator0.hasNext()) {
            assertTrue(reference.remove(iterator0.next()));
            c0++;
        }

        assertEquals(reference.size(), 0);
        assertEquals(c0, 3);

        list.remove(node0);
        list.remove(node1);
        list.remove(node2);

        int c1 = 0;
        Iterator<Node> iterator1 = list.iterator();
        while (iterator1.hasNext()) {
            iterator1.next();
            c1++;
        }

        assertEquals(c1, 0);
    }
}