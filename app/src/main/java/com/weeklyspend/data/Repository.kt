package com.weeklyspend.data

import android.content.Context
import com.weeklyspend.budget.AlertEvaluator
import com.weeklyspend.budget.Budget
import com.weeklyspend.budget.Category
import com.weeklyspend.budget.MerchantMatcher
import com.weeklyspend.budget.WeekStatus
import com.weeklyspend.ingest.ProcessOutcome
import com.weeklyspend.ingest.SmsProcessor
import com.weeklyspend.notify.Notifications
import com.weeklyspend.parser.DefaultParserRules
import com.weeklyspend.parser.TxType
import com.weeklyspend.widget.BudgetWidget
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId

/** Central place for all writes, so every change refreshes the widget and the persistent notification. */
class Repository(private val context: Context) {
    val db = AppDatabase.get(context)
    val settings = SettingsStore(context)
    private val seedLock = Mutex()
    private val ingestLock = Mutex()

    val zone: ZoneId get() = ZoneId.systemDefault()
    fun today(): LocalDate = LocalDate.now(zone)

    suspend fun ensureSeeded() = seedLock.withLock {
        if (settings.seeded) return@withLock
        DefaultParserRules.rules.forEach { db.rules().upsertParserRule(ParserRuleEntity.from(it)) }
        MerchantMatcher.defaults.forEach { db.rules().upsertMerchantRule(MerchantRuleEntity.from(it)) }
        settings.seeded = true
    }

    suspend fun resetParserRules() {
        db.rules().clearParserRules()
        DefaultParserRules.rules.forEach { db.rules().upsertParserRule(ParserRuleEntity.from(it)) }
    }

    private suspend fun processor(): SmsProcessor {
        ensureSeeded()
        val s = settings.current
        return SmsProcessor(
            trackedSenders = s.senders,
            parserRules = db.rules().parserRules().map { it.toModel() },
            merchantRules = db.rules().merchantRules().map { it.toModel() },
            ownNames = s.ownNames,
            zone = zone,
        )
    }

    suspend fun weekStatus(date: LocalDate = today()): WeekStatus {
        val r = Budget.weekRangeMillis(date, zone)
        return Budget.status(date, db.transactions().range(r.first, r.last).map { it.toSpend() }, settings.current.budget, zone)
    }

    /**
     * Handles one SMS. [live] = just received (alerts may fire); false = inbox scan (no alerts).
     * Returns true if a new transaction was stored.
     */
    suspend fun ingestSms(sender: String, body: String, receivedAt: Long, live: Boolean): Boolean = ingestLock.withLock {
        when (val o = processor().process(sender, body, receivedAt)) {
            ProcessOutcome.NotTracked, ProcessOutcome.Ignored -> false
            is ProcessOutcome.NeedsReview -> {
                // The live receiver and the inbox scan report slightly different times for the same SMS.
                val window = 10 * 60_000L
                if (db.review().countSimilar(o.sender, o.body, o.receivedAt - window, o.receivedAt + window) == 0) {
                    db.review().insert(ReviewEntity(sender = o.sender, body = o.body, receivedAt = o.receivedAt))
                }
                false
            }
            is ProcessOutcome.Transaction -> {
                val d = o.draft
                val before = weekStatus()
                val id = db.transactions().insert(
                    TransactionEntity(
                        sender = d.sender, amountMinor = d.amountMinor, merchant = d.merchant, timestamp = d.timestamp,
                        card = d.card, balanceMinor = d.balanceMinor, type = d.type.name, category = d.category.name,
                        sideLine = d.sideLine, fixed = d.fixed, ownTransfer = d.ownTransfer,
                    ),
                )
                if (id == -1L) return@withLock false // duplicate (sender + amount + timestamp)
                if (live) maybeAlert(before, d.timestamp)
                afterChange()
                true
            }
        }
    }

    suspend fun addManual(amountMinor: Long, category: Category, sideLine: Boolean, note: String?, timestamp: Long = System.currentTimeMillis()) {
        val before = weekStatus()
        db.transactions().insert(
            TransactionEntity(
                sender = "manual", amountMinor = amountMinor, merchant = note?.takeIf { it.isNotBlank() },
                timestamp = timestamp, card = null, balanceMinor = null, type = TxType.MANUAL.name,
                category = category.name, sideLine = sideLine, fixed = false, ownTransfer = false,
            ),
        )
        maybeAlert(before, timestamp)
        afterChange()
    }

    private suspend fun maybeAlert(before: WeekStatus, timestamp: Long) {
        val today = today()
        if (Budget.weekStart(Budget.localDate(timestamp, zone)) != Budget.weekStart(today)) return
        val after = weekStatus(today)
        val decision = AlertEvaluator.evaluate(before, after, settings.current.budget)
        if (decision.shouldNotify) Notifications.budgetAlert(context, AlertEvaluator.title(decision, after), AlertEvaluator.body(after))
    }

    suspend fun updateTransaction(t: TransactionEntity, alwaysForMerchant: Boolean) {
        db.transactions().update(t)
        val m = t.merchant
        if (alwaysForMerchant && !m.isNullOrBlank()) {
            db.rules().deleteMerchantRulesFor(m)
            db.rules().upsertMerchantRule(MerchantRuleEntity(pattern = m, category = t.category, sideLine = t.sideLine, fixed = t.fixed))
            db.transactions().recategorizeMerchant(m, t.category, t.sideLine, t.fixed)
        }
        afterChange()
    }

    suspend fun deleteTransaction(t: TransactionEntity) {
        db.transactions().delete(t)
        afterChange()
    }

    suspend fun restoreTransaction(t: TransactionEntity) {
        db.transactions().insert(t)
        afterChange()
    }

    /** Refreshes everything that mirrors the budget outside the app. */
    suspend fun afterChange() {
        val st = weekStatus()
        BudgetWidget.updateAll(context, st)
        if (settings.current.persistentNotification) Notifications.showPersistent(context, st) else Notifications.cancelPersistent(context)
    }

    companion object {
        @Suppress("StaticFieldLeak") // holds the application context only
        @Volatile private var instance: Repository? = null
        fun get(context: Context): Repository = instance ?: synchronized(this) {
            instance ?: Repository(context.applicationContext).also { instance = it }
        }
    }
}
