package com.naumov.taskpool.salsa;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Mock for read sync-free list.
 * @param <E>
 */
public class SomeSingleWriterMultiReaderList<E> implements List<E> {
    private final CopyOnWriteArrayList<E> it = new CopyOnWriteArrayList<>();

    @Override
    public int size() {
        return it.size();
    }

    @Override
    public boolean isEmpty() {
        return it.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return it.contains(o);
    }

    @Override
    public Iterator<E> iterator() {
        return it.iterator();
    }

    @Override
    public Object[] toArray() {
        return it.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return it.toArray(a);
    }

    @Override
    public boolean add(E e) {
        return it.add(e);
    }

    @Override
    public boolean remove(Object o) {
        return it.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return it.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        return it.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        return it.addAll(index, c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return it.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return it.retainAll(c);
    }

    @Override
    public void clear() {
        it.clear();
    }

    @Override
    public E get(int index) {
        return it.get(index);
    }

    @Override
    public E set(int index, E element) {
        return it.set(index, element);
    }

    @Override
    public void add(int index, E element) {
        it.add(index, element);
    }

    @Override
    public E remove(int index) {
        return it.remove(index);
    }

    @Override
    public int indexOf(Object o) {
        return it.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return it.lastIndexOf(o);
    }

    @Override
    public ListIterator<E> listIterator() {
        return it.listIterator();
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        return it.listIterator(index);
    }

    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        return it.subList(fromIndex, toIndex);
    }
}
