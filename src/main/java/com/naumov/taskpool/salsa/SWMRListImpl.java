package com.naumov.taskpool.salsa;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

public class SWMRListImpl<E> implements SWMRList<E> {

    private final AtomicLong ownerId = new AtomicLong(-1L); // owner id
    private final ListNode head;
    private final ListNode tail;
    private final int cleanupCycles;

    public SWMRListImpl() {
        head = new ListNode(null);
        tail = new ListNode(null);
        head.next = tail;
        tail.prev = head;
        this.cleanupCycles = Integer.MAX_VALUE; // total cleanup when unspecified
    }

    public SWMRListImpl(int cleanupCycles) {
        head = new ListNode(null);
        tail = new ListNode(null);
        head.next = tail;
        tail.prev = head;
        this.cleanupCycles = cleanupCycles;
    }

    // inserts items in the end of the list (before the tail) in O(1)
    @Override
    public void add(E item) {
        checkOwner();
        if (item == null) throw new NullPointerException("Null items are not allowed");

        ListNode listNode = new ListNode(item);
        listNode.next = tail;
        listNode.prev = tail.prev;
        tail.prev = listNode;
        listNode.prev.next = listNode; // <--- commit

        // physically delete deletion-pending node
        if (listNode.prev != head && listNode.prev.deleted) {
            ListNode beforeDeleted = listNode.prev.prev;
            listNode.prev.prev = null; // unlink deleted node backwards
            listNode.prev = beforeDeleted;
            beforeDeleted.next = listNode; // <--- commit
        }
    }

    // removes the item, if found in list
    // first removes logically, then, if it's not the last node in the list - physically
    // the last logically removed node is removed physically in add() method
    @Override
    public boolean remove(E item) {
        checkOwner();
        if (item == null) throw new NullPointerException("Null items are not allowed");

        // find node to delete
        ListNode beforeDeleted = head;
        while (beforeDeleted.next != tail) {
            if (!beforeDeleted.next.deleted && beforeDeleted.next.item.equals(item)) {
                // found not deleted node with the item
                beforeDeleted.next.deleted = true; // <-- commit 1
                if (beforeDeleted.next.next != tail) {
                    // not last node - remove physically
                    beforeDeleted.next.prev = null; // unlink deleted node backwards
                    beforeDeleted.next.next.prev = beforeDeleted;
                    beforeDeleted.next = beforeDeleted.next.next; // <-- commit 2
                }
                return true;
            }
            beforeDeleted = beforeDeleted.next;
        }

        // not found
        return false;
    }

    @Override
    public boolean replace(E item, E replacement) {
        checkOwner();
        if (item == null) throw new NullPointerException("Null items are not allowed");

        // find node to replace item
        ListNode current = head.next;
        while (current != tail) {
            if (!current.deleted && current.item.equals(item)) {
                // found not deleted node with the item
                current.item = replacement; // <--- commit
                return true;
            }
            current = current.next;
        }

        // node with the item not found
        return false;
    }

    @Override
    public void cleanup(Predicate<E> cleanupPredicate) {
        checkOwner();

        ListNode beforeDeleted = head;
        int deletedCount = 0;
        while (beforeDeleted.next != tail && deletedCount < this.cleanupCycles) {
            if (beforeDeleted.next.deleted || cleanupPredicate.test(beforeDeleted.next.item)) {
                // found node to delete
                beforeDeleted.next.deleted = true; // <-- commit 1
                deletedCount++;
                if (beforeDeleted.next.next != tail) {
                    // not last node - remove physically
                    beforeDeleted.next.prev = null; // unlink deleted node backwards
                    beforeDeleted.next.next.prev = beforeDeleted;
                    beforeDeleted.next = beforeDeleted.next.next; // <-- commit 2
                } else {
                    return;
                }
            } else {
                beforeDeleted = beforeDeleted.next;
            }
        }
    }

    @Override
    public boolean contains(E item) {
        if (item == null) return false;

        // find node containing the item
        ListNode current = head.next;
        while (current != tail) {
            if (!current.deleted && current.item.equals(item)) {
                // found not deleted node with the item
                return true;
            }
            current = current.next;
        }

        // node with the item not found
        return false;
    }

    private void checkOwner() {
        if (Thread.currentThread().getId() == ownerId.get()) return;
        if (ownerId.get() == -1L && ownerId.compareAndSet(-1L, Thread.currentThread().getId())) return;

        throw new UnsupportedOperationException(SWMRListImpl.class.getSimpleName()
                + " instance can only be modified by owner thread.");
    }

    @Override
    public Iterator<E> iterator() {
        return new WeakIterator();
    }

    @Override
    public SWMRListIterator<E> consistentIterator() {
        return new StrongIterator();
    }

    private class ListNode {
        private volatile ListNode next; // for owner and iterators
        private ListNode prev; // non-volatile since only for owner thread
        private volatile E item; // volatile since can be replaced in method replace(...)
        private volatile boolean deleted = false; // for owner and iterators

        public ListNode(E item) {
            this.item = item;
        }
    }

    // consistent iterator
    private class StrongIterator implements SWMRListIterator<E> {
        private ListNode returnCandidate = head.next;

        @Override
        public E next() {
            while (returnCandidate.deleted && returnCandidate != tail) {
                returnCandidate = returnCandidate.next;
            }

            // came to the tail or not deleted node
            if (returnCandidate == tail) return null;

            E item = returnCandidate.item;
            returnCandidate = returnCandidate.next;

            return item;
        }
    }

    // weakly consistent iterator
    private class WeakIterator implements Iterator<E> {
        private ListNode current = head; // look to next from here when calling hasNext()
        private ListNode currentNext = head.next; // item to return from next() method

        @Override
        public boolean hasNext() {
            // next никогда не будет == tail
            currentNext = current.next; // обновляем next чтобы с ним работать
            if (currentNext == tail) return false; // дошли до конца, next вернет Exception

            // skip logically deleted nodes
            while (currentNext.deleted && currentNext != tail) {
                current = currentNext;
                currentNext = current.next;
            }

            // наткнулись на не удаленный либо на конец
            return currentNext != tail; // дошли до конца (next вернет Exception) либо нашли не удаленный currentNext
        }

        // не учитывает удаленные ноды после while (currentNext.deleted && currentNext != tail) {
        @Override
        public E next() {
            if (currentNext == tail) throw new NoSuchElementException("No more elements to traverse");

            E item = currentNext.item; // get the item to return
            current = currentNext; // move one step forward
            currentNext = currentNext.next;

            return item;
        }
    }

    @Override
    public String toString() {
        return "SWMRList{" +
                "ownerId=" + ownerId.get() +
                '}';
    }
}
