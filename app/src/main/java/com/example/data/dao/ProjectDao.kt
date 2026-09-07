package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.DailyMaterialRequest
import com.example.data.model.DailyRequestMaterial
import com.example.data.model.DispatchRecord
import com.example.data.model.EngineerEstimate
import com.example.data.model.EstimateMaterial
import com.example.data.model.GateConfirmation
import com.example.data.model.GateConfirmationMaterial
import com.example.data.model.Project
import com.example.data.model.ProjectMaterial
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    // --- Project ---
    @Insert
    suspend fun insertProject(project: Project): Long

    @Update
    suspend fun updateProject(project: Project)

    @Query("SELECT * FROM project WHERE drrCode = :drrCode LIMIT 1")
    suspend fun getProjectByDrrCode(drrCode: String): Project?

    @Query("SELECT * FROM project WHERE id = :id")
    suspend fun getProjectById(id: Long): Project?

    @Query("SELECT * FROM project ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<Project>>

    @Query("SELECT * FROM project WHERE status = :status ORDER BY updatedAt DESC")
    fun getProjectsByStatus(status: String): Flow<List<Project>>

    @Query("SELECT COUNT(*) FROM project")
    fun getTotalProjectCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM project WHERE status = 'COMPLETED'")
    fun getCompletedProjectCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM daily_material_request WHERE status = 'PENDING'")
    fun getPendingRequestCount(): Flow<Int>

    /** Every ProjectMaterial row across every project — used to compute live Finished/Low counts for the Dashboard. */
    @Query("SELECT * FROM project_material")
    suspend fun getAllProjectMaterials(): List<ProjectMaterial>

    // --- ProjectMaterial ---
    @Insert
    suspend fun insertProjectMaterial(material: ProjectMaterial): Long

    @Insert
    suspend fun insertProjectMaterials(materials: List<ProjectMaterial>)

    @Update
    suspend fun updateProjectMaterial(material: ProjectMaterial)

    @Query("SELECT * FROM project_material WHERE projectId = :projectId")
    suspend fun getMaterialsForProject(projectId: Long): List<ProjectMaterial>

    @Query("SELECT * FROM project_material WHERE projectId = :projectId")
    fun getMaterialsForProjectFlow(projectId: Long): Flow<List<ProjectMaterial>>

    @Query("SELECT * FROM project_material WHERE id = :id")
    suspend fun getProjectMaterialById(id: Long): ProjectMaterial?

    @Query("DELETE FROM project_material WHERE projectId = :projectId")
    suspend fun deleteProjectMaterialsForProject(projectId: Long)

    // --- EngineerEstimate ---
    @Insert
    suspend fun insertEngineerEstimate(estimate: EngineerEstimate): Long

    @Update
    suspend fun updateEngineerEstimate(estimate: EngineerEstimate)

    @Query("SELECT * FROM engineer_estimate WHERE projectId = :projectId ORDER BY createdAt DESC")
    suspend fun getEstimatesForProject(projectId: Long): List<EngineerEstimate>

    @Query("SELECT * FROM engineer_estimate WHERE status = :status ORDER BY createdAt DESC")
    fun getEstimatesByStatus(status: String): Flow<List<EngineerEstimate>>

    // --- EstimateMaterial ---
    @Insert
    suspend fun insertEstimateMaterials(materials: List<EstimateMaterial>)

    @Query("SELECT * FROM estimate_material WHERE estimateId = :estimateId")
    suspend fun getMaterialsForEstimate(estimateId: Long): List<EstimateMaterial>

    // --- DailyMaterialRequest ---
    @Insert
    suspend fun insertDailyRequest(request: DailyMaterialRequest): Long

    @Update
    suspend fun updateDailyRequest(request: DailyMaterialRequest)

    @Query("SELECT * FROM daily_material_request WHERE id = :id")
    suspend fun getDailyRequestById(id: Long): DailyMaterialRequest?

    @Query("SELECT * FROM daily_material_request WHERE projectId = :projectId ORDER BY requestDate DESC")
    suspend fun getRequestsForProject(projectId: Long): List<DailyMaterialRequest>

    @Query("SELECT * FROM daily_material_request WHERE status = :status ORDER BY createdAt ASC")
    fun getRequestsByStatus(status: String): Flow<List<DailyMaterialRequest>>

    /** Boss-approved (fully or partially) requests the Warehouse Keeper hasn't dispatched yet. */
    @Query("SELECT * FROM daily_material_request WHERE status IN ('APPROVED', 'PARTIALLY_APPROVED') ORDER BY createdAt ASC")
    fun getRequestsReadyForDispatch(): Flow<List<DailyMaterialRequest>>

    // --- DailyRequestMaterial ---
    @Insert
    suspend fun insertDailyRequestMaterials(materials: List<DailyRequestMaterial>)

    @Update
    suspend fun updateDailyRequestMaterial(material: DailyRequestMaterial)

    @Query("SELECT * FROM daily_request_material WHERE dailyRequestId = :dailyRequestId")
    suspend fun getMaterialsForDailyRequest(dailyRequestId: Long): List<DailyRequestMaterial>

    /** Sum of everything ever approved+dispatched for one ProjectMaterial line, across all past daily requests. */
    @Query(
        """
        SELECT COALESCE(SUM(actualDispatchedQuantity), 0.0)
        FROM daily_request_material
        WHERE projectMaterialId = :projectMaterialId AND actualDispatchedQuantity IS NOT NULL
        """
    )
    suspend fun getTotalActualDispatchedForProjectMaterial(projectMaterialId: Long): Double

    /**
     * Every past dispatch event for one material line, oldest first — the permanent history
     * spec section 23 requires ("this history must never be lost"). Each row is a completed
     * Warehouse dispatch (actualDispatchedQuantity set); its parent DailyMaterialRequest gives
     * the date.
     */
    @Query(
        """
        SELECT * FROM daily_request_material
        WHERE projectMaterialId = :projectMaterialId AND actualDispatchedQuantity IS NOT NULL
        ORDER BY id ASC
        """
    )
    suspend fun getDispatchedLinesForProjectMaterial(projectMaterialId: Long): List<DailyRequestMaterial>

    /**
     * Sum of quantity already "spoken for" on this ProjectMaterial line: Boss-approved
     * quantities plus still-pending (not yet decided) requested quantities. Rejected requests
     * contribute nothing. Used to enforce "Available = Approved - Previously Approved/Pending"
     * (spec section 11) before the Boss even has to look at it.
     */
    @Query(
        """
        SELECT COALESCE(SUM(
            CASE WHEN drm.approvedQuantity IS NOT NULL THEN drm.approvedQuantity ELSE drm.requestedQuantity END
        ), 0.0)
        FROM daily_request_material drm
        INNER JOIN daily_material_request dr ON drm.dailyRequestId = dr.id
        WHERE drm.projectMaterialId = :projectMaterialId AND dr.status != 'REJECTED'
        """
    )
    suspend fun getTotalCommittedForProjectMaterial(projectMaterialId: Long): Double

    // --- GateConfirmation ---
    @Insert
    suspend fun insertGateConfirmation(confirmation: GateConfirmation): Long

    @Query("SELECT * FROM gate_confirmation WHERE dispatchId = :dispatchId LIMIT 1")
    suspend fun getGateConfirmationForDispatch(dispatchId: Long): GateConfirmation?

    /**
     * CFW MaterialFlow dispatches (dailyRequestId set by the Warehouse step) that the
     * Gate/Collector hasn't confirmed yet. Queries dispatch_record directly rather than
     * duplicating it in a new table (spec section 34: reuse the existing Dispatch tables).
     */
    @Query(
        """
        SELECT * FROM dispatch_record
        WHERE dailyRequestId IS NOT NULL
        AND id NOT IN (SELECT dispatchId FROM gate_confirmation)
        ORDER BY createdAt ASC
        """
    )
    fun getDispatchesPendingGateConfirmation(): Flow<List<DispatchRecord>>

    // --- GateConfirmationMaterial ---
    @Insert
    suspend fun insertGateConfirmationMaterials(materials: List<GateConfirmationMaterial>)

    @Query("SELECT * FROM gate_confirmation_material WHERE gateConfirmationId = :gateConfirmationId")
    suspend fun getMaterialsForGateConfirmation(gateConfirmationId: Long): List<GateConfirmationMaterial>
}
