package com.weeklyspend.parser

/** The initial parser table. Users can edit, disable or add rows in Settings > Parser table. */
object DefaultParserRules {
    const val BARQ = "barq app"
    const val ALINMA = "alinma"

    val rules: List<ParserRule> = listOf(
        // Service messages: ignored completely (one-time codes are also blocked by OtpGuard, which cannot be edited).
        ParserRule(sender = "*", type = RuleType.IGNORE, match = """\bcode\s*(is|:)?\s*\d{3,8}\b|\botp\b""", priority = 1, label = "One-time codes"),
        ParserRule(sender = "*", type = RuleType.IGNORE, match = """has been activated|activated successfully""", priority = 2, label = "Activation notices"),

        // Barq
        ParserRule(sender = BARQ, type = RuleType.POS, match = """^\s*POS\s+Purchase""", priority = 10, label = "Barq POS"),
        ParserRule(sender = BARQ, type = RuleType.ONLINE, match = """^\s*Online\s+Purchase""", priority = 11, label = "Barq online"),
        ParserRule(sender = BARQ, type = RuleType.TRANSFER_IN, match = """(transfer\s+incoming|incoming\s+transfer|received)""", priority = 12, label = "Barq transfer in"),
        ParserRule(sender = BARQ, type = RuleType.TRANSFER_OUT, match = """(transfer\s+outgoing|outgoing\s+transfer|debit\s+transfer|transfer\s+sent)""", priority = 13, label = "Barq transfer out"),

        // Alinma
        ParserRule(sender = ALINMA, type = RuleType.POS, match = """^\s*POS\s+Purchase""", priority = 20, label = "Alinma POS"),
        ParserRule(sender = ALINMA, type = RuleType.ONLINE, match = """^\s*Online\s+Purchase""", priority = 21, label = "Alinma online"),
        ParserRule(
            sender = ALINMA, type = RuleType.TRANSFER_IN, match = """^\s*(Transfer\s+Incoming|Incoming\s+Transfer)""",
            amount = """incoming\s*:?\s*(\d[\d,]*(?:\.\d{1,3})?)\s*(?:SAR|SAUDI\s+RIYALS?)""",
            merchant = """^\s*from\s*:?\s*(.+?)\s*$""",
            priority = 22, label = "Alinma transfer in",
        ),
        ParserRule(
            sender = ALINMA, type = RuleType.TRANSFER_OUT, match = """^\s*(Debit\s+Transfer|Transfer\s+Outgoing|Outgoing\s+Transfer)""",
            merchant = """^\s*to\s*:\s*(?!\s*\*)(.+?)\s*$""",
            priority = 23, label = "Alinma transfer out",
        ),
        ParserRule(sender = ALINMA, type = RuleType.SALARY, match = """\b(salary|payroll)\b|راتب""", priority = 24, label = "Alinma salary"),
    )
}
