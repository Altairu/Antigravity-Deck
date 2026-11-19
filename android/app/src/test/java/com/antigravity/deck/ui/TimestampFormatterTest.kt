package com.antigravity.deck.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class TimestampFormatterTest {

    private val tokyoZone: ZoneId = ZoneId.of("Asia/Tokyo")
    private val newYorkZone: ZoneId = ZoneId.of("America/New_York")

    @Test
    fun formatUtcTimestamp_convertsToTokyoTime() {
        assertEquals("10:37", formatShortTime("2026-11-19T01:37:24Z", tokyoZone))
        assertEquals("2026-11-19 10:37", formatShortDateTime("2026-11-19T01:37:24Z", tokyoZone))

        assertEquals("10:37", formatShortTime("2026-11-19T01:37:24.123Z", tokyoZone))
        assertEquals("2026-11-19 10:37", formatShortDateTime("2026-11-19T01:37:24.123Z", tokyoZone))
    }

    @Test
    fun formatUtcTimestamp_rollsDateAcrossMidnightToTokyoTime() {
        assertEquals("01:37", formatShortTime("2026-11-18T16:37:24Z", tokyoZone))
        assertEquals("2026-11-19 01:37", formatShortDateTime("2026-11-18T16:37:24Z", tokyoZone))
    }

    @Test
    fun formatOffsetTimestamp_convertsAcrossZones() {
        assertEquals("10:37", formatShortTime("2026-11-19T01:37:24+00:00", tokyoZone))
        assertEquals("2026-11-19 10:37", formatShortDateTime("2026-11-19T01:37:24+00:00", tokyoZone))

        assertEquals("20:37", formatShortTime("2026-11-19T10:37:24+09:00", newYorkZone))
        assertEquals("2026-11-18 20:37", formatShortDateTime("2026-11-19T10:37:24+09:00", newYorkZone))
    }

    @Test
    fun formatLocalDateTimeWithoutOffset_preservesLocalClock() {
        assertEquals("10:37", formatShortTime("2026-11-19T10:37:24", tokyoZone))
        assertEquals("2026-11-19 10:37", formatShortDateTime("2026-11-19T10:37:24", tokyoZone))
    }

    @Test
    fun formatBlankOrInvalidTimestamp_returnsNull() {
        assertNull(formatShortTime(null, tokyoZone))
        assertNull(formatShortTime("", tokyoZone))
        assertNull(formatShortTime("   ", tokyoZone))
        assertNull(formatShortTime("invalid-timestamp", tokyoZone))

        assertNull(formatShortDateTime(null, tokyoZone))
        assertNull(formatShortDateTime("", tokyoZone))
        assertNull(formatShortDateTime("   ", tokyoZone))
        assertNull(formatShortDateTime("invalid-timestamp", tokyoZone))
    }
}
