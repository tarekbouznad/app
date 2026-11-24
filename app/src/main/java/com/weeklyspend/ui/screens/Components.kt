package com.weeklyspend.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weeklyspend.budget.Category
import com.weeklyspend.budget.Money
import com.weeklyspend.data.TransactionEntity
import com.weeklyspend.parser.TxType
import com.weeklyspend.ui.Amber
import com.weeklyspend.ui.Green
import com.weeklyspend.ui.Red
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
val DAY_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.US)

fun fmt(millis: Long, f: DateTimeFormatter): String = f.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

fun progressColor(spent: Long, limit: Long, t1: Int): Color = when {
    spent > limit -> Red
    limit > 0 && spent * 100 >= limit * t1 -> Amber
    else -> Green
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
}

fun tagsFor(t: TransactionEntity): String = buildList {
    add(if (t.txType == TxType.MANUAL) "Manual" else t.txType.label)
    add(t.cat.label)
    if (t.sideLine) add("Side line")
    if (t.fixed) add("Fixed")
    if (t.ownTransfer) add("Own account")
}.joinToString(" · ")

@Composable
fun TransactionRow(t: TransactionEntity, showDay: Boolean, onClick: () -> Unit) {
    val excluded = !t.toSpend().countsTowardEnvelope
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(t.merchant ?: t.txType.label, maxLines = 1) },
        supportingContent = { Text("${fmt(t.timestamp, if (showDay) DAY_TIME else TIME)} · ${tagsFor(t)}", maxLines = 1) },
        trailingContent = {
            Text(
                Money.format(t.amountMinor),
                fontWeight = FontWeight.SemiBold,
                color = if (excluded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        },
    )
}

/** Swipe right = recategorize, swipe left = delete. */
@Composable
fun SwipeableTransaction(t: TransactionEntity, showDay: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            when (it) {
                SwipeToDismissBoxValue.StartToEnd -> { onEdit(); false }
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); true }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val dir = state.dismissDirection
            val (color, icon, align) = when (dir) {
                SwipeToDismissBoxValue.StartToEnd -> Triple(MaterialTheme.colorScheme.primaryContainer, Icons.Filled.Edit, Alignment.CenterStart)
                SwipeToDismissBoxValue.EndToStart -> Triple(MaterialTheme.colorScheme.errorContainer, Icons.Filled.Delete, Alignment.CenterEnd)
                else -> Triple(Color.Transparent, null, Alignment.Center)
            }
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize().background(color).padding(horizontal = 20.dp),
                contentAlignment = align,
            ) { if (icon != null) Icon(icon, null) }
        },
    ) { TransactionRow(t, showDay, onEdit) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryChips(selected: Category?, onSelect: (Category) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Category.entries.forEach { c -> FilterChip(selected = selected == c, onClick = { onSelect(c) }, label = { Text(c.label) }) }
    }
}

@Composable
fun LabeledSwitch(label: String, sub: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun EditTransactionDialog(t: TransactionEntity, onDismiss: () -> Unit, onSave: (TransactionEntity, Boolean) -> Unit, onDelete: () -> Unit) {
    var category by remember { mutableStateOf(t.cat) }
    var sideLine by remember { mutableStateOf(t.sideLine) }
    var fixed by remember { mutableStateOf(t.fixed) }
    var always by remember { mutableStateOf(false) }
    val isPurchase = t.txType.isPurchase
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${Money.format(t.amountMinor)} · ${t.merchant ?: t.txType.label}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(fmt(t.timestamp, DAY_TIME) + " · " + t.txType.label + (t.card?.let { " · card $it" } ?: ""), style = MaterialTheme.typography.bodySmall)
                if (isPurchase) {
                    Spacer(Modifier.width(4.dp))
                    Text("Category", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                    CategoryChips(category) { category = it }
                    LabeledSwitch("Side line", "Uber, football, one-offs: not in the envelope", sideLine) { sideLine = it }
                    LabeledSwitch("Fixed", "Recurring bill: not in the envelope", fixed) { fixed = it }
                    if (!t.merchant.isNullOrBlank()) {
                        Row(Modifier.fillMaxWidth().clickable { always = !always }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(always, { always = it })
                            Text("Always put \"${t.merchant}\" here")
                        }
                    }
                } else {
                    Text(
                        if (t.txType == TxType.TRANSFER_OUT) "Transfers out never count toward the envelope." else "Not counted as spending.",
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(t.copy(category = category.name, sideLine = sideLine, fixed = fixed), always) }, enabled = isPurchase) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
