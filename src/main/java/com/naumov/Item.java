package com.naumov;

import java.util.Objects;

/**
 * Needed for assigning an unique id to each item in the pool
 */
public class Item {
    private final long id;
    private final Object payload;

    public Item(long id, Object payload) {
        this.id = id;
        this.payload = payload;
    }

    public long getId() {
        return id;
    }

    public Object getPayload() {
        return payload;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Item item = (Item) o;
        return id == item.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
