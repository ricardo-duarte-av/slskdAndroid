package com.slskdandroid.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/**
 * Replaces the identical QualityLabelTest that used to exist in both feature:search:impl and
 * feature:browse:impl. Locale is passed explicitly so these stay deterministic on any machine —
 * the previous copies would have failed on a JVM defaulting to a comma-decimal locale.
 */
class FormattersTest {

    @Test
    fun `quality label pairs bit depth with sample rate`() {
        assertEquals("16/44.1 kHz", qualityLabel(bitDepth = 16, sampleRate = 44_100, locale = Locale.US))
        assertEquals("24/96 kHz", qualityLabel(bitDepth = 24, sampleRate = 96_000, locale = Locale.US))
    }

    @Test
    fun `quality label falls back to the sample rate alone`() {
        assertEquals("48 kHz", qualityLabel(bitDepth = null, sampleRate = 48_000, locale = Locale.US))
    }

    @Test
    fun `quality label is null when neither is reported`() {
        assertNull(qualityLabel(bitDepth = null, sampleRate = null))
        assertNull(qualityLabel(bitDepth = 0, sampleRate = 0))
    }

    @Test
    fun `sample rate decimal separator follows the locale`() {
        assertEquals("16/44.1 kHz", qualityLabel(16, 44_100, Locale.US))
        assertEquals("16/44,1 kHz", qualityLabel(16, 44_100, Locale.GERMANY))
    }

    @Test
    fun `duration is minutes and zero-padded seconds`() {
        assertEquals("0:05", formatDuration(5, Locale.US))
        assertEquals("3:07", formatDuration(187, Locale.US))
        assertEquals("70:00", formatDuration(4_200, Locale.US))
    }
}
