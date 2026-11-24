package com.weeklyspend.data

import android.content.Context
import com.weeklyspend.budget.BudgetSettings
import com.weeklyspend.parser.DefaultParserRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

data class AppSettings(
    val budget: BudgetSettings = BudgetSettings(),
    val senders: List<String> = listOf(DefaultParserRules.BARQ, DefaultParserRules.ALINMA),
    val ownNames: List<String> = emptyList(),
    val persistentNotification: Boolean = false,
    val dailyReminder: Boolean = true,
    val weeklySummary: Boolean = true,
    val balanceSender: String = DefaultParserRules.BARQ,
)

/** Small key/value settings in SharedPreferences, exposed as a StateFlow for Compose. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _flow = MutableStateFlow(load())
    val flow: StateFlow<AppSettings> = _flow.asStateFlow()
    val current: AppSettings get() = _flow.value

    var inboxScanDone: Boolean
        get() = prefs.getBoolean("inbox_scan_done", false)
        set(v) = prefs.edit().putBoolean("inbox_scan_done", v).apply()

    var seeded: Boolean
        get() = prefs.getBoolean("seeded", false)
        set(v) = prefs.edit().putBoolean("seeded", v).apply()

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(current)
        val b = s.budget
        prefs.edit()
            .putLong("weekly_before", b.weeklyBeforeMinor)
            .putLong("daily_before", b.dailyBeforeMinor ?: -1)
            .putLong("weekly_after", b.weeklyAfterMinor)
            .putLong("daily_after", b.dailyAfterMinor ?: -1)
            .putString("switch_date", b.switchDate.toString())
            .putInt("t1", b.threshold1Pct)
            .putInt("t2", b.threshold2Pct)
            .putString("senders", s.senders.joinToString("\n"))
            .putString("own_names", s.ownNames.joinToString("\n"))
            .putBoolean("persistent", s.persistentNotification)
            .putBoolean("daily_reminder", s.dailyReminder)
            .putBoolean("weekly_summary", s.weeklySummary)
            .putString("balance_sender", s.balanceSender)
            .apply()
        _flow.value = s
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        if (!prefs.contains("weekly_before")) return d
        fun lines(key: String, def: List<String>) =
            prefs.getString(key, null)?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: def
        return AppSettings(
            budget = BudgetSettings(
                weeklyBeforeMinor = prefs.getLong("weekly_before", d.budget.weeklyBeforeMinor),
                dailyBeforeMinor = prefs.getLong("daily_before", -1).takeIf { it >= 0 },
                weeklyAfterMinor = prefs.getLong("weekly_after", d.budget.weeklyAfterMinor),
                dailyAfterMinor = prefs.getLong("daily_after", -1).takeIf { it >= 0 },
                switchDate = runCatching { LocalDate.parse(prefs.getString("switch_date", null)) }.getOrDefault(d.budget.switchDate),
                threshold1Pct = prefs.getInt("t1", 80),
                threshold2Pct = prefs.getInt("t2", 100),
            ),
            senders = lines("senders", d.senders),
            ownNames = lines("own_names", emptyList()),
            persistentNotification = prefs.getBoolean("persistent", false),
            dailyReminder = prefs.getBoolean("daily_reminder", true),
            weeklySummary = prefs.getBoolean("weekly_summary", true),
            balanceSender = prefs.getString("balance_sender", d.balanceSender) ?: d.balanceSender,
        )
    }
}
