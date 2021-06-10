package com.naumov.taskpool.salsa;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * Single-writer multi-reader linked list, used in {@link SalsaSCPool}'s {@code chunkLists} field.
 * todo add more details
 */
public class SalsaNodeList implements Iterable<Node> {
    private final AtomicLong ownerId = new AtomicLong(-1L); // owner id

    // two different objects for head and tail sentinels
    private final ListNode head;
    private final ListNode tail;

    public SalsaNodeList() {
        head = new ListNode(null);
        tail = new ListNode(null);
        head.next = tail;
    }

    public void add(Node node) {
        add(node, null, false);
    }

    public void addWithMinorCleanup(Node item, Predicate<Node> cleanupPredicate) {
        add(item, cleanupPredicate, false);
    }

    public void addWithTotalCleanup(Node item, Predicate<Node> cleanupPredicate) {
        add(item, cleanupPredicate, true);
    }

    private void add(Node item, Predicate<Node> cleanupPredicate, boolean isTotalCleanup) {
        checkOwner();
        if (item == null) throw new NullPointerException("Null items are not allowed");

        ListNode current = head;

        boolean deletedOne = false;
        while (current.next != tail) {

            if (cleanupPredicate != null && (!deletedOne || isTotalCleanup)) {
                // need to clean up
                if (cleanupPredicate.test(current.next.item)) {
                    // delete next item
                    current.next.deleted = true;
                    current.next = current.next.next;
                    deletedOne = true;
                }
            }

            // double check since could have moved closer to the tail
            if (current.next != tail) current = current.next;
        }

        ListNode listNode = new ListNode(item);
        listNode.next = tail;
        current.next = listNode;

        return;
    }

    public boolean remove(Node item) {
        return remove(item, null, false);
    }

    public boolean removeWithMinorCleanup(Node item, Predicate<Node> cleanupPredicate) {
        return remove(item, cleanupPredicate, false);
    }

    public boolean removeWithTotalCleanup(Node item, Predicate<Node> cleanupPredicate) {
        return remove(item, cleanupPredicate, true);
    }

    public boolean remove(Object item, Predicate<Node> cleanupPredicate, boolean isTotalCleanup) {
        checkOwner();
        if (item == null) throw new NullPointerException("Null items are not allowed");

        ListNode current = head;
        if (current.next == tail) return false; // empty list

        boolean deletedOne = false;
        do {
            // clean up phase
            if (cleanupPredicate != null && (!deletedOne || isTotalCleanup)) {
                if (cleanupPredicate.test(current.next.item)) {
                    // delete next item
                    ListNode next = current.next;
                    next.deleted = true;
                    current.next = current.next.next;
                    deletedOne = true;
                    if (next.item.equals(item)) return true; // deleted requested item during cleanup
                }

                if (current.next == tail) return false; // left with empty list
            }

            if (current.next.item.equals(item)) {
                current.next.deleted = true;
                current.next = current.next.next;
                return true;
            }

            current = current.next;
        } while (current.next != tail);

        return false;
    }

    private void checkOwner() {
        if (Thread.currentThread().getId() == ownerId.get()) return;
        if (ownerId.get() == -1L && ownerId.compareAndSet(-1L, Thread.currentThread().getId())) return;

        throw new UnsupportedOperationException(SalsaNodeList.class.getSimpleName()
                + " instance can only be modified by owner thread.");
    }

    /**
     * Only this method is allowed for reader threads.
     * @return
     */
    @Override
    public Iterator<Node> iterator() {
        return new SWMRIterator();
    }

    private static class ListNode {
        private volatile ListNode next;
        private final Node item;
        private volatile boolean deleted = false; // for iterators only

        public ListNode(Node item) {
            this.item = item;
        }
    }

    /**
     * Iterator for readers. Weakly consistent.
     * 1. Newer traverses one element twice
     * 2. May not see additions after iterator was created
     * 3. Must see deletions of uniterated elements, added before the iterator was created
     */
    private class SWMRIterator implements Iterator<Node> {
        private ListNode current = head; // отсюда смотрим в next, когда зовем hasNext
        private ListNode currentNext = head.next; // мы это запоминаем когда зовем hasNext и возвращаем в next

        @Override
        public boolean hasNext() {
            // next никогда не будет == tail
            currentNext = current.next; // обновляем next чтобы с ним работать

            if (currentNext == tail) return false; // дошли до конца, next вернет Exception

            // todo двигать current если будем натыкаться на currentNext.deleted == true
            while (currentNext.deleted && currentNext != tail) {
                current = currentNext;
                currentNext = current.next;
            }

            // наткнулись на не удаленный либо на конец
            if (currentNext == tail) return false; // дошли до конца, next вернет Exception

            // нашли не удаленный currentNext!
            return true;
        }

        // не учитывает удаленные ноды после while (currentNext.deleted && currentNext != tail) {
        @Override
        public Node next() {
            if (currentNext == tail) throw new NoSuchElementException("No more elements to traverse");

            Node item = currentNext.item; // то, что вернем
            current = currentNext; // подвинемся вперед
            currentNext = currentNext.next;

            return item;
        }
    }
}
