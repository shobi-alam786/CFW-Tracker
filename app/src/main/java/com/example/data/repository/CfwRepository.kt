package com.example.data.repository

import com.example.data.dao.AuditDao
import com.example.data.dao.CfwDao
import com.example.data.model.AuditLog
import com.example.data.model.BeneficiaryFullDetails
import com.example.data.model.CfwBeneficiary
import com.example.data.model.DispatchItemDetail
import com.example.data.model.DispatchMaterial
import com.example.data.model.DispatchRecord
import com.example.data.model.MaterialRequest
import com.example.data.model.MaterialSummaryItem
import com.example.data.model.RequestedMaterial
import com.example.data.model.RequestedMaterialWithStatus
import com.example.data.model.SyncRowData
import com.example.data.preferences.UserPreferences
import com.example.data.sync.SyncEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SyncSummary(
    val attempted: Int,
    val successCount: Int,
    val failCount: Int,
    val lastError: String?
)

class CfwRepository(
    private val cfwDao: CfwDao,
    private val auditDao: AuditDao,
    private val userDao: com.example.data.dao.UserDao,
    private val userPreferences: UserPreferences,
    private val syncEngine: SyncEngine
) {
    val allBeneficiaries: Flow<List<CfwBeneficiary>> = cfwDao.getAllBeneficiaries()
    val allDispatches: Flow<List<DispatchRecord>> = cfwDao.getAllDispatches()
    val pendingSyncDispatches: Flow<List<DispatchRecord>> = cfwDao.getPendingSyncDispatches()

    // --- App Users (access control) ---
    val allUsers: Flow<List<com.example.data.model.AppUser>> = userDao.getAllUsers()

    suspend fun getUserCount(): Int = withContext(Dispatchers.IO) {
        userDao.getUserCount()
    }

    /**
     * Adds a new user. Returns an error message on failure, or null on success.
     */
    suspend fun addUser(name: String, password: String): String? = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        val cleanPassword = password.trim()
        if (cleanName.isBlank()) return@withContext "Name is required."
        if (cleanPassword.length != 8 || cleanPassword.any { !it.isDigit() }) {
            return@withContext "Password must be exactly 8 digits."
        }
        if (userDao.getUserByName(cleanName) != null) {
            return@withContext "A user with this name already exists."
        }
        userDao.insertUser(com.example.data.model.AppUser(name = cleanName, password = cleanPassword))
        null
    }

    suspend fun deleteUser(id: Long) = withContext(Dispatchers.IO) {
        userDao.deleteUserById(id)
    }

    /**
     * Verifies an 8-digit password against all registered users.
     */
    suspend fun verifyUserPassword(password: String): Boolean = withContext(Dispatchers.IO) {
        userDao.getUserByPassword(password.trim()) != null
    }

    val totalBeneficiariesCount: Flow<Int> = cfwDao.getTotalBeneficiariesCount()
    val totalRequestsCount: Flow<Int> = cfwDao.getTotalMaterialRequestsCount()
    val totalDispatchesCount: Flow<Int> = cfwDao.getTotalDispatchesCount()
    val pendingRequestsCount: Flow<Int> = cfwDao.getPendingRequestsCount()
    val completedRequestsCount: Flow<Int> = cfwDao.getCompletedRequestsCount()
    val pendingSyncCount: Flow<Int> = cfwDao.getPendingSyncCount()
    val syncedCount: Flow<Int> = cfwDao.getSyncedCount()
    val auditLogs: Flow<List<AuditLog>> = auditDao.getRecentAuditLogs()

    fun getTodayDispatchesCount(): Flow<Int> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return cfwDao.getTodayDispatchesCount(today)
    }

    suspend fun getBeneficiaryByDrrCode(drrCode: String): CfwBeneficiary? {
        return cfwDao.getBeneficiaryByDrrCode(drrCode.trim())
    }

    fun searchBeneficiaries(query: String): Flow<List<CfwBeneficiary>> {
        return cfwDao.searchBeneficiaries(query.trim())
    }

    suspend fun getRequestForBeneficiary(beneficiaryId: Long): MaterialRequest? {
        return cfwDao.getRequestForBeneficiary(beneficiaryId)
    }

    fun getBeneficiaryFullDetailsFlow(beneficiaryId: Long): Flow<BeneficiaryFullDetails?> = flow {
        val ben = cfwDao.getBeneficiaryById(beneficiaryId)
        if (ben == null) {
            emit(null)
            return@flow
        }
        val request = cfwDao.getRequestForBeneficiary(beneficiaryId)
        val requestedMaterials = mutableListOf<RequestedMaterialWithStatus>()
        var dispatches = emptyList<DispatchRecord>()

        if (request != null) {
            val reqMats = cfwDao.getRequestedMaterialsForRequest(request.id)
            for (rm in reqMats) {
                val totalDispatched = cfwDao.getTotalDispatchedForMaterial(rm.id)
                requestedMaterials.add(RequestedMaterialWithStatus(rm, totalDispatched))
            }
            dispatches = cfwDao.getDispatchesForRequest(request.id)
        }

        val allMatsCompleted = requestedMaterials.isNotEmpty() && requestedMaterials.all { it.isCompleted }
        val calculatedStatus = if (allMatsCompleted) "COMPLETED" else "PENDING"

        emit(
            BeneficiaryFullDetails(
                beneficiary = ben,
                request = request,
                requestedMaterials = requestedMaterials,
                dispatches = dispatches,
                status = calculatedStatus
            )
        )
    }

    suspend fun getBeneficiaryFullDetails(beneficiaryId: Long): BeneficiaryFullDetails? = withContext(Dispatchers.IO) {
        val ben = cfwDao.getBeneficiaryById(beneficiaryId) ?: return@withContext null
        val request = cfwDao.getRequestForBeneficiary(beneficiaryId)
        val requestedMaterials = mutableListOf<RequestedMaterialWithStatus>()
        var dispatches = emptyList<DispatchRecord>()

        if (request != null) {
            val reqMats = cfwDao.getRequestedMaterialsForRequest(request.id)
            for (rm in reqMats) {
                val totalDispatched = cfwDao.getTotalDispatchedForMaterial(rm.id)
                requestedMaterials.add(RequestedMaterialWithStatus(rm, totalDispatched))
            }
            dispatches = cfwDao.getDispatchesForRequest(request.id)
        }

        val allMatsCompleted = requestedMaterials.isNotEmpty() && requestedMaterials.all { it.isCompleted }
        val calculatedStatus = if (allMatsCompleted) "COMPLETED" else "PENDING"

        BeneficiaryFullDetails(
            beneficiary = ben,
            request = request,
            requestedMaterials = requestedMaterials,
            dispatches = dispatches,
            status = calculatedStatus
        )
    }

    suspend fun registerBeneficiaryAndRequest(
        drrCode: String,
        cfwName: String,
        fcnNumber: String,
        approvedMaterials: List<Pair<String, Pair<Double, String>>>, // Name -> (ApprovedQty, Unit)
        projectId: String = "PROJ-2026-CFW",
        requestDate: String? = null
    ): Long = withContext(Dispatchers.IO) {
        val cleanDrr = drrCode.trim()
        val cleanName = cfwName.trim()
        val cleanFcn = fcnNumber.trim()
        val dateStr = requestDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // Step 1: Beneficiary Registration (Unique DRR Code)
        var beneficiary = cfwDao.getBeneficiaryByDrrCode(cleanDrr)
        var beneficiaryId: Long

        if (beneficiary == null) {
            beneficiary = CfwBeneficiary(
                drrCode = cleanDrr,
                cfwName = cleanName,
                fcnNumber = cleanFcn,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            beneficiaryId = cfwDao.insertBeneficiary(beneficiary)
            logAudit("REGISTER_CFW_WORKER", "Registered CFW Worker: $cleanName ($cleanDrr)")
        } else {
            beneficiaryId = beneficiary.id
            if (beneficiary.cfwName != cleanName || beneficiary.fcnNumber != cleanFcn) {
                val updated = beneficiary.copy(
                    cfwName = cleanName,
                    fcnNumber = cleanFcn,
                    updatedAt = System.currentTimeMillis()
                )
                cfwDao.updateBeneficiary(updated)
            }
        }

        // Step 2: Create Material Request
        // Scoped to today's date so a new day always gets a fresh request (and fresh approved
        // quantities) instead of silently reusing an older — possibly already fully-dispatched —
        // request for this same DRR code, which is what was discarding today's submission.
        var request = cfwDao.getRequestForBeneficiaryOnDate(beneficiaryId, dateStr)
        var requestId: Long

        if (request == null) {
            request = MaterialRequest(
                beneficiaryId = beneficiaryId,
                projectId = projectId,
                requestDate = dateStr,
                status = "PENDING"
            )
            requestId = cfwDao.insertMaterialRequest(request)

            val reqMats = approvedMaterials.filter { it.second.first > 0 }.map { (name, pair) ->
                userPreferences.addCustomMaterial(name)
                userPreferences.addCustomUnit(pair.second)
                RequestedMaterial(
                    requestId = requestId,
                    materialName = name,
                    approvedQuantity = pair.first,
                    unit = pair.second
                )
            }
            cfwDao.insertRequestedMaterials(reqMats)
            logAudit("CREATE_MATERIAL_REQUEST", "Created Material Request #${requestId} for $cleanName ($cleanDrr) with ${reqMats.size} items")
        } else {
            requestId = request.id
        }

        requestId
    }

    suspend fun createDispatchRecord(
        requestId: Long,
        recipientName: String,
        recipientFcn: String,
        collectorName: String? = null,
        dispatches: List<Pair<Long, Double>>, // materialId -> dispatchQuantity
        note: String = "",
        latitude: Double? = null,
        longitude: Double? = null,
        customDate: String? = null,
        customTime: String? = null
    ): Result<Long> = withContext(Dispatchers.IO) {
        val request = cfwDao.getRequestById(requestId)
            ?: return@withContext Result.failure(Exception("Material request not found"))
        val beneficiary = cfwDao.getBeneficiaryById(request.beneficiaryId)
            ?: return@withContext Result.failure(Exception("CFW Worker not found"))

        val cleanRecipientName = recipientName.trim()
        val cleanRecipientFcn = recipientFcn.trim()
        if (cleanRecipientName.isBlank()) {
            return@withContext Result.failure(Exception("Recipient Name is required for this dispatch."))
        }
        if (cleanRecipientFcn.isBlank()) {
            return@withContext Result.failure(Exception("Recipient FCN Number is required for this dispatch."))
        }

        // --- VALIDATION RULE ---
        // Verify each item <= remaining quantity
        val dispatchItems = mutableListOf<DispatchMaterial>()

        for ((materialId, qty) in dispatches) {
            if (qty <= 0) continue
            val reqMat = cfwDao.getRequestedMaterialById(materialId)
                ?: return@withContext Result.failure(Exception("Material item not found"))

            val totalAlreadyDispatched = cfwDao.getTotalDispatchedForMaterial(materialId)
            val remainingQty = maxOf(0.0, reqMat.approvedQuantity - totalAlreadyDispatched)

            if (qty > remainingQty + 0.0001) {
                return@withContext Result.failure(
                    Exception("Validation error: Cannot dispatch ${qty} ${reqMat.unit} of ${reqMat.materialName}. Remaining is only ${remainingQty} ${reqMat.unit}.")
                )
            }
        }

        val previousDispatches = cfwDao.getDispatchesForRequest(requestId)
        val nextVisitNumber = previousDispatches.size + 1

        val dateStr = customDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val timeStr = customTime ?: SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val collector = collectorName ?: userPreferences.collectorName.value

        val dispatchRecord = DispatchRecord(
            requestId = requestId,
            dispatchDate = dateStr,
            time = timeStr,
            collectorName = collector,
            recipientName = cleanRecipientName,
            recipientFcn = cleanRecipientFcn,
            latitude = latitude,
            longitude = longitude,
            note = note,
            visitNumber = nextVisitNumber,
            syncStatus = "PENDING"
        )

        val dispatchId = cfwDao.insertDispatchRecord(dispatchRecord)

        val dispatchMaterials = dispatches.filter { it.second > 0 }.map { (matId, qty) ->
            DispatchMaterial(
                dispatchId = dispatchId,
                materialId = matId,
                dispatchedQuantity = qty,
                remarks = note
            )
        }
        cfwDao.insertDispatchMaterials(dispatchMaterials)

        // Check if all requested materials for this request are now fully dispatched
        val allReqMats = cfwDao.getRequestedMaterialsForRequest(requestId)
        var allCompleted = true
        for (rm in allReqMats) {
            val totalDispatched = cfwDao.getTotalDispatchedForMaterial(rm.id)
            if (rm.approvedQuantity - totalDispatched > 0.0001) {
                allCompleted = false
                break
            }
        }

        if (allCompleted) {
            cfwDao.updateMaterialRequest(request.copy(status = "COMPLETED"))
            logAudit("REQUEST_COMPLETED", "Material Request #${requestId} for ${beneficiary.cfwName} is fully completed!")
        }

        logAudit("DISPATCH_CREATED", "Visit #$nextVisitNumber dispatched to $cleanRecipientName (FCN: $cleanRecipientFcn) under DRR ${beneficiary.drrCode}")

        Result.success(dispatchId)
    }

    suspend fun getDispatchItemDetails(dispatchId: Long): List<DispatchItemDetail> = withContext(Dispatchers.IO) {
        val dispatch = cfwDao.getDispatchById(dispatchId) ?: return@withContext emptyList()
        val mats = cfwDao.getDispatchMaterialsForDispatch(dispatchId)
        val list = mutableListOf<DispatchItemDetail>()

        for (dm in mats) {
            val reqMat = cfwDao.getRequestedMaterialById(dm.materialId) ?: continue
            val totalDispatched = cfwDao.getTotalDispatchedForMaterial(reqMat.id)
            val remainingAtDispatch = maxOf(0.0, reqMat.approvedQuantity - totalDispatched)

            list.add(
                DispatchItemDetail(
                    dispatchMaterial = dm,
                    materialName = reqMat.materialName,
                    approvedQty = reqMat.approvedQuantity,
                    totalDispatchedSoFar = totalDispatched,
                    remainingQtyAtDispatch = remainingAtDispatch,
                    unit = reqMat.unit
                )
            )
        }
        list
    }

    suspend fun syncAllPending(): SyncSummary = withContext(Dispatchers.IO) {
        val pending = cfwDao.getPendingSyncDispatches().first()
        var successCount = 0
        var lastError: String? = null
        pending.forEach { dispatch ->
            val result = syncEngine.syncDispatch(dispatch)
            if (result.isSuccess) {
                successCount++
            } else {
                lastError = result.exceptionOrNull()?.message ?: "Unknown sync error"
            }
        }
        logAudit("SYNC_ALL", "Triggered sync for ${pending.size} pending dispatches. Successful: $successCount")
        SyncSummary(
            attempted = pending.size,
            successCount = successCount,
            failCount = pending.size - successCount,
            lastError = lastError
        )
    }

    suspend fun syncSingleDispatch(dispatchId: Long): SyncSummary = withContext(Dispatchers.IO) {
        val dispatch = cfwDao.getDispatchById(dispatchId)
            ?: return@withContext SyncSummary(attempted = 0, successCount = 0, failCount = 1, lastError = "Dispatch record not found")
        val result = syncEngine.syncDispatch(dispatch)
        logAudit("SYNC_DISPATCH", "Synced dispatch ID: $dispatchId. Success: ${result.isSuccess}")
        if (result.isSuccess) {
            SyncSummary(attempted = 1, successCount = 1, failCount = 0, lastError = null)
        } else {
            SyncSummary(attempted = 1, successCount = 0, failCount = 1, lastError = result.exceptionOrNull()?.message ?: "Unknown sync error")
        }
    }

    suspend fun deleteBeneficiary(beneficiaryId: Long) = withContext(Dispatchers.IO) {
        val ben = cfwDao.getBeneficiaryById(beneficiaryId)
        cfwDao.deleteBeneficiaryWithAllData(beneficiaryId)
        logAudit("DELETE_CFW_WORKER", "Deleted CFW worker: ${ben?.cfwName ?: beneficiaryId}")
    }

    suspend fun updateBeneficiary(beneficiary: CfwBeneficiary) = withContext(Dispatchers.IO) {
        cfwDao.updateBeneficiary(beneficiary)
        logAudit("UPDATE_CFW_WORKER", "Updated CFW worker details for ID: ${beneficiary.id}")
    }

    suspend fun logAudit(action: String, details: String) {
        auditDao.insertAuditLog(
            AuditLog(
                action = action,
                details = details,
                user = userPreferences.collectorName.value
            )
        )
    }

    suspend fun exportAllCsv(): String = withContext(Dispatchers.IO) {
        val allDispatchesList = cfwDao.getAllDispatches().first()
        val syncRows = mutableListOf<SyncRowData>()

        for (dispatch in allDispatchesList) {
            val req = cfwDao.getRequestById(dispatch.requestId) ?: continue
            val ben = cfwDao.getBeneficiaryById(req.beneficiaryId) ?: continue
            val dMats = cfwDao.getDispatchMaterialsForDispatch(dispatch.id)

            val timestamp = "${dispatch.dispatchDate} ${dispatch.time}".trim()
            for (dm in dMats) {
                val reqMat = cfwDao.getRequestedMaterialById(dm.materialId) ?: continue
                val totalDispatchedForMat = cfwDao.getTotalDispatchedForMaterial(reqMat.id)
                val remainingQty = maxOf(0.0, reqMat.approvedQuantity - totalDispatchedForMat)

                syncRows.add(
                    SyncRowData(
                        timestamp = timestamp,
                        drrCode = ben.drrCode,
                        cfwName = ben.cfwName,
                        fcnNumber = ben.fcnNumber,
                        recipientName = dispatch.recipientName,
                        recipientFcn = dispatch.recipientFcn,
                        projectId = req.projectId,
                        materialName = reqMat.materialName,
                        approvedQuantity = reqMat.approvedQuantity,
                        dispatchedQuantity = dm.dispatchedQuantity,
                        remainingQuantity = remainingQty,
                        unit = reqMat.unit,
                        dispatchVisitNumber = dispatch.visitNumber,
                        collectorName = dispatch.collectorName,
                        syncStatus = dispatch.syncStatus
                    )
                )
            }
        }

        syncEngine.generateCsvExport(syncRows)
    }

    suspend fun clearSampleDemoData(): Int = withContext(Dispatchers.IO) {
        val sampleIds = cfwDao.getSampleBeneficiaryIds()
        var count = 0
        for (id in sampleIds) {
            cfwDao.deleteBeneficiaryWithAllData(id)
            count++
        }
        if (count > 0) {
            logAudit("CLEANUP_SAMPLE_DATA", "Cleaned up $count sample demo records")
        }
        count
    }

    suspend fun clearAllDatabaseData() = withContext(Dispatchers.IO) {
        cfwDao.clearAllData()
        logAudit("RESET_DATABASE", "Cleared all database records")
    }
}
