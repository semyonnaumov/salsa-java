package com.naumov;

public interface Pool {
    void put(Object item);
    Object get();
    boolean isEmpty();
}