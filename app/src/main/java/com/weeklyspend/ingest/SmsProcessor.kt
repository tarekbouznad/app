package com.weeklyspend.ingest

import com.weeklyspend.budget.Category
import com.weeklyspend.budget.MerchantMatcher
import com.weeklyspend.budget.MerchantRule
import com.weeklyspend.parser.ParseResult
import com.weeklyspend.parser.ParserRule
import com.weeklyspend.parser.SenderMatch
import com.weeklyspend.parser.SmsParser
import com.weeklyspend.parser.TxType
import java.time.ZoneId

/** A transaction ready to be stored (before dedupe). */
data class TxDraft(
    val sender: String,
    val type: TxType,
    val amountMinor: Long,
    val merchant: String?,
    val timestamp: Long,
    val card: String?,
    val balanceMinor: Long?,
    val category: Category,
    val sideLine: Boolean,
    val fixed: Boolean,
    /** Transfer to one of the user's own names/accounts: ignored entirely. */
    val ownTransfer: Boolean,
)

sealed interface ProcessOutcome {
    data object NotTracked : ProcessOutcome
    data object Ignored : ProcessOutcome
    data class NeedsReview(val sender: String, val body: String, val receivedAt: Long) : ProcessOutcome
    data class Transaction(val draft: TxDraft) : ProcessOutcome
}

class SmsProcessor(
    private val trackedSenders: List<String>,
    parserRules: List<ParserRule>,
    private val merchantRules: List<MerchantRule>,
    private val ownNames: List<String>,
    zone: ZoneId,
) {
    private val parser = SmsParser(parserRules, zone)

    fun process(sender: String, body: String, receivedAt: Long): ProcessOutcome {
        if (!SenderMatch.isTracked(trackedSenders, sender)) return ProcessOutcome.NotTracked
        // Canonical sender = the configured name, so "BARQ APP" and "barq app" dedupe together.
        val canonical = trackedSenders.first { it.isNotBlank() && SenderMatch.matches(it, sender) }.trim().lowercase()
        return when (val r = parser.parse(canonical, body, receivedAt)) {
            ParseResult.Ignored -> ProcessOutcome.Ignored
            ParseResult.Unrecognized -> ProcessOutcome.NeedsReview(canonical, body, receivedAt)
            is ParseResult.Parsed -> {
                val p = r.sms
                val cat = MerchantMatcher.categorize(p.merchant, merchantRules)
                val own = p.type == TxType.TRANSFER_OUT && isOwn(p.merchant, body)
                ProcessOutcome.Transaction(
                    TxDraft(
                        sender = canonical,
                        type = p.type,
                        amountMinor = p.amountMinor,
                        merchant = p.merchant,
                        timestamp = p.timestamp,
                        card = p.card,
                        balanceMinor = p.balanceMinor,
                        category = cat.category,
                        sideLine = p.type.isPurchase && cat.sideLine,
                        fixed = p.type.isPurchase && cat.fixed,
                        ownTransfer = own,
                    ),
                )
            }
        }
    }

    /** True if any of the user's own names/accounts appears in the recipient or the message (case-insensitive). */
    private fun isOwn(merchant: String?, body: String): Boolean {
        val hay = listOfNotNull(merchant, body).map { it.lowercase() }
        return ownNames.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.any { n -> hay.any { it.contains(n) } }
    }
}
