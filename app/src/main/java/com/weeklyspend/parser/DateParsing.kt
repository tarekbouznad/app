package com.weeklyspend.parser

import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Parses the date formats the banks use:
 *  - yyyy-MM-dd HH:mm  (Barq, Alinma purchases)
 *  - yy-MM-dd HH:mm    (Alinma online, e.g. "26-11-22 16:32")
 *  - MM-dd-yyyy HH:mm  (Alinma incoming transfer, e.g. "11-19-2026 17:41")
 * A 4-digit last part where the first part is > 12 is read as dd-MM-yyyy.
 */
object DateParsing {
    private val PATTERN = Regex("""(\d{2,4})[-/](\d{1,2})[-/](\d{2,4})\s+(\d{1,2}):(\d{2})""")

    fun parseLocal(text: String): LocalDateTime? {
        val m = PATTERN.find(text) ?: return null
        val (a, b, c, hh, mm) = m.destructured
        return try {
            val date = when {
                a.length == 4 -> LocalDate.of(a.toInt(), b.toInt(), c.toInt())
                c.length == 4 -> if (a.toInt() > 12) {
                    LocalDate.of(c.toInt(), b.toInt(), a.toInt())
                } else {
                    LocalDate.of(c.toInt(), a.toInt(), b.toInt())
                }
                a.length == 2 && c.length == 2 -> LocalDate.of(2000 + a.toInt(), b.toInt(), c.toInt())
                else -> return null
            }
            LocalDateTime.of(date, LocalTime.of(hh.toInt(), mm.toInt()))
        } catch (e: DateTimeException) {
            null
        }
    }

    /**
     * Returns epoch millis for the date in [text] in [zone], or null if missing, invalid,
     * or implausible relative to [receivedAt] (more than 1 day in the future or 400 days in the past).
     */
    fun parseEpochMillis(text: String, zone: ZoneId, receivedAt: Long): Long? {
        val local = parseLocal(text) ?: return null
        val millis = local.atZone(zone).toInstant().toEpochMilli()
        val day = 24L * 60 * 60 * 1000
        if (millis > receivedAt + day || millis < receivedAt - 400 * day) return null
        return millis
    }
}
