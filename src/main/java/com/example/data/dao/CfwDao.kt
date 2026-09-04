package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.AuditLog
import com.example.data.model.AppUser
import com.example.data.model.CfwBeneficiary
import com.example.data.model.DispatchMaterial
import com.example.data.model.DispatchRecord
import com.example.data.model.MaterialRequest
import com.example.data.model.RequestedMaterial
import kotlinx.coroutines.flow.Flow

@Dao
interface CfwDao {

    // --- Beneficiaries ---
    @Query("SELECT * FROM cfw_beneficiary ORDER BY cfwName ASC")
    fun getAllBeneficiaries(): Flow<List<CfwBeneficiary>>

    @Query("SELECT * FROM cfw_beneficiary WHERE id = :id")
    suspend fun getBeneficiaryById(id: Long): CfwBeneficiary?

    @Query("SELECT * FROM cfw_beneficiary WHERE LOWER(drrCode) = LOWER(:drrCode) LIMIT 1")
    suspend fun getBeneficiaryByDrrCode(drrCode: String): CfwBeneficiary?

    @Query("SELECT * FROM cfw_beneficiary WHERE LOWER(cfwName) LIKE LOWER('%' || :query || '%') OR LOWER(drrCode) LIKE LOWER('%' || :query || '%') OR LOWER(fcnNumber) LIKE LOWER('%' || :query || '%') ORDER BY cfwName ASC")
    fun searchBeneficiaries(query: String): Flow<List<CfwBeneficiary>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBeneficiary(beneficiary: CfwBeneficiary): Long

    @Update
    suspend fun updateBeneficiary(beneficiary: CfwBeneficiary)

    @Query("DELETE FROM cfw_beneficiary WHERE id = :id")
    suspend fun deleteBeneficiaryById(id: Long)

    @Query("SELECT COUNT(*) FROM cfw_beneficiary")
    fun getTotalBeneficiariesCount(): Flow<Int>


    // --- Material Requests ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterialRequest(request: MaterialRequest): Long

    @Update
    suspend fun updateMaterialRequest(request: MaterialRequest)

    @Query("SELECT * FROM material_request WHERE beneficiaryId = :beneficiaryId ORDER BY id DESC LIMIT 1")
    suspend fun getRequestForBeneficiary(beneficiaryId: Long): MaterialRequest?

    // Used specifically when (re)registering: only reuse a request already created *today* so a
    // duplicate tap doesn't create two rows for the same day. A request from an earlier day must
    // NOT be matched here, or a new day's registration silently reuses yesterday's (possibly
    // already-completed) request instead of starting a fresh dispatch cycle.
    @Query("SELECT * FROM material_request WHERE beneficiaryId = :beneficiaryId AND requestDate = :requestDate ORDER BY id DESC LIMIT 1")
    suspend fun getRequestForBeneficiaryOnDate(beneficiaryId: Long, requestDate: String): MaterialRequest?

    @Query("SELECT * FROM material_request WHERE beneficiaryId = :beneficiaryId ORDER BY id DESC LIMIT 1")
    fun getRequestForBeneficiaryFlow(beneficiaryId: Long): Flow<MaterialRequest?>

    @Query("SELECT * FROM material_request WHERE id = :requestId")
    suspend fun getRequestById(requestId: Long): MaterialRequest?

    @Query("SELECT * FROM material_request")
    fun getAllMaterialRequests(): Flow<List<MaterialRequest>>

    @Query("SELECT COUNT(*) FROM material_request")
    fun getTotalMaterialRequestsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM material_request WHERE status = 'COMPLETED'")
    fun getCompletedRequestsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM material_request WHERE status = 'PENDING'")
    fun getPendingRequestsCount(): Flow<Int>


    // --- Requested Materials ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequestedMaterials(items: List<RequestedMaterial>)

    @Query("SELECT * FROM requested_material WHERE requestId = :requestId")
    suspend fun getRequestedMaterialsForRequest(requestId: Long): List<RequestedMaterial>

    @Query("SELECT * FROM requested_material WHERE requestId = :requestId")
    fun getRequestedMaterialsForRequestFlow(requestId: Long): Flow<List<RequestedMaterial>>

    @Query("SELECT * FROM requested_material WHERE id = :materialId")
    suspend fun getRequestedMaterialById(materialId: Long): RequestedMaterial?

    @Query("SELECT * FROM requested_material")
    suspend fun getAllRequestedMaterials(): List<RequestedMaterial>


    // --- Dispatch Records ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDispatchRecord(dispatch: DispatchRecord): Long

    @Query("SELECT * FROM dispatch_record WHERE requestId = :requestId ORDER BY dispatchDate DESC, time DESC")
    suspend fun getDispatchesForRequest(requestId: Long): List<DispatchRecord>

    @Query("SELECT * FROM dispatch_record WHERE requestId = :requestId ORDER BY dispatchDate DESC, time DESC")
    fun getDispatchesForRequestFlow(requestId: Long): Flow<List<DispatchRecord>>

    @Query("SELECT * FROM dispatch_record ORDER BY dispatchDate DESC, time DESC")
    fun getAllDispatches(): Flow<List<DispatchRecord>>

    @Query("SELECT * FROM dispatch_record WHERE id = :dispatchId")
    suspend fun getDispatchById(dispatchId: Long): DispatchRecord?

