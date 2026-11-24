package com.weeklyspend

import com.weeklyspend.Fixtures.RIYADH
import com.weeklyspend.Fixtures.millis
import com.weeklyspend.budget.AlertEvaluator
import com.weeklyspend.budget.AlertLevel
import com.weeklyspend.budget.Budget
import com.weeklyspend.budget.BudgetSettings
import com.weeklyspend.budget.SpendItem
import com.weeklyspend.parser.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BudgetTest {
    private val s = BudgetSettings()
    private fun d(s: String) = LocalDate.parse(s)
    private fun buy(at: String, sar: Double, type: TxType = TxType.POS, side: Boolean = false, fixed: Boolean = false) =
        SpendItem(Math.round(sar * 100), millis(at), type, side, fixed)

    @Test fun weekStartsOnSunday() {
        assertEquals(d("2026-11-22"), Budget.weekStart(d("2026-11-22"))) // Sunday
        assertEquals(d("2026-11-22"), Budget.weekStart(d("2026-11-24"))) // Tuesday
        assertEquals(d("2026-11-22"), Budget.weekStart(d("2026-11-28"))) // Saturday
        assertEquals(d("2026-11-29"), Budget.weekStart(d("2026-11-29"))) // next Sunday
    }

    @Test fun limitsBeforeSwitch() {
        assertEquals(24_500L, s.weekLimit(d("2026-11-24")))
        assertEquals(3_500L, s.dailyLimitOn(d("2026-11-24")))
        assertEquals(24_500L, s.weekLimit(d("2027-04-24"))) // Sat before the switch week
    }

    @Test fun switchWeekIsProrated() {
        // 2027-04-26 is a Monday; its week starts Sun 2027-04-25: 1 day at 35 + 6 days at 45 = 305.
        assertEquals(d("2027-04-25"), Budget.weekStart(d("2027-04-26")))
        assertEquals(30_500L, s.weekLimit(d("2027-04-25")))
        assertEquals(30_500L, s.weekLimit(d("2027-05-01")))
        assertEquals(3_500L, s.dailyLimitOn(d("2027-04-25")))
        assertEquals(4_500L, s.dailyLimitOn(d("2027-04-26")))
    }

    @Test fun limitsAfterSwitch() {
        assertEquals(31_500L, s.weekLimit(d("2027-05-02")))
        assertEquals(4_500L, s.dailyLimitOn(d("2027-06-01")))
    }

    @Test fun customDailyOverride() {
        val c = s.copy(dailyBeforeMinor = 5_000)
        assertEquals(5_000L, c.dailyLimitOn(d("2026-11-24")))
        assertEquals(24_500L, c.weekLimit(d("2026-11-24")))
    }

    @Test fun editedSwitchDate() {
        val c = s.copy(switchDate = d("2026-11-24"))
        assertEquals(divRound(2 * 24_500L + 5 * 31_500L), c.weekLimit(d("2026-11-24")))
    }
    private fun divRound(x: Long) = (x + 3) / 7

    @Test fun statusCountsOnlyEnvelopePurchases() {
        val items = listOf(
            buy("2026-11-22T10:00", 20.0), // Sunday
            buy("2026-11-24T08:00", 4.6), // today
            buy("2026-11-24T09:00", 19.0, TxType.ONLINE, fixed = true), // Noon One: fixed
            buy("2026-11-24T10:00", 30.0, side = true), // Uber: side line
            buy("2026-11-24T11:00", 500.0, TxType.TRANSFER_OUT), // transfer: never counts
            buy("2026-11-24T12:00", 23.0, TxType.TRANSFER_IN),
            buy("2026-11-24T13:00", 10.0, TxType.MANUAL),
            buy("2026-11-21T23:59", 99.0), // previous week (Saturday)
        )
        val st = Budget.status(d("2026-11-24"), items, s, RIYADH)
        assertEquals(3_460L, st.weekSpent)
        assertEquals(1_460L, st.daySpent)
        assertEquals(24_500L - 3_460L, st.weekLeft)
        assertEquals(3_500L - 1_460L, st.dayLeft)
        assertEquals(5, st.daysLeft) // Tue..Sat
        assertEquals(3_000L, st.sideLineTotal)
        assertEquals(1_900L, st.fixedTotal)
        // pace: (245 - 20 spent before today) / 5 days = 45
        assertEquals(4_500L, st.pacePerDay)
    }

    @Test fun daysLeft() {
        assertEquals(7, Budget.status(d("2026-11-22"), emptyList(), s, RIYADH).daysLeft)
        assertEquals(1, Budget.status(d("2026-11-28"), emptyList(), s, RIYADH).daysLeft)
    }

    @Test fun weekSummaryBestWorst() {
        val items = listOf(buy("2026-11-22T10:00", 50.0), buy("2026-11-23T10:00", 10.0), buy("2026-11-23T11:00", 5.0, side = true))
        val sum = Budget.summary(d("2026-11-22"), items, s, RIYADH)
        assertEquals(6_000L, sum.spent)
        assertEquals(d("2026-11-22"), sum.worst!!.date)
        assertEquals(0L, sum.best!!.spent)
        assertEquals(500L, sum.sideLineTotal)
    }

    @Test fun alertLevels() {
        val limit = 24_500L
        assertEquals(AlertLevel.NONE, AlertEvaluator.level(10_000, 15_000, limit, 80, 100))
        assertEquals(AlertLevel.WARNING, AlertEvaluator.level(19_000, 19_600, limit, 80, 100)) // crosses 80%
        assertEquals(AlertLevel.NONE, AlertEvaluator.level(19_600, 20_000, limit, 80, 100)) // already past 80, below 100
        assertEquals(AlertLevel.LIMIT_REACHED, AlertEvaluator.level(20_000, 24_500, limit, 80, 100))
        assertEquals(AlertLevel.LIMIT_REACHED, AlertEvaluator.level(10_000, 30_000, limit, 80, 100)) // jump straight past both
        assertEquals(AlertLevel.STILL_OVER, AlertEvaluator.level(24_500, 25_000, limit, 80, 100)) // every purchase while over
        assertEquals(AlertLevel.NONE, AlertEvaluator.level(25_000, 25_000, limit, 80, 100)) // non-counting item
    }

    @Test fun alertDecisionForDayAndWeek() {
        val before = Budget.status(d("2026-11-24"), listOf(buy("2026-11-24T08:00", 27.0)), s, RIYADH)
        val after = Budget.status(d("2026-11-24"), listOf(buy("2026-11-24T08:00", 27.0), buy("2026-11-24T09:00", 2.0)), s, RIYADH)
        val dec = AlertEvaluator.evaluate(before, after, s)
        assertEquals(AlertLevel.WARNING, dec.day) // 29/35 = 82%
        assertEquals(AlertLevel.NONE, dec.week)
        assertTrue(dec.shouldNotify)
        assertTrue(AlertEvaluator.body(after).contains("5 days left"))
    }

    @Test fun countsTowardEnvelope() {
        assertTrue(Budget.countsTowardEnvelope(TxType.POS, false, false))
        assertTrue(Budget.countsTowardEnvelope(TxType.ONLINE, false, false))
        assertTrue(Budget.countsTowardEnvelope(TxType.MANUAL, false, false))
        assertFalse(Budget.countsTowardEnvelope(TxType.POS, true, false))
        assertFalse(Budget.countsTowardEnvelope(TxType.ONLINE, false, true))
        assertFalse(Budget.countsTowardEnvelope(TxType.TRANSFER_OUT, false, false))
        assertFalse(Budget.countsTowardEnvelope(TxType.TRANSFER_IN, false, false))
        assertFalse(Budget.countsTowardEnvelope(TxType.SALARY, false, false))
    }
}
