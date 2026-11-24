package com.weeklyspend.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.weeklyspend.R
import com.weeklyspend.budget.Money
import com.weeklyspend.budget.WeekStatus
import com.weeklyspend.budget.WeekSummary
import com.weeklyspend.ingest.QuickAddReceiver
import com.weeklyspend.ui.MainActivity
import java.time.format.TextStyle
import java.util.Locale

object Notifications {
    const val CH_ALERTS = "budget_alerts"
    const val CH_REMINDERS = "reminders"
    const val CH_STATUS = "status"

    private const val ID_ALERT = 1001
    private const val ID_REMINDER = 1002
    private const val ID_SUMMARY = 1003
    private const val ID_PERSISTENT = 1004

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERTS, "Budget alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "When today or this week reaches your alert thresholds"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_REMINDERS, "Reminders and summaries", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Daily 21:00 reminder and Sunday weekly summary"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_STATUS, "Budget status", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Optional persistent 'left this week' notification"
                setShowBadge(false)
            },
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(context: Context, route: String? = null): PendingIntent {
        val i = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            route?.let { putExtra(MainActivity.EXTRA_ROUTE, it) }
        }
        return PendingIntent.getActivity(context, route.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    @Suppress("MissingPermission")
    private fun post(context: Context, id: Int, b: NotificationCompat.Builder) {
        if (!canPost(context)) return
        NotificationManagerCompat.from(context).notify(id, b.build())
    }

    fun budgetAlert(context: Context, title: String, body: String) {
        post(
            context, ID_ALERT,
            NotificationCompat.Builder(context, CH_ALERTS)
                .setSmallIcon(R.drawable.ic_stat_wallet)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(openApp(context))
                .setAutoCancel(true),
        )
    }

    fun reminder(context: Context, error: String? = null) {
        val replyIntent = Intent(context, QuickAddReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val replyPi = PendingIntent.getBroadcast(context, 7, replyIntent, flags)
        val input = RemoteInput.Builder(QuickAddReceiver.KEY_AMOUNT).setLabel("Amount, e.g. 12.5 coffee").build()
        val action = NotificationCompat.Action.Builder(R.drawable.ic_stat_wallet, "Add expense", replyPi)
            .addRemoteInput(input)
            .setAllowGeneratedReplies(false)
            .build()
        post(
            context, ID_REMINDER,
            NotificationCompat.Builder(context, CH_REMINDERS)
                .setSmallIcon(R.drawable.ic_stat_wallet)
                .setContentTitle("Anything missing today?")
                .setContentText(error ?: "Cash or anything without an SMS? Add it here.")
                .setContentIntent(openApp(context, "add"))
                .addAction(action)
                .setAutoCancel(true),
        )
    }

    fun reminderDone(context: Context, text: String) {
        post(
            context, ID_REMINDER,
            NotificationCompat.Builder(context, CH_REMINDERS)
                .setSmallIcon(R.drawable.ic_stat_wallet)
                .setContentTitle(text)
                .setContentIntent(openApp(context))
                .setTimeoutAfter(10_000)
                .setAutoCancel(true),
        )
    }

    fun weeklySummary(context: Context, s: WeekSummary) {
        fun day(d: com.weeklyspend.budget.DayTotal) = "${d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)} ${Money.format(d.spent)}"
        val status = if (s.spent <= s.limit) "under by ${Money.format(s.limit - s.spent)}" else "over by ${Money.format(s.spent - s.limit)}"
        val lines = buildString {
            appendLine("Spent ${Money.format(s.spent)} of ${Money.format(s.limit)} ($status)")
            s.best?.let { appendLine("Best day: ${day(it)}") }
            s.worst?.let { appendLine("Worst day: ${day(it)}") }
            appendLine("Side line: ${Money.format(s.sideLineTotal)}")
            append("Fixed: ${Money.format(s.fixedTotal)}")
        }
        post(
            context, ID_SUMMARY,
            NotificationCompat.Builder(context, CH_REMINDERS)
                .setSmallIcon(R.drawable.ic_stat_wallet)
                .setContentTitle("Week of ${s.weekStart}: ${Money.format(s.spent)} / ${Money.format(s.limit)}")
                .setContentText(lines.lineSequence().first())
                .setStyle(NotificationCompat.BigTextStyle().bigText(lines))
                .setContentIntent(openApp(context, "week"))
                .setAutoCancel(true),
        )
    }

    fun showPersistent(context: Context, st: WeekStatus) {
        post(
            context, ID_PERSISTENT,
            NotificationCompat.Builder(context, CH_STATUS)
                .setSmallIcon(R.drawable.ic_stat_wallet)
                .setContentTitle("Left this week: ${Money.format(st.weekLeft)} (${Money.format(st.dayLeft, false)} today)")
                .setContentText("Spent ${Money.format(st.weekSpent)} of ${Money.format(st.weekLimit)} · ${st.daysLeft} days left")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)
                .setContentIntent(openApp(context)),
        )
    }

    fun cancelPersistent(context: Context) = NotificationManagerCompat.from(context).cancel(ID_PERSISTENT)
}
