package com.naumov.taskpool.salsa;

public interface SalsaIterator<E> {
    /**
     * Gets the current next item in the list.
     * Always returns items, added before this method call event,
     * and not deleted before the return event of this method.
     * Doesn't return items, deleted before the method call. Never throws exceptions.
     * @return next item or {@code null} when the list is over
     */
    E next();
}