package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.AuditDao
import com.example.data.dao.CfwDao
import com.example.data.dao.UserDao
import com.example.data.kobo.KoboDao
import com.example.data.kobo.KoboSubmission
import com.example.data.model.AppUser
import com.example.data.model.AuditLog
import com.example.data.model.CfwBeneficiary
import com.example.data.model.DispatchMaterial
import com.example.data.model.DispatchRecord
import com.example.data.model.MaterialRequest
import com.example.data.model.RequestedMaterial

@Database(
    entities = [
        CfwBeneficiary::class,
        MaterialRequest::class,
        RequestedMaterial::class,
        DispatchRecord::class,
        DispatchMaterial::class,
        AuditLog::class,
        AppUser::class,
        KoboSubmission::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun cfwDao(): CfwDao
    abstract fun auditDao(): AuditDao
    abstract fun userDao(): UserDao
    abstract fun koboDao(): KoboDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Adds the kobo_submission table used by the new KoboToolbox Data feature.
         * This is an explicit migration (rather than relying on the destructive
         * fallback below) specifically so existing CFW Worker / dispatch data is
         * never wiped just because this new feature was added.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `kobo_submission` (
                        `submissionId` INTEGER NOT NULL,
                        `assetUid` TEXT NOT NULL,
                        `rawJson` TEXT NOT NULL,
                        `submissionTime` TEXT NOT NULL,
                        `validationStatus` TEXT NOT NULL,
                        `downloadedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`submissionId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_kobo_submission_assetUid` " +
                        "ON `kobo_submission` (`assetUid`)"
                )
            }
        }

        /**
         * Adds recipientName / recipientFcn to dispatch_record: since one DRR Code project can
         * now dispatch materials to different people over time, each dispatch visit needs to
         * record who actually received the materials that day, separate from the DRR's original
         * registrant. Existing dispatch rows default to empty strings — no data loss.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `dispatch_record` ADD COLUMN `recipientName` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `dispatch_record` ADD COLUMN `recipientFcn` TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cfw_tracker_database"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    // Only used if a device somehow lands on a version with no migration
                    // path at all (e.g. pre-v3 installs). Existing CFW data added in v3,
                    // Kobo data in v4, and per-dispatch recipients in v5 all go through
                    // addMigrations above.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
