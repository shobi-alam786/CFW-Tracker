package com.example.data.repository

import com.example.data.dao.AuditDao
import com.example.data.dao.CfwDao
import com.example.data.dao.NotificationDao
import com.example.data.dao.ProjectDao
import com.example.data.model.AppNotification
import com.example.data.model.AuditLog
import com.example.data.model.DailyMaterialRequest
import com.example.data.model.DailyRequestMaterial
import com.example.data.model.DispatchMaterial
import com.example.data.model.DispatchRecord
import com.example.data.model.EngineerEstimate
import com.example.data.model.EstimateMaterial
import com.example.data.model.GateConfirmation
import com.example.data.model.GateConfirmationMaterial
import com.example.data.model.Project
import com.example.data.model.ProjectFullDetails
import com.example.data.model.ProjectMaterial
import com.example.data.model.ProjectMaterialStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * A single material line as entered by the Engineer or approved by the Boss:
 * name, quantity, unit. Kept generic (never one field per material — spec section 33).
 */
data class MaterialLine(val materialName: String, val quantity: Double, val unit: String)

/** A Warehouse-ready request, paired with its lines and how much is still owed on each. */
data class DispatchableRequest(
    val request: DailyMaterialRequest,
    val lines: List<DailyRequestMaterial>
)

/** One dispatch pending Gate confirmation, with its material lines (name/unit resolved for display). */
data class PendingGateDispatch(
    val dispatch: DispatchRecord,
    val materials: List<GateMaterialLine>
)

/** A DispatchMaterial paired with the material name/unit it was dispatched for (DispatchMaterial itself only stores an id link). */
data class GateMaterialLine(
    val dispatchMaterial: DispatchMaterial,
    val materialName: String,
    val unit: String
)

/** Dashboard summary counts (spec section 21). */
data class DashboardSummary(
    val totalProjects: Int,
    val activeProjects: Int,
    val completedProjects: Int,
    val pendingRequests: Int,
    val finishedMaterialsCount: Int,
    val lowMaterialsCount: Int
)

/** One past dispatch event for a material — the permanent history spec section 23 requires. */
data class MaterialHistoryEntry(val date: String, val quantity: Double)

