package com.weeklyspend.parser

import java.math.BigDecimal
import java.time.ZoneId
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/** Hard-coded guard that runs before the editable table, so a table edit can never cause a code to be stored. */
object OtpGuard {
    private val OTP = Regex(
        """\b(otp|one[- ]?time\s+(password|code|pin)|verification\s+code|passcode|password)\b|\bcode\s*(is|:)?\s*\d{3,8}\b|رمز|كلمة\s*المرور""",
        RegexOption.IGNORE_CASE,
    )

    fun looksLikeOtp(body: String): Boolean = OTP.containsMatchIn(body)
}

object SenderMatch {
    fun normalize(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    /** Sender names are matched case-insensitively, ignoring spaces and punctuation ("BARQ APP" == "barq app" == "BarqApp"). */
    fun matches(configured: String, smsSender: String): Boolean {
        if (configured.trim() == "*") return true
        val c = normalize(configured)
        return c.isNotEmpty() && c == normalize(smsSender)
    }

    fun isTracked(senders: List<String>, smsSender: String): Boolean =
        senders.any { it.isNotBlank() && matches(it, smsSender) }
}

object Amounts {
    /** "1,234.50" -> 123450 (halalas). Returns null if not a number. */
    fun toMinor(text: String): Long? = try {
        BigDecimal(text.replace(",", "").trim()).movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
    } catch (e: Exception) {
        null
    }
}

class SmsParser(rules: List<ParserRule>, private val zone: ZoneId) {

    private class Compiled(
        val rule: ParserRule,
        val match: Pattern,
        val amount: Pattern,
        val merchant: Pattern,
        val date: Pattern,
        val card: Pattern,
        val balance: Pattern,
    )

    private val compiled: List<Compiled> = rules
        .filter { it.enabled }
        .sortedWith(compareBy({ it.priority }, { it.id }))
        .mapNotNull { r ->
            try {
                Compiled(
                    rule = r,
                    match = compile(r.match),
                    amount = compile(r.amount.ifBlank { FieldDefaults.AMOUNT }),
                    merchant = compile(r.merchant.ifBlank { FieldDefaults.MERCHANT }),
                    date = compile(r.date.ifBlank { FieldDefaults.DATE }),
                    card = compile(r.card.ifBlank { FieldDefaults.CARD }),
                    balance = compile(r.balance.ifBlank { FieldDefaults.BALANCE }),
                )
            } catch (e: PatternSyntaxException) {
                null // an invalid row is skipped; Settings shows a validation error when editing
            }
        }

    fun parse(sender: String, body: String, receivedAt: Long): ParseResult {
        if (OtpGuard.looksLikeOtp(body)) return ParseResult.Ignored
        val text = body.replace("\r\n", "\n").replace('\r', '\n')

        for (c in compiled) {
            if (!SenderMatch.matches(c.rule.sender, sender)) continue
            if (!c.match.matcher(text).find()) continue
            val txType = c.rule.type.toTxType() ?: return ParseResult.Ignored

            val amount = group(c.amount, text)?.let(Amounts::toMinor) ?: continue
            val merchant = group(c.merchant, text)?.let(::cleanMerchant)?.takeIf { it.isNotEmpty() }
            val dateText = group(c.date, text)
            val parsedTs = dateText?.let { DateParsing.parseEpochMillis(it, zone, receivedAt) }
            val card = group(c.card, text)
            val balance = group(c.balance, text)?.let(Amounts::toMinor)

            return ParseResult.Parsed(
                ParsedSms(
                    type = txType,
                    amountMinor = amount,
                    merchant = merchant,
                    timestamp = parsedTs ?: (receivedAt / 60_000L) * 60_000L,
                    dateFromText = parsedTs != null,
                    card = card,
                    balanceMinor = balance,
                    ruleId = c.rule.id,
                ),
            )
        }
        return ParseResult.Unrecognized
    }

    private fun group(p: Pattern, text: String): String? {
        val m = p.matcher(text)
        if (!m.find()) return null
        return if (m.groupCount() >= 1) m.group(1) else m.group()
    }

    companion object {
        fun compile(regex: String): Pattern =
            Pattern.compile(regex, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE or Pattern.MULTILINE)

        /** Returns an error message if [regex] is invalid, else null. */
        fun validate(regex: String): String? = try {
            compile(regex); null
        } catch (e: PatternSyntaxException) {
            e.description ?: "Invalid regex"
        }

        fun cleanMerchant(raw: String): String =
            raw.trim().trimEnd(';', ',', '.', '*').trim().replace(Regex("\\s+"), " ")
    }
}
