package com.weeklyspend.ingest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.app.RemoteInput
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.weeklyspend.budget.Category
import com.weeklyspend.budget.MerchantMatcher
import com.weeklyspend.budget.Money
import com.weeklyspend.data.Repository
import com.weeklyspend.notify.Notifications
import com.weeklyspend.parser.Amounts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Runs [block] off the main thread while keeping the broadcast alive. Never logs SMS content. */
internal fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // Multi-part messages arrive as several PDUs: join them per sender.
        val bySender = parts.filterNotNull().groupBy { it.displayOriginatingAddress ?: it.originatingAddress ?: "" }
        runAsync {
            val repo = Repository.get(context)
            for ((sender, msgs) in bySender) {
                val body = msgs.joinToString("") { it.displayMessageBody ?: it.messageBody ?: "" }
                val received = msgs.minOf { it.timestampMillis }.takeIf { it > 0 } ?: System.currentTimeMillis()
                repo.ingestSms(sender, body, received, live = true)
            }
        }
    }
}

/** One-time scan of the last 60 days of the SMS inbox (also available from Settings). */
class InboxScanWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = Repository.get(applicationContext)
        val since = System.currentTimeMillis() - 60L * 24 * 60 * 60 * 1000
        val cursor = try {
            applicationContext.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} >= ?",
                arrayOf(since.toString()),
                "${Telephony.Sms.DATE} ASC",
            )
        } catch (e: SecurityException) {
            return Result.failure()
        } ?: return Result.failure()
        cursor.use { c ->
            while (c.moveToNext()) {
                val sender = c.getString(0) ?: continue
                val body = c.getString(1) ?: continue
                repo.ingestSms(sender, body, c.getLong(2), live = false)
            }
        }
        repo.settings.inboxScanDone = true
        repo.afterChange()
        return Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "inbox-scan", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<InboxScanWorker>().build(),
            )
        }
    }
}

/** Handles the "Add expense" reply action on the 21:00 reminder: "12.5" or "12.5 coffee". */
class QuickAddReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_AMOUNT)?.toString()?.trim().orEmpty()
        runAsync {
            val parsed = parse(text)
            if (parsed == null) {
                Notifications.reminder(context, error = "Couldn't read \"$text\". Type an amount like 12.5 or 12.5 coffee.")
                return@runAsync
            }
            Repository.get(context).addManual(parsed.first, parsed.second, sideLine = false, note = parsed.third)
            Notifications.reminderDone(context, "Added ${Money.format(parsed.first)} (${parsed.second.label})")
        }
    }

    companion object {
        const val KEY_AMOUNT = "amount"

        /** Returns (amount in halalas, category, note). */
        fun parse(text: String): Triple<Long, Category, String?>? {
            val m = Regex("""(\d+(?:[.,]\d{1,2})?)""").find(text) ?: return null
            val amount = Amounts.toMinor(m.value.replace(',', '.'))?.takeIf { it > 0 } ?: return null
            val rest = text.removeRange(m.range).trim().ifEmpty { null }
            val cat = rest?.let { MerchantMatcher.categoryFromWord(it) } ?: Category.OTHER
            return Triple(amount, cat, rest)
        }
    }
}