class ProjectRepository(
    private val projectDao: ProjectDao,
    private val auditDao: AuditDao,
    private val cfwDao: CfwDao,
    private val notificationDao: NotificationDao
) {
    val allProjects: Flow<List<Project>> = projectDao.getAllProjects()
    val pendingEstimates: Flow<List<EngineerEstimate>> = projectDao.getEstimatesByStatus("SUBMITTED")
    val pendingDailyRequests: Flow<List<DailyMaterialRequest>> = projectDao.getRequestsByStatus("PENDING")

    suspend fun getProjectByDrrCode(drrCode: String): Project? = withContext(Dispatchers.IO) {
        projectDao.getProjectByDrrCode(drrCode.trim())
    }

    // --- Project balance / status (spec sections 18-19) ---

    suspend fun getProjectFullDetails(projectId: Long): ProjectFullDetails? = withContext(Dispatchers.IO) {
        val project = projectDao.getProjectById(projectId) ?: return@withContext null
        val statuses = projectDao.getMaterialsForProject(projectId).map { mat ->
            ProjectMaterialStatus(mat, projectDao.getTotalActualDispatchedForProjectMaterial(mat.id))
        }
        ProjectFullDetails(project, statuses)
    }

    /** Last 100 actions across every project — the accountability trail spec section 25 asks for. */
    val recentActivity: Flow<List<AuditLog>> = auditDao.getRecentAuditLogs()

    /**
     * Dashboard counts (spec section 21). Finished/Low counts are computed live from every
     * ProjectMaterial's current balance — not from the one-shot finishedNotifiedAt/
     * lowStockNotifiedAt flags, which only mark "was this notification already sent" and can
     * go stale (e.g. after a project is re-approved with a larger quantity).
     */
    suspend fun getDashboardSummary(): DashboardSummary = withContext(Dispatchers.IO) {
        val allProjectsSnapshot = projectDao.getAllProjects().first()
        val totalProjects = allProjectsSnapshot.size
        val completedProjects = allProjectsSnapshot.count { it.status == "COMPLETED" }
        val pendingRequests = projectDao.getRequestsByStatus("PENDING").first().size

        var finishedCount = 0
        var lowCount = 0
        for (mat in projectDao.getAllProjectMaterials()) {
            val dispatched = projectDao.getTotalActualDispatchedForProjectMaterial(mat.id)
            val remaining = maxOf(0.0, mat.approvedQuantity - dispatched)
            when {
                remaining <= 0.0001 -> finishedCount++
                mat.approvedQuantity > 0 && remaining <= mat.approvedQuantity * 0.1 -> lowCount++
            }
        }

        DashboardSummary(
            totalProjects = totalProjects,
            activeProjects = totalProjects - completedProjects,
            completedProjects = completedProjects,
            pendingRequests = pendingRequests,
            finishedMaterialsCount = finishedCount,
            lowMaterialsCount = lowCount
        )
    }

    /** Every past dispatch for one material, oldest first — never overwritten, never lost (spec section 23). */
    suspend fun getMaterialHistory(projectMaterialId: Long): List<MaterialHistoryEntry> = withContext(Dispatchers.IO) {
        projectDao.getDispatchedLinesForProjectMaterial(projectMaterialId).mapNotNull { line ->
            val request = projectDao.getDailyRequestById(line.dailyRequestId) ?: return@mapNotNull null
            MaterialHistoryEntry(request.requestDate, line.actualDispatchedQuantity ?: 0.0)
        }
    }

    // --- Engineer Estimate (spec section 8) ---

    suspend fun submitEngineerEstimate(
        projectId: Long,
        engineerName: String,
        visitDate: String,
        measurementDetails: String,
        notes: String,
        materials: List<MaterialLine>
    ): Result<Long> = withContext(Dispatchers.IO) {
        if (engineerName.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Engineer name is required."))
        }
        val cleanMaterials = materials.filter { it.materialName.isNotBlank() && it.quantity > 0 }
        if (cleanMaterials.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Add at least one material with a quantity."))
        }

        val estimateId = projectDao.insertEngineerEstimate(
            EngineerEstimate(
                projectId = projectId,
                engineerName = engineerName.trim(),
                visitDate = visitDate,
                measurementDetails = measurementDetails.trim(),
                notes = notes.trim(),
                status = "SUBMITTED"
            )
        )
        projectDao.insertEstimateMaterials(
            cleanMaterials.map { line ->
                EstimateMaterial(
                    estimateId = estimateId,
                    materialName = line.materialName.trim(),
                    estimatedQuantity = line.quantity,
                    unit = line.unit.trim()
                )
            }
        )

        projectDao.getProjectById(projectId)?.let { project ->
            projectDao.updateProject(project.copy(status = "ENGINEER_ESTIMATE_SUBMITTED", updatedAt = System.currentTimeMillis()))
        }

        logAudit(
            action = "ENGINEER_ESTIMATE_SUBMITTED",
            details = "${engineerName.trim()} submitted a material estimate",
            user = engineerName.trim(),
            projectId = projectId
        )
        projectDao.getProjectById(projectId)?.let { project ->
            notify(
                "NEW_ESTIMATE", "BOSS", projectId, "🔔 New Engineer Estimate",
                "${engineerName.trim()} submitted an estimate for ${project.projectType} (DRR: ${project.drrCode})."
            )
        }
        Result.success(estimateId)
    }

    /** Most recent estimate for a project, if any (used by the Boss review screen). */
    suspend fun getLatestEstimate(projectId: Long): EngineerEstimate? = withContext(Dispatchers.IO) {
        projectDao.getEstimatesForProject(projectId).firstOrNull()
    }

    suspend fun getEstimateMaterials(estimateId: Long): List<EstimateMaterial> = withContext(Dispatchers.IO) {
        projectDao.getMaterialsForEstimate(estimateId)
    }

    // --- Boss review of the Engineer's estimate (spec section 9) ---

    /**
     * Approves an estimate. [approvedMaterials] is the Boss's final numbers — they may differ
     * from what the Engineer estimated (spec: "Modify approved quantities if the workflow
     * allows it"). Writing fresh ProjectMaterial rows (rather than editing estimate rows in
     * place) keeps the Engineer's original estimate as a permanent, unaltered record.
     */
    suspend fun approveEngineerEstimate(
        estimateId: Long,
        projectId: Long,
        approvedMaterials: List<MaterialLine>,
        decidedBy: String,
        bossRemarks: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanMaterials = approvedMaterials.filter { it.materialName.isNotBlank() && it.quantity > 0 }
        if (cleanMaterials.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Approve at least one material with a quantity."))
        }
        val estimate = projectDao.getEstimatesForProject(projectId).find { it.id == estimateId }
            ?: return@withContext Result.failure(IllegalStateException("Estimate not found."))

        projectDao.updateEngineerEstimate(
            estimate.copy(status = "APPROVED", bossRemarks = bossRemarks.trim(), updatedAt = System.currentTimeMillis())
        )

        // Re-approving a project (e.g. after an updated estimate) replaces the balance lines
        // rather than appending to them, so Reports never shows duplicate material rows.
        // Existing dispatch/request history is untouched — it references dispatch_record, not
        // project_material — so nothing here can lose past transactions.
        projectDao.deleteProjectMaterialsForProject(projectId)
        projectDao.insertProjectMaterials(
            cleanMaterials.map { line ->
                ProjectMaterial(
                    projectId = projectId,
                    materialName = line.materialName.trim(),
                    approvedQuantity = line.quantity,
                    unit = line.unit.trim(),
                    source = "ENGINEER_ESTIMATE"
                )
            }
        )

        projectDao.getProjectById(projectId)?.let { project ->
            projectDao.updateProject(project.copy(status = "MATERIALS_APPROVED", updatedAt = System.currentTimeMillis()))
        }

        logAudit(
            action = "MATERIALS_APPROVED",
            details = "Boss approved materials for the project",
            user = decidedBy,
            projectId = projectId,
            remarks = bossRemarks
        )
        projectDao.getProjectById(projectId)?.let { project ->
            notify(
                "MATERIALS_APPROVED", "TM", projectId, "🔔 Materials Approved",
                "Boss approved materials for ${project.projectType}.\nDRR: ${project.drrCode}"
            )
        }
        Result.success(Unit)
    }

    suspend fun rejectEngineerEstimate(
        estimateId: Long,
        projectId: Long,
        decidedBy: String,
        bossRemarks: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val estimate = projectDao.getEstimatesForProject(projectId).find { it.id == estimateId }
            ?: return@withContext Result.failure(IllegalStateException("Estimate not found."))
        projectDao.updateEngineerEstimate(
            estimate.copy(status = "REJECTED", bossRemarks = bossRemarks.trim(), updatedAt = System.currentTimeMillis())
        )
        logAudit(
            action = "ESTIMATE_REJECTED",
            details = "Boss rejected the engineer estimate",
            user = decidedBy,
            projectId = projectId,
            remarks = bossRemarks
        )
        Result.success(Unit)
    }

    // --- Daily Material Request (spec sections 10-11) ---

    /**
     * How much of one ProjectMaterial line a TM can still request right now: approved total
     * minus everything already committed (Boss-approved on past requests, plus anything still
     * PENDING a decision — reserved so two simultaneous requests can't both over-promise the
     * same stock). Rejected requests free their quantity back up immediately.
     */
    suspend fun getAvailableToRequest(projectMaterialId: Long): Double = withContext(Dispatchers.IO) {
        val material = projectDao.getProjectMaterialById(projectMaterialId) ?: return@withContext 0.0
        maxOf(0.0, material.approvedQuantity - projectDao.getTotalCommittedForProjectMaterial(projectMaterialId))
    }

    /** All currently-approved materials for a project, each paired with how much is still requestable. */
    suspend fun getRequestableMaterials(projectId: Long): List<Pair<ProjectMaterial, Double>> =
        withContext(Dispatchers.IO) {
            projectDao.getMaterialsForProject(projectId).map { it to getAvailableToRequest(it.id) }
        }

    /**
     * Submits a TM's daily request. Validates every line against [getAvailableToRequest] up
     * front and rejects the whole submission — naming the exact material and remaining amount —
     * rather than silently partial-saving (spec section 11).
     */
    suspend fun submitDailyRequest(
        projectId: Long,
        requestDate: String,
        requestedBy: String,
        lines: List<Pair<Long, Double>> // ProjectMaterial.id to requested quantity
    ): Result<Long> = withContext(Dispatchers.IO) {
        if (requestedBy.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("TM name is required."))
        }
        val cleanLines = lines.filter { it.second > 0 }
        if (cleanLines.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Enter at least one material quantity."))
        }

        val materials = mutableMapOf<Long, ProjectMaterial>()
        for ((projectMaterialId, requestedQty) in cleanLines) {
            val material = projectDao.getProjectMaterialById(projectMaterialId)
                ?: return@withContext Result.failure(IllegalStateException("Material not found."))
            materials[projectMaterialId] = material
            val available = getAvailableToRequest(projectMaterialId)
            if (requestedQty > available + 0.0001) {
                return@withContext Result.failure(
                    OverRequestException("Only ${cleanQty(available)} ${material.unit} ${material.materialName} remaining.")
                )
            }
        }

        val requestId = projectDao.insertDailyRequest(
            DailyMaterialRequest(
                projectId = projectId,
                requestDate = requestDate,
                requestedBy = requestedBy.trim(),
                status = "PENDING"
            )
        )
        projectDao.insertDailyRequestMaterials(
            cleanLines.map { (projectMaterialId, requestedQty) ->
                val material = materials.getValue(projectMaterialId)
                DailyRequestMaterial(
                    dailyRequestId = requestId,
                    projectMaterialId = projectMaterialId,
                    materialName = material.materialName,
                    unit = material.unit,
                    requestedQuantity = requestedQty
                )
            }
        )

        logAudit(
            action = "DAILY_REQUEST_SUBMITTED",
            details = "${requestedBy.trim()} submitted a daily material request",
            user = requestedBy.trim(),
            projectId = projectId
        )
        projectDao.getProjectById(projectId)?.let { project ->
            notify(
                "NEW_REQUEST", "BOSS", projectId, "🔔 New Material Request",
                "${requestedBy.trim()} requested materials for ${project.projectType}.\nDRR: ${project.drrCode}"
            )
        }
        Result.success(requestId)
    }

    suspend fun getDailyRequestDetails(requestId: Long): Pair<DailyMaterialRequest, List<DailyRequestMaterial>>? =
        withContext(Dispatchers.IO) {
            val request = projectDao.getDailyRequestById(requestId) ?: return@withContext null
            request to projectDao.getMaterialsForDailyRequest(requestId)
        }

    suspend fun getRequestsForProject(projectId: Long): List<DailyMaterialRequest> = withContext(Dispatchers.IO) {
        projectDao.getRequestsForProject(projectId)
    }

    // --- Boss review of a Daily Request (spec sections 12-13) ---

    /**
     * [decisions] maps each DailyRequestMaterial.id to the Boss's approved quantity for that
     * line (0 or omitted = that line is rejected). Supports full, partial, or zero approval per
     * line independently. The request's overall status is derived from what happened across
     * all its lines.
     */
    suspend fun decideDailyRequest(
        requestId: Long,
        decisions: Map<Long, Double>,
        decidedBy: String,
        bossRemarks: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val request = projectDao.getDailyRequestById(requestId)
            ?: return@withContext Result.failure(IllegalStateException("Request not found."))
        val lines = projectDao.getMaterialsForDailyRequest(requestId)

        var anyApproved = false
        var anyReduced = false

        for (line in lines) {
            val decidedQty = (decisions[line.id] ?: 0.0).coerceAtLeast(0.0)
            if (decidedQty > line.requestedQuantity + 0.0001) {
                return@withContext Result.failure(IllegalStateException("Approved quantity can't exceed what was requested."))
            }
            val materialApproved = projectDao.getProjectMaterialById(line.projectMaterialId)?.approvedQuantity ?: 0.0
            val committedElsewhere = projectDao.getTotalCommittedForProjectMaterial(line.projectMaterialId) -
                (line.approvedQuantity ?: line.requestedQuantity)
            val availableForThisLine = maxOf(0.0, materialApproved - committedElsewhere)
            if (decidedQty > availableForThisLine + 0.0001) {
                return@withContext Result.failure(
                    OverRequestException("Only ${cleanQty(availableForThisLine)} ${line.unit} ${line.materialName} remaining to approve.")
                )
            }

            if (decidedQty > 0.0001) anyApproved = true
            if (decidedQty < line.requestedQuantity - 0.0001) anyReduced = true

            projectDao.updateDailyRequestMaterial(line.copy(approvedQuantity = decidedQty, bossRemarks = bossRemarks.trim()))
        }

        val newStatus = when {
            !anyApproved -> "REJECTED"
            anyReduced -> "PARTIALLY_APPROVED"
            else -> "APPROVED"
        }
        projectDao.updateDailyRequest(
            request.copy(status = newStatus, bossRemarks = bossRemarks.trim(), updatedAt = System.currentTimeMillis())
        )

        logAudit(
            action = if (newStatus == "REJECTED") "DAILY_REQUEST_REJECTED" else "DAILY_REQUEST_APPROVED",
            details = "Boss set daily request #$requestId to $newStatus",
            user = decidedBy,
            projectId = request.projectId,
            remarks = bossRemarks
        )

        val project = projectDao.getProjectById(request.projectId)
        val drrLine = project?.let { " ${it.projectType} (DRR: ${it.drrCode})" } ?: ""
        if (newStatus == "REJECTED") {
            notify("REQUEST_REJECTED", "TM", request.projectId, "🔔 Daily Request Rejected", "Your material request for$drrLine was rejected.")
        } else {
            notify(
                "REQUEST_APPROVED", "TM", request.projectId, "🔔 Daily Request Approved",
                "Your material request for$drrLine was ${if (newStatus == "PARTIALLY_APPROVED") "partially " else ""}approved."
            )
            val approvedSummary = lines.filter { (decisions[it.id] ?: 0.0) > 0.0001 }
                .joinToString("\n") { "${it.materialName}: ${cleanQty(decisions[it.id] ?: 0.0)} ${it.unit}" }
            notify(
                "DISPATCH_APPROVED", "WAREHOUSE", request.projectId, "🔔 Dispatch Approved",
                "Please prepare and dispatch for$drrLine:\n$approvedSummary"
            )
        }
        Result.success(Unit)
    }

    // --- Warehouse Keeper: actual dispatch (spec sections 14-15) ---

    val requestsReadyForDispatch: Flow<List<DailyMaterialRequest>> = projectDao.getRequestsReadyForDispatch()

    /** A ready-to-dispatch request with only its Boss-approved (>0) lines. */
    suspend fun getDispatchableRequest(requestId: Long): DispatchableRequest? = withContext(Dispatchers.IO) {
        val request = projectDao.getDailyRequestById(requestId) ?: return@withContext null
        val lines = projectDao.getMaterialsForDailyRequest(requestId).filter { (it.approvedQuantity ?: 0.0) > 0.0 }
        DispatchableRequest(request, lines)
    }

    /**
     * Records what the Warehouse Keeper actually sent out. [actualQuantities] maps each
     * DailyRequestMaterial.id to the actual dispatched quantity (spec section 15: this may be
     * less than what was approved — the Boss's approved number is never overwritten, both are
     * kept). Writes one DispatchRecord + its DispatchMaterial lines, reusing the same tables
     * the existing Dispatch screen uses, linked via dailyRequestId/dailyRequestMaterialId
     * instead of duplicating a parallel dispatch history.
     */
    suspend fun dispatchApprovedRequest(
        requestId: Long,
        actualQuantities: Map<Long, Double>,
        warehouseKeeperName: String,
        dispatchDate: String,
        remarks: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (warehouseKeeperName.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Warehouse Keeper name is required."))
        }
        val request = projectDao.getDailyRequestById(requestId)
            ?: return@withContext Result.failure(IllegalStateException("Request not found."))
        if (request.status != "APPROVED" && request.status != "PARTIALLY_APPROVED") {
            return@withContext Result.failure(IllegalStateException("This request isn't approved for dispatch."))
        }
        val approvedLines = projectDao.getMaterialsForDailyRequest(requestId).filter { (it.approvedQuantity ?: 0.0) > 0.0 }
        if (approvedLines.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Nothing was approved on this request."))
        }

        val project = projectDao.getProjectById(request.projectId)

        val toDispatch = mutableListOf<Pair<DailyRequestMaterial, Double>>()
        for (line in approvedLines) {
            val actual = (actualQuantities[line.id] ?: 0.0).coerceAtLeast(0.0)
            val approved = line.approvedQuantity ?: 0.0
            if (actual > approved + 0.0001) {
                return@withContext Result.failure(
                    OverRequestException("Actual dispatched can't exceed the approved ${cleanQty(approved)} ${line.unit} ${line.materialName}.")
                )
            }
            if (actual > 0.0001) toDispatch.add(line to actual)
        }
        if (toDispatch.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Enter at least one actual dispatched quantity."))
        }

        val dispatchId = cfwDao.insertDispatchRecord(
            DispatchRecord(
                requestId = 0, // sentinel: this dispatch belongs to the CFW MaterialFlow workflow, not a legacy MaterialRequest
                dispatchDate = dispatchDate,
                collectorName = warehouseKeeperName.trim(),
                recipientName = project?.tmName ?: "",
                note = remarks.trim(),
                dailyRequestId = requestId
            )
        )
        cfwDao.insertDispatchMaterials(
            toDispatch.map { (line, actual) ->
                DispatchMaterial(
                    dispatchId = dispatchId,
                    materialId = 0, // sentinel: see dailyRequestMaterialId instead
                    dispatchedQuantity = actual,
                    dailyRequestMaterialId = line.id
                )
            }
        )
        for ((line, actual) in toDispatch) {
            projectDao.updateDailyRequestMaterial(line.copy(actualDispatchedQuantity = actual))
        }
        projectDao.updateDailyRequest(request.copy(status = "DISPATCHED", updatedAt = System.currentTimeMillis()))

        logAudit(
            action = "WAREHOUSE_DISPATCHED",
            details = "${warehouseKeeperName.trim()} dispatched materials for daily request #$requestId",
            user = warehouseKeeperName.trim(),
            projectId = request.projectId,
            remarks = remarks
        )

        val summary = toDispatch.joinToString("\n") { (line, actual) -> "${line.materialName}: ${cleanQty(actual)} ${line.unit}" }
        notify(
            "COLLECTION_PENDING", "GATE", request.projectId, "🔔 Material Collection Pending",
            "Materials dispatched for ${project?.tmName ?: "TM"}${project?.let { " — DRR: ${it.drrCode}" } ?: ""}:\n$summary"
        )
        checkAndNotifyMaterialStatus(request.projectId)
        Result.success(Unit)
    }

    // --- Gate/Collector confirmation (spec sections 16-17) ---

    val dispatchesPendingGateConfirmation: Flow<List<DispatchRecord>> = projectDao.getDispatchesPendingGateConfirmation()

    suspend fun getPendingGateDispatch(dispatchId: Long): PendingGateDispatch? = withContext(Dispatchers.IO) {
        val dispatch = cfwDao.getDispatchById(dispatchId) ?: return@withContext null
        val dispatchMaterials = cfwDao.getDispatchMaterialsForDispatch(dispatchId)
        val requestLines = dispatch.dailyRequestId?.let { projectDao.getMaterialsForDailyRequest(it) } ?: emptyList()
        val lines = dispatchMaterials.map { dm ->
            val match = requestLines.find { it.id == dm.dailyRequestMaterialId }
            GateMaterialLine(dm, match?.materialName ?: "Material", match?.unit ?: "")
        }
        PendingGateDispatch(dispatch, lines)
    }

    /**
     * Records the Gate/Collector's independently-verified quantities. [actualCollected] maps
     * each DispatchMaterial.id to what the Gate/Collector actually saw carried out — this can
     * differ from what the Warehouse recorded (loss, damage, miscount); both numbers are kept
     * rather than one overwriting the other. Does not change the project balance (spec's
     * "Remaining = Approved − Confirmed Dispatched" already uses the Warehouse's actual
     * quantity) — this is the accountability/audit layer on top (spec section 25).
     */
    suspend fun confirmGateCollection(
        dispatchId: Long,
        collectorName: String,
        confirmationDate: String,
        confirmationTime: String,
        remarks: String,
        actualCollected: Map<Long, Double> // DispatchMaterial.id -> actual collected quantity
    ): Result<String> = withContext(Dispatchers.IO) {
        if (collectorName.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Collector name is required."))
        }
        val dispatch = cfwDao.getDispatchById(dispatchId)
            ?: return@withContext Result.failure(IllegalStateException("Dispatch not found."))
        if (projectDao.getGateConfirmationForDispatch(dispatchId) != null) {
            return@withContext Result.failure(IllegalStateException("This dispatch was already confirmed."))
        }
        val dispatchMaterials = cfwDao.getDispatchMaterialsForDispatch(dispatchId)
        if (dispatchMaterials.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("No materials on this dispatch."))
        }

        val gateConfirmationId = projectDao.insertGateConfirmation(
            GateConfirmation(
                dispatchId = dispatchId,
                collectorName = collectorName.trim(),
                confirmationDate = confirmationDate,
                confirmationTime = confirmationTime,
                remarks = remarks.trim()
            )
        )

        val variances = mutableListOf<String>()

        // Resolve material name/unit per line from the originating DailyRequestMaterial.
        val nameAndUnitByDispatchMaterialId = mutableMapOf<Long, Pair<String, String>>()
        for (dm in dispatchMaterials) {
            val id = dm.dailyRequestMaterialId
            if (id != null) {
                val requestId = dispatch.dailyRequestId
                val line = if (requestId != null) {
                    projectDao.getMaterialsForDailyRequest(requestId).find { it.id == id }
                } else null
                if (line != null) nameAndUnitByDispatchMaterialId[dm.id] = line.materialName to line.unit
            }
        }

        val gateMaterialRows = dispatchMaterials.map { dm ->
            val collected = (actualCollected[dm.id] ?: dm.dispatchedQuantity).coerceAtLeast(0.0)
            val (name, unit) = nameAndUnitByDispatchMaterialId[dm.id] ?: ("Material" to "")
            if (kotlin.math.abs(collected - dm.dispatchedQuantity) > 0.0001) {
                variances.add("$name: dispatched ${cleanQty(dm.dispatchedQuantity)} $unit, collected ${cleanQty(collected)} $unit")
            }
            GateConfirmationMaterial(
                gateConfirmationId = gateConfirmationId,
                dispatchMaterialId = dm.id,
                materialName = name,
                unit = unit,
                actualCollectedQuantity = collected
            )
        }
        projectDao.insertGateConfirmationMaterials(gateMaterialRows)

        dispatch.dailyRequestId?.let { requestId ->
            projectDao.getDailyRequestById(requestId)?.let { request ->
                projectDao.updateDailyRequest(request.copy(status = "COMPLETED", updatedAt = System.currentTimeMillis()))
            }
        }

        logAudit(
            action = "GATE_CONFIRMED",
            details = "${collectorName.trim()} confirmed collection for dispatch #$dispatchId" +
                if (variances.isNotEmpty()) ". Variance: ${variances.joinToString("; ")}" else "",
            user = collectorName.trim(),
            projectId = null,
            remarks = remarks
        )

        val projectForNotice = dispatch.dailyRequestId
            ?.let { projectDao.getDailyRequestById(it) }
            ?.let { projectDao.getProjectById(it.projectId) }
        notify(
            "DISPATCH_COMPLETED", "BOSS", projectForNotice?.id, "🔔 Dispatch Completed",
            "${collectorName.trim()} confirmed collection${projectForNotice?.let { " for ${it.projectType} (DRR: ${it.drrCode})" } ?: ""}." +
                if (variances.isNotEmpty()) "\nVariance noted: ${variances.joinToString("; ")}" else ""
        )

        Result.success(
            if (variances.isEmpty()) "Collection confirmed. No variance from what was dispatched."
            else "Collection confirmed. Variance noted: ${variances.joinToString("; ")}"
        )
    }

    private fun cleanQty(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private suspend fun notify(type: String, targetRole: String, projectId: Long?, title: String, message: String) {
        notificationDao.insertNotification(
            AppNotification(type = type, targetRole = targetRole, projectId = projectId, title = title, message = message)
        )
    }

    /**
     * Checks every material on a project after a Warehouse dispatch and raises a Material
     * Finished / Low Material notification the moment it first crosses that threshold (spec
     * section 20: only once per state change — finishedNotifiedAt/lowStockNotifiedAt on
     * ProjectMaterial record that it already happened). Re-approving a project (which replaces
     * ProjectMaterial rows) naturally resets these flags, so a re-approved project can notify
     * again if it runs out a second time.
     */
    private suspend fun checkAndNotifyMaterialStatus(projectId: Long) {
        val project = projectDao.getProjectById(projectId) ?: return
        val now = System.currentTimeMillis()

        for (mat in projectDao.getMaterialsForProject(projectId)) {
            val dispatched = projectDao.getTotalActualDispatchedForProjectMaterial(mat.id)
            val remaining = maxOf(0.0, mat.approvedQuantity - dispatched)

            if (remaining <= 0.0001 && mat.finishedNotifiedAt == null) {
                val message = "${mat.materialName} has been completely dispatched.\n" +
                    "Project: ${project.projectType}\nDRR: ${project.drrCode}\n" +
                    "Approved: ${cleanQty(mat.approvedQuantity)} ${mat.unit}\n" +
                    "Dispatched: ${cleanQty(dispatched)} ${mat.unit}\nRemaining: 0 ${mat.unit}"
                notify("MATERIAL_FINISHED", "TM", projectId, "🔴 Material Finished", message)
                notify("MATERIAL_FINISHED", "BOSS", projectId, "🔴 Material Finished", message)
                projectDao.updateProjectMaterial(mat.copy(finishedNotifiedAt = now))
            } else if (
                mat.approvedQuantity > 0 &&
                remaining > 0.0001 &&
                remaining <= mat.approvedQuantity * 0.1 &&
                mat.lowStockNotifiedAt == null
            ) {
                val message = "${mat.materialName} is running low.\n" +
                    "Project: ${project.projectType}\nDRR: ${project.drrCode}\n" +
                    "Remaining: ${cleanQty(remaining)} ${mat.unit} of ${cleanQty(mat.approvedQuantity)} ${mat.unit} approved"
                notify("LOW_STOCK", "TM", projectId, "🟠 Low Material", message)
                notify("LOW_STOCK", "BOSS", projectId, "🟠 Low Material", message)
                projectDao.updateProjectMaterial(mat.copy(lowStockNotifiedAt = now))
            }
        }
    }

    private suspend fun logAudit(action: String, details: String, user: String, projectId: Long? = null, remarks: String = "") {
        val fullDetails = buildString {
            append(details)
            if (projectId != null) append(" [Project #$projectId]")
            if (remarks.isNotBlank()) append(". Remarks: $remarks")
        }
        auditDao.insertAuditLog(AuditLog(action = action, details = fullDetails, user = user))
    }
}

/** Thrown when a request/approval exceeds the currently available balance — the UI shows [message] directly (spec's "⚠️ Only X remaining" wording). */
class OverRequestException(message: String) : IllegalStateException(message)
