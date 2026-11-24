package com.weeklyspend.export

import com.weeklyspend.budget.Money
import com.weeklyspend.data.ReviewEntity
import com.weeklyspend.data.TransactionEntity
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CsvExporter {
    private val FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun escape(v: String?): String {
        val s = v ?: ""
        return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    fun transactionsCsv(items: List<TransactionEntity>, zone: ZoneId): String = buildString {
        append("id,datetime,sender,type,amount_sar,merchant,category,side_line,fixed,own_transfer,counts_toward_envelope,card,balance_sar,note\r\n")
        for (t in items) {
            val cols = listOf(
                t.id.toString(),
                FMT.format(Instant.ofEpochMilli(t.timestamp).atZone(zone)),
                t.sender, t.type, Money.plain(t.amountMinor), t.merchant, t.category,
                t.sideLine.toString(), t.fixed.toString(), t.ownTransfer.toString(),
                t.toSpend().countsTowardEnvelope.toString(),
                t.card, t.balanceMinor?.let(Money::plain), t.note,
            )
            append(cols.joinToString(",") { escape(it) }).append("\r\n")
        }
    }

    fun reviewCsv(items: List<ReviewEntity>, zone: ZoneId): String = buildString {
        append("received,sender,body\r\n")
        for (r in items) append(listOf(FMT.format(Instant.ofEpochMilli(r.receivedAt).atZone(zone)), r.sender, r.body).joinToString(",") { escape(it) }).append("\r\n")
    }

    /** Writes transactions, then a blank line and the "To review" messages, as one UTF-8 CSV (with BOM for Excel). */
    fun write(out: OutputStream, tx: List<TransactionEntity>, review: List<ReviewEntity>, zone: ZoneId) {
        out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        out.write(transactionsCsv(tx, zone).toByteArray())
        if (review.isNotEmpty()) {
            out.write("\r\n".toByteArray())
            out.write(reviewCsv(review, zone).toByteArray())
        }
        out.flush()
    }
}
