package com.naumov.taskpool.salsa;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class SWMRModifiedListTest {
    @Test
    public void seqTest0() {
        Set<Object> reference = new HashSet<>();
        SWMRModifiedList<Object> list = new SWMRModifiedList<>();

        Object node0 = new Object();
        Object node1 = new Object();
        Object node2 = new Object();

        list.add(node0);
        list.add(node1);
        list.add(node2);
        reference.add(node0);
        reference.add(node1);
        reference.add(node2);

        int c0 = 0;
        for (Object o : list) {
            assertTrue(reference.remove(o));
            c0++;
        }

        assertEquals(reference.size(), 0);
        assertEquals(c0, 3);

        list.remove(node0);
        list.remove(node1);
        list.remove(node2);

        int c1 = 0;
        for (Object o : list) {
            c1++;
        }

        assertEquals(c1, 0);
    }

    @Test
    public void seqRemoveTest() {
        SWMRModifiedList<String> list = new SWMRModifiedList<>();

        String item0 = "aaaaaaa";
        String item1 = "aaaaaaa";
        String item2 = "bbbbbbb";

        list.add(item0);
        list.add(item1);
        list.add(item2);

        assertTrue(list.remove(item0));
        assertTrue(list.remove(item0)); // since item0.equals(item1)
        assertFalse(list.remove(item0));
        assertFalse(list.remove(item1));
        assertTrue(list.remove(item2));
        assertFalse(list.remove(item2));
    }
}