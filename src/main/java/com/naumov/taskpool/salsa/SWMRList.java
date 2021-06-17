package com.naumov.taskpool.salsa;

import java.util.function.Predicate;

public interface SWMRList<E> extends Iterable<E> {

    /**
     * Adds the {@code item} to the list
     * @param item item to add
     */
    void add(E item);

    /**
     * Removes the first found {@code item}
     * @param item item to remove
     * @return {@code true} if item found
     */
    boolean remove(E item);

    /**
     * Replaces firts found {@code item} with the {@code replacement}
     * @param item item to replace
     * @param replacement replacement
     * @return {@code true} if replacement is successful
     */
    boolean replace(E item, E replacement);

    /**
     * Deletes items by given {@code cleanupPredicate} condition.
     * @param cleanupPredicate condition
     */
    void cleanup(Predicate<E> cleanupPredicate);

    /**
     * Looks for the item in the list.
     * @param item item to detect
     * @return {@code true} if found
     */
    boolean contains(E item);

    /**
     * Linearizable iterator.
     * @return an iterator
     */
    SWMRListIterator<E> consistentIterator();
}
