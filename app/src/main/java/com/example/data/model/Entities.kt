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
    val requestId: Long,
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
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "dispatch_material")
data class DispatchMaterial(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dispatchId: Long,
    val materialId: Long, // references RequestedMaterial.id
    val dispatchedQuantity: Double,
    val remarks: String = ""
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
    val createdAt: Long = System.currentTimeMillis()
)

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
