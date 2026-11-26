package com.ramkotimartha.expensetracker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ramkotimartha.expensetracker.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Integrated Database tests using Robolectric and an In-Memory Room database.
 * Tests CRUD operations and relationship integrity (Cascades).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var transactionDao: TransactionDao
    private lateinit var tagDao: TagDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        transactionDao = db.transactionDao()
        tagDao = db.tagDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndReadTag() = runBlocking {
        val tag = Tag(name = "Food", type = TransactionType.EXPENSE)
        tagDao.insertTag(tag)
        val tags = tagDao.getTagsByType(TransactionType.EXPENSE).first()
        assertEquals(1, tags.size)
        assertEquals("Food", tags[0].name)
    }

    @Test
    fun insertAndReadTransactionWithTag() = runBlocking {
        val tagId = tagDao.insertTag(Tag(name = "Salary", type = TransactionType.INCOME))
        val transaction = Transaction(
            amount = 5000.0,
            type = TransactionType.INCOME,
            tagId = tagId,
            note = "Bonus"
        )
        transactionDao.insertTransaction(transaction)

        val list = transactionDao.getAllTransactionsWithTags().first()
        assertEquals(1, list.size)
        assertEquals(5000.0, list[0].transaction.amount, 0.0)
        assertEquals("Salary", list[0].tag.name)
    }

    @Test
    fun deleteTagCascadesToTransactions() = runBlocking {
        // Setup tag and linked transaction
        val tagId = tagDao.insertTag(Tag(name = "Transport", type = TransactionType.EXPENSE))
        val tag = tagDao.getTagsByType(TransactionType.EXPENSE).first().first()
        
        transactionDao.insertTransaction(Transaction(amount = 20.0, type = TransactionType.EXPENSE, tagId = tagId))
        
        // Verify transaction exists
        var transactions = transactionDao.getAllTransactionsWithTags().first()
        assertEquals(1, transactions.size)

        // Delete tag
        tagDao.deleteTag(tag)

        // Verify transaction is gone (Cascade)
        transactions = transactionDao.getAllTransactionsWithTags().first()
        assertTrue("Transactions should be deleted when their tag is deleted", transactions.isEmpty())
    }

    @Test
    fun updateTransactionPreservesId() = runBlocking {
        val tagId = tagDao.insertTag(Tag(name = "Bills", type = TransactionType.EXPENSE))
        val originalId = transactionDao.insertTransaction(Transaction(amount = 100.0, type = TransactionType.EXPENSE, tagId = tagId))
        
        val updatedTransaction = Transaction(id = originalId, amount = 150.0, type = TransactionType.EXPENSE, tagId = tagId, note = "Revised")
        transactionDao.insertTransaction(updatedTransaction)

        val list = transactionDao.getAllTransactionsWithTags().first()
        assertEquals(1, list.size)
        assertEquals(150.0, list[0].transaction.amount, 0.0)
        assertEquals("Revised", list[0].transaction.note)
    }
}
