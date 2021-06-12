package com.naumov.taskpool.salsa;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * Single-writer multi-reader linked list, used in {@link SalsaSCPool}'s {@code chunkLists} field.
 * todo add more details
 */
public class SWMRList<E> implements Iterable<E> {
    private final AtomicLong ownerId = new AtomicLong(-1L); // owner id

    // two different objects for head and tail sentinels
    private final ListNode<E> head;
    private final ListNode<E> tail;

    public SWMRList() {
        head = new ListNode<>(null);
        tail = new ListNode<>(null);
        head.next = tail;
    }

    public void add(E item) {
        add(item, null, false);
    }

    public void addWithMinorCleanup(E item, Predicate<E> cleanupPredicate) {
        add(item, cleanupPredicate, false);
    }

    public void addWithTotalCleanup(E item, Predicate<E> cleanupPredicate) {
        add(item, cleanupPredicate, true);
    }

    private void add(E item, Predicate<E> cleanupPredicate, boolean isTotalCleanup) {
        checkOwner();
        if (item == null) throw new NullPointerException("Null items are not allowed");

        ListNode<E> current = head;

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

        ListNode<E> listNode = new ListNode<>(item);
        listNode.next = tail;
        current.next = listNode;
    }

    public boolean remove(E item) {
        return remove(item, null, false);
    }

    public boolean removeWithMinorCleanup(E item, Predicate<E> cleanupPredicate) {
        return remove(item, cleanupPredicate, false);
    }

    public boolean removeWithTotalCleanup(E item, Predicate<E> cleanupPredicate) {
        return remove(item, cleanupPredicate, true);
    }

    public boolean remove(E item, Predicate<E> cleanupPredicate, boolean isTotalCleanup) {
        checkOwner();
        if (item == null) throw new NullPointerException("Null items are not allowed");

        ListNode<E> current = head;
        if (current.next == tail) return false; // empty list

        boolean deletedOne = false;
        do {
            // clean up phase
            if (cleanupPredicate != null && (!deletedOne || isTotalCleanup)) {
                if (cleanupPredicate.test(current.next.item)) {
                    // delete next item
                    ListNode<E> next = current.next;
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

        throw new UnsupportedOperationException(SWMRList.class.getSimpleName()
                + " instance can only be modified by owner thread.");
    }

    /**
     * Only this method is allowed for reader threads.
     * @return
     */
    @Override
    public Iterator<E> iterator() {
        return new SWMRIterator();
    }

    private static class ListNode<E> {
        private volatile ListNode<E> next;
        private final E item;
        private volatile boolean deleted = false; // for iterators only

        public ListNode(E item) {
            this.item = item;
        }
    }

    /**
     * Iterator for readers. Weakly consistent.
     * 1. Newer traverses one element twice
     * 2. May not see additions after iterator was created
     * 3. Must see deletions of uniterated elements, added before the iterator was created
     */
    private class SWMRIterator implements Iterator<E> {
        private ListNode<E> current = head; // отсюда смотрим в next, когда зовем hasNext
        private ListNode<E> currentNext = head.next; // мы это запоминаем когда зовем hasNext и возвращаем в next

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
        public E next() {
            if (currentNext == tail) throw new NoSuchElementException("No more elements to traverse");

            E item = currentNext.item; // то, что вернем
            current = currentNext; // подвинемся вперед
            currentNext = currentNext.next;

            return item;
        }
    }
}
