package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cfw_beneficiary",
    indices = [Index(value = ["drrCode"], unique = true)]
)
data class CfwBeneficiary(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val drrCode: String,
    val cfwName: String,
    val fcnNumber: String,
    val projectId: Long? = null, // links to Project.id once the new workflow is in use; null for legacy rows
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "material_request")
data class MaterialRequest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val beneficiaryId: Long,
    val projectId: String = "PROJ-2026-CFW",
    val requestDate: String, // YYYY-MM-DD
    val status: String = "PENDING", // PENDING, COMPLETED
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "requested_material")
data class RequestedMaterial(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val requestId: Long,
    val materialName: String,
    val approvedQuantity: Double,
    val unit: String
)

@Entity(tableName = "dispatch_record")
data class DispatchRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val requestId: Long, // legacy MaterialRequest.id; 0 (sentinel) for CFW MaterialFlow dispatches, which use dailyRequestId instead
    val dispatchDate: String, // YYYY-MM-DD
    val time: String = "", // HH:mm
    val collectorName: String,
    val recipientName: String = "", // who actually received materials on this visit
    val recipientFcn: String = "",  // that recipient's FCN Number (may differ from the DRR's registrant)
    val latitude: Double? = null,
    val longitude: Double? = null,
    val note: String = "",
    val visitNumber: Int = 1,
    val syncStatus: String = "PENDING", // PENDING, SYNCED, FAILED
    val syncErrorMessage: String? = null,
    val dailyRequestId: Long? = null, // links this dispatch to a DailyMaterialRequest (Warehouse's actual-dispatch step); null for legacy dispatches
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "dispatch_material")
data class DispatchMaterial(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dispatchId: Long,
    val materialId: Long, // references RequestedMaterial.id; 0 (sentinel) for CFW MaterialFlow lines, which use dailyRequestMaterialId instead
    val dispatchedQuantity: Double,
    val remarks: String = "",
    val dailyRequestMaterialId: Long? = null // links this line to a DailyRequestMaterial (Warehouse's actual quantity for that line); null for legacy dispatches
)

@Entity(tableName = "audit_log")
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val action: String,
    val details: String,
    val timestamp: Long = System.currentTimeMillis(),
    val user: String
)

@Entity(
    tableName = "app_user",
    indices = [Index(value = ["name"], unique = true)]
)
data class AppUser(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val password: String, // 8-digit numeric access code
    val role: String = "TM", // TM, BOSS, ENGINEER, WAREHOUSE, GATE
    val createdAt: Long = System.currentTimeMillis()
)

// --- CFW MaterialFlow project workflow entities ---
// Project sits above the existing CfwBeneficiary/MaterialRequest/DispatchRecord chain:
// a Project is the thing Boss approves materials for; CfwBeneficiary (a DRR-registered
// person) can optionally link to one via CfwBeneficiary.projectId. Existing dispatch
// history is reused as-is (see GateConfirmationMaterial, which references DispatchMaterial
// instead of duplicating the dispatched-quantity column).

@Entity(
    tableName = "project",
    indices = [Index(value = ["drrCode"], unique = true)]
)
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val koboSubmissionId: String? = null, // Kobo's stable submission id, used to avoid duplicate imports
    val drrCode: String,
    val projectType: String,
    val tmName: String,
    val projectLocation: String,
    val block: String,
    val status: String = "PLANNED", // PLANNED, ENGINEER_ESTIMATE_SUBMITTED, MATERIALS_APPROVED, IN_PROGRESS, COMPLETED
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** Boss-approved material quantities for a Project. Generic name/qty/unit — never one column per material. */
@Entity(tableName = "project_material")
data class ProjectMaterial(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val materialName: String,
    val approvedQuantity: Double,
    val unit: String,
    val source: String = "KOBO", // KOBO, ENGINEER_ESTIMATE, MANUAL
    // Set once, the first time this material's remaining quantity crosses the threshold, so the
    // app notifies exactly once per state change instead of every time a screen opens.
    val lowStockNotifiedAt: Long? = null,
    val finishedNotifiedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "engineer_estimate")
data class EngineerEstimate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val engineerName: String,
    val visitDate: String, // YYYY-MM-DD
    val measurementDetails: String = "",
    val notes: String = "",
    val status: String = "SUBMITTED", // SUBMITTED, APPROVED, REJECTED
    val bossRemarks: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "estimate_material")
data class EstimateMaterial(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val estimateId: Long,
    val materialName: String,
    val estimatedQuantity: Double,
    val unit: String
)

/** A TM's request for one day's materials, drawn against a Project's approved balance. */
@Entity(tableName = "daily_material_request")
data class DailyMaterialRequest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val requestDate: String, // YYYY-MM-DD
    val requestedBy: String, // TM name
    val status: String = "PENDING", // PENDING, APPROVED, PARTIALLY_APPROVED, REJECTED, DISPATCHED, COMPLETED
    val bossRemarks: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * One material line on a daily request. Keeps requested / boss-approved / warehouse-actual
 * as three separate numbers per section 11-15 of the spec — none of them overwrite each other.
 */
@Entity(tableName = "daily_request_material")
data class DailyRequestMaterial(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dailyRequestId: Long,
    val projectMaterialId: Long, // which ProjectMaterial line this draws down
    val materialName: String,
    val unit: String,
    val requestedQuantity: Double,
    val approvedQuantity: Double? = null, // null until Boss acts; may be less than requested
    val actualDispatchedQuantity: Double? = null, // filled in by Warehouse Keeper; may differ from approved
    val bossRemarks: String = ""
)

