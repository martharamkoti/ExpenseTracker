package com.ramkotimartha.expensetracker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ramkotimartha.expensetracker.data.AppDatabase
import com.ramkotimartha.expensetracker.data.ExpenseRepository
import com.ramkotimartha.expensetracker.data.Tag
import com.ramkotimartha.expensetracker.data.Transaction
import com.ramkotimartha.expensetracker.data.TransactionType
import com.ramkotimartha.expensetracker.utils.BackupManager
import com.ramkotimartha.expensetracker.utils.ReportExporter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupAndExportTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: ExpenseRepository
    private val zone = ZoneOffset.UTC

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ExpenseRepository(db.transactionDao(), db.tagDao(), db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `serializes and restores mtbak backup in both replace and merge modes`() = runBlocking {
        val foodId = repository.insertTag(Tag(name = "Food", type = TransactionType.EXPENSE, iconKey = "food"))
        val salaryId = repository.insertTag(Tag(name = "Salary", type = TransactionType.INCOME, iconKey = "salary"))

        val nov10 = LocalDate.of(2026, 11, 10).atStartOfDay(zone).toInstant().toEpochMilli()
        repository.insertTransaction(
            Transaction(amount = 540.0, timestamp = nov10, note = "Swiggy lunch", tagId = foodId, type = TransactionType.EXPENSE)
        )
        repository.insertTransaction(
            Transaction(amount = 40000.0, timestamp = nov10, note = "Monthly pay", tagId = salaryId, type = TransactionType.INCOME)
        )

        val (tags, transactions) = repository.getBackupSnapshot()
        val serialized = BackupManager.serializeBackup(tags, transactions, exportedAtMillis = 1795850000000L)
        val parsed = BackupManager.deserializeBackup(serialized)

        assertEquals(2, parsed.tags.size)
        assertEquals(2, parsed.transactions.size)

        // Add an extra transaction after backup, then test REPLACE_ALL overwrites back to 2 transactions
        repository.insertTransaction(
            Transaction(amount = 999.0, timestamp = nov10, note = "Extra spend", tagId = foodId, type = TransactionType.EXPENSE)
        )
        assertEquals(3, repository.allTransactions.first().size)

        val replaceSummary = repository.restoreFromBackup(
            backupTags = parsed.tags,
            backupTransactions = parsed.transactions,
            replaceExisting = true
        )
        assertEquals(2, replaceSummary.restoredTransactionsCount)
        assertEquals(2, repository.allTransactions.first().size)

        // Test MERGE skips identical duplicates while adding genuinely new entries
        val mergeDuplicateSummary = repository.restoreFromBackup(
            backupTags = parsed.tags,
            backupTransactions = parsed.transactions,
            replaceExisting = false
        )
        assertEquals(0, mergeDuplicateSummary.restoredTransactionsCount)
        assertEquals(2, repository.allTransactions.first().size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects CSV report when passed to backup restore`() {
        BackupManager.deserializeBackup("Date,Type,Category,Amount (INR),Note\n2026-11-10,EXPENSE,Food,540,Swiggy")
    }

    @Test
    fun `generates accurate date-range summary and escaped CSV report`() = runBlocking {
        val foodId = repository.insertTag(Tag(name = "Food", type = TransactionType.EXPENSE, iconKey = "food"))
        val salaryId = repository.insertTag(Tag(name = "Salary", type = TransactionType.INCOME, iconKey = "salary"))
        val oct15 = LocalDate.of(2026, 10, 15).atStartOfDay(zone).toInstant().toEpochMilli()
        val nov12 = LocalDate.of(2026, 11, 12).atStartOfDay(zone).toInstant().toEpochMilli()

        repository.insertTransaction(
            Transaction(amount = 300.0, timestamp = oct15, note = "Oct snack", tagId = foodId, type = TransactionType.EXPENSE)
        )
        repository.insertTransaction(
            Transaction(amount = 750.0, timestamp = nov12, note = "Dinner, drinks", tagId = foodId, type = TransactionType.EXPENSE)
        )
        repository.insertTransaction(
            Transaction(amount = 40000.0, timestamp = nov12, note = "Monthly salary", tagId = salaryId, type = TransactionType.INCOME)
        )

        val allTx = repository.allTransactions.first()
        val from = LocalDate.of(2026, 11, 1)
        val to = LocalDate.of(2026, 11, 30)
        val summary = ReportExporter.buildRangeSummary(
            allTransactions = allTx,
            fromDate = from,
            toDate = to,
            zoneId = zone
        )

        assertEquals(2, summary.totalRecordsCount)
        assertEquals(1, summary.expenseCategories.size)
        assertEquals(1, summary.incomeCategories.size)
        assertEquals(750.0, summary.totalExpense, 0.001)
        assertEquals(40000.0, summary.totalIncome, 0.001)
        assertEquals(39250.0, summary.netBalance, 0.001)

        val csv = ReportExporter.generateCsvContent(summary, zone)
        assertTrue(csv.contains("2026-11-12,EXPENSE,Food,750,\"Dinner, drinks\""))
        assertTrue(csv.contains("2026-11-12,INCOME,Salary,40000,Monthly salary"))
        assertTrue(!csv.contains("Oct snack"))

        assertEquals(
            "MoneyTrack_Report_20261101_20261130.pdf",
            ReportExporter.suggestedFileName(from, to, "pdf")
        )
    }
}
