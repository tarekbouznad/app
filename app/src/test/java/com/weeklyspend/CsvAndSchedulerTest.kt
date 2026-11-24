package com.weeklyspend

import com.weeklyspend.Fixtures.RIYADH
import com.weeklyspend.budget.Money
import com.weeklyspend.export.CsvExporter
import com.weeklyspend.notify.Scheduler
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime
import java.time.ZonedDateTime

class CsvAndSchedulerTest {
    @Test fun csvEscaping() {
        assertEquals("plain", CsvExporter.escape("plain"))
        assertEquals("\"a,b\"", CsvExporter.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvExporter.escape("say \"hi\""))
        assertEquals("", CsvExporter.escape(null))
    }

    @Test fun money() {
        assertEquals("245 SAR", Money.format(24_500))
        assertEquals("4.60 SAR", Money.format(460))
        assertEquals("-12.05 SAR", Money.format(-1_205))
        assertEquals("4.60", Money.plain(460))
    }

    @Test fun nextDailyAt21() {
        val before = ZonedDateTime.of(2026, 11, 24, 20, 0, 0, 0, RIYADH)
        assertEquals(ZonedDateTime.of(2026, 11, 24, 21, 0, 0, 0, RIYADH), Scheduler.nextDaily(before))
        val after = ZonedDateTime.of(2026, 11, 24, 21, 0, 0, 0, RIYADH)
        assertEquals(ZonedDateTime.of(2026, 11, 25, 21, 0, 0, 0, RIYADH), Scheduler.nextDaily(after))
        assertEquals(LocalTime.of(0, 1), Scheduler.nextDaily(after, LocalTime.of(0, 1)).toLocalTime())
    }

    @Test fun nextSundayAt20() {
        val tue = ZonedDateTime.of(2026, 11, 24, 12, 0, 0, 0, RIYADH)
        assertEquals(ZonedDateTime.of(2026, 11, 29, 20, 0, 0, 0, RIYADH), Scheduler.nextWeekly(tue))
        val sunEarly = ZonedDateTime.of(2026, 11, 29, 19, 0, 0, 0, RIYADH)
        assertEquals(ZonedDateTime.of(2026, 11, 29, 20, 0, 0, 0, RIYADH), Scheduler.nextWeekly(sunEarly))
        val sunLate = ZonedDateTime.of(2026, 11, 29, 20, 30, 0, 0, RIYADH)
        assertEquals(ZonedDateTime.of(2026, 12, 6, 20, 0, 0, 0, RIYADH), Scheduler.nextWeekly(sunLate))
    }
}
