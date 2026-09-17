package com.dualshield.phone.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dualshield.phone.data.db.entity.AllowRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AllowRuleDao {

    @Query("SELECT * FROM allow_rules ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AllowRuleEntity>>

    @Query("SELECT * FROM allow_rules")
    suspend fun getAll(): List<AllowRuleEntity>

    @Query("SELECT COUNT(*) FROM allow_rules WHERE simScope = :scope OR simScope = 'BOTH'")
    fun observeCountForScope(scope: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: AllowRuleEntity): Long

    @Delete
    suspend fun delete(rule: AllowRuleEntity)

    @Query("DELETE FROM allow_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM allow_rules WHERE normalizedNumber = :number")
    suspend fun findByNumber(number: String): List<AllowRuleEntity>
}
