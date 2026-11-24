package com.weeklyspend

import com.weeklyspend.Fixtures.RECEIVED
import com.weeklyspend.Fixtures.RIYADH
import com.weeklyspend.budget.Category
import com.weeklyspend.budget.MerchantMatcher
import com.weeklyspend.ingest.ProcessOutcome
import com.weeklyspend.ingest.QuickAddReceiver
import com.weeklyspend.ingest.SmsProcessor
import com.weeklyspend.ingest.TxDraft
import com.weeklyspend.parser.DefaultParserRules
import com.weeklyspend.parser.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsProcessorTest {
    private fun processor(own: List<String> = emptyList()) =
        SmsProcessor(listOf("barq app", "alinma"), DefaultParserRules.rules, MerchantMatcher.defaults, own, RIYADH)

    private fun draft(sender: String, body: String, own: List<String> = emptyList()): TxDraft {
        val o = processor(own).process(sender, body, RECEIVED)
        assertTrue("expected Transaction, got $o", o is ProcessOutcome.Transaction)
        return (o as ProcessOutcome.Transaction).draft
    }

    @Test fun untrackedSenderIgnored() = assertSame(ProcessOutcome.NotTracked, processor().process("STC", Fixtures.BARQ_1, RECEIVED))

    @Test fun senderMatchedCaseInsensitively() {
        assertEquals("barq app", draft("BARQ APP", Fixtures.BARQ_1).sender)
        assertEquals("alinma", draft("ALINMA", Fixtures.ALINMA_4).sender)
    }

    @Test fun defaultMerchantRules() {
        draft("barq app", Fixtures.BARQ_1).let { assertEquals(Category.FOOD, it.category); assertFalse(it.sideLine); assertFalse(it.fixed) }
        draft("barq app", Fixtures.BARQ_2).let { assertEquals(Category.FOOD, it.category) }
        draft("barq app", Fixtures.BARQ_3).let { assertTrue("Noon One is fixed", it.fixed) }
        draft("alinma", Fixtures.ALINMA_5).let { assertTrue("Tabby is fixed", it.fixed) }
    }

    @Test fun transferOutToOtherPersonIsKeptButNotOwn() {
        val d = draft("alinma", Fixtures.ALINMA_7)
        assertEquals(TxType.TRANSFER_OUT, d.type)
        assertFalse(d.ownTransfer)
        assertFalse(d.fixed || d.sideLine)
    }

    @Test fun transferToOwnNameIsFlagged() {
        assertTrue(draft("alinma", Fixtures.ALINMA_7, own = listOf("mohammed azzouz")).ownTransfer)
    }

    @Test fun accountSuffixAloneDoesNotMakeItOwn() {
        // *6000 appears in the message, but it's only "own" if the user lists it.
        assertFalse(draft("alinma", Fixtures.ALINMA_7).ownTransfer)
        assertTrue(draft("alinma", Fixtures.ALINMA_7, own = listOf("*6000")).ownTransfer)
    }

    @Test fun otpAndActivationProduceNothing() {
        assertSame(ProcessOutcome.Ignored, processor().process("alinma", Fixtures.ALINMA_8, RECEIVED))
        assertSame(ProcessOutcome.Ignored, processor().process("alinma", Fixtures.ALINMA_9, RECEIVED))
    }

    @Test fun unparseableGoesToReview() {
        val o = processor().process("Alinma", "Something new from the bank", RECEIVED)
        assertTrue(o is ProcessOutcome.NeedsReview)
        assertEquals("alinma", (o as ProcessOutcome.NeedsReview).sender)
    }

    @Test fun merchantMatcherLongestWins() {
        val rules = MerchantMatcher.defaults + com.weeklyspend.budget.MerchantRule(pattern = "Uber Eats", category = Category.FOOD)
        val c = MerchantMatcher.categorize("UBER EATS RIYADH", rules)
        assertEquals(Category.FOOD, c.category)
        assertFalse(c.sideLine)
        assertTrue(MerchantMatcher.categorize("Uber *Trip", rules).sideLine)
        assertEquals(Category.COFFEE, MerchantMatcher.categorize("STARBUCKS 123", rules).category)
        assertEquals(Category.OTHER, MerchantMatcher.categorize(null, rules).category)
    }

    @Test fun quickAddParsing() {
        assertEquals(Triple(1250L, Category.OTHER, null), QuickAddReceiver.parse("12.5"))
        assertEquals(Triple(800L, Category.COFFEE, "coffee"), QuickAddReceiver.parse("8 coffee"))
        assertEquals(1250L, QuickAddReceiver.parse("12,5 lunch")!!.first)
        assertEquals(Category.FOOD, QuickAddReceiver.parse("12,5 lunch")!!.second)
        assertNull(QuickAddReceiver.parse("nothing"))
        assertNull(QuickAddReceiver.parse("0"))
    }
}
