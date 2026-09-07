package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.AuditDao
import com.example.data.dao.CfwDao
import com.example.data.dao.NotificationDao
import com.example.data.dao.ProjectDao
import com.example.data.dao.UserDao
import com.example.data.kobo.KoboDao
import com.example.data.kobo.KoboSubmission
import com.example.data.model.AppNotification
import com.example.data.model.AppUser
import com.example.data.model.AuditLog
import com.example.data.model.CfwBeneficiary
import com.example.data.model.DailyMaterialRequest
import com.example.data.model.DailyRequestMaterial
import com.example.data.model.DispatchMaterial
import com.example.data.model.DispatchRecord
import com.example.data.model.EngineerEstimate
import com.example.data.model.EstimateMaterial
import com.example.data.model.GateConfirmation
import com.example.data.model.GateConfirmationMaterial
import com.example.data.model.MaterialRequest
import com.example.data.model.Project
import com.example.data.model.ProjectMaterial
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
        KoboSubmission::class,
        // CFW MaterialFlow project workflow (added in v6) — see AppDatabase migration notes below.
        Project::class,
        ProjectMaterial::class,
        EngineerEstimate::class,
        EstimateMaterial::class,
        DailyMaterialRequest::class,
        DailyRequestMaterial::class,
        GateConfirmation::class,
        GateConfirmationMaterial::class,
        AppNotification::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun cfwDao(): CfwDao
    abstract fun auditDao(): AuditDao
    abstract fun userDao(): UserDao
    abstract fun koboDao(): KoboDao
    abstract fun projectDao(): ProjectDao
    abstract fun notificationDao(): NotificationDao

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

        /**
         * CFW MaterialFlow (v6): adds the Project approval/dispatch workflow —
         * Project, ProjectMaterial, EngineerEstimate(+materials), DailyMaterialRequest(+materials),
         * GateConfirmation(+materials), AppNotification — plus two small additive columns on
         * existing tables (cfw_beneficiary.projectId, app_user.role). Nothing existing is
         * dropped, renamed, or backfilled destructively: all new columns are nullable or have
         * safe defaults, so every pre-v6 row keeps working exactly as before.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `cfw_beneficiary` ADD COLUMN `projectId` INTEGER")
                db.execSQL("ALTER TABLE `app_user` ADD COLUMN `role` TEXT NOT NULL DEFAULT 'TM'")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `project` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `koboSubmissionId` TEXT,
                        `drrCode` TEXT NOT NULL,
                        `projectType` TEXT NOT NULL,
                        `tmName` TEXT NOT NULL,
                        `projectLocation` TEXT NOT NULL,
                        `block` TEXT NOT NULL,
                        `status` TEXT NOT NULL DEFAULT 'PLANNED',
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_project_drrCode` ON `project` (`drrCode`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `project_material` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `projectId` INTEGER NOT NULL,
                        `materialName` TEXT NOT NULL,
                        `approvedQuantity` REAL NOT NULL,
                        `unit` TEXT NOT NULL,
                        `source` TEXT NOT NULL DEFAULT 'KOBO',
                        `lowStockNotifiedAt` INTEGER,
                        `finishedNotifiedAt` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_project_material_projectId` ON `project_material` (`projectId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `engineer_estimate` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `projectId` INTEGER NOT NULL,
                        `engineerName` TEXT NOT NULL,
                        `visitDate` TEXT NOT NULL,
                        `measurementDetails` TEXT NOT NULL DEFAULT '',
                        `notes` TEXT NOT NULL DEFAULT '',
                        `status` TEXT NOT NULL DEFAULT 'SUBMITTED',
                        `bossRemarks` TEXT NOT NULL DEFAULT '',
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_engineer_estimate_projectId` ON `engineer_estimate` (`projectId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `estimate_material` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `estimateId` INTEGER NOT NULL,
                        `materialName` TEXT NOT NULL,
                        `estimatedQuantity` REAL NOT NULL,
                        `unit` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_estimate_material_estimateId` ON `estimate_material` (`estimateId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_material_request` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `projectId` INTEGER NOT NULL,
                        `requestDate` TEXT NOT NULL,
                        `requestedBy` TEXT NOT NULL,
                        `status` TEXT NOT NULL DEFAULT 'PENDING',
                        `bossRemarks` TEXT NOT NULL DEFAULT '',
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_material_request_projectId` ON `daily_material_request` (`projectId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_request_material` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `dailyRequestId` INTEGER NOT NULL,
                        `projectMaterialId` INTEGER NOT NULL,
                        `materialName` TEXT NOT NULL,
                        `unit` TEXT NOT NULL,
                        `requestedQuantity` REAL NOT NULL,
                        `approvedQuantity` REAL,
                        `actualDispatchedQuantity` REAL,
                        `bossRemarks` TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_request_material_dailyRequestId` ON `daily_request_material` (`dailyRequestId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_request_material_projectMaterialId` ON `daily_request_material` (`projectMaterialId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `gate_confirmation` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `dispatchId` INTEGER NOT NULL,
                        `collectorName` TEXT NOT NULL,
                        `confirmationDate` TEXT NOT NULL,
                        `confirmationTime` TEXT NOT NULL DEFAULT '',
                        `remarks` TEXT NOT NULL DEFAULT '',
                        `photoPath` TEXT,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gate_confirmation_dispatchId` ON `gate_confirmation` (`dispatchId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `gate_confirmation_material` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `gateConfirmationId` INTEGER NOT NULL,
                        `dispatchMaterialId` INTEGER NOT NULL,
                        `materialName` TEXT NOT NULL,
                        `unit` TEXT NOT NULL,
                        `actualCollectedQuantity` REAL NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gate_confirmation_material_gateConfirmationId` ON `gate_confirmation_material` (`gateConfirmationId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `app_notification` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `type` TEXT NOT NULL,
                        `targetRole` TEXT NOT NULL,
                        `projectId` INTEGER,
                        `title` TEXT NOT NULL,
                        `message` TEXT NOT NULL,
                        `isRead` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_app_notification_targetRole` ON `app_notification` (`targetRole`)")
            }
        }

        /**
         * CFW MaterialFlow (v7): links the existing dispatch_record/dispatch_material tables
         * (Warehouse's actual-dispatch step) to the new daily_material_request/
         * daily_request_material tables, instead of creating parallel dispatch tables. Both
         * new columns are nullable — every pre-v7 dispatch row is untouched and keeps working
         * exactly as before; only new CFW MaterialFlow dispatches populate them.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `dispatch_record` ADD COLUMN `dailyRequestId` INTEGER")
                db.execSQL("ALTER TABLE `dispatch_material` ADD COLUMN `dailyRequestMaterialId` INTEGER")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cfw_tracker_database" // kept as-is on purpose: renaming would orphan existing installs' data
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    // Only used if a device somehow lands on a version with no migration
                    // path at all (e.g. pre-v3 installs). Existing CFW data added in v3,
                    // Kobo data in v4, per-dispatch recipients in v5, the CFW MaterialFlow
                    // project workflow in v6, and its Warehouse/Gate dispatch linking in v7
                    // all go through addMigrations above.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
