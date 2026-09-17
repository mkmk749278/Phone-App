package com.dualshield.phone.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.dualshield.phone.data.db.entity.BlockedCallEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedCallDao {

    @Query("SELECT * FROM blocked_calls ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 500): Flow<List<BlockedCallEntity>>

    @Query("SELECT * FROM blocked_calls WHERE id = :id")
    fun observeById(id: Long): Flow<BlockedCallEntity?>

    @Query("SELECT COUNT(*) FROM blocked_calls")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM blocked_calls WHERE timestamp >= :since")
    fun observeCountSince(since: Long): Flow<Int>

    @Insert
    suspend fun insert(record: BlockedCallEntity): Long

    @Query("DELETE FROM blocked_calls WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM blocked_calls WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** Clearing the Vault is a history operation only; it must never touch rules. */
    @Query("DELETE FROM blocked_calls")
    suspend fun clear()
}
