package com.naumov.taskpool.salsa;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

// simple sequential tests
public class SalsaSCPoolTest {
    private SalsaSCPool zeroOwnerPool;

    private SalsaSCPool unpopulatedPool(int ownerId) {
        return new SalsaSCPool(ownerId, 1000, 10, 10);
    }

    @Before
    public void initSCPool() {
        zeroOwnerPool = unpopulatedPool(0);
    }

    @Test
    public void emptyAtStart() {
        assertTrue(zeroOwnerPool.isEmpty());
    }

    @Test
    public void produceWithoutRegistration() {
        assertThrows(IllegalStateException.class, () -> zeroOwnerPool.produce(() -> {
        }));
        assertThrows(IllegalStateException.class, () -> zeroOwnerPool.produceForce(() -> {
        }));
    }

    @Test
    public void produceToEmptyPool() {
        zeroOwnerPool.registerProducer(0);

        // produce fails since newly initialized pool has zero capacity
        assertFalse(zeroOwnerPool.produce(() -> {
        }));
        assertTrue(zeroOwnerPool.isEmpty());
    }


    @Test
    public void produceForceToEmptyPool() {
        zeroOwnerPool.registerProducer(0);

        // produceForce expands zero capacity pool
        zeroOwnerPool.produceForce(() -> {
        });
        assertFalse(zeroOwnerPool.isEmpty());
    }

    @Test
    public void consumeWithoutRegistration() {
        assertThrows(IllegalStateException.class, () -> zeroOwnerPool.consume());
    }

    @Test
    public void consumeFromEmpty() {
        zeroOwnerPool.registerOwner();
        assertNull(zeroOwnerPool.consume());
    }

    @Test
    public void consumeNormally() {
        zeroOwnerPool.registerProducer(0);
        Runnable runnable = () -> {
        };
        zeroOwnerPool.produceForce(runnable);

        zeroOwnerPool.registerOwner();
        assertEquals(zeroOwnerPool.consume(), runnable);
        assertNull(zeroOwnerPool.consume());
        assertTrue(zeroOwnerPool.isEmpty());
    }

    @Test
    public void stealFromYourself() {
        zeroOwnerPool.registerOwner();
        assertThrows(IllegalArgumentException.class, () -> zeroOwnerPool.steal(zeroOwnerPool));
    }

    @Test
    public void stealFromEmptyOther() {
        zeroOwnerPool.registerOwner();
        assertNull(zeroOwnerPool.steal(unpopulatedPool(1)));
    }

    @Test
    public void stealNormally() {
        SalsaSCPool otherPool = unpopulatedPool(1);
        otherPool.registerProducer(0);

        Runnable runnable = () -> {
        };
        otherPool.produceForce(runnable);

        assertFalse(otherPool.isEmpty());
        assertTrue(zeroOwnerPool.isEmpty());

        zeroOwnerPool.registerOwner();

        assertEquals(zeroOwnerPool.steal(otherPool), runnable);
        assertTrue(otherPool.isEmpty());
        assertTrue(zeroOwnerPool.isEmpty()); // true since the task was removed from the pool after stealing
    }

    @Test
    public void stealChunkWith2Tasks() {
        SalsaSCPool otherPool = unpopulatedPool(1);
        otherPool.registerProducer(0);

        Runnable runnable0 = () -> {
        };
        Runnable runnable1 = () -> {
        };
        otherPool.produceForce(runnable0);
        otherPool.produceForce(runnable1);

        assertFalse(otherPool.isEmpty());
        assertTrue(zeroOwnerPool.isEmpty());

        zeroOwnerPool.registerOwner();

        assertEquals(zeroOwnerPool.steal(otherPool), runnable0);
        assertTrue(otherPool.isEmpty());
        assertFalse(zeroOwnerPool.isEmpty()); // false since second task is not extracted yet
        assertEquals(zeroOwnerPool.consume(), runnable1); // true since second task is not extracted yet
    }

    @Test
    public void emptyIndicatorOnConsume() {
        zeroOwnerPool.registerOwner();
        for (int i = 0; i < 32; i++) assertFalse(zeroOwnerPool.checkIndicator(i));
        for (int i = 0; i < 32; i++) zeroOwnerPool.setIndicator(i);
        for (int i = 0; i < 32; i++) assertTrue(zeroOwnerPool.checkIndicator(i));

        Runnable runnable = () -> {};
        zeroOwnerPool.registerProducer(0);
        zeroOwnerPool.produceForce(runnable);
        assertFalse(zeroOwnerPool.isEmpty());
        assertEquals(zeroOwnerPool.consume(), runnable);

        for (int i = 0; i < 32; i++) assertFalse(zeroOwnerPool.checkIndicator(i)); // indicators should have been cleaned
    }

    @Test
    public void emptyIndicatorOnSteal() {
        zeroOwnerPool.registerOwner();

        SalsaSCPool otherPool = unpopulatedPool(1);

        for (int i = 0; i < 32; i++) assertFalse(otherPool.checkIndicator(i));
        for (int i = 0; i < 32; i++) otherPool.setIndicator(i);
        for (int i = 0; i < 32; i++) assertTrue(otherPool.checkIndicator(i));

        otherPool.registerProducer(0);
        Runnable runnable = () -> {};
        otherPool.produceForce(runnable);
        assertFalse(otherPool.isEmpty());
        assertTrue(zeroOwnerPool.isEmpty());
        assertEquals(zeroOwnerPool.steal(otherPool), runnable);

        for (int i = 0; i < 32; i++) assertFalse(otherPool.checkIndicator(i)); // indicators should have been cleaned
    }
}