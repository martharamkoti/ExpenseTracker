package com.ramkotimartha.expensetracker

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ramkotimartha.expensetracker.ai.CoachMessage
import com.ramkotimartha.expensetracker.ai.FinanceCoachEngine
import com.ramkotimartha.expensetracker.ai.MessageRole
import com.ramkotimartha.expensetracker.ai.ReceiptScanner
import com.ramkotimartha.expensetracker.ai.ScannedReceiptResult
import com.ramkotimartha.expensetracker.ai.SmartTagSuggester
import com.ramkotimartha.expensetracker.ai.TagSuggestion
import com.ramkotimartha.expensetracker.data.*
import com.ramkotimartha.expensetracker.utils.CrashlyticsLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth

data class CoachUiState(
    val messages: List<CoachMessage> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val lastFailedQuestion: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ExpenseRepository
    private val themeManager by lazy { ThemeManager(application) }
    
    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    private val _coachUiState = MutableStateFlow(CoachUiState())
    val coachUiState: StateFlow<CoachUiState> = _coachUiState.asStateFlow()

    private val _isTransactionsLoading = MutableStateFlow(true)
    val isTransactionsLoading: StateFlow<Boolean> = _isTransactionsLoading.asStateFlow()

    val allTransactions: StateFlow<List<TransactionWithTag>>
    val incomeTags: StateFlow<List<Tag>>
    val expenseTags: StateFlow<List<Tag>>
    val tagsInUse: StateFlow<Set<Long>>
    val tagUsageCounts: StateFlow<Map<Long, Int>>

    // Expose synchronous StateFlow directly from ThemeManager so Frame 1 uses the exact
    // saved theme without briefly flashing AppTheme.DARK on cold start.
    val theme: StateFlow<AppTheme> = themeManager.theme

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ExpenseRepository(database.transactionDao(), database.tagDao())

        allTransactions = repository.allTransactions
            .onEach { _isTransactionsLoading.value = false }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        incomeTags = repository.getTagsByType(TransactionType.INCOME)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        expenseTags = repository.getTagsByType(TransactionType.EXPENSE)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        val rawTagsInUse = repository.getTagsInUse()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        tagUsageCounts = rawTagsInUse
            .map { ids -> ids.groupingBy { it }.eachCount() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

        tagsInUse = rawTagsInUse
            .map { it.toSet() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
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

    fun restoreTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.insertTransaction(transaction)
        }
    }

    fun addTag(name: String, type: TransactionType, iconKey: String? = null) {
        viewModelScope.launch {
            repository.insertTag(Tag(name = name, type = type, iconKey = iconKey))
        }
    }

    fun deleteTag(tag: Tag) {
        viewModelScope.launch {
            repository.deleteTag(tag)
        }
    }

    fun reassignAndDeleteTag(tagToDelete: Tag, replacementTagId: Long) {
        viewModelScope.launch {
            repository.reassignAndDeleteTag(tagToDelete, replacementTagId)
        }
    }

    fun suggestTag(
        note: String,
        type: TransactionType,
        candidateTags: List<Tag>? = null
    ): TagSuggestion? {
        val tags = candidateTags ?: when (type) {
            TransactionType.INCOME -> incomeTags.value
            TransactionType.EXPENSE -> expenseTags.value
        }
        return SmartTagSuggester.suggestTag(
            note = note,
            type = type,
            availableTags = tags,
            pastTransactions = allTransactions.value
        )
    }

    suspend fun scanReceipt(
        context: Context,
        imageUri: Uri,
        candidateTags: List<Tag>? = null
    ): ScannedReceiptResult {
        val tags = candidateTags ?: expenseTags.value
        return ReceiptScanner.scanReceipt(
            context = context,
            imageUri = imageUri,
            availableTags = tags,
            pastTransactions = allTransactions.value
        )
    }

    fun askFinanceCoach(question: String, typingDelayMs: Long = 260L) {
        val trimmed = question.trim()
        if (trimmed.isEmpty() || _coachUiState.value.isLoading) return

        val userMessage = CoachMessage(
            role = MessageRole.USER,
            text = trimmed
        )

        _coachUiState.update {
            it.copy(
                messages = it.messages + userMessage,
                isLoading = true,
                errorMessage = null,
                lastFailedQuestion = null
            )
        }

        viewModelScope.launch {
            try {
                if (typingDelayMs > 0L) {
                    delay(typingDelayMs)
                }
                val currentTransactions = allTransactions.value.ifEmpty {
                    repository.allTransactions.first()
                }
                val currentTags = (incomeTags.value + expenseTags.value).ifEmpty {
                    repository.getTagsByType(TransactionType.INCOME).first() +
                        repository.getTagsByType(TransactionType.EXPENSE).first()
                }
                val reply = FinanceCoachEngine.answerQuestion(
                    question = trimmed,
                    transactions = currentTransactions,
                    availableTags = currentTags,
                    defaultMonth = YearMonth.now()
                )
                _coachUiState.update {
                    it.copy(
                        messages = it.messages + reply,
                        isLoading = false,
                        errorMessage = null,
                        lastFailedQuestion = null
                    )
                }
            } catch (e: Exception) {
                CrashlyticsLogger.log(e, "Finance Coach failed to answer question")
                _coachUiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Unable to analyze expenses right now. Please try again.",
                        lastFailedQuestion = trimmed
                    )
                }
            }
        }
    }

    fun retryLastCoachQuestion(typingDelayMs: Long = 260L) {
        val lastFailed = _coachUiState.value.lastFailedQuestion ?: return
        // Remove the trailing failed user bubble before re-submitting cleanly
        _coachUiState.update { state ->
            val trimmedMessages = if (state.messages.lastOrNull()?.role == MessageRole.USER &&
                state.messages.lastOrNull()?.text == lastFailed
            ) {
                state.messages.dropLast(1)
            } else {
                state.messages
            }
            state.copy(
                messages = trimmedMessages,
                errorMessage = null,
                lastFailedQuestion = null
            )
        }
        askFinanceCoach(lastFailed, typingDelayMs)
    }

    fun clearCoachError() {
        _coachUiState.update { it.copy(errorMessage = null, lastFailedQuestion = null) }
    }

    fun clearCoachConversation() {
        _coachUiState.value = CoachUiState()
    }
}
