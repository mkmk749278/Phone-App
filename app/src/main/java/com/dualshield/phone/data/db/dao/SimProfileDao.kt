package com.dualshield.phone.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.dualshield.phone.data.db.entity.SimProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SimProfileDao {

    @Query("SELECT * FROM sim_profiles ORDER BY slotIndex ASC")
    fun observeAll(): Flow<List<SimProfileEntity>>

    @Query("SELECT * FROM sim_profiles ORDER BY slotIndex ASC")
    suspend fun getAll(): List<SimProfileEntity>

    @Query("SELECT * FROM sim_profiles WHERE slotIndex = :slotIndex")
    suspend fun getBySlot(slotIndex: Int): SimProfileEntity?

    @Upsert
    suspend fun upsert(profile: SimProfileEntity)

    @Upsert
    suspend fun upsertAll(profiles: List<SimProfileEntity>)

    @Query("UPDATE sim_profiles SET filteringEnabled = :enabled WHERE slotIndex = :slotIndex")
    suspend fun setFilteringEnabled(slotIndex: Int, enabled: Boolean)

    @Query("UPDATE sim_profiles SET label = :label WHERE slotIndex = :slotIndex")
    suspend fun setLabel(slotIndex: Int, label: String)

    @Query("UPDATE sim_profiles SET allowContacts = :allow WHERE slotIndex = :slotIndex")
    suspend fun setAllowContacts(slotIndex: Int, allow: Boolean)

    @Query(
        "UPDATE sim_profiles SET subscriptionId = :subscriptionId, carrierName = :carrierName, " +
            "lastSeenAt = :seenAt WHERE slotIndex = :slotIndex",
    )
    suspend fun setHardwareInfo(
        slotIndex: Int,
        subscriptionId: Int,
        carrierName: String,
        seenAt: Long,
    )
}
