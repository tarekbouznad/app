package com.weeklyspend.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.weeklyspend.budget.Budget
import com.weeklyspend.budget.Category
import com.weeklyspend.budget.WeekStatus
import com.weeklyspend.budget.WeekSummary
import com.weeklyspend.data.AppSettings
import com.weeklyspend.data.MerchantRuleEntity
import com.weeklyspend.data.ParserRuleEntity
import com.weeklyspend.data.Repository
import com.weeklyspend.data.ReviewEntity
import com.weeklyspend.data.TransactionEntity
import com.weeklyspend.export.CsvExporter
import com.weeklyspend.ingest.InboxScanWorker
import com.weeklyspend.notify.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repository.get(app)
    private val dao = repo.db.transactions()

    val settings: StateFlow<AppSettings> = repo.settings.flow
    val today = MutableStateFlow(repo.today())

    fun refreshDay() {
        today.value = repo.today()
    }

    val weekTx: StateFlow<List<TransactionEntity>> = today.flatMapLatest { d ->
        val r = Budget.weekRangeMillis(d, repo.zone)
        dao.observeRange(r.first, r.last)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val status: StateFlow<WeekStatus> = combine(weekTx, settings, today) { tx, s, d ->
        Budget.status(d, tx.map { it.toSpend() }, s.budget, repo.zone)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Budget.status(repo.today(), emptyList(), settings.value.budget, repo.zone))

    val todayTx: StateFlow<List<TransactionEntity>> = combine(weekTx, today) { tx, d ->
        tx.filter { Budget.localDate(it.timestamp, repo.zone) == d && it.type != "TRANSFER_IN" && it.type != "SALARY" }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The current week and the 7 before it, oldest first. */
    val history: StateFlow<List<WeekSummary>> = today.flatMapLatest { d ->
        val first = Budget.weekStart(d).minusWeeks(7)
        val from = Budget.weekRangeMillis(first, repo.zone).first
        val to = Budget.weekRangeMillis(d, repo.zone).last
        combine(dao.observeRange(from, to), settings) { tx, s ->
            val items = tx.map { it.toSpend() }
            (0L..7L).map { Budget.summary(first.plusWeeks(it), items, s.budget, repo.zone) }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val transfers = dao.observeTransfers().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val review = repo.db.review().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val lastBalance: StateFlow<TransactionEntity?> = settings.map { it.balanceSender.trim().lowercase() }
        .flatMapLatest { dao.observeLastBalance(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val merchantRules = repo.db.rules().observeMerchantRules().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val parserRules = repo.db.rules().observeParserRules().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) { repo.ensureSeeded(); repo.afterChange() }
    }

    private fun io(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { block() }
    }

    fun addManual(amountMinor: Long, category: Category, sideLine: Boolean, note: String?, date: LocalDate) = io {
        val now = java.time.ZonedDateTime.now(repo.zone)
        val ts = if (date == now.toLocalDate()) now.toInstant().toEpochMilli()
        else date.atTime(now.toLocalTime()).atZone(repo.zone).toInstant().toEpochMilli()
        repo.addManual(amountMinor, category, sideLine, note, ts)
    }

    fun update(t: TransactionEntity, always: Boolean) = io { repo.updateTransaction(t, always) }
    fun delete(t: TransactionEntity) = io { repo.deleteTransaction(t) }
    fun restore(t: TransactionEntity) = io { repo.restoreTransaction(t) }

    fun dismissReview(r: ReviewEntity) = io { repo.db.review().delete(r) }
    fun clearReview() = io { repo.db.review().clear() }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = io {
        repo.settings.update(transform)
        Scheduler.scheduleAll(getApplication())
        repo.afterChange()
    }

    fun saveParserRule(r: ParserRuleEntity) = io { repo.db.rules().upsertParserRule(r) }
    fun deleteParserRule(r: ParserRuleEntity) = io { repo.db.rules().deleteParserRule(r) }
    fun resetParserRules() = io { repo.resetParserRules() }

    fun saveMerchantRule(r: MerchantRuleEntity) = io { repo.db.rules().upsertMerchantRule(r) }
    fun deleteMerchantRule(r: MerchantRuleEntity) = io { repo.db.rules().deleteMerchantRule(r) }

    fun rescanInbox() {
        androidx.work.WorkManager.getInstance(getApplication()).cancelUniqueWork("inbox-scan")
        InboxScanWorker.enqueue(getApplication())
    }

    fun startInitialScanIfNeeded() {
        if (!repo.settings.inboxScanDone) InboxScanWorker.enqueue(getApplication())
    }

    fun export(uri: Uri, done: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val tx = dao.all()
                    val rv = repo.db.review().all()
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                        CsvExporter.write(it, tx, rv, repo.zone)
                    } ?: error("no stream")
                }.isSuccess
            }
            done(ok)
        }
    }
}
