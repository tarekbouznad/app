package com.weeklyspend.budget

enum class AlertLevel { NONE, WARNING, LIMIT_REACHED, STILL_OVER }

data class AlertDecision(val day: AlertLevel, val week: AlertLevel) {
    val shouldNotify: Boolean get() = day != AlertLevel.NONE || week != AlertLevel.NONE
}

/**
 * Decides whether a newly counted purchase triggers a budget alert.
 *  - crossing threshold 1 (default 80%) -> WARNING
 *  - crossing threshold 2 (default 100%) -> LIMIT_REACHED
 *  - any further purchase while already at/over threshold 2 -> STILL_OVER (fires every time)
 * Stateless: compares spending before and after the new purchase.
 */
object AlertEvaluator {
    fun level(before: Long, after: Long, limit: Long, t1: Int, t2: Int): AlertLevel {
        if (after <= before) return AlertLevel.NONE
        val pb = Budget.pct(before, limit)
        val pa = Budget.pct(after, limit)
        return when {
            pb >= t2 -> AlertLevel.STILL_OVER
            pa >= t2 -> AlertLevel.LIMIT_REACHED
            pa >= t1 && pb < t1 -> AlertLevel.WARNING
            else -> AlertLevel.NONE
        }
    }

    /** [before] is the status without the new purchase, [after] includes it. */
    fun evaluate(before: WeekStatus, after: WeekStatus, s: BudgetSettings): AlertDecision = AlertDecision(
        day = level(before.daySpent, after.daySpent, after.dayLimit, s.threshold1Pct, s.threshold2Pct),
        week = level(before.weekSpent, after.weekSpent, after.weekLimit, s.threshold1Pct, s.threshold2Pct),
    )

    fun title(d: AlertDecision, after: WeekStatus): String {
        fun word(l: AlertLevel, scope: String, pct: Int) = when (l) {
            AlertLevel.WARNING -> "$scope at $pct%"
            AlertLevel.LIMIT_REACHED -> "$scope limit reached"
            AlertLevel.STILL_OVER -> "Over $scope budget"
            AlertLevel.NONE -> null
        }
        val parts = listOfNotNull(word(d.week, "week", after.weekPct), word(d.day, "today", after.dayPct))
        return parts.joinToString(" · ").replaceFirstChar { it.uppercase() }
    }

    fun body(after: WeekStatus): String {
        val days = if (after.daysLeft == 1) "1 day left" else "${after.daysLeft} days left"
        return "Left this week: ${Money.format(after.weekLeft)} · today: ${Money.format(after.dayLeft)} · $days"
    }
}
