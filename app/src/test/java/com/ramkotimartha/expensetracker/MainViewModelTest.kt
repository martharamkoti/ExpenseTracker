package com.ramkotimartha.expensetracker

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.ramkotimartha.expensetracker.data.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.*
import java.time.YearMonth

/**
 * Unit tests for MainViewModel using MockK.
 * Tests core navigation and transaction/tag action delegation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val application = mockk<Application>(relaxed = true)
    private val database = mockk<AppDatabase>()
    private val transactionDao = mockk<TransactionDao>()
    private val tagDao = mockk<TagDao>()
    
    private lateinit var viewModel: MainViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        mockkObject(AppDatabase)
        every { AppDatabase.getDatabase(any()) } returns database
        every { database.transactionDao() } returns transactionDao
        every { database.tagDao() } returns tagDao
        
        // Mock initial flows to avoid initialization crashes
        every { transactionDao.getAllTransactionsWithTags() } returns flowOf(emptyList())
        every { tagDao.getTagsByType(TransactionType.INCOME) } returns flowOf(emptyList())
        every { tagDao.getTagsByType(TransactionType.EXPENSE) } returns flowOf(emptyList())
        every { transactionDao.getTagsInUse() } returns flowOf(emptyList())

        viewModel = MainViewModel(application)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `initial month is current month`() {
        Assert.assertEquals(YearMonth.now(), viewModel.selectedMonth.value)
    }

    @Test
    fun `nextMonth increments selected month`() {
        val current = viewModel.selectedMonth.value
        viewModel.nextMonth()
        Assert.assertEquals(current.plusMonths(1), viewModel.selectedMonth.value)
    }

    @Test
    fun `previousMonth decrements selected month`() {
        val current = viewModel.selectedMonth.value
        viewModel.previousMonth()
        Assert.assertEquals(current.minusMonths(1), viewModel.selectedMonth.value)
    }

    @Test
    fun `addTransaction calls dao insert`() = runTest {
        val amount = 100.0
        val type = TransactionType.EXPENSE
        val tagId = 1L
        val note = "Test"
        val timestamp = System.currentTimeMillis()

        coEvery { transactionDao.insertTransaction(any()) } returns 1L

        viewModel.addTransaction(amount, type, tagId, note, timestamp)
        advanceUntilIdle()

        coVerify { 
            transactionDao.insertTransaction(match { 
                it.amount == amount && it.type == type && it.tagId == tagId && it.note == note 
            }) 
        }
    }

    @Test
    fun `deleteTransaction calls dao delete`() = runTest {
        val transaction = Transaction(id = 1L, amount = 50.0, type = TransactionType.EXPENSE, tagId = 1L)
        
        coEvery { transactionDao.deleteTransaction(any()) } just Runs

        viewModel.deleteTransaction(transaction)
        advanceUntilIdle()

        coVerify { transactionDao.deleteTransaction(transaction) }
    }

    @Test
    fun `suggestTag returns matching tag for candidate tags`() {
        val tags = listOf(
            Tag(id = 1L, name = "Food", type = TransactionType.EXPENSE),
            Tag(id = 2L, name = "Transport", type = TransactionType.EXPENSE)
        )

        val suggestion = viewModel.suggestTag(
            note = "Uber cab ride",
            type = TransactionType.EXPENSE,
            candidateTags = tags
        )

        Assert.assertNotNull(suggestion)
        Assert.assertEquals("Transport", suggestion?.tag?.name)
    }
}
