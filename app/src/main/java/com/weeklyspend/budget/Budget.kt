package com.weeklyspend.budget

import com.weeklyspend.parser.TxType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs

/** Money is stored as Long halalas (1 SAR = 100). */
object Money {
    fun format(minor: Long, withCurrency: Boolean = true): String {
        val sign = if (minor < 0) "-" else ""
        val a = abs(minor)
        val s = if (a % 100 == 0L) "${a / 100}" else String.format(Locale.US, "%d.%02d", a / 100, a % 100)
        return if (withCurrency) "$sign$s SAR" else "$sign$s"
    }

    fun plain(minor: Long): String = String.format(Locale.US, "%s%d.%02d", if (minor < 0) "-" else "", abs(minor) / 100, abs(minor) % 100)
}

/**
 * Limits configuration. Before [switchDate] the "before" values apply, on and after it the "after" values.
 * A null daily limit means "weekly / 7".
 */
data class BudgetSettings(
    val weeklyBeforeMinor: Long = 24_500,
    val dailyBeforeMinor: Long? = null,
    val weeklyAfterMinor: Long = 31_500,
    val dailyAfterMinor: Long? = null,
    val switchDate: LocalDate = LocalDate.of(2027, 4, 26),
    val threshold1Pct: Int = 80,
    val threshold2Pct: Int = 100,
) {
    fun weeklyRateOn(date: LocalDate): Long = if (date.isBefore(switchDate)) weeklyBeforeMinor else weeklyAfterMinor

    fun dailyLimitOn(date: LocalDate): Long =
        (if (date.isBefore(switchDate)) dailyBeforeMinor else dailyAfterMinor) ?: divRound(weeklyRateOn(date), 7)

    /**
     * The limit of the Sunday-Saturday week containing [date]. A week that straddles the switch date is
     * prorated day by day: e.g. Sun 2027-04-25 at 35/day + Mon-Sat at 45/day = 305 SAR.
     */
    fun weekLimit(date: LocalDate): Long {
        val start = Budget.weekStart(date)
        val sum = (0L until 7L).sumOf { weeklyRateOn(start.plusDays(it)) }
        return divRound(sum, 7)
    }

    private fun divRound(a: Long, b: Long): Long = (a + b / 2) / b
}

/** Minimal view of a transaction for budget maths. */
data class SpendItem(
    val amountMinor: Long,
    val timestamp: Long,
    val type: TxType,
    val sideLine: Boolean,
    val fixed: Boolean,
    val ownTransfer: Boolean = false,
) {
    /** Only POS/online/manual purchases count, and never side-line or fixed items. Transfers never count. */
    val countsTowardEnvelope: Boolean get() = Budget.countsTowardEnvelope(type, sideLine, fixed)
}

data class WeekStatus(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val today: LocalDate,
    val weekLimit: Long,
    val weekSpent: Long,
    val dayLimit: Long,
    val daySpent: Long,
    /** Days left in the week including today (Sunday = 7, Saturday = 1). */
    val daysLeft: Int,
    val sideLineTotal: Long,
    val fixedTotal: Long,
) {
    val weekLeft: Long get() = weekLimit - weekSpent
    val dayLeft: Long get() = dayLimit - daySpent
    val weekPct: Int get() = Budget.pct(weekSpent, weekLimit)
    val dayPct: Int get() = Budget.pct(daySpent, dayLimit)

    /** What can still be spent per remaining day (including today) to finish on budget. */
    val pacePerDay: Long get() = if (daysLeft <= 0) 0 else maxOf(0, (weekLimit - (weekSpent - daySpent)) / daysLeft)
}

data class DayTotal(val date: LocalDate, val spent: Long, val limit: Long)

data class WeekSummary(
    val weekStart: LocalDate,
    val limit: Long,
    val spent: Long,
    val days: List<DayTotal>,
    val sideLineTotal: Long,
    val fixedTotal: Long,
) {
    val best: DayTotal? get() = days.minByOrNull { it.spent }
    val worst: DayTotal? get() = days.maxByOrNull { it.spent }?.takeIf { it.spent > 0 }
}

object Budget {
    fun countsTowardEnvelope(type: TxType, sideLine: Boolean, fixed: Boolean): Boolean =
        type.isPurchase && !sideLine && !fixed

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

    fun weekRangeMillis(date: LocalDate, zone: ZoneId): LongRange {
        val start = weekStart(date)
        val from = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = start.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return from..to
    }

    fun pct(spent: Long, limit: Long): Int = if (limit <= 0) (if (spent > 0) 999 else 0) else ((spent * 100) / limit).toInt()

    fun localDate(millis: Long, zone: ZoneId): LocalDate = java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun status(today: LocalDate, items: List<SpendItem>, s: BudgetSettings, zone: ZoneId): WeekStatus {
        val start = weekStart(today)
        val end = start.plusDays(6)
        var week = 0L
        var day = 0L
        var side = 0L
        var fixed = 0L
        for (it in items) {
            val d = localDate(it.timestamp, zone)
            if (d.isBefore(start) || d.isAfter(end)) continue
            if (it.type.isPurchase && it.sideLine) side += it.amountMinor
            if (it.type.isPurchase && it.fixed && !it.sideLine) fixed += it.amountMinor
            if (!it.countsTowardEnvelope) continue
            week += it.amountMinor
            if (d == today) day += it.amountMinor
        }
        return WeekStatus(
            weekStart = start,
            weekEnd = end,
            today = today,
            weekLimit = s.weekLimit(today),
            weekSpent = week,
            dayLimit = s.dailyLimitOn(today),
            daySpent = day,
            daysLeft = (7 - (today.dayOfWeek.value % 7)),
            sideLineTotal = side,
            fixedTotal = fixed,
        )
    }

    fun summary(weekStartDate: LocalDate, items: List<SpendItem>, s: BudgetSettings, zone: ZoneId): WeekSummary {
        val start = weekStart(weekStartDate)
        val days = (0L until 7L).map { off ->
            val d = start.plusDays(off)
            DayTotal(d, items.filter { it.countsTowardEnvelope && localDate(it.timestamp, zone) == d }.sumOf { it.amountMinor }, s.dailyLimitOn(d))
        }
        val inWeek = items.filter { val d = localDate(it.timestamp, zone); !d.isBefore(start) && !d.isAfter(start.plusDays(6)) }
        return WeekSummary(
            weekStart = start,
            limit = s.weekLimit(start),
            spent = days.sumOf { it.spent },
            days = days,
            sideLineTotal = inWeek.filter { it.type.isPurchase && it.sideLine }.sumOf { it.amountMinor },
            fixedTotal = inWeek.filter { it.type.isPurchase && it.fixed && !it.sideLine }.sumOf { it.amountMinor },
        )
    }
}
