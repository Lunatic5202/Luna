package com.luna.music.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicFiltersTest {
    private fun bytes(prefix: String, size: Int = 16) =
        ByteArray(size).also { prefix.toByteArray(Charsets.ISO_8859_1).copyInto(it) }

    @Test
    fun genericRecordingLabelsAreRejected() {
        listOf("AUD-20240101-WA0001.mp3", "audio_123", "AUDIO 7", "aud", "Audio.m4a")
            .forEach { assertTrue(it, isGenericAudioLabel(it)) }
    }

    @Test
    fun realSongTitlesAreKept() {
        listOf("Ethereal", "Audioslave - Like a Stone", "Auditorium", "Audible Heartbeat.flac")
            .forEach { assertFalse(it, isGenericAudioLabel(it)) }
    }

    @Test
    fun containerSignaturesMatchTheirMimeType() {
        assertTrue(matchesAudioSignature(bytes("fLaC"), "audio/flac"))
        assertTrue(matchesAudioSignature(bytes("OggS"), "audio/opus"))
        assertTrue(matchesAudioSignature(bytes("ID3"), "audio/mpeg"))
        assertTrue(matchesAudioSignature(bytes("RIFF\u0000\u0000\u0000\u0000WAVE"), "audio/wav"))
        assertTrue(matchesAudioSignature(bytes("\u0000\u0000\u0000\u0020ftypM4A "), "audio/mp4"))
    }

    @Test
    fun bareMp3FrameSyncIsAccepted() {
        val header = ByteArray(16).also { it[0] = 0xFF.toByte(); it[1] = 0xFB.toByte() }
        assertTrue(matchesAudioSignature(header, "audio/mpeg"))
    }

    @Test
    fun mismatchedOrTruncatedHeadersAreRejected() {
        assertFalse(matchesAudioSignature(bytes("fLaC"), "audio/ogg"))
        assertFalse(matchesAudioSignature(ByteArray(16), "audio/mpeg"))
        assertFalse(matchesAudioSignature(ByteArray(4), "audio/flac"))
        assertFalse(matchesAudioSignature(bytes("fLaC"), "audio/unknown"))
    }
}