/**
 * The Gate/Collector's verification of one warehouse dispatch. Links to the existing
 * DispatchRecord/DispatchMaterial (the actual transaction history) rather than duplicating it.
 */
@Entity(tableName = "gate_confirmation")
data class GateConfirmation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dispatchId: Long, // references DispatchRecord.id
    val collectorName: String,
    val confirmationDate: String, // YYYY-MM-DD
    val confirmationTime: String = "", // HH:mm
    val remarks: String = "",
    val photoPath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "gate_confirmation_material")
data class GateConfirmationMaterial(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gateConfirmationId: Long,
    val dispatchMaterialId: Long, // references DispatchMaterial.id
    val materialName: String,
    val unit: String,
    val actualCollectedQuantity: Double
)

@Entity(tableName = "app_notification")
data class AppNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // MATERIALS_APPROVED, REQUEST_APPROVED, REQUEST_REJECTED, MATERIAL_FINISHED, LOW_STOCK, NEW_ESTIMATE, NEW_REQUEST, DISPATCH_APPROVED, COLLECTION_PENDING
    val targetRole: String, // TM, BOSS, ENGINEER, WAREHOUSE, GATE
    val projectId: Long? = null,
    val title: String,
    val message: String,
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/** One Project material line with its computed balance and status. */
data class ProjectMaterialStatus(
    val material: ProjectMaterial,
    val totalDispatched: Double // Remaining = Approved - Confirmed Dispatched (spec section 18)
) {
    val remainingQuantity: Double
        get() = maxOf(0.0, material.approvedQuantity - totalDispatched)

    val isOverDispatched: Boolean
        get() = totalDispatched > material.approvedQuantity + 0.0001

    /** AVAILABLE (green), LOW (orange, remaining <= 10% of approved), or FINISHED (red). */
    val statusLevel: String
        get() = when {
            remainingQuantity <= 0.0001 -> "FINISHED"
            material.approvedQuantity > 0 && remainingQuantity <= material.approvedQuantity * 0.1 -> "LOW"
            else -> "AVAILABLE"
        }
}

data class ProjectFullDetails(
    val project: Project,
    val materials: List<ProjectMaterialStatus>
) {
    val finishedCount: Int get() = materials.count { it.statusLevel == "FINISHED" }
    val lowStockCount: Int get() = materials.count { it.statusLevel == "LOW" }
}

// Computed UI & Helper Models

data class RequestedMaterialWithStatus(
    val material: RequestedMaterial,
    val totalDispatched: Double
) {
    val remainingQuantity: Double
        get() = maxOf(0.0, material.approvedQuantity - totalDispatched)

    val progressPercent: Float
        get() = if (material.approvedQuantity > 0) {
            minOf(100f, ((totalDispatched / material.approvedQuantity) * 100).toFloat())
        } else 0f

    val isCompleted: Boolean
        get() = remainingQuantity <= 0.0001
}

data class BeneficiaryFullDetails(
    val beneficiary: CfwBeneficiary,
    val request: MaterialRequest?,
    val requestedMaterials: List<RequestedMaterialWithStatus>,
    val dispatches: List<DispatchRecord>,
    val status: String // "PENDING" or "COMPLETED"
) {
    val totalApproved: Double
        get() = requestedMaterials.sumOf { it.material.approvedQuantity }

    val totalDispatched: Double
        get() = requestedMaterials.sumOf { it.totalDispatched }

    val totalRemaining: Double
        get() = requestedMaterials.sumOf { it.remainingQuantity }

    val isCompleted: Boolean
        get() = status == "COMPLETED" || (requestedMaterials.isNotEmpty() && requestedMaterials.all { it.isCompleted })
}

data class MaterialSummaryItem(
    val materialName: String,
    val approvedQty: Double,
    val dispatchedQty: Double,
    val remainingQty: Double,
    val unit: String
)

data class DispatchItemDetail(
    val dispatchMaterial: DispatchMaterial,
    val materialName: String,
    val approvedQty: Double,
    val totalDispatchedSoFar: Double,
    val remainingQtyAtDispatch: Double,
    val unit: String
)

data class SyncRowData(
    val timestamp: String,
    val drrCode: String,
    val cfwName: String,
    val fcnNumber: String,
    val recipientName: String,
    val recipientFcn: String,
    val projectId: String,
    val materialName: String,
    val approvedQuantity: Double,
    val dispatchedQuantity: Double,
    val remainingQuantity: Double,
    val unit: String,
    val dispatchVisitNumber: Int,
    val collectorName: String,
    val syncStatus: String
)

// --- Report screen helper models (Daily Dispatch / Weekly / Monthly / TM Project Status) ---

data class DispatchDetailMaterialLine(
    val materialName: String,
    val quantity: Double,
    val unit: String
)

/** One dispatch visit, enriched with beneficiary and material info, for the Reports screen. */
data class DispatchDetail(
    val dispatchId: Long,
    val dispatchDate: String, // YYYY-MM-DD
    val time: String,
    val drrCode: String,
    val cfwName: String,
    val recipientName: String,
    val recipientFcn: String,
    val collectorName: String,
    val materials: List<DispatchDetailMaterialLine>
)
