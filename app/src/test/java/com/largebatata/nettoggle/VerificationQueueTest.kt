package com.largebatata.nettoggle

import org.junit.Assert.*
import org.junit.Test

class VerificationQueueTest {
    @Test fun eachPackageAppearsOnceAndBatchIsBounded() {
        val queue = VerificationQueue(2)
        val token = queue.restart(listOf("a", "b", "a", "c"))
        assertEquals(listOf("a", "b"), queue.next(token))
        assertTrue(queue.complete(token))
        assertEquals(listOf("c"), queue.next(token))
        assertTrue(queue.complete(token))
        assertTrue(queue.next(token).isEmpty())
        assertFalse(queue.running)
    }

    @Test fun cannotSubmitDuplicateWhileBatchIsInFlight() {
        val queue = VerificationQueue(1)
        val token = queue.restart(listOf("a", "b"))
        assertEquals(listOf("a"), queue.next(token))
        assertTrue(queue.next(token).isEmpty())
        assertTrue(queue.running)
        assertTrue(queue.complete(token))
        assertEquals(listOf("b"), queue.next(token))
    }

    @Test fun canceledReadCannotPublishOrAdvanceTheNewSweep() {
        val queue = VerificationQueue(1)
        val old = queue.restart(listOf("old", "never"))
        queue.next(old)
        queue.cancel()
        val fresh = queue.restart(listOf("new"))
        assertFalse(queue.complete(old))
        assertTrue(queue.next(old).isEmpty())
        assertEquals(listOf("new"), queue.next(fresh))
        assertFalse(queue.complete(old))
        assertTrue(queue.complete(fresh))
    }

    @Test fun cancelDropsAllPendingReadsForAWrite() {
        val queue = VerificationQueue()
        val token = queue.restart(listOf("a", "b"))
        queue.next(token)
        queue.cancel()
        assertFalse(queue.running)
        assertFalse(queue.complete(token))
    }

    @Test fun emptySweepDoesNotStayBusy() {
        val queue = VerificationQueue()
        val token = queue.restart(emptyList())
        assertTrue(queue.next(token).isEmpty())
        assertFalse(queue.running)
        assertFalse(queue.complete(token))
    }
}
