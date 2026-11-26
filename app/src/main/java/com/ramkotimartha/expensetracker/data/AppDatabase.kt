package com.ramkotimartha.expensetracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [Transaction::class, Tag::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun tagDao(): TagDao

    companion object {
        @Volatile
        private var Instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Production-safe data migration using SQL
                // Using INSERT OR IGNORE based on tag name and type to prevent duplicates
                // Note: Since tags table doesn't have a unique constraint on name+type in version 1,
                // we check existence before inserting to be absolutely safe.
                
                val defaultExpenses = listOf("Food", "Groceries", "Transport", "Bills", "Shopping", "Health")
                val defaultIncomes = listOf("Salary", "Other")

                defaultExpenses.forEach { name ->
                    db.execSQL(
                        "INSERT INTO tags (name, type) " +
                        "SELECT '$name', 'EXPENSE' " +
                        "WHERE NOT EXISTS (SELECT 1 FROM tags WHERE name = '$name' AND type = 'EXPENSE')"
                    )
                }

                defaultIncomes.forEach { name ->
                    db.execSQL(
                        "INSERT INTO tags (name, type) " +
                        "SELECT '$name', 'INCOME' " +
                        "WHERE NOT EXISTS (SELECT 1 FROM tags WHERE name = '$name' AND type = 'INCOME')"
                    )
                }
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "expense_database")
                    .addMigrations(MIGRATION_1_2)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Still preload for fresh installs (onCreate only runs if DB file is new)
                            Instance?.let { database ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    val tagDao = database.tagDao()
                                    listOf("Food", "Groceries", "Transport", "Bills", "Shopping", "Health").forEach {
                                        tagDao.insertTag(Tag(name = it, type = TransactionType.EXPENSE))
                                    }
                                    listOf("Salary", "Other").forEach {
                                        tagDao.insertTag(Tag(name = it, type = TransactionType.INCOME))
                                    }
                                }
                            }
                        }
                    })
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { Instance = it }
            }
        }
    }
}
