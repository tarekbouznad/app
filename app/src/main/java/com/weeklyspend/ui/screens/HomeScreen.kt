package com.weeklyspend.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.weeklyspend.budget.Money
import com.weeklyspend.data.TransactionEntity
import kotlinx.coroutines.launch

private fun granted(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

@Composable
fun HomeScreen(vm: com.weeklyspend.ui.MainViewModel, snackbar: SnackbarHostState, onAdd: () -> Unit) {
    val ctx = LocalContext.current
    val st by vm.status.collectAsStateWithLifecycle()
    val todayTx by vm.todayTx.collectAsStateWithLifecycle()
    val balance by vm.lastBalance.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<TransactionEntity?>(null) }

    // Re-check permissions every time the screen resumes.
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val smsOk = remember(tick) { granted(ctx, Manifest.permission.RECEIVE_SMS) && granted(ctx, Manifest.permission.READ_SMS) }
    val notifOk = remember(tick) { Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS) }
    val batteryOk = remember(tick) { ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        tick++
        if (res[Manifest.permission.READ_SMS] == true) vm.startInitialScanIfNeeded()
    }
    LaunchedEffect(smsOk) { if (smsOk) vm.startInitialScanIfNeeded() }

    fun delete(t: TransactionEntity) {
        vm.delete(t)
        scope.launch {
            if (snackbar.showSnackbar("Deleted ${Money.format(t.amountMinor)}", actionLabel = "Undo") == SnackbarResult.ActionPerformed) vm.restore(t)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            if (!smsOk || !notifOk) item {
                SetupCard(
                    title = "Allow SMS and notifications",
                    text = "Weekly Spend reads bank SMS from your configured senders on this phone only, and warns you at your limits.",
                    button = "Allow",
                ) {
                    val perms = mutableListOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
                    if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
                    permLauncher.launch(perms.toTypedArray())
                }
            }
            if (!batteryOk) item {
                SetupCard(
                    title = "Keep alerts reliable",
                    text = "Samsung may put the app to sleep and delay SMS alerts and reminders. Exempt it from battery optimization " +
                        "(on Samsung also: Settings > Apps > Weekly Spend > Battery > Unrestricted, and remove it from Sleeping apps).",
                    button = "Exempt",
                ) { requestBatteryExemption(ctx) }
            }
            item { BudgetHeader(st, settings.budget.threshold1Pct) }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        Modifier.weight(1f), "Today",
                        Money.format(st.dayLeft) + " left",
                        "${Money.format(st.daySpent)} of ${Money.format(st.dayLimit)} · pace ${Money.format(st.pacePerDay)}/day",
                        progressColor(st.daySpent, st.dayLimit, settings.budget.threshold1Pct),
                    )
                    StatCard(
                        Modifier.weight(1f), "Barq balance",
                        balance?.balanceMinor?.let { Money.format(it) } ?: "—",
                        balance?.let { "from SMS ${fmt(it.timestamp, DAY_TIME)}" } ?: "No balance SMS yet",
                        null,
                    )
                }
            }
            item {
                Text(
                    "Side line ${Money.format(st.sideLineTotal)} · Fixed ${Money.format(st.fixedTotal)} this week (not in envelope)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item { SectionTitle("Today") }
            if (todayTx.isEmpty()) item {
                Text("Nothing yet today.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(todayTx, key = { it.id }) { t ->
                SwipeableTransaction(t, showDay = false, onEdit = { editing = t }, onDelete = { delete(t) })
            }
            item {
                Text(
                    "Swipe right to recategorize, left to delete.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                Spacer(Modifier.height(72.dp))
            }
        }
        ExtendedFloatingActionButton(
            onClick = onAdd,
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("Add") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    editing?.let { t ->
        EditTransactionDialog(
            t,
            onDismiss = { editing = null },
            onSave = { updated, always -> vm.update(updated, always); editing = null },
            onDelete = { editing = null; delete(t) },
        )
    }
}

@SuppressLint("BatteryLife")
fun requestBatteryExemption(ctx: Context) {
    val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
    runCatching { ctx.startActivity(direct) }.onFailure {
        runCatching { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }
}

@Composable
private fun BudgetHeader(st: com.weeklyspend.budget.WeekStatus, t1: Int) {
    val color = progressColor(st.weekSpent, st.weekLimit, t1)
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Left this week", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(Money.format(st.weekLeft), fontSize = 48.sp, fontWeight = FontWeight.Bold, color = color)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (st.weekSpent.toFloat() / st.weekLimit.coerceAtLeast(1)).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Spent ${Money.format(st.weekSpent)} of ${Money.format(st.weekLimit)} (${st.weekPct}%) · " +
                (if (st.daysLeft == 1) "last day" else "${st.daysLeft} days left") + " · ${st.weekStart} to ${st.weekEnd}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun StatCard(modifier: Modifier, title: String, value: String, sub: String, accent: androidx.compose.ui.graphics.Color?) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = accent ?: MaterialTheme.colorScheme.onSurface)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SetupCard(title: String, text: String, button: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Button(onClick = onClick) { Text(button) } }
        }
    }
}
