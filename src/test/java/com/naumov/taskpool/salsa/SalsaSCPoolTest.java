package com.naumov.taskpool.salsa;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

// simple sequential tests
public class SalsaSCPoolTest {
    private SalsaSCPool pool;

    private SalsaSCPool unpopulatedPool(int ownerId) {
        return new SalsaSCPool(ownerId, 1000, 10, 10);
    }

    @Before
    public void initSCPool() {
        pool = unpopulatedPool(0);
    }

    @Test
    public void emptyAtStart() {
        assertTrue(pool.isEmpty());
    }

    @Test
    public void produceWithoutRegistration() {
        assertThrows(IllegalStateException.class, () -> pool.produce(() -> {}));
        assertThrows(IllegalStateException.class, () -> pool.produceForce(() -> {}));
    }

    @Test
    public void produceToEmptyPool() {
        pool.registerProducer(0);

        // produce fails since newly initialized pool has zero capacity
        assertFalse(pool.produce(() -> {}));
        assertTrue(pool.isEmpty());
    }


    @Test
    public void produceForceToEmptyPool() {
        pool.registerProducer(0);

        // produceForce expands zero capacity pool
        pool.produceForce(() -> {});
        assertFalse(pool.isEmpty());
    }

    @Test
    public void consumeWithoutRegistration() {
        assertThrows(IllegalStateException.class, () -> pool.consume());
    }

    @Test
    public void consumeFromEmpty() {
        pool.registerOwner();
        assertNull(pool.consume());
    }

    @Test
    public void consumeNormally() {
        pool.registerProducer(0);
        Runnable runnable = () -> {};
        pool.produceForce(runnable);

        pool.registerOwner();
        assertEquals(pool.consume(), runnable);
        assertNull(pool.consume());
        assertTrue(pool.isEmpty());
    }

    @Test
    public void stealFromYourself() {
        pool.registerOwner();
        assertThrows(IllegalArgumentException.class, () -> pool.steal(pool));
    }

    @Test
    public void stealFromEmptyOther() {
        pool.registerOwner();
        assertNull(pool.steal(unpopulatedPool(1)));
    }

    @Test
    public void stealNormally() {
        SalsaSCPool otherPool = unpopulatedPool(1);
        otherPool.registerProducer(0);

        Runnable runnable = () -> {};
        otherPool.produceForce(runnable);

        assertFalse(otherPool.isEmpty());
        assertTrue(pool.isEmpty());

        pool.registerOwner();

        assertEquals(pool.steal(otherPool), runnable);
        assertTrue(otherPool.isEmpty());
        assertTrue(pool.isEmpty()); // true since the task was removed from the pool after stealing
    }

    @Test
    public void stealChunkWith2Tasks() {
        SalsaSCPool otherSCPool = unpopulatedPool(1);
        otherSCPool.registerProducer(0);

        Runnable runnable0 = () -> {};
        Runnable runnable1 = () -> {};
        otherSCPool.produceForce(runnable0);
        otherSCPool.produceForce(runnable1);

        assertFalse(otherSCPool.isEmpty());
        assertTrue(pool.isEmpty());

        pool.registerOwner();

        assertEquals(pool.steal(otherSCPool), runnable0);
        assertTrue(otherSCPool.isEmpty());
        assertFalse(pool.isEmpty()); // false since second task is not extracted yet
        assertEquals(pool.consume(), runnable1); // true since second task is not extracted yet
    }
}