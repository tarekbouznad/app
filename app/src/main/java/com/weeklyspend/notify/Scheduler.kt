package com.weeklyspend.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.weeklyspend.budget.Budget
import com.weeklyspend.data.Repository
import com.weeklyspend.ingest.runAsync
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Exact alarms for the 21:00 reminder, the Sunday 20:00 summary and a just-after-midnight refresh of the
 * widget / persistent notification. Each alarm re-arms itself when it fires; BootReceiver re-arms after reboot.
 * Uses USE_EXACT_ALARM (granted automatically on Android 13+) so it survives Doze, with an inexact fallback.
 */
object Scheduler {
    const val ACTION_DAILY = "com.weeklyspend.DAILY_REMINDER"
    const val ACTION_WEEKLY = "com.weeklyspend.WEEKLY_SUMMARY"
    const val ACTION_MIDNIGHT = "com.weeklyspend.MIDNIGHT"

    fun nextDaily(now: ZonedDateTime, at: LocalTime = LocalTime.of(21, 0)): ZonedDateTime {
        val t = now.with(at).withSecond(0).withNano(0)
        return if (t.isAfter(now)) t else t.plusDays(1)
    }

    fun nextWeekly(now: ZonedDateTime, day: DayOfWeek = DayOfWeek.SUNDAY, at: LocalTime = LocalTime.of(20, 0)): ZonedDateTime {
        val t = now.with(TemporalAdjusters.nextOrSame(day)).with(at).withSecond(0).withNano(0)
        return if (t.isAfter(now)) t else t.plusWeeks(1)
    }

    fun scheduleAll(context: Context) {
        val s = Repository.get(context).settings.current
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        if (s.dailyReminder) set(context, ACTION_DAILY, nextDaily(now)) else cancel(context, ACTION_DAILY)
        if (s.weeklySummary) set(context, ACTION_WEEKLY, nextWeekly(now)) else cancel(context, ACTION_WEEKLY)
        set(context, ACTION_MIDNIGHT, nextDaily(now, LocalTime.of(0, 1)))
    }

    private fun pi(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, action.hashCode(),
            Intent(context, AlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun set(context: Context, action: String, at: ZonedDateTime) {
        val am = context.getSystemService(AlarmManager::class.java)
        val millis = at.toInstant().toEpochMilli()
        val exactOk = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exactOk) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi(context, action))
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi(context, action))
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi(context, action))
        }
    }

    private fun cancel(context: Context, action: String) =
        context.getSystemService(AlarmManager::class.java).cancel(pi(context, action))
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        runAsync {
            val repo = Repository.get(context)
            when (action) {
                Scheduler.ACTION_DAILY -> Notifications.reminder(context)
                Scheduler.ACTION_WEEKLY -> {
                    // Sunday 20:00: summarize the week that ended yesterday (Saturday).
                    val lastWeek = Budget.weekStart(repo.today()).minusDays(7)
                    val r = Budget.weekRangeMillis(lastWeek, repo.zone)
                    val items = repo.db.transactions().range(r.first, r.last).map { it.toSpend() }
                    Notifications.weeklySummary(context, Budget.summary(lastWeek, items, repo.settings.current.budget, repo.zone))
                }
            }
            repo.afterChange()
            Scheduler.scheduleAll(context)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    private val actions = setOf(
        Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED,
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in actions) return
        runAsync {
            Scheduler.scheduleAll(context)
            Repository.get(context).afterChange()
        }
    }
}
