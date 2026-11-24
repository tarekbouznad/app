package com.weeklyspend

import com.weeklyspend.Fixtures.RECEIVED
import com.weeklyspend.Fixtures.RIYADH
import com.weeklyspend.Fixtures.millis
import com.weeklyspend.parser.DefaultParserRules
import com.weeklyspend.parser.ParseResult
import com.weeklyspend.parser.ParsedSms
import com.weeklyspend.parser.SmsParser
import com.weeklyspend.parser.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParserTest {
    private val parser = SmsParser(DefaultParserRules.rules, RIYADH)

    private fun parsed(sender: String, body: String, received: Long = RECEIVED): ParsedSms {
        val r = parser.parse(sender, body, received)
        assertTrue("expected Parsed but was $r", r is ParseResult.Parsed)
        return (r as ParseResult.Parsed).sms
    }

    @Test fun barq1_posVisa() {
        val p = parsed("barq app", Fixtures.BARQ_1)
        assertEquals(TxType.POS, p.type)
        assertEquals(460L, p.amountMinor)
        assertEquals("KING FAHAD U O P M", p.merchant)
        assertEquals("9715", p.card)
        assertEquals(31421L, p.balanceMinor)
        assertEquals(millis("2026-11-23T19:52"), p.timestamp)
        assertTrue(p.dateFromText)
    }

    @Test fun barq2_posMada_gluedAt_lowercaseAmountNoSpace() {
        val p = parsed("BARQ APP", Fixtures.BARQ_2)
        assertEquals(TxType.POS, p.type)
        assertEquals(750L, p.amountMinor)
        assertEquals("Shatirti", p.merchant)
        assertEquals("9310", p.card)
        assertEquals(34735L, p.balanceMinor)
        assertEquals(millis("2026-11-22T22:02"), p.timestamp)
    }

    @Test fun barq3_online_noColons() {
        val p = parsed("Barq App", Fixtures.BARQ_3)
        assertEquals(TxType.ONLINE, p.type)
        assertEquals(1900L, p.amountMinor)
        assertEquals("Noon One Subscription", p.merchant)
        assertEquals("9715", p.card)
        assertEquals(32341L, p.balanceMinor)
        assertEquals(millis("2026-11-23T06:58"), p.timestamp)
    }

    @Test fun alinma4_pos_saudiRiyal() {
        val p = parsed("alinma", Fixtures.ALINMA_4)
        assertEquals(TxType.POS, p.type)
        assertEquals(460L, p.amountMinor)
        assertEquals("KING FAHAD U O P M", p.merchant)
        assertEquals("4026", p.card)
        assertNull(p.balanceMinor)
        assertEquals(millis("2026-11-11T12:45"), p.timestamp)
    }

    @Test fun alinma5_online_yyDate() {
        val p = parsed("Alinma", Fixtures.ALINMA_5)
        assertEquals(TxType.ONLINE, p.type)
        assertEquals(4333L, p.amountMinor)
        assertEquals("TABBY", p.merchant)
        assertEquals("4026", p.card)
        assertEquals(millis("2026-11-22T16:32"), p.timestamp)
    }

    @Test fun alinma6_transferIncoming_usDate() {
        val p = parsed("alinma", Fixtures.ALINMA_6)
        assertEquals(TxType.TRANSFER_IN, p.type)
        assertEquals(2300L, p.amountMinor)
        assertTrue(p.merchant!!.startsWith("HOUSSEM ED"))
        assertEquals(millis("2026-11-19T17:41"), p.timestamp)
    }

    @Test fun alinma7_debitTransfer() {
        val p = parsed("alinma", Fixtures.ALINMA_7)
        assertEquals(TxType.TRANSFER_OUT, p.type)
        assertEquals(50000L, p.amountMinor)
        assertEquals("MOHAMMED AZZOUZ KAIROUANI", p.merchant)
        assertEquals(millis("2026-11-23T17:26"), p.timestamp)
    }

    @Test fun alinma8_oneTimeCode_ignored() {
        assertSame(ParseResult.Ignored, parser.parse("alinma", Fixtures.ALINMA_8, RECEIVED))
    }

    @Test fun alinma8_oneTimeCode_ignoredEvenWithEmptyTable() {
        // The OTP guard is hard-coded, so editing/removing table rows can never store a code.
        assertSame(ParseResult.Ignored, SmsParser(emptyList(), RIYADH).parse("alinma", Fixtures.ALINMA_8, RECEIVED))
    }

    @Test fun alinma9_activation_ignored() {
        assertSame(ParseResult.Ignored, parser.parse("alinma", Fixtures.ALINMA_9, RECEIVED))
    }

    @Test fun unknownFormat_unrecognized() {
        assertSame(ParseResult.Unrecognized, parser.parse("alinma", "Your statement is ready", RECEIVED))
    }

    @Test fun missingDate_fallsBackToReceivedTime() {
        val p = parsed("barq app", "POS Purchase\nVisa card: **9715\nAmount: 12.00 SAR\nAt: Dunkin")
        assertEquals(1200L, p.amountMinor)
        assertEquals(RECEIVED, p.timestamp)
        assertTrue(!p.dateFromText)
    }

    @Test fun windowsLineEndings_andThousands() {
        val p = parsed("barq app", "POS Purchase\r\nVisa card: **9715\r\nAmount: 1,204.50 SAR\r\nAt: Kiabi\r\n2026-11-23 10:00")
        assertEquals(120450L, p.amountMinor)
        assertEquals("Kiabi", p.merchant)
    }

    @Test fun merchantWithCodeInName_notTreatedAsOtp() {
        val p = parsed("barq app", "POS Purchase\nVisa card: **9715\nAmount: 9.00 SAR\nAt: Code Cafe\n2026-11-23 10:00")
        assertEquals("Code Cafe", p.merchant)
    }

    @Test fun invalidRegexRowIsSkipped() {
        val broken = DefaultParserRules.rules.map { if (it.label == "Barq POS") it.copy(match = "([") else it }
        val r = SmsParser(broken, RIYADH).parse("barq app", Fixtures.BARQ_1, RECEIVED)
        assertSame(ParseResult.Unrecognized, r)
    }
}
