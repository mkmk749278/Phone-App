package com.dualshield.phone.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dualshield.phone.data.db.entity.CallRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallRuleDao {

    @Query("SELECT * FROM call_rules ORDER BY priority ASC, name ASC")
    fun observeAll(): Flow<List<CallRuleEntity>>

    @Query("SELECT * FROM call_rules WHERE enabled = 1 ORDER BY priority ASC")
    fun observeEnabled(): Flow<List<CallRuleEntity>>

    @Query("SELECT * FROM call_rules WHERE enabled = 1 ORDER BY priority ASC")
    suspend fun getEnabled(): List<CallRuleEntity>

    @Query("SELECT * FROM call_rules ORDER BY priority ASC, name ASC")
    suspend fun getAll(): List<CallRuleEntity>

    @Query("SELECT * FROM call_rules WHERE id = :id")
    suspend fun getById(id: Long): CallRuleEntity?

    @Query("SELECT * FROM call_rules WHERE id = :id")
    fun observeById(id: Long): Flow<CallRuleEntity?>

    @Query("SELECT * FROM call_rules WHERE stableId = :stableId")
    suspend fun getByStableId(stableId: String): CallRuleEntity?

    @Query("SELECT COUNT(*) FROM call_rules WHERE enabled = 1 AND (simScope = :scope OR simScope = 'BOTH')")
    fun observeEnabledCountForScope(scope: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(rule: CallRuleEntity): Long

    @Update
    suspend fun update(rule: CallRuleEntity)

    @Delete
    suspend fun delete(rule: CallRuleEntity)

    @Query("DELETE FROM call_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM call_rules WHERE builtIn = 1 AND packId = :packId")
    suspend fun deleteBuiltInsForPack(packId: String)

    @Query("UPDATE call_rules SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, updatedAt: Long)

    @Query("UPDATE call_rules SET matchCount = matchCount + 1 WHERE id = :id")
    suspend fun incrementMatchCount(id: Long)

    /**
     * Applies a rule-pack row without clobbering user choices.
     *
     * A re-import refreshes the pattern and wording of a built-in rule but leaves
     * `enabled` and `simScope` alone, because those two fields are the user's decision.
     */
    @Query(
        """
        UPDATE call_rules
        SET name = :name,
            category = :category,
            pattern = :pattern,
            patternType = :patternType,
            action = :action,
            confidence = :confidence,
            provenance = :provenance,
            description = :description,
            priority = :priority,
            packId = :packId,
            updatedAt = :updatedAt
        WHERE stableId = :stableId AND builtIn = 1
        """,
    )
    suspend fun refreshBuiltIn(
        stableId: String,
        name: String,
        category: String,
        pattern: String,
        patternType: String,
        action: String,
        confidence: String,
        provenance: String,
        description: String,
        priority: Int,
        packId: String?,
        updatedAt: Long,
    )
}
