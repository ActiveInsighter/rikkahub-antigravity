package me.rerere.rikkahub.data.agent

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgentJobInvalidationsTest {
    @Test
    fun pushesKeepActiveJobIncrementalUntilStreamGoesQuiet() = runTest {
        val updates = AgentJobInvalidations(initiallyActive = true)
        assertTrue(updates.next())

        repeat(3) {
            val next = async { updates.next() }
            advanceTimeBy(30_000)
            assertFalse(next.isCompleted)
            updates.invalidate(refreshJob = false)
            assertFalse(next.await())
        }

        val next = async { updates.next() }
        advanceTimeBy(44_999)
        assertFalse(next.isCompleted)
        assertTrue(next.await())
    }

    @Test
    fun terminalJobWaitsForARealEventWithoutPolling() = runTest {
        val updates = AgentJobInvalidations(initiallyActive = true)
        assertTrue(updates.next())
        updates.setActive(false)
        val next = async { updates.next() }

        advanceTimeBy(120_000)
        assertFalse(next.isCompleted)

        updates.invalidate(refreshJob = true)
        assertTrue(next.await())
    }
}
