package com.ramkotimartha.expensetracker.data

import kotlinx.coroutines.flow.Flow

class ExpenseRepository(
    private val transactionDao: TransactionDao,
    private val tagDao: TagDao
) {
    val allTransactions: Flow<List<TransactionWithTag>> = transactionDao.getAllTransactionsWithTags()
    
    fun getTagsByType(type: TransactionType): Flow<List<Tag>> = tagDao.getTagsByType(type)
    
    suspend fun insertTransaction(transaction: Transaction): Long {
        return transactionDao.insertTransaction(transaction)
    }

    suspend fun deleteTransaction(transaction: Transaction) {
        transactionDao.deleteTransaction(transaction)
    }
    
    suspend fun insertTag(tag: Tag): Long {
        return tagDao.insertTag(tag)
    }

    fun getTagsInUse(): Flow<List<Long>> = transactionDao.getTagsInUse()

    suspend fun deleteTag(tag: Tag) {
        tagDao.deleteTag(tag)
    }

    suspend fun reassignAndDeleteTag(tagToDelete: Tag, replacementTagId: Long) {
        transactionDao.reassignTransactionsTag(oldTagId = tagToDelete.id, newTagId = replacementTagId)
        tagDao.deleteTag(tagToDelete)
    }
}
