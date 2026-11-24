package com.weeklyspend.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.weeklyspend.budget.Budget
import com.weeklyspend.budget.Money
import com.weeklyspend.budget.WeekSummary
import com.weeklyspend.data.TransactionEntity
import com.weeklyspend.ui.MainViewModel
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun WeekScreen(vm: MainViewModel) {
    val history by vm.history.collectAsStateWithLifecycle()
    val weekTx by vm.weekTx.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<TransactionEntity?>(null) }
    val current = history.lastOrNull()
    val t1 = settings.budget.threshold1Pct

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(16.dp)) {
                Text("This week", style = MaterialTheme.typography.headlineSmall)
                if (current != null) {
                    Text("${Money.format(current.spent)} of ${Money.format(current.limit)} · side line ${Money.format(current.sideLineTotal)} · fixed ${Money.format(current.fixedTotal)}")
                }
            }
        }
        if (current != null) items(current.days) { d ->
            val isToday = d.date == today
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US),
                    Modifier.width(44.dp),
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                )
                Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(7.dp))) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(color = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.2f))
                        val frac = if (d.limit > 0) (d.spent.toFloat() / d.limit).coerceIn(0f, 1f) else 0f
                        drawRect(color = progressColor(d.spent, d.limit, t1), size = Size(size.width * frac, size.height))
                    }
                }
                Text(Money.format(d.spent, false), Modifier.width(72.dp), textAlign = TextAlign.End, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
            }
        }
        item { SectionTitle("Last 8 weeks") }
        item { WeeksChart(history, t1) }
        item { SectionTitle("Transactions this week") }
        val visible = weekTx.filter { it.type != "TRANSFER_IN" && it.type != "SALARY" }
        if (visible.isEmpty()) item { Text("No transactions this week.", Modifier.padding(16.dp)) }
        items(visible, key = { it.id }) { t -> TransactionRow(t, showDay = true) { editing = t } }
    }

    editing?.let { t ->
        EditTransactionDialog(
            t,
            onDismiss = { editing = null },
            onSave = { u, always -> vm.update(u, always); editing = null },
            onDelete = { vm.delete(t); editing = null },
        )
    }
}

private val SHORT_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.US)

@Composable
private fun WeeksChart(weeks: List<WeekSummary>, t1: Int) {
    if (weeks.isEmpty()) return
    val maxV = (weeks.maxOf { maxOf(it.spent, it.limit) }).coerceAtLeast(1)
    val lineColor = MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            val slot = size.width / weeks.size
            val barW = slot * 0.6f
            weeks.forEachIndexed { i, w ->
                val x = i * slot + (slot - barW) / 2
                val h = size.height * (w.spent.toFloat() / maxV)
                drawRect(progressColor(w.spent, w.limit, t1), topLeft = Offset(x, size.height - h), size = Size(barW, h))
                val ly = size.height * (1 - w.limit.toFloat() / maxV)
                drawLine(
                    lineColor, Offset(i * slot + 2, ly), Offset((i + 1) * slot - 2, ly), strokeWidth = 3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            weeks.forEach { w ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Money.format(w.spent, false).substringBefore('.'), style = MaterialTheme.typography.labelSmall)
                    Text(SHORT_DATE.format(Budget.weekStart(w.weekStart)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text("Dashed line = weekly limit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
