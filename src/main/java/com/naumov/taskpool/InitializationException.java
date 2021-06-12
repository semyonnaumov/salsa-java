package com.naumov.taskpool;

public class InitializationException extends RuntimeException {
    public InitializationException() {
        super();
    }

    public InitializationException(String message) {
        super(message);
    }
}
