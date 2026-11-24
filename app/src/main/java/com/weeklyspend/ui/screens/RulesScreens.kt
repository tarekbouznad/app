package com.weeklyspend.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.weeklyspend.budget.Category
import com.weeklyspend.budget.Money
import com.weeklyspend.data.MerchantRuleEntity
import com.weeklyspend.data.ParserRuleEntity
import com.weeklyspend.parser.FieldDefaults
import com.weeklyspend.parser.ParseResult
import com.weeklyspend.parser.RuleType
import com.weeklyspend.parser.SmsParser
import com.weeklyspend.ui.MainViewModel
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        actions = { actions() },
    )
}

@Composable
fun ParserRulesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val rules by vm.parserRules.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ParserRuleEntity?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        BackBar("Parser table", onBack) {
            TextButton(onClick = {
                editing = ParserRuleEntity(0, "*", RuleType.POS.name, "", "", "", "", "", "", (rules.maxOfOrNull { it.priority } ?: 0) + 1, true, "New rule")
            }) { Text("Add") }
            TextButton(onClick = { confirmReset = true }) { Text("Reset") }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item { RuleTester(rules) }
            items(rules, key = { it.id }) { r ->
                ListItem(
                    modifier = Modifier.clickable { editing = r },
                    overlineContent = { Text("#${r.priority} · ${r.sender} · ${r.type}" + if (!r.enabled) " · disabled" else "") },
                    headlineContent = { Text(r.label.ifBlank { r.type }) },
                    supportingContent = { Text(r.match, fontFamily = FontFamily.Monospace, maxLines = 2) },
                )
            }
            item {
                Text(
                    "Rows are tried in priority order; the first enabled row whose sender and match regex fit wins. Field regexes use their first " +
                        "capture group; blank fields use the defaults. One-time codes are always dropped, whatever the table says.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    editing?.let { r ->
        ParserRuleDialog(r, onDismiss = { editing = null }, onSave = { vm.saveParserRule(it); editing = null }, onDelete = {
            vm.deleteParserRule(r); editing = null
        })
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset parser table?") },
            text = { Text("Replaces all rows with the built-in defaults.") },
            confirmButton = { TextButton(onClick = { vm.resetParserRules(); confirmReset = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

/** Paste an SMS and see how the current table reads it. Nothing is stored. */
@Composable
private fun RuleTester(rules: List<ParserRuleEntity>) {
    var sender by remember { mutableStateOf("alinma") }
    var body by remember { mutableStateOf("") }
    val result = remember(rules, sender, body) {
        if (body.isBlank()) null
        else SmsParser(rules.map { it.toModel() }, ZoneId.systemDefault()).parse(sender.trim().lowercase(), body, System.currentTimeMillis())
    }
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Test an SMS", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(sender, { sender = it }, label = { Text("Sender") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(body, { body = it }, label = { Text("SMS text") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            when (result) {
                null -> {}
                ParseResult.Ignored -> Text("Ignored (not stored)")
                ParseResult.Unrecognized -> Text("No rule matched: would go to \"To review\"", color = MaterialTheme.colorScheme.error)
                is ParseResult.Parsed -> with(result.sms) {
                    Text(
                        "${type.label} · ${Money.format(amountMinor)} · ${merchant ?: "no merchant"}\n" +
                            "${fmt(timestamp, DAY_TIME)}${if (dateFromText) "" else " (received time)"} · card ${card ?: "-"} · balance ${balanceMinor?.let { Money.format(it) } ?: "-"}",
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParserRuleDialog(r: ParserRuleEntity, onDismiss: () -> Unit, onSave: (ParserRuleEntity) -> Unit, onDelete: () -> Unit) {
    var label by remember { mutableStateOf(r.label) }
    var sender by remember { mutableStateOf(r.sender) }
    var type by remember { mutableStateOf(r.type) }
    var match by remember { mutableStateOf(r.match) }
    var amount by remember { mutableStateOf(r.amount) }
    var merchant by remember { mutableStateOf(r.merchant) }
    var date by remember { mutableStateOf(r.date) }
    var card by remember { mutableStateOf(r.card) }
    var balance by remember { mutableStateOf(r.balance) }
    var priority by remember { mutableStateOf(r.priority.toString()) }
    var enabled by remember { mutableStateOf(r.enabled) }

    val errors = listOf(match, amount, merchant, date, card, balance).map { if (it.isBlank()) null else SmsParser.validate(it) }
    val valid = match.isNotBlank() && errors.all { it == null } && priority.toIntOrNull() != null && sender.isNotBlank()

    @Composable
    fun RegexField(label: String, value: String, err: String?, placeholder: String, onChange: (String) -> Unit) {
        OutlinedTextField(
            value, onChange, label = { Text(label) }, isError = err != null,
            supportingText = { Text(err ?: if (value.isBlank()) "default: $placeholder" else "", maxLines = 2) },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (r.id == 0L) "New rule" else "Edit rule") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(label, { label = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(sender, { sender = it }, label = { Text("Sender or *") }, singleLine = true, modifier = Modifier.weight(2f))
                    OutlinedTextField(priority, { priority = it.filter(Char::isDigit) }, label = { Text("Priority") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RuleType.entries.forEach { t -> FilterChip(selected = type == t.name, onClick = { type = t.name }, label = { Text(t.name) }) }
                }
                RegexField("Match regex (required)", match, if (match.isBlank()) "Required" else errors[0], "") { match = it }
                RegexField("Amount", amount, errors[1], FieldDefaults.AMOUNT) { amount = it }
                RegexField("Merchant", merchant, errors[2], FieldDefaults.MERCHANT) { merchant = it }
                RegexField("Date", date, errors[3], FieldDefaults.DATE) { date = it }
                RegexField("Card", card, errors[4], FieldDefaults.CARD) { card = it }
                RegexField("Balance", balance, errors[5], FieldDefaults.BALANCE) { balance = it }
                LabeledSwitch("Enabled", null, enabled) { enabled = it }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(r.copy(label = label, sender = sender.trim().lowercase(), type = type, match = match, amount = amount, merchant = merchant,
                    date = date, card = card, balance = balance, priority = priority.toInt(), enabled = enabled))
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (r.id != 0L) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
fun MerchantRulesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val rules by vm.merchantRules.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<MerchantRuleEntity?>(null) }
    Column(Modifier.fillMaxSize()) {
        BackBar("Merchant rules", onBack) {
            TextButton(onClick = { editing = MerchantRuleEntity(0, "", Category.OTHER.name, false, false) }) { Text("Add") }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(rules, key = { it.id }) { r ->
                val m = r.toModel()
                ListItem(
                    modifier = Modifier.clickable { editing = r },
                    headlineContent = { Text(r.pattern) },
                    supportingContent = {
                        Text(listOfNotNull(m.category?.label, "Side line".takeIf { r.sideLine }, "Fixed".takeIf { r.fixed }).joinToString(" · "))
                    },
                )
            }
            item {
                Text(
                    "A rule applies when the merchant contains the text (case and spaces ignored); the longest match wins. " +
                        "Tap a transaction and tick \"Always put … here\" to add one quickly. Changes apply to new transactions.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
    editing?.let { r ->
        var pattern by remember(r) { mutableStateOf(r.pattern) }
        var cat by remember(r) { mutableStateOf(r.toModel().category ?: Category.OTHER) }
        var side by remember(r) { mutableStateOf(r.sideLine) }
        var fixed by remember(r) { mutableStateOf(r.fixed) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (r.id == 0L) "New merchant rule" else "Edit merchant rule") },
            text = {
                Column {
                    OutlinedTextField(pattern, { pattern = it }, label = { Text("Merchant contains") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    CategoryChips(cat) { cat = it }
                    LabeledSwitch("Side line", null, side) { side = it }
                    LabeledSwitch("Fixed", null, fixed) { fixed = it }
                }
            },
            confirmButton = {
                TextButton(enabled = pattern.isNotBlank(), onClick = {
                    vm.saveMerchantRule(r.copy(pattern = pattern.trim(), category = cat.name, sideLine = side, fixed = fixed)); editing = null
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (r.id != 0L) TextButton(onClick = { vm.deleteMerchantRule(r); editing = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { editing = null }) { Text("Cancel") }
                }
            },
        )
    }
}
