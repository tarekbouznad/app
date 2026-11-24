package com.weeklyspend.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.weeklyspend.budget.Category
import com.weeklyspend.budget.MerchantRule
import com.weeklyspend.budget.SpendItem
import com.weeklyspend.parser.ParserRule
import com.weeklyspend.parser.RuleType
import com.weeklyspend.parser.TxType
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["sender", "amountMinor", "timestamp"], unique = true), Index("timestamp")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val amountMinor: Long,
    val merchant: String?,
    val timestamp: Long,
    val card: String?,
    val balanceMinor: Long?,
    val type: String,
    val category: String,
    val sideLine: Boolean,
    val fixed: Boolean,
    val ownTransfer: Boolean,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val txType: TxType get() = runCatching { TxType.valueOf(type) }.getOrDefault(TxType.OTHER)
    val cat: Category get() = runCatching { Category.valueOf(category) }.getOrDefault(Category.OTHER)
    fun toSpend() = SpendItem(amountMinor, timestamp, txType, sideLine, fixed, ownTransfer)
}

@Entity(tableName = "review", indices = [Index(value = ["sender", "receivedAt", "body"], unique = true)])
data class ReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    val receivedAt: Long,
)

@Entity(tableName = "merchant_rules")
data class MerchantRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pattern: String,
    val category: String?,
    val sideLine: Boolean,
    val fixed: Boolean,
) {
    fun toModel() = MerchantRule(id, pattern, category?.let { runCatching { Category.valueOf(it) }.getOrNull() }, sideLine, fixed)

    companion object {
        fun from(m: MerchantRule) = MerchantRuleEntity(m.id, m.pattern, m.category?.name, m.sideLine, m.fixed)
    }
}

@Entity(tableName = "parser_rules")
data class ParserRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val type: String,
    val match: String,
    val amount: String,
    val merchant: String,
    val date: String,
    val card: String,
    val balance: String,
    val priority: Int,
    val enabled: Boolean,
    val label: String,
) {
    fun toModel() = ParserRule(
        id, sender, runCatching { RuleType.valueOf(type) }.getOrDefault(RuleType.OTHER),
        match, amount, merchant, date, card, balance, priority, enabled, label,
    )

    companion object {
        fun from(r: ParserRule) = ParserRuleEntity(
            r.id, r.sender, r.type.name, r.match, r.amount, r.merchant, r.date, r.card, r.balance, r.priority, r.enabled, r.label,
        )
    }
}

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(t: TransactionEntity): Long

    @Update
    suspend fun update(t: TransactionEntity)

    @Delete
    suspend fun delete(t: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp DESC")
    fun observeRange(from: Long, to: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp DESC")
    suspend fun range(from: Long, to: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE type = 'TRANSFER_OUT' ORDER BY timestamp DESC")
    fun observeTransfers(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE balanceMinor IS NOT NULL AND sender = :sender ORDER BY timestamp DESC LIMIT 1")
    fun observeLastBalance(sender: String): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions ORDER BY timestamp")
    suspend fun all(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: Long): TransactionEntity?

    @Query("UPDATE transactions SET category = :category, sideLine = :sideLine, fixed = :fixed WHERE merchant = :merchant COLLATE NOCASE AND type IN ('POS','ONLINE','MANUAL')")
    suspend fun recategorizeMerchant(merchant: String, category: String, sideLine: Boolean, fixed: Boolean)
}

@Dao
interface ReviewDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(r: ReviewEntity): Long

    @Delete
    suspend fun delete(r: ReviewEntity)

    @Query("SELECT * FROM review ORDER BY receivedAt DESC")
    fun observeAll(): Flow<List<ReviewEntity>>

    @Query("SELECT COUNT(*) FROM review WHERE sender = :sender AND body = :body AND receivedAt BETWEEN :from AND :to")
    suspend fun countSimilar(sender: String, body: String, from: Long, to: Long): Int

    @Query("SELECT * FROM review ORDER BY receivedAt")
    suspend fun all(): List<ReviewEntity>

    @Query("DELETE FROM review")
    suspend fun clear()
}

@Dao
interface RulesDao {
    @Query("SELECT * FROM merchant_rules ORDER BY pattern COLLATE NOCASE")
    fun observeMerchantRules(): Flow<List<MerchantRuleEntity>>

    @Query("SELECT * FROM merchant_rules")
    suspend fun merchantRules(): List<MerchantRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMerchantRule(r: MerchantRuleEntity): Long

    @Query("DELETE FROM merchant_rules WHERE pattern = :pattern COLLATE NOCASE")
    suspend fun deleteMerchantRulesFor(pattern: String)

    @Delete
    suspend fun deleteMerchantRule(r: MerchantRuleEntity)

    @Query("SELECT * FROM parser_rules ORDER BY priority, id")
    fun observeParserRules(): Flow<List<ParserRuleEntity>>

    @Query("SELECT * FROM parser_rules ORDER BY priority, id")
    suspend fun parserRules(): List<ParserRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertParserRule(r: ParserRuleEntity): Long

    @Delete
    suspend fun deleteParserRule(r: ParserRuleEntity)

    @Query("DELETE FROM parser_rules")
    suspend fun clearParserRules()
}

@Database(
    entities = [TransactionEntity::class, ReviewEntity::class, MerchantRuleEntity::class, ParserRuleEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactions(): TransactionDao
    abstract fun review(): ReviewDao
    abstract fun rules(): RulesDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "weekly_spend.db")
                .build().also { instance = it }
        }
    }
}
