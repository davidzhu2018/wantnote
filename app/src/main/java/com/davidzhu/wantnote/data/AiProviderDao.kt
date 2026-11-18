package com.davidzhu.wantnote.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AiProviderDao {
    @Query("SELECT * FROM ai_providers ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<AiProvider>>

    @Query("SELECT * FROM ai_providers ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<AiProvider>

    @Query("SELECT * FROM ai_providers WHERE id = :id")
    suspend fun getById(id: String): AiProvider?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(provider: AiProvider)

    @Query("DELETE FROM ai_providers WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM ai_providers")
    suspend fun deleteAll()
}
