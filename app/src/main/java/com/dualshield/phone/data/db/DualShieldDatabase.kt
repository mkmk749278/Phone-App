package com.dualshield.phone.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
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

        /**
         * Adds the calls/SMS target to every rule, and the per-SIM "always allow contacts"
         * switch.
         *
         * Existing rules keep acting on calls only, which is what they did before the column
         * existed — an upgrade must not silently widen what a user's rule blocks.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE call_rules ADD COLUMN blocksCalls INTEGER NOT NULL DEFAULT 1",
                )
                db.execSQL(
                    "ALTER TABLE call_rules ADD COLUMN blocksSms INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE sim_profiles ADD COLUMN allowContacts INTEGER NOT NULL DEFAULT 1",
                )
            }
        }

        @Volatile
        private var instance: DualShieldDatabase? = null

        fun get(context: Context): DualShieldDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): DualShieldDatabase =
            Room.databaseBuilder(context, DualShieldDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                // No destructive fallback: losing a user's blocklist silently would be a
                // far worse outcome than a loud failure during development.
                .build()
    }
}
