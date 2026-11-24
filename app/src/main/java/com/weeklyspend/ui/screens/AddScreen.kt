package com.weeklyspend.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.weeklyspend.budget.Category
import com.weeklyspend.budget.MerchantMatcher
import com.weeklyspend.parser.Amounts
import com.weeklyspend.ui.MainViewModel
import java.time.LocalDate

@Composable
fun AddScreen(vm: MainViewModel, initialAmount: String, initialNote: String, onDone: () -> Unit) {
    var amount by remember { mutableStateOf(initialAmount) }
    var note by remember { mutableStateOf(initialNote) }
    var category by remember { mutableStateOf(Category.FOOD) }
    var sideLine by remember { mutableStateOf(false) }
    var daysAgo by remember { mutableStateOf(0L) }
    val focus = remember { FocusRequester() }
    val minor = Amounts.toMinor(amount.replace(',', '.'))?.takeIf { it > 0 }

    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Add expense", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
            label = { Text("Amount (SAR)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            isError = amount.isNotEmpty() && minor == null,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        OutlinedTextField(
            value = note,
            onValueChange = {
                note = it
                MerchantMatcher.categoryFromWord(it)?.let { c -> category = c }
            },
            label = { Text("What / where (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Category", style = MaterialTheme.typography.labelLarge)
        CategoryChips(category) { category = it }
        LabeledSwitch("Side line", "Uber, football, one-offs: not counted in the weekly envelope", sideLine) { sideLine = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0L to "Today", 1L to "Yesterday", 2L to "2 days ago").forEach { (d, l) ->
                FilterChip(selected = daysAgo == d, onClick = { daysAgo = d }, label = { Text(l) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDone) { Text("Cancel") }
            Button(
                enabled = minor != null,
                onClick = {
                    vm.addManual(minor!!, category, sideLine, note.trim().ifEmpty { null }, LocalDate.now().minusDays(daysAgo))
                    onDone()
                },
            ) { Text("Save") }
        }
    }
}
