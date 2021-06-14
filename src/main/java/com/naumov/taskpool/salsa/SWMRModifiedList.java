package com.naumov.taskpool.salsa;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * Single-writer multi-reader linked list, used in {@link SalsaSCPool}'s {@code chunkLists} field.
 * todo add more details
 */
public class SWMRModifiedList<E> implements List<E> {
    private final AtomicLong ownerId = new AtomicLong(-1L); // owner id

    // two different objects for head and tail sentinels
    private final ListNode<E> head;
    private final ListNode<E> tail;

    public SWMRModifiedList() {
        head = new ListNode<>(null);
        tail = new ListNode<>(null);
        head.next = tail;
    }

    public boolean add(E item) {
        add(item, null, false);
        return true;
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

        while (current.next != tail) {
            current = current.next;
        }

        ListNode<E> listNode = new ListNode<>(item);
        listNode.next = tail;
        current.next = listNode; // <-- commit point
    }

    public boolean remove(Object item) {
        return remove(item, null, false);
    }

    public boolean removeWithMinorCleanup(Object item, Predicate<E> cleanupPredicate) {
        return remove(item, cleanupPredicate, false);
    }

    public boolean removeWithTotalCleanup(Object item, Predicate<E> cleanupPredicate) {
        return remove(item, cleanupPredicate, true);
    }

    public boolean remove(Object item, Predicate<E> cleanupPredicate, boolean isTotalCleanup) {
        checkOwner();
        if (item == null) throw new NullPointerException("Null items are not allowed");

        ListNode<E> current = head;
        if (current.next == tail) return false; // empty list

        do {
            if (current.next.item.equals(item) && !current.next.deleted) {
                current.next.deleted = true;
                return true;
            }

            current = current.next;
        } while (current.next != tail);

        return false;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public E get(int index) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public E set(int index, E element) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void add(int index, E element) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public E remove(int index) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public int indexOf(Object o) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public int lastIndexOf(Object o) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public ListIterator<E> listIterator() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException("Not implemented");
    }

    private void checkOwner() {
        if (Thread.currentThread().getId() == ownerId.get()) return;
        if (ownerId.get() == -1L && ownerId.compareAndSet(-1L, Thread.currentThread().getId())) return;

        throw new UnsupportedOperationException(SWMRModifiedList.class.getSimpleName()
                + " instance can only be modified by owner thread.");
    }

    @Override
    public int size() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public boolean isEmpty() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public boolean contains(Object o) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Only this method is allowed for reader threads.
     * @return
     */
    @Override
    public Iterator<E> iterator() {
        return new SWMRIterator();
    }

    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public <T> T[] toArray(T[] a) {
        throw new UnsupportedOperationException("Not implemented");
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
            if (currentNext == tail) return null;

            E item = currentNext.item; // то, что вернем
            current = currentNext; // подвинемся вперед
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