    @Query("SELECT COUNT(*) FROM dispatch_record")
    fun getTotalDispatchesCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM dispatch_record WHERE dispatchDate = :todayDate")
    fun getTodayDispatchesCount(todayDate: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM dispatch_record WHERE syncStatus = 'PENDING'")
    fun getPendingSyncCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM dispatch_record WHERE syncStatus = 'SYNCED'")
    fun getSyncedCount(): Flow<Int>

    @Query("SELECT * FROM dispatch_record WHERE syncStatus = 'PENDING' OR syncStatus = 'FAILED'")
    fun getPendingSyncDispatches(): Flow<List<DispatchRecord>>

    @Query("UPDATE dispatch_record SET syncStatus = :status, syncErrorMessage = :errorMsg WHERE id = :dispatchId")
    suspend fun updateDispatchSyncStatus(dispatchId: Long, status: String, errorMsg: String? = null)

    @Query("DELETE FROM dispatch_record WHERE id = :dispatchId")
    suspend fun deleteDispatchById(dispatchId: Long)


    // --- Dispatch Materials ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDispatchMaterials(items: List<DispatchMaterial>)

    @Query("SELECT * FROM dispatch_material WHERE dispatchId = :dispatchId")
    suspend fun getDispatchMaterialsForDispatch(dispatchId: Long): List<DispatchMaterial>

    @Query("SELECT * FROM dispatch_material WHERE materialId = :materialId")
    suspend fun getDispatchMaterialsForMaterial(materialId: Long): List<DispatchMaterial>

    @Query("SELECT COALESCE(SUM(dispatchedQuantity), 0.0) FROM dispatch_material WHERE materialId = :materialId")
    suspend fun getTotalDispatchedForMaterial(materialId: Long): Double

    @Query("SELECT COALESCE(SUM(dispatchedQuantity), 0.0) FROM dispatch_material WHERE materialId = :materialId")
    fun getTotalDispatchedForMaterialFlow(materialId: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(dispatchedQuantity), 0.0) FROM dispatch_material")
    fun getTotalDispatchedQuantity(): Flow<Double?>

    @Query("SELECT * FROM dispatch_material")
    suspend fun getAllDispatchMaterials(): List<DispatchMaterial>


    // --- Cleanup Helper Transactions ---
    @Query("DELETE FROM dispatch_material WHERE dispatchId = :dispatchId")
    suspend fun deleteDispatchMaterialsForDispatch(dispatchId: Long)

    @Query("DELETE FROM dispatch_material WHERE dispatchId IN (SELECT id FROM dispatch_record WHERE requestId = :requestId)")
    suspend fun deleteDispatchMaterialsForRequest(requestId: Long)

    @Query("DELETE FROM dispatch_record WHERE requestId = :requestId")
    suspend fun deleteDispatchesForRequest(requestId: Long)

    @Query("DELETE FROM requested_material WHERE requestId = :requestId")
    suspend fun deleteRequestedMaterialsForRequest(requestId: Long)

    @Query("DELETE FROM material_request WHERE beneficiaryId = :beneficiaryId")
    suspend fun deleteRequestsForBeneficiary(beneficiaryId: Long)

    @Query("SELECT id FROM cfw_beneficiary WHERE UPPER(drrCode) IN ('DRR-1001', 'DRR-1002', 'DRR-1003', 'DRR-1004', 'DRR-1005')")
    suspend fun getSampleBeneficiaryIds(): List<Long>

    @Query("DELETE FROM dispatch_material")
    suspend fun deleteAllDispatchMaterials()

    @Query("DELETE FROM dispatch_record")
    suspend fun deleteAllDispatchRecords()

    @Query("DELETE FROM requested_material")
    suspend fun deleteAllRequestedMaterials()

    @Query("DELETE FROM material_request")
    suspend fun deleteAllMaterialRequests()

    @Query("DELETE FROM cfw_beneficiary")
    suspend fun deleteAllBeneficiaries()

    @Transaction
    suspend fun clearAllData() {
        deleteAllDispatchMaterials()
        deleteAllDispatchRecords()
        deleteAllRequestedMaterials()
        deleteAllMaterialRequests()
        deleteAllBeneficiaries()
    }

    @Transaction
    suspend fun deleteBeneficiaryWithAllData(beneficiaryId: Long) {
        val request = getRequestForBeneficiary(beneficiaryId)
        if (request != null) {
            deleteDispatchMaterialsForRequest(request.id)
            deleteDispatchesForRequest(request.id)
            deleteRequestedMaterialsForRequest(request.id)
            deleteRequestsForBeneficiary(beneficiaryId)
        }
        deleteBeneficiaryById(beneficiaryId)
    }
}

@Dao
interface AuditDao {
    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT 100")
    fun getRecentAuditLogs(): Flow<List<AuditLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLog)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM app_user ORDER BY name ASC")
    fun getAllUsers(): Flow<List<AppUser>>

    @Query("SELECT * FROM app_user WHERE password = :password LIMIT 1")
    suspend fun getUserByPassword(password: String): AppUser?

    @Query("SELECT * FROM app_user WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getUserByName(name: String): AppUser?

    @Query("SELECT COUNT(*) FROM app_user")
    suspend fun getUserCount(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: AppUser): Long

    @Query("DELETE FROM app_user WHERE id = :id")
    suspend fun deleteUserById(id: Long)
}
