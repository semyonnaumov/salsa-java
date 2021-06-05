package com.naumov;

public final class ThreadUtil {
    public static void logAction(String action) {
        // использование этой штуки кардинально меняет поведение системы, т.к. sout блокирующий, и взаимно исключает
        // одновременную запись из разных потоков
        System.out.println("[" + Thread.currentThread().getName() + "] " + action);
    }

    private ThreadUtil() {
    }
}
