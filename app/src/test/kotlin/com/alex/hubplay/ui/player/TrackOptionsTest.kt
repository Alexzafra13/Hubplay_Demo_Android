package com.alex.hubplay.ui.player

import com.alex.hubplay.data.api.dto.MediaStreamDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackOptionsTest {

    private val streams = listOf(
        MediaStreamDto(streamIndex = 0, streamType = "video", codec = "hevc"),
        MediaStreamDto(streamIndex = 4, streamType = "subtitle", codec = "hdmv_pgs_subtitle", language = "es"),
        MediaStreamDto(streamIndex = 2, streamType = "audio", codec = "aac", channels = 2, language = "en"),
        MediaStreamDto(streamIndex = 1, streamType = "audio", codec = "eac3", channels = 6, language = "es", isDefault = true),
        MediaStreamDto(streamIndex = 3, streamType = "subtitle", codec = "subrip", language = "es", title = "SDH"),
        MediaStreamDto(streamIndex = 5, streamType = "subtitle", codec = "subrip", language = "en", isForced = true),
    )

    @Test
    fun `audio ordinals follow stream index order`() {
        val audio = TrackOptions.audio(streams)
        assertEquals(listOf(0, 1), audio.map { it.ordinal })
        assertEquals("Español · 5.1 · EAC3", audio[0].label)
        assertTrue(audio[0].isDefault)
        assertEquals("Inglés · Estéreo · AAC", audio[1].label)
    }

    @Test
    fun `subtitle ordinals count only subtitles and keep the absolute stream index`() {
        val subs = TrackOptions.subtitles(streams)
        assertEquals(listOf(0, 1, 2), subs.map { it.ordinal })
        assertEquals(listOf(3, 4, 5), subs.map { it.streamIndex })
    }

    @Test
    fun `image subtitles are burn-in and text ones are not`() {
        val subs = TrackOptions.subtitles(streams)
        assertFalse(subs[0].burnIn)
        assertTrue(subs[1].burnIn)
        assertFalse(subs[2].burnIn)
        assertTrue(TrackOptions.isBurnIn("ASS"))
        assertFalse(TrackOptions.isBurnIn("mov_text"))
        assertFalse(TrackOptions.isBurnIn(null))
    }

    @Test
    fun `labels carry title, forced flag and codec`() {
        val subs = TrackOptions.subtitles(streams)
        assertEquals("Español · SDH · SRT", subs[0].label)
        assertEquals("Español · PGS", subs[1].label)
        assertEquals("Inglés · forzados · SRT", subs[2].label)
        assertTrue(subs[2].isForced)
    }

    @Test
    fun `sideloaded list skips burn-in tracks and points at the VTT endpoint`() {
        val side = TrackOptions.sideloaded("item1", TrackOptions.subtitles(streams))
        assertEquals(listOf("/api/v1/stream/item1/subtitles/3", "/api/v1/stream/item1/subtitles/5"), side.map { it.url })
        assertEquals(TrackOptions.sideloadId(3), side[0].id)
        assertEquals("es", side[0].language)
    }

    @Test
    fun `master url carries audio and burn-in params only when chosen`() {
        assertEquals("/api/v1/stream/x/master.m3u8", TrackOptions.masterUrl("x", -1, -1))
        assertEquals("/api/v1/stream/x/master.m3u8?audio=1", TrackOptions.masterUrl("x", 1, -1))
        assertEquals("/api/v1/stream/x/master.m3u8?subtitle=0", TrackOptions.masterUrl("x", -1, 0))
        assertEquals("/api/v1/stream/x/master.m3u8?audio=2&subtitle=1", TrackOptions.masterUrl("x", 2, 1))
    }

    @Test
    fun `three-letter ffprobe language codes get a Spanish display name`() {
        assertEquals("Español", TrackOptions.languageName("spa"))
        assertEquals("Rumano", TrackOptions.languageName("rum"))
        assertEquals("Alemán", TrackOptions.languageName("ger"))
        assertEquals("Inglés", TrackOptions.languageName("en"))
        assertEquals("XYZ", TrackOptions.languageName("xyz"))
        assertEquals(null, TrackOptions.languageName("und"))
    }

    @Test
    fun `repeated labels get numbered so the picker can tell them apart`() {
        val dupes = listOf(
            MediaStreamDto(streamIndex = 3, streamType = "subtitle", codec = "subrip", language = "spa"),
            MediaStreamDto(streamIndex = 4, streamType = "subtitle", codec = "subrip", language = "spa"),
            MediaStreamDto(streamIndex = 5, streamType = "subtitle", codec = "subrip", language = "eng"),
            MediaStreamDto(streamIndex = 6, streamType = "subtitle", codec = "subrip", language = "spa"),
        )
        assertEquals(
            listOf("Español · SRT (1)", "Español · SRT (2)", "Inglés · SRT", "Español · SRT (3)"),
            TrackOptions.subtitles(dupes).map { it.label },
        )
    }

    @Test
    fun `unnamed tracks fall back to a numbered label`() {
        val only = listOf(MediaStreamDto(streamIndex = 7, streamType = "subtitle", codec = "subrip", language = "und"))
        assertEquals("Subtítulo 1 · SRT", TrackOptions.subtitles(only).single().label)
    }
}
