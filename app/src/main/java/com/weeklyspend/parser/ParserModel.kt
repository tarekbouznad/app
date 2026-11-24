package com.weeklyspend.parser

/** What a bank SMS represents. MANUAL is only used for expenses typed in by the user. */
enum class TxType(val label: String) {
    POS("POS purchase"),
    ONLINE("Online purchase"),
    TRANSFER_OUT("Transfer out"),
    TRANSFER_IN("Transfer in"),
    SALARY("Salary"),
    OTHER("Other"),
    MANUAL("Manual");

    val isPurchase: Boolean get() = this == POS || this == ONLINE || this == MANUAL
}

/** The action a parser rule assigns to a matching SMS. IGNORE drops the message without storing anything. */
enum class RuleType {
    IGNORE, POS, ONLINE, TRANSFER_OUT, TRANSFER_IN, SALARY, OTHER;

    fun toTxType(): TxType? = when (this) {
        IGNORE -> null
        POS -> TxType.POS
        ONLINE -> TxType.ONLINE
        TRANSFER_OUT -> TxType.TRANSFER_OUT
        TRANSFER_IN -> TxType.TRANSFER_IN
        SALARY -> TxType.SALARY
        OTHER -> TxType.OTHER
    }
}

/**
 * One row of the parser table.
 *
 * - [sender]: a sender name from the senders list (compared case-insensitively, ignoring spaces), or "*" for any.
 * - [match]: regex that must be found in the body for this row to apply.
 * - Field regexes: the first capture group is the value. Blank means "use the default for that field"
 *   (see [FieldDefaults]). All regexes are compiled case-insensitive and multi-line.
 * - Rows are tried in ascending [priority]; the first matching enabled row wins.
 */
data class ParserRule(
    val id: Long = 0,
    val sender: String,
    val type: RuleType,
    val match: String,
    val amount: String = "",
    val merchant: String = "",
    val date: String = "",
    val card: String = "",
    val balance: String = "",
    val priority: Int = 100,
    val enabled: Boolean = true,
    val label: String = "",
)

object FieldDefaults {
    /** "Amount: 4.60 SAR", "amount:7.50 SAR", "Amount 19.00 SAR", "4.60 SAUDI RIYAL", "Incoming 23 SAR". */
    const val AMOUNT = """(?:amount\s*:?\s*)?(\d[\d,]*(?:\.\d{1,3})?)\s*(?:SAR|SAUDI\s+RIYALS?|ريال|ر\.س)"""

    /** "At: X", "atX", "At X", "at:X", "To:X" — a line starting with at/to/from (but not "To Account"). */
    const val MERCHANT = """^\s*(?:at|to|from)(?!\s*account)\s*:?\s*(.+?)\s*$"""

    /** yyyy-MM-dd HH:mm, yy-MM-dd HH:mm or MM-dd-yyyy HH:mm (also with '/'). */
    const val DATE = """(\d{2,4}[-/]\d{1,2}[-/]\d{2,4}\s+\d{1,2}:\d{2})"""

    /** "card: **9715", "card **9715", "card: 4026*", "card:4026*". */
    const val CARD = """card\s*:?\s*\**\s*(\d{4})"""

    /** "Balance: 314.21", "Balance 323.41". */
    const val BALANCE = """balance\s*:?\s*(\d[\d,]*(?:\.\d{1,3})?)"""
}

data class ParsedSms(
    val type: TxType,
    val amountMinor: Long,
    val merchant: String?,
    /** Epoch millis. From the SMS text when present and plausible, otherwise the received time. */
    val timestamp: Long,
    val dateFromText: Boolean,
    val card: String?,
    val balanceMinor: Long?,
    val ruleId: Long,
)

sealed interface ParseResult {
    /** One-time codes and service messages: drop completely, store nothing. */
    data object Ignored : ParseResult

    /** From a tracked sender but no rule could extract a transaction: goes to "To review". */
    data object Unrecognized : ParseResult

    data class Parsed(val sms: ParsedSms) : ParseResult
}
