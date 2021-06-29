package com.naumov.taskpool.salsa;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class SWMRLinkedListImplTest {

    @Test
    public void seqTestWithForeach() {
        Set<Object> reference = new HashSet<>();
        SWMRLinkedListImpl<Object> list = new SWMRLinkedListImpl<>();

        Object node0 = new Object();
        Object node1 = new Object();
        Object node2 = new Object();

        list.add(node0);
        list.add(node1);
        list.add(node2);
        reference.add(node0);
        reference.add(node1);
        reference.add(node2);

        assertTrue(list.contains(node0));
        assertTrue(list.contains(node1));
        assertTrue(list.contains(node2));

        int c0 = 0;
        for (Object o : list) {
            assertTrue(reference.remove(o));
            c0++;
        }

        assertEquals(reference.size(), 0);
        assertEquals(c0, 3);

        list.remove(node0);
        assertFalse(list.contains(node0));
        list.remove(node1);
        assertFalse(list.contains(node1));
        list.remove(node2);
        assertFalse(list.contains(node2));

        int c1 = 0;
        for (Object o : list) {
            c1++;
        }

        assertEquals(c1, 0);
    }

    @Test
    public void seqTestWithSalsaIterator() {
        Set<Object> reference = new HashSet<>();
        SWMRLinkedListImpl<Object> list = new SWMRLinkedListImpl<>();

        Object node0 = new Object();
        Object node1 = new Object();
        Object node2 = new Object();

        list.add(node0);
        list.add(node1);
        list.add(node2);
        reference.add(node0);
        reference.add(node1);
        reference.add(node2);

        assertTrue(list.contains(node0));
        assertTrue(list.contains(node1));
        assertTrue(list.contains(node2));

        SWMRLinkedListIterator<Object> it = list.consistentIterator();
        int c0 = 0;
        Object nextItem0 = it.next();
        while (nextItem0 != null) {
            assertTrue(reference.remove(nextItem0));
            assertTrue(list.contains(nextItem0));
            c0++;
            nextItem0 = it.next();
        }

        assertEquals(reference.size(), 0);
        assertEquals(c0, 3);

        list.remove(node0);
        assertFalse(list.contains(node0));
        list.remove(node1);
        assertFalse(list.contains(node1));
        list.remove(node2);
        assertFalse(list.contains(node2));

        int c1 = 0;
        Object nextItem1 = it.next();
        while (nextItem1 != null) {
            c1++;
            nextItem1 = it.next();
        }

        assertEquals(c1, 0);
    }

    @Test
    public void seqRemoveTest() {
        SWMRLinkedListImpl<String> list = new SWMRLinkedListImpl<>();

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

        assertFalse(list.contains(item0));
        assertFalse(list.contains(item1));
        assertFalse(list.contains(item2));
    }

    @Test
    public void seqCleanupTest() {
        SWMRLinkedListImpl<String> list = new SWMRLinkedListImpl<>();

        String item0 = "aaaaaaa";
        String item1 = "aaaaaaa";
        String item2 = "bbbbbba";
        String item3 = "bbbbbbb";
        String item4 = "bbbbbaa";

        list.add(item0);
        list.add(item1);
        list.add(item2);
        list.add(item3);
        list.add(item4);

        list.cleanup(i -> i.contains("a"));

        SWMRLinkedListIterator<String> it = list.consistentIterator();
        String next = it.next();
        assertEquals(item3, next);
        assertNull(it.next());

        assertFalse(list.contains(item0));
        assertFalse(list.contains(item1));
        assertFalse(list.contains(item2));
        assertTrue(list.contains(item3));
        assertFalse(list.contains(item4));
    }
}