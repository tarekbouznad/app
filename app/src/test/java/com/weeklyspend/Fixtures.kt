package com.weeklyspend

import java.time.LocalDateTime
import java.time.ZoneId

object Fixtures {
    val RIYADH: ZoneId = ZoneId.of("Asia/Riyadh")
    fun millis(s: String): Long = LocalDateTime.parse(s).atZone(RIYADH).toInstant().toEpochMilli()

    const val BARQ_1 = "POS Purchase\nVisa card: **9715\nAmount: 4.60 SAR\nBalance: 314.21\nAt: KING FAHAD U O P M\n2026-11-23 19:52"
    const val BARQ_2 = "POS Purchase\nMada card: **9310\namount:7.50 SAR\nBalance: 347.35\natShatirti\n2026-11-22 22:02"
    const val BARQ_3 = "Online Purchase\nVISA card **9715\nAmount 19.00 SAR\nBalance 323.41\nAt Noon One Subscription\n2026-11-23 06:58"
    const val ALINMA_4 = "POS Purchase\nBy: Samsung Wallet\nAmount: 4.60 SAUDI RIYAL\nmada card: 4026*\nAccount: *6000\nAt: KING FAHAD U O P M\nOn: 2026-11-11 12:45"
    const val ALINMA_5 = "Online Purchase\nAmount:43.33 SAR\nmada card:4026*\nAccount:*6000\nat:TABBY\nOn:26-11-22 16:32"
    const val ALINMA_6 = "Transfer Incoming 23 SAR\nFrom HOUSSEM ED*; *2000\nOn 11-19-2026 17:41"
    const val ALINMA_7 = "Debit Transfer Internal\nAmount:500.00 SAR\nTo:MOHAMMED AZZOUZ KAIROUANI\nTo Account:*6000\nOn:2026-11-23 17:26"
    const val ALINMA_8 = "Please use the code: 6978\nAmount: 500 SAR\nTo: Internal Transfer"
    const val ALINMA_9 = "The beneficiary Alinma Account has been activated successfully"

    /** Received "now" for the tests: shortly after the latest sample. */
    val RECEIVED = millis("2026-11-24T09:00")
}
