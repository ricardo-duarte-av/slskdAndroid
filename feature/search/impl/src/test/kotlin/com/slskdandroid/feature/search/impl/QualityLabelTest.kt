package com.slskdandroid.feature.search.impl

import com.slskdandroid.core.model.SearchResultFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QualityLabelTest {

    @Test
    fun `bit depth and sample rate render slskd-style`() {
        assertEquals("16/44.1 kHz", qualityLabel(file(bitDepth = 16, sampleRate = 44_100)))
        assertEquals("24/96 kHz", qualityLabel(file(bitDepth = 24, sampleRate = 96_000)))
    }

    @Test
    fun `sample rate alone renders without a depth prefix`() {
        assertEquals("48 kHz", qualityLabel(file(bitDepth = null, sampleRate = 48_000)))
    }

    @Test
    fun `no usable attributes yields null (typical of lossy files)`() {
        assertNull(qualityLabel(file(bitDepth = null, sampleRate = null)))
        assertNull(qualityLabel(file(bitDepth = 0, sampleRate = 0)))
    }
}

private fun file(bitDepth: Int?, sampleRate: Int?) = SearchResultFile(
    filename = "song.flac",
    sizeBytes = 1,
    bitRate = null,
    lengthSeconds = null,
    bitDepth = bitDepth,
    sampleRate = sampleRate,
    isVariableBitRate = null,
    extension = "flac",
    isLocked = false,
)
