package com.ramkotimartha.expensetracker.data

import androidx.room.*
import androidx.room.Transaction as RoomTransaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags WHERE type = :type")
    fun getTagsByType(type: TransactionType): Flow<List<Tag>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: Tag): Long

    @Delete
    suspend fun deleteTag(tag: Tag)
}

data class TransactionWithTag(
    @Embedded
    val transaction: Transaction,
    @Relation(
        parentColumn = "tagId",
        entityColumn = "id"
    )
    val tag: Tag
)

@Dao
interface TransactionDao {
    @RoomTransaction
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC, id DESC")
    fun getAllTransactionsWithTags(): Flow<List<TransactionWithTag>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Query("SELECT SUM(amount) FROM transactions WHERE type = :type")
    fun getTotalAmountByType(type: TransactionType): Flow<Double?>

    @Query("SELECT tagId FROM transactions")
    fun getTagsInUse(): Flow<List<Long>>
}
