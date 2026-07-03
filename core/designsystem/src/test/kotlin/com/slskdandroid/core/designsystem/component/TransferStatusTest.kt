package com.slskdandroid.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [transferStatusOf] — the aggregate-progress formula shown on parent cards. */
class TransferStatusTest {

    private fun items(vararg items: Pair<TransferPhase, Double>) =
        items.map { TransferItem(it.first, it.second) }

    @Test
    fun `empty group is inactive with zero progress`() {
        val status = transferStatusOf(emptyList())
        assertEquals(0, status.total)
        assertEquals(0f, status.progress, 0.0001f)
        assertFalse(status.isActive)
    }

    @Test
    fun `all completed reads 100 percent`() {
        val status = transferStatusOf(items(TransferPhase.Completed to 100.0, TransferPhase.Completed to 100.0))
        assertEquals(1f, status.progress, 0.0001f)
        assertEquals(2, status.completed)
        assertFalse(status.isActive)
    }

    @Test
    fun `all queued reads 0 percent and is inactive`() {
        val status = transferStatusOf(items(TransferPhase.Queued to 0.0, TransferPhase.Queued to 0.0, TransferPhase.Queued to 0.0))
        assertEquals(0f, status.progress, 0.0001f)
        assertEquals(3, status.queued)
        assertFalse(status.isActive)
    }

    @Test
    fun `in-progress items are weighted by their live percent`() {
        // 5 in-progress at 100%, 3 queued, 2 errored -> (5*1.0)/10 = 0.5, and it IS active.
        val status = transferStatusOf(
            items(
                *Array(5) { TransferPhase.InProgress to 100.0 },
                *Array(3) { TransferPhase.Queued to 0.0 },
                *Array(2) { TransferPhase.Failed to 0.0 },
            ),
        )
        assertTrue(status.isActive)
        assertEquals(0.5f, status.progress, 0.0001f)
        assertEquals(5, status.inProgress)
        assertEquals(3, status.queued)
        assertEquals(2, status.failed)
    }

    @Test
    fun `early in-progress items pull the aggregate below the categorical value`() {
        // 5 in-progress at 40% + 3 queued + 2 errored -> (5*0.4)/10 = 0.2 (not 0.5).
        val status = transferStatusOf(
            items(
                *Array(5) { TransferPhase.InProgress to 40.0 },
                *Array(3) { TransferPhase.Queued to 0.0 },
                *Array(2) { TransferPhase.Failed to 0.0 },
            ),
        )
        assertEquals(0.2f, status.progress, 0.0001f)
    }

    @Test
    fun `bar never reads full while items are still running`() {
        // 5 completed + 5 just-started in-progress -> (5*1.0 + 5*0.0)/10 = 0.5, not 1.0.
        val status = transferStatusOf(
            items(
                *Array(5) { TransferPhase.Completed to 100.0 },
                *Array(5) { TransferPhase.InProgress to 0.0 },
            ),
        )
        assertTrue(status.isActive)
        assertEquals(0.5f, status.progress, 0.0001f)
    }

    @Test
    fun `mixed terminal states are counted per bucket`() {
        val status = transferStatusOf(
            items(
                TransferPhase.Completed to 100.0,
                TransferPhase.Completed to 100.0,
                TransferPhase.Failed to 0.0,
            ),
        )
        assertFalse(status.isActive)
        assertEquals(2, status.completed)
        assertEquals(1, status.failed)
        // Two of three fully done -> 2/3.
        assertEquals(2f / 3f, status.progress, 0.0001f)
    }
}
