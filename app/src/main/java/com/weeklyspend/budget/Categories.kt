package com.weeklyspend.budget

import com.weeklyspend.parser.SenderMatch

enum class Category(val label: String) { FOOD("Food"), COFFEE("Coffee"), OUTING("Outing"), TRANSPORT("Transport"), OTHER("Other") }

/**
 * "Always put this merchant in ...". [pattern] matches if the normalized merchant contains the normalized
 * pattern (case-insensitive, spaces/punctuation ignored). [category] null keeps the default.
 */
data class MerchantRule(
    val id: Long = 0,
    val pattern: String,
    val category: Category?,
    val sideLine: Boolean = false,
    val fixed: Boolean = false,
)

data class Categorization(val category: Category, val sideLine: Boolean, val fixed: Boolean)

object MerchantMatcher {
    val defaults = listOf(
        MerchantRule(pattern = "KING FAHAD U O P M", category = Category.FOOD),
        MerchantRule(pattern = "Saudi Marketing Co", category = Category.FOOD),
        MerchantRule(pattern = "Shatirti", category = Category.FOOD),
        MerchantRule(pattern = "Dunkin", category = Category.COFFEE),
        MerchantRule(pattern = "Starbucks", category = Category.COFFEE),
        MerchantRule(pattern = "Uber", category = Category.TRANSPORT, sideLine = true),
        MerchantRule(pattern = "Kiabi", category = Category.OTHER, sideLine = true),
        MerchantRule(pattern = "TABBY", category = Category.OTHER, fixed = true),
        MerchantRule(pattern = "Tamara", category = Category.OTHER, fixed = true),
        MerchantRule(pattern = "Noon One", category = Category.OTHER, fixed = true),
        MerchantRule(pattern = "Claude", category = Category.OTHER, fixed = true),
        MerchantRule(pattern = "Anthropic", category = Category.OTHER, fixed = true),
        MerchantRule(pattern = "Spotify", category = Category.OTHER, fixed = true),
    )

    /** Longest matching pattern wins, so a specific rule beats a generic one. */
    fun categorize(merchant: String?, rules: List<MerchantRule>): Categorization {
        val m = SenderMatch.normalize(merchant ?: "")
        val hit = if (m.isEmpty()) null else rules
            .filter { val p = SenderMatch.normalize(it.pattern); p.isNotEmpty() && m.contains(p) }
            .maxByOrNull { SenderMatch.normalize(it.pattern).length }
        return Categorization(hit?.category ?: Category.OTHER, hit?.sideLine ?: false, hit?.fixed ?: false)
    }

    /** Matches a word typed into the quick-add notification ("12 coffee") to a category. */
    fun categoryFromWord(text: String): Category? {
        val t = text.lowercase()
        return Category.entries.firstOrNull { t.contains(it.label.lowercase()) }
            ?: when {
                Regex("taxi|careem|bus|metro|fuel|petrol").containsMatchIn(t) -> Category.TRANSPORT
                Regex("lunch|dinner|breakfast|meal|grocer").containsMatchIn(t) -> Category.FOOD
                Regex("cafe|latte|tea").containsMatchIn(t) -> Category.COFFEE
                else -> null
            }
    }
}
