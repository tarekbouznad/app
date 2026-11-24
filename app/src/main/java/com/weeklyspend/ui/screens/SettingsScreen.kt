package com.weeklyspend.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.weeklyspend.budget.Money
import com.weeklyspend.parser.Amounts
import com.weeklyspend.ui.MainViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

private fun sarOrNull(s: String): Long? = Amounts.toMinor(s.replace(',', '.'))?.takeIf { it >= 0 }

@Composable
fun SettingsScreen(vm: MainViewModel, snackbar: SnackbarHostState, onParserRules: () -> Unit, onMerchantRules: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    fun toast(msg: String) { scope.launch { snackbar.showSnackbar(msg) } }

    val b = s.budget
    var weeklyBefore by remember(b) { mutableStateOf(Money.plain(b.weeklyBeforeMinor)) }
    var dailyBefore by remember(b) { mutableStateOf(b.dailyBeforeMinor?.let(Money::plain) ?: "") }
    var switchDate by remember(b) { mutableStateOf(b.switchDate.toString()) }
    var weeklyAfter by remember(b) { mutableStateOf(Money.plain(b.weeklyAfterMinor)) }
    var dailyAfter by remember(b) { mutableStateOf(b.dailyAfterMinor?.let(Money::plain) ?: "") }
    var t1 by remember(b) { mutableStateOf(b.threshold1Pct.toString()) }
    var t2 by remember(b) { mutableStateOf(b.threshold2Pct.toString()) }
    var senders by remember(s.senders) { mutableStateOf(s.senders.joinToString("\n")) }
    var balanceSender by remember(s.balanceSender) { mutableStateOf(s.balanceSender) }
    var ownNames by remember(s.ownNames) { mutableStateOf(s.ownNames.joinToString("\n")) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.export(uri) { ok -> toast(if (ok) "Exported" else "Export failed") }
    }

    val dateOk = runCatching { LocalDate.parse(switchDate.trim()) }.isSuccess
    val limitsValid = sarOrNull(weeklyBefore) != null && sarOrNull(weeklyAfter) != null && dateOk &&
        (dailyBefore.isBlank() || sarOrNull(dailyBefore) != null) && (dailyAfter.isBlank() || sarOrNull(dailyAfter) != null) &&
        t1.toIntOrNull() != null && t2.toIntOrNull() != null

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        SectionTitle("Limits")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField("Weekly limit", weeklyBefore, Modifier.weight(1f)) { weeklyBefore = it }
                MoneyField("Daily (blank = weekly/7)", dailyBefore, Modifier.weight(1f)) { dailyBefore = it }
            }
            OutlinedTextField(
                switchDate, { switchDate = it }, label = { Text("Switch date (yyyy-MM-dd)") }, singleLine = true,
                isError = !dateOk, modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField("Weekly from switch", weeklyAfter, Modifier.weight(1f)) { weeklyAfter = it }
                MoneyField("Daily from switch", dailyAfter, Modifier.weight(1f)) { dailyAfter = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("First alert %", t1, Modifier.weight(1f)) { t1 = it }
                NumberField("Second alert %", t2, Modifier.weight(1f)) { t2 = it }
            }
            Text(
                "A week that contains the switch date is prorated per day. Alerts fire when today or the week crosses the first and second " +
                    "threshold, then on every purchase while over.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(enabled = limitsValid, onClick = {
                vm.updateSettings {
                    it.copy(
                        budget = it.budget.copy(
                            weeklyBeforeMinor = sarOrNull(weeklyBefore)!!,
                            dailyBeforeMinor = dailyBefore.takeIf { d -> d.isNotBlank() }?.let(::sarOrNull),
                            switchDate = LocalDate.parse(switchDate.trim()),
                            weeklyAfterMinor = sarOrNull(weeklyAfter)!!,
                            dailyAfterMinor = dailyAfter.takeIf { d -> d.isNotBlank() }?.let(::sarOrNull),
                            threshold1Pct = t1.toInt(),
                            threshold2Pct = t2.toInt(),
                        ),
                    )
                }
                toast("Limits saved")
            }) { Text("Save limits") }
        }

        HorizontalDivider(Modifier.padding(top = 16.dp))
        SectionTitle("Notifications")
        Column(Modifier.padding(horizontal = 16.dp)) {
            LabeledSwitch("Persistent notification", "\"Left this week: X SAR (Y today)\"", s.persistentNotification) { v ->
                vm.updateSettings { it.copy(persistentNotification = v) }
            }
            LabeledSwitch("Daily reminder at 21:00", "\"Anything missing today?\" with quick add", s.dailyReminder) { v ->
                vm.updateSettings { it.copy(dailyReminder = v) }
            }
            LabeledSwitch("Weekly summary Sunday 20:00", null, s.weeklySummary) { v ->
                vm.updateSettings { it.copy(weeklySummary = v) }
            }
            OutlinedButton(onClick = { requestBatteryExemption(ctx) }) { Text("Battery optimization exemption") }
        }

        HorizontalDivider(Modifier.padding(top = 16.dp))
        SectionTitle("SMS senders")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                senders, { senders = it }, label = { Text("One sender name per line (case-insensitive)") },
                minLines = 2, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(balanceSender, { balanceSender = it }, label = { Text("Sender whose balance shows on Home") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                vm.updateSettings { it.copy(senders = lines(senders), balanceSender = balanceSender.trim()) }
                toast("Senders saved")
            }) { Text("Save senders") }
        }

        HorizontalDivider(Modifier.padding(top = 16.dp))
        SectionTitle("My own names / accounts")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Transfers whose recipient or text contains any of these (e.g. your full name, \"*1234\") are marked as own-account and ignored.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(ownNames, { ownNames = it }, label = { Text("One per line") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Button(onClick = { vm.updateSettings { it.copy(ownNames = lines(ownNames)) }; toast("Saved. Applies to new SMS.") }) { Text("Save") }
        }

        HorizontalDivider(Modifier.padding(top = 16.dp))
        SectionTitle("Rules")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onParserRules, modifier = Modifier.fillMaxWidth()) { Text("Parser table (regex per sender and type)") }
            OutlinedButton(onClick = onMerchantRules, modifier = Modifier.fillMaxWidth()) { Text("Merchant rules (categories, side line, fixed)") }
        }

        HorizontalDivider(Modifier.padding(top = 16.dp))
        SectionTitle("Data")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.rescanInbox(); toast("Scanning the last 60 days of SMS…") }, modifier = Modifier.fillMaxWidth()) {
                Text("Rescan inbox (last 60 days)")
            }
            OutlinedButton(onClick = { exportLauncher.launch("weekly-spend-${LocalDate.now()}.csv") }, modifier = Modifier.fillMaxWidth()) {
                Text("Export all data to CSV")
            }
            Text(
                "All data stays on this phone. The app has no internet permission, no accounts and no analytics, and is excluded from cloud backup.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun lines(s: String) = s.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

@Composable
private fun MoneyField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, { onChange(it.filter { c -> c.isDigit() || c == '.' || c == ',' }) }, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = modifier,
    )
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, { onChange(it.filter(Char::isDigit)) }, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = modifier,
        isError = value.toIntOrNull() == null,
    )
}
