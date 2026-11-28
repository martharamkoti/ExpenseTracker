package com.ramkotimartha.expensetracker.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.util.Locale

data class RestoreSummary(
    val restoredTagsCount: Int,
    val restoredTransactionsCount: Int
)

class ExpenseRepository(
    private val transactionDao: TransactionDao,
    private val tagDao: TagDao,
    private val database: AppDatabase? = null
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

    suspend fun getBackupSnapshot(): Pair<List<Tag>, List<Transaction>> {
        val tags = tagDao.getAllTagsOnce()
        val transactions = transactionDao.getAllTransactionsOnce()
        return tags to transactions
    }

    suspend fun restoreFromBackup(
        backupTags: List<Tag>,
        backupTransactions: List<Transaction>,
        replaceExisting: Boolean
    ): RestoreSummary {
        val executeRestore: suspend () -> RestoreSummary = {
            if (replaceExisting) {
                transactionDao.deleteAllTransactions()
                tagDao.deleteAllTags()

                val tagIdMap = mutableMapOf<Long, Long>()
                for (tag in backupTags) {
                    val newId = tagDao.insertTag(
                        Tag(
                            id = 0,
                            name = tag.name,
                            type = tag.type,
                            iconKey = tag.iconKey
                        )
                    )
                    tagIdMap[tag.id] = newId
                }

                // Ensure standard default tags remain present if backup had none for a type
                if (backupTags.none { it.type == TransactionType.EXPENSE }) {
                    listOf("Food", "Groceries", "Transport", "Bills", "Shopping", "Health").forEach {
                        tagDao.insertTag(Tag(name = it, type = TransactionType.EXPENSE))
                    }
                }
                if (backupTags.none { it.type == TransactionType.INCOME }) {
                    listOf("Salary", "Other").forEach {
                        tagDao.insertTag(Tag(name = it, type = TransactionType.INCOME))
                    }
                }

                var insertedTxCount = 0
                for (tx in backupTransactions) {
                    val mappedTagId = tagIdMap[tx.tagId] ?: continue
                    transactionDao.insertTransaction(
                        Transaction(
                            id = 0,
                            amount = tx.amount,
                            timestamp = tx.timestamp,
                            note = tx.note,
                            tagId = mappedTagId,
                            type = tx.type
                        )
                    )
                    insertedTxCount++
                }
                RestoreSummary(
                    restoredTagsCount = tagIdMap.size,
                    restoredTransactionsCount = insertedTxCount
                )
            } else {
                // Merge mode: match existing tags by (lowercase name + type), insert only new ones
                val existingTags = tagDao.getAllTagsOnce()
                val existingByKey = existingTags.associateBy {
                    "${it.type.name}:${it.name.trim().lowercase(Locale.ROOT)}"
                }.toMutableMap()

                val tagIdMap = mutableMapOf<Long, Long>()
                var addedTagsCount = 0

                for (tag in backupTags) {
                    val lookupKey = "${tag.type.name}:${tag.name.trim().lowercase(Locale.ROOT)}"
                    val existing = existingByKey[lookupKey]
                    if (existing != null) {
                        if (existing.iconKey == null && tag.iconKey != null) {
                            tagDao.insertTag(existing.copy(iconKey = tag.iconKey))
                        }
                        tagIdMap[tag.id] = existing.id
                    } else {
                        val newId = tagDao.insertTag(
                            Tag(
                                id = 0,
                                name = tag.name.trim(),
                                type = tag.type,
                                iconKey = tag.iconKey
                            )
                        )
                        val created = Tag(id = newId, name = tag.name.trim(), type = tag.type, iconKey = tag.iconKey)
                        existingByKey[lookupKey] = created
                        tagIdMap[tag.id] = newId
                        addedTagsCount++
                    }
                }

                val existingTransactions = transactionDao.getAllTransactionsOnce()
                val existingSignatures = existingTransactions.map { tx ->
                    "${tx.type.name}|${tx.tagId}|${tx.amount}|${tx.timestamp}|${tx.note?.trim().orEmpty()}"
                }.toMutableSet()

                var addedTxCount = 0
                for (tx in backupTransactions) {
                    val mappedTagId = tagIdMap[tx.tagId] ?: continue
                    val signature = "${tx.type.name}|$mappedTagId|${tx.amount}|${tx.timestamp}|${tx.note?.trim().orEmpty()}"
                    if (existingSignatures.add(signature)) {
                        transactionDao.insertTransaction(
                            Transaction(
                                id = 0,
                                amount = tx.amount,
                                timestamp = tx.timestamp,
                                note = tx.note,
                                tagId = mappedTagId,
                                type = tx.type
                            )
                        )
                        addedTxCount++
                    }
                }
                RestoreSummary(
                    restoredTagsCount = addedTagsCount,
                    restoredTransactionsCount = addedTxCount
                )
            }
        }

        return if (database != null) {
            database.withTransaction { executeRestore() }
        } else {
            executeRestore()
        }
    }
}
