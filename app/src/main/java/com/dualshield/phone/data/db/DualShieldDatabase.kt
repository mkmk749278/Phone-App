package com.dualshield.phone.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.dualshield.phone.data.db.dao.AllowRuleDao
import com.dualshield.phone.data.db.dao.BlockedCallDao
import com.dualshield.phone.data.db.dao.BlockedMessageDao
import com.dualshield.phone.data.db.dao.CallRuleDao
import com.dualshield.phone.data.db.dao.RulePackMetadataDao
import com.dualshield.phone.data.db.dao.SimProfileDao
import com.dualshield.phone.data.db.entity.AllowRuleEntity
import com.dualshield.phone.data.db.entity.BlockedCallEntity
import com.dualshield.phone.data.db.entity.BlockedMessageEntity
import com.dualshield.phone.data.db.entity.CallRuleEntity
import com.dualshield.phone.data.db.entity.RulePackMetadataEntity
import com.dualshield.phone.data.db.entity.SimProfileEntity

/**
 * The single local database. There is no remote counterpart and never will be.
 */
@Database(
    entities = [
        SimProfileEntity::class,
        CallRuleEntity::class,
        AllowRuleEntity::class,
        BlockedCallEntity::class,
        BlockedMessageEntity::class,
        RulePackMetadataEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class DualShieldDatabase : RoomDatabase() {

    abstract fun simProfileDao(): SimProfileDao
    abstract fun callRuleDao(): CallRuleDao
    abstract fun allowRuleDao(): AllowRuleDao
    abstract fun blockedCallDao(): BlockedCallDao
    abstract fun blockedMessageDao(): BlockedMessageDao
    abstract fun rulePackMetadataDao(): RulePackMetadataDao

    companion object {
        private const val NAME = "dualshield.db"

        @Volatile
        private var instance: DualShieldDatabase? = null

        fun get(context: Context): DualShieldDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): DualShieldDatabase =
            Room.databaseBuilder(context, DualShieldDatabase::class.java, NAME)
                // No destructive fallback: losing a user's blocklist silently would be a
                // far worse outcome than a loud failure during development.
                .build()
    }
}
