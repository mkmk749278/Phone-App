package com.dualshield.phone.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Provenance for an installed rule pack. Shown on the Settings → Rule packs screen. */
@Entity(tableName = "rule_pack_metadata")
data class RulePackMetadataEntity(
    @PrimaryKey val packId: String,
    val schemaVersion: Int,
    val packVersion: Int,
    val country: String,
    val source: String,
    val ruleCount: Int,
    val installedAt: Long,
)
