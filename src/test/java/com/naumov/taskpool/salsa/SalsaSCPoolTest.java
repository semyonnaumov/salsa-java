package com.naumov.taskpool.salsa;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

// simple sequential tests
public class SalsaSCPoolTest {
    private SalsaSCPool emptyNewlyInitializedPool;

    private SalsaSCPool newSCPool(int ownerId) {
        return new SalsaSCPool(ownerId, 1000, 10, 10);
    }

    @Before
    public void initSCPool() {
        emptyNewlyInitializedPool = newSCPool(0);
    }

    @Test
    public void emptyPoolInvariant() {
        assertTrue(emptyNewlyInitializedPool.isEmpty());
    }

    @Test
    public void produceWithoutRegistration() {
        assertThrows(IllegalStateException.class, () -> emptyNewlyInitializedPool.produce(() -> {}));
        assertThrows(IllegalStateException.class, () -> emptyNewlyInitializedPool.produceForce(() -> {}));
    }

    @Test
    public void produceToEmptyPool() {
        emptyNewlyInitializedPool.registerProducer(0);

        // produce fails since newly initialized pool has zero capacity
        assertFalse(emptyNewlyInitializedPool.produce(() -> {}));
        assertTrue(emptyNewlyInitializedPool.isEmpty());
    }


    @Test
    public void produceForceToEmptyPool() {
        emptyNewlyInitializedPool.registerProducer(0);

        // produceForce expands zero capacity pool
        emptyNewlyInitializedPool.produceForce(() -> {});
        assertFalse(emptyNewlyInitializedPool.isEmpty());
    }

    @Test
    public void consumeWithoutRegistration() {
        assertThrows(IllegalStateException.class, () -> emptyNewlyInitializedPool.consume());
    }

    @Test
    public void consumeFromEmpty() {
        emptyNewlyInitializedPool.registerOwner();
        assertNull(emptyNewlyInitializedPool.consume());
    }

    @Test
    public void consumeNormally() {
        emptyNewlyInitializedPool.registerProducer(0);
        Runnable runnable = () -> {};
        emptyNewlyInitializedPool.produceForce(runnable);

        emptyNewlyInitializedPool.registerOwner();
        assertEquals(emptyNewlyInitializedPool.consume(), runnable);
        assertNull(emptyNewlyInitializedPool.consume());
        assertTrue(emptyNewlyInitializedPool.isEmpty());
    }

    @Test
    public void testStealFromYourself() {
        emptyNewlyInitializedPool.registerOwner();
        assertThrows(IllegalArgumentException.class, () -> emptyNewlyInitializedPool.steal(emptyNewlyInitializedPool));
    }

    @Test
    public void testStealFromEmptyOther() {
        emptyNewlyInitializedPool.registerOwner();
        assertNull(emptyNewlyInitializedPool.steal(newSCPool(1)));
    }

    @Test
    public void stealNormally() {
        SalsaSCPool otherSCPool = newSCPool(1);
        otherSCPool.registerProducer(0);

        Runnable runnable = () -> {};
        otherSCPool.produceForce(runnable);

        assertFalse(otherSCPool.isEmpty());
        assertTrue(emptyNewlyInitializedPool.isEmpty());

        emptyNewlyInitializedPool.registerOwner();

        assertEquals(emptyNewlyInitializedPool.steal(otherSCPool), runnable);
        assertTrue(otherSCPool.isEmpty());
        assertTrue(emptyNewlyInitializedPool.isEmpty()); // true since the task was removed from the pool after stealing
    }

    @Test
    public void stealChunkWith2Tasks() {
        SalsaSCPool otherSCPool = newSCPool(1);
        otherSCPool.registerProducer(0);

        Runnable runnable0 = () -> {};
        Runnable runnable1 = () -> {};
        otherSCPool.produceForce(runnable0);
        otherSCPool.produceForce(runnable1);

        assertFalse(otherSCPool.isEmpty());
        assertTrue(emptyNewlyInitializedPool.isEmpty());

        emptyNewlyInitializedPool.registerOwner();

        assertEquals(emptyNewlyInitializedPool.steal(otherSCPool), runnable0);
        assertTrue(otherSCPool.isEmpty());
        assertFalse(emptyNewlyInitializedPool.isEmpty()); // false since second task is not extracted yet
        assertEquals(emptyNewlyInitializedPool.consume(), runnable1); // true since second task is not extracted yet
    }
}