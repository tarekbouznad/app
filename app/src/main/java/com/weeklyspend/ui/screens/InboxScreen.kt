package com.weeklyspend.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.weeklyspend.budget.Money
import com.weeklyspend.ui.MainViewModel

@Composable
fun InboxScreen(vm: MainViewModel, onAddFromReview: (String, String) -> Unit) {
    val review by vm.review.collectAsStateWithLifecycle()
    val transfers by vm.transfers.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("To review (${review.size})") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Transfers") })
        }
        if (tab == 0) {
            LazyColumn(Modifier.fillMaxSize()) {
                if (review.isEmpty()) item {
                    Text("Nothing to review. SMS from your banks that the parser can't read land here.", Modifier.padding(16.dp))
                } else item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = vm::clearReview) { Text("Dismiss all") }
                    }
                }
                items(review, key = { it.id }) { r ->
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${r.sender} · ${fmt(r.receivedAt, DAY_TIME)}", style = MaterialTheme.typography.labelMedium)
                            Text(r.body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { vm.dismissReview(r) }) { Text("Dismiss") }
                                TextButton(onClick = {
                                    val amt = Regex("""(\d[\d,]*(?:\.\d{1,2})?)\s*(SAR|SAUDI)""", RegexOption.IGNORE_CASE).find(r.body)?.groupValues?.get(1)?.replace(",", "") ?: ""
                                    vm.dismissReview(r)
                                    onAddFromReview(amt, r.body.lineSequence().firstOrNull()?.take(40) ?: "")
                                }) { Text("Add as expense") }
                            }
                        }
                    }
                }
                item {
                    Text(
                        "Tip: fix the parser table in Settings so similar messages are read automatically.",
                        Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    val total = transfers.filter { !it.ownTransfer }.sumOf { it.amountMinor }
                    Text(
                        "Transfers out never count toward the envelope. Total to others: ${Money.format(total)}",
                        Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (transfers.isEmpty()) item { Text("No transfers yet.", Modifier.padding(16.dp)) }
                items(transfers, key = { it.id }) { t ->
                    ListItem(
                        headlineContent = { Text(t.merchant ?: "Unknown recipient") },
                        supportingContent = { Text(fmt(t.timestamp, DAY_TIME) + (if (t.ownTransfer) " · own account (ignored)" else "")) },
                        trailingContent = { Text(Money.format(t.amountMinor), fontWeight = FontWeight.SemiBold) },
                    )
                }
            }
        }
    }
}
