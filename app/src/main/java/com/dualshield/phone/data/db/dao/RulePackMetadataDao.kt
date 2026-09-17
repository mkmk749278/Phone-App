package com.dualshield.phone.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.dualshield.phone.data.db.entity.RulePackMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RulePackMetadataDao {

    @Query("SELECT * FROM rule_pack_metadata ORDER BY packId ASC")
    fun observeAll(): Flow<List<RulePackMetadataEntity>>

    @Query("SELECT * FROM rule_pack_metadata WHERE packId = :packId")
    suspend fun get(packId: String): RulePackMetadataEntity?

    @Upsert
    suspend fun upsert(metadata: RulePackMetadataEntity)
}
