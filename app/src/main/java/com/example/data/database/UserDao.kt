package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    // --- User Portfolio Assets ---
    @Query("SELECT * FROM user_assets")
    fun getAllAssetsFlow(): Flow<List<UserAsset>>

    @Query("SELECT * FROM user_assets")
    suspend fun getAllAssets(): List<UserAsset>

    @Query("SELECT * FROM user_assets WHERE symbol = :symbol")
    suspend fun getAsset(symbol: String): UserAsset?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsset(asset: UserAsset)

    @Update
    suspend fun updateAsset(asset: UserAsset)

    @Delete
    suspend fun deleteAsset(asset: UserAsset)

    @Query("DELETE FROM user_assets")
    suspend fun clearAssets()

    // --- Trade Transactions ---
    @Query("SELECT * FROM trade_transactions ORDER BY timestamp DESC")
    fun getAllTransactionsFlow(): Flow<List<TradeTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(tx: TradeTransaction)

    @Query("DELETE FROM trade_transactions")
    suspend fun clearTransactions()

    // --- User Profile ---
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getProfileFlow(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getProfile(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfile)

    @Update
    suspend fun updateProfile(profile: UserProfile)

    // --- Lesson Progress ---
    @Query("SELECT * FROM lesson_progress")
    fun getAllLessonProgressFlow(): Flow<List<LessonProgress>>

    @Query("SELECT * FROM lesson_progress WHERE lessonId = :lessonId")
    suspend fun getLessonProgress(lessonId: String): LessonProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLessonProgress(progress: LessonProgress)
}
