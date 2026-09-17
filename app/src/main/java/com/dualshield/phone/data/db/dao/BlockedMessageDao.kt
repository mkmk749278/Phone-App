package com.dualshield.phone.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.dualshield.phone.data.db.entity.BlockedMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedMessageDao {

    @Query("SELECT * FROM blocked_messages ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 500): Flow<List<BlockedMessageEntity>>

    @Query("SELECT COUNT(*) FROM blocked_messages")
    fun observeCount(): Flow<Int>

    @Insert
    suspend fun insert(record: BlockedMessageEntity): Long

    @Query("DELETE FROM blocked_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM blocked_messages")
    suspend fun clear()
}
