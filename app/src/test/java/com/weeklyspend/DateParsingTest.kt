package com.weeklyspend

import com.weeklyspend.Fixtures.RIYADH
import com.weeklyspend.parser.DateParsing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class DateParsingTest {
    @Test fun isoFormat() = assertEquals(LocalDateTime.of(2026, 11, 23, 19, 52), DateParsing.parseLocal("2026-11-23 19:52"))

    @Test fun twoDigitYearFirst() = assertEquals(LocalDateTime.of(2026, 11, 22, 16, 32), DateParsing.parseLocal("On:26-11-22 16:32"))

    @Test fun monthDayYear() = assertEquals(LocalDateTime.of(2026, 11, 19, 17, 41), DateParsing.parseLocal("On 11-19-2026 17:41"))

    @Test fun dayMonthYearWhenFirstPartAbove12() = assertEquals(LocalDateTime.of(2026, 11, 19, 8, 5), DateParsing.parseLocal("19/11/2026 8:05"))

    @Test fun invalidDate() = assertNull(DateParsing.parseLocal("2026-13-45 10:00"))

    @Test fun noDate() = assertNull(DateParsing.parseLocal("no date here"))

    @Test fun interpretedInRiyadh() {
        val received = Fixtures.millis("2026-11-24T00:00")
        // 19:52 Riyadh (UTC+3) = 16:52 UTC
        assertEquals(java.time.Instant.parse("2026-11-23T16:52:00Z").toEpochMilli(), DateParsing.parseEpochMillis("2026-11-23 19:52", RIYADH, received))
    }

    @Test fun implausibleFutureDateRejected() {
        val received = Fixtures.millis("2026-11-24T00:00")
        assertNull(DateParsing.parseEpochMillis("2027-11-23 19:52", RIYADH, received))
    }
}
