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
    public void tryProduceWithoutRegistration() {
        assertThrows(IllegalCallerException.class, () -> zeroOwnerPool.tryProduce(() -> {
        }));
        assertThrows(IllegalCallerException.class, () -> zeroOwnerPool.produce(() -> {
        }));
    }

    @Test
    public void tryProduceToEmptyPool() {
        zeroOwnerPool.registerCurrentThreadAsProducer(0);

        // produce fails since newly initialized pool has zero capacity
        assertFalse(zeroOwnerPool.tryProduce(() -> {
        }));
        assertTrue(zeroOwnerPool.isEmpty());
    }


    @Test
    public void produceToEmptyPool() {
        zeroOwnerPool.registerCurrentThreadAsProducer(0);

        // produceForce expands zero capacity pool
        zeroOwnerPool.produce(() -> {
        });
        assertFalse(zeroOwnerPool.isEmpty());
    }

    @Test
    public void consumeWithoutRegistration() {
        assertThrows(IllegalCallerException.class, () -> zeroOwnerPool.consume());
    }

    @Test
    public void consumeFromEmpty() {
        zeroOwnerPool.registerCurrentThreadAsOwner();
        assertNull(zeroOwnerPool.consume());
    }

    @Test
    public void consumeNormally() {
        zeroOwnerPool.registerCurrentThreadAsProducer(0);
        Runnable runnable = () -> {
        };
        zeroOwnerPool.produce(runnable);

        zeroOwnerPool.registerCurrentThreadAsOwner();
        assertEquals(zeroOwnerPool.consume(), runnable);
        assertNull(zeroOwnerPool.consume());
        assertTrue(zeroOwnerPool.isEmpty());
    }

    @Test
    public void stealFromYourself() {
        zeroOwnerPool.registerCurrentThreadAsOwner();
        assertThrows(IllegalArgumentException.class, () -> zeroOwnerPool.steal(zeroOwnerPool));
    }

    @Test
    public void stealFromEmptyOther() {
        zeroOwnerPool.registerCurrentThreadAsOwner();
        assertNull(zeroOwnerPool.steal(unpopulatedPool(1)));
    }

    @Test
    public void stealNormally() {
        SalsaSCPool otherPool = unpopulatedPool(1);
        otherPool.registerCurrentThreadAsProducer(0);

        Runnable runnable = () -> {
        };
        otherPool.produce(runnable);

        assertFalse(otherPool.isEmpty());
        assertTrue(zeroOwnerPool.isEmpty());

        zeroOwnerPool.registerCurrentThreadAsOwner();

        assertEquals(zeroOwnerPool.steal(otherPool), runnable);
        assertTrue(otherPool.isEmpty());
        assertTrue(zeroOwnerPool.isEmpty()); // true since the task was removed from the pool after stealing
    }

    @Test
    public void stealChunkWith2Tasks() {
        SalsaSCPool otherPool = unpopulatedPool(1);
        otherPool.registerCurrentThreadAsProducer(0);

        Runnable runnable0 = () -> {
        };
        Runnable runnable1 = () -> {
        };
        otherPool.produce(runnable0);
        otherPool.produce(runnable1);

        assertFalse(otherPool.isEmpty());
        assertTrue(zeroOwnerPool.isEmpty());

        zeroOwnerPool.registerCurrentThreadAsOwner();

        assertEquals(zeroOwnerPool.steal(otherPool), runnable0);
        assertTrue(otherPool.isEmpty());
        assertFalse(zeroOwnerPool.isEmpty()); // false since second task is not extracted yet
        assertEquals(zeroOwnerPool.consume(), runnable1); // true since second task is not extracted yet
    }

    @Test
    public void emptyIndicatorOnConsume() {
        zeroOwnerPool.registerCurrentThreadAsOwner();
        for (int i = 0; i < 32; i++) assertFalse(zeroOwnerPool.checkIndicator(i));
        for (int i = 0; i < 32; i++) zeroOwnerPool.setIndicator(i);
        for (int i = 0; i < 32; i++) assertTrue(zeroOwnerPool.checkIndicator(i));

        Runnable runnable = () -> {
        };
        zeroOwnerPool.registerCurrentThreadAsProducer(0);
        zeroOwnerPool.produce(runnable);
        assertFalse(zeroOwnerPool.isEmpty());
        assertEquals(zeroOwnerPool.consume(), runnable);

        for (int i = 0; i < 32; i++)
            assertFalse(zeroOwnerPool.checkIndicator(i)); // indicators should have been cleaned
    }

    @Test
    public void emptyIndicatorOnSteal() {
        zeroOwnerPool.registerCurrentThreadAsOwner();

        SalsaSCPool otherPool = unpopulatedPool(1);

        for (int i = 0; i < 32; i++) assertFalse(otherPool.checkIndicator(i));
        for (int i = 0; i < 32; i++) otherPool.setIndicator(i);
        for (int i = 0; i < 32; i++) assertTrue(otherPool.checkIndicator(i));

        otherPool.registerCurrentThreadAsProducer(0);
        Runnable runnable = () -> {
        };
        otherPool.produce(runnable);
        assertFalse(otherPool.isEmpty());
        assertTrue(zeroOwnerPool.isEmpty());
        assertEquals(zeroOwnerPool.steal(otherPool), runnable);

        for (int i = 0; i < 32; i++) assertFalse(otherPool.checkIndicator(i)); // indicators should have been cleaned
    }
}