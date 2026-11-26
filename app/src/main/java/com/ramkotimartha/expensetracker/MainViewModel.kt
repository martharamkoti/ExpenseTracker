package com.ramkotimartha.expensetracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ramkotimartha.expensetracker.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ExpenseRepository
    private val themeManager by lazy { ThemeManager(application) }
    
    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    val allTransactions: StateFlow<List<TransactionWithTag>>
    val incomeTags: StateFlow<List<Tag>>
    val expenseTags: StateFlow<List<Tag>>
    val tagsInUse: StateFlow<Set<Long>>

    val theme: StateFlow<AppTheme> = themeManager.theme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppTheme.DARK)

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ExpenseRepository(database.transactionDao(), database.tagDao())

        allTransactions = repository.allTransactions
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        incomeTags = repository.getTagsByType(TransactionType.INCOME)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        expenseTags = repository.getTagsByType(TransactionType.EXPENSE)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        tagsInUse = repository.getTagsInUse()
            .map { it.toSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    }

    fun selectMonth(month: YearMonth) {
        _selectedMonth.value = month
    }

    fun nextMonth() {
        _selectedMonth.value = _selectedMonth.value.plusMonths(1)
    }

    fun previousMonth() {
        _selectedMonth.value = _selectedMonth.value.minusMonths(1)
    }

    fun setTheme(theme: AppTheme) {
        themeManager.setTheme(theme)
    }

    fun addTransaction(amount: Double, type: TransactionType, tagId: Long, note: String?, timestamp: Long) {
        viewModelScope.launch {
            repository.insertTransaction(
                Transaction(amount = amount, type = type, tagId = tagId, note = note, timestamp = timestamp)
            )
        }
    }

    fun updateTransaction(id: Long, amount: Double, type: TransactionType, tagId: Long, note: String?, timestamp: Long) {
        viewModelScope.launch {
            repository.insertTransaction(
                Transaction(id = id, amount = amount, type = type, tagId = tagId, note = note, timestamp = timestamp)
            )
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }

    fun addTag(name: String, type: TransactionType) {
        viewModelScope.launch {
            repository.insertTag(Tag(name = name, type = type))
        }
    }

    fun deleteTag(tag: Tag) {
        viewModelScope.launch {
            repository.deleteTag(tag)
        }
    }
}
