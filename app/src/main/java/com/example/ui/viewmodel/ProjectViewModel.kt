package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.dao.ProjectDao
import com.example.data.db.AppDatabase
import com.example.data.model.AppNotification
import com.example.data.model.AuditLog
import com.example.data.model.DailyMaterialRequest
import com.example.data.model.DailyRequestMaterial
import com.example.data.model.DispatchRecord
import com.example.data.model.EngineerEstimate
import com.example.data.model.EstimateMaterial
import com.example.data.model.Project
import com.example.data.model.ProjectFullDetails
import com.example.data.model.ProjectMaterial
import com.example.data.repository.DashboardSummary
import com.example.data.repository.DispatchableRequest
import com.example.data.repository.MaterialHistoryEntry
import com.example.data.repository.MaterialLine
import com.example.data.repository.PendingGateDispatch
import com.example.data.repository.ProjectRepository
import com.example.notification.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectViewModel(application: Application) : AndroidViewModel(application) {

    private val projectDao: ProjectDao = AppDatabase.getDatabase(application).projectDao()
    private val auditDao = AppDatabase.getDatabase(application).auditDao()
    private val cfwDao = AppDatabase.getDatabase(application).cfwDao()
    private val notificationDao = AppDatabase.getDatabase(application).notificationDao()
    val repository = ProjectRepository(projectDao, auditDao, cfwDao, notificationDao)

    /**
     * All CFW MaterialFlow notifications, newest first. Every physical role realistically has
     * its own phone, but this app has one local Room database per install and no server sync
     * for AppNotification yet — so this list (and the system-tray alerts below) only shows what
     * happened *on this device*. See NotificationHelper for the same note.
     */
    val allNotifications: StateFlow<List<AppNotification>> =
        notificationDao.getAllNotifications().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadNotificationCount: StateFlow<Int> =
        notificationDao.getAllNotifications()
            .map { list -> list.count { !it.isRead } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        // Pushes a system-tray alert for any notification row that appears *after* this
        // ViewModel starts observing — not for rows that already existed (avoids re-notifying
        // old alerts every time the Project Workflow screen is reopened).
        viewModelScope.launch {
            var seenIds: Set<Long>? = null
            notificationDao.getAllNotifications().collect { list ->
                if (seenIds != null) {
                    list.filter { it.id !in seenIds!! }.forEach { NotificationHelper.show(getApplication(), it) }
                }
                seenIds = list.map { it.id }.toSet()
            }
        }
    }

    fun markNotificationRead(id: Long) {
        viewModelScope.launch { notificationDao.markAsRead(id) }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch { notificationDao.markAllAsRead() }
    }

    val allProjects: StateFlow<List<Project>> =
        repository.allProjects.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingEstimates: StateFlow<List<EngineerEstimate>> =
        repository.pendingEstimates.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingDailyRequests: StateFlow<List<DailyMaterialRequest>> =
        repository.pendingDailyRequests.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val requestsReadyForDispatch: StateFlow<List<DailyMaterialRequest>> =
        repository.requestsReadyForDispatch.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dispatchesPendingGateConfirmation: StateFlow<List<DispatchRecord>> =
        repository.dispatchesPendingGateConfirmation.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentActivity: StateFlow<List<AuditLog>> =
        repository.recentActivity.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _dashboardSummary = MutableStateFlow<DashboardSummary?>(null)
    val dashboardSummary: StateFlow<DashboardSummary?> = _dashboardSummary.asStateFlow()

    /** Called when the Overview tab appears, and again after any workflow action that could change the counts. */
    fun refreshDashboardSummary() {
        viewModelScope.launch {
            _dashboardSummary.value = repository.getDashboardSummary()
        }
    }

    suspend fun getMaterialHistory(projectMaterialId: Long): List<MaterialHistoryEntry> =
        repository.getMaterialHistory(projectMaterialId)

    private val _userNotice = MutableStateFlow<String?>(null)
    val userNotice: StateFlow<String?> = _userNotice.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _selectedProjectDetails = MutableStateFlow<ProjectFullDetails?>(null)
    val selectedProjectDetails: StateFlow<ProjectFullDetails?> = _selectedProjectDetails.asStateFlow()

    fun clearUserNotice() {
        _userNotice.value = null
    }

    fun loadProjectDetails(projectId: Long) {
        viewModelScope.launch {
            _selectedProjectDetails.value = repository.getProjectFullDetails(projectId)
        }
    }

    // --- Engineer ---

    suspend fun getProjectById(projectId: Long): Project? = projectDao.getProjectById(projectId)

    fun submitEngineerEstimate(
        projectId: Long,
        engineerName: String,
        visitDate: String,
        measurementDetails: String,
        notes: String,
        materials: List<MaterialLine>,
        onDone: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.submitEngineerEstimate(
                projectId, engineerName, visitDate, measurementDetails, notes, materials
            )
            _isBusy.value = false
            _userNotice.value = result.fold(
                onSuccess = { "Estimate submitted. Waiting for Boss approval." },
                onFailure = { it.message ?: "Could not submit the estimate." }
            )
            onDone(result.isSuccess)
        }
    }

    // --- Boss: estimate review ---

    suspend fun getLatestEstimate(projectId: Long): EngineerEstimate? = repository.getLatestEstimate(projectId)

    suspend fun getEstimateMaterials(estimateId: Long): List<EstimateMaterial> = repository.getEstimateMaterials(estimateId)

    fun approveEngineerEstimate(
        estimateId: Long,
        projectId: Long,
        approvedMaterials: List<MaterialLine>,
        decidedBy: String,
        bossRemarks: String,
        onDone: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.approveEngineerEstimate(estimateId, projectId, approvedMaterials, decidedBy, bossRemarks)
            _isBusy.value = false
            _userNotice.value = result.fold(
                onSuccess = { "Materials approved. The TM has been notified." },
                onFailure = { it.message ?: "Could not approve the estimate." }
            )
            if (result.isSuccess) loadProjectDetails(projectId)
            onDone(result.isSuccess)
        }
    }

    fun rejectEngineerEstimate(estimateId: Long, projectId: Long, decidedBy: String, bossRemarks: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.rejectEngineerEstimate(estimateId, projectId, decidedBy, bossRemarks)
            _isBusy.value = false
            _userNotice.value = result.fold(
                onSuccess = { "Estimate rejected." },
                onFailure = { it.message ?: "Could not reject the estimate." }
            )
            onDone(result.isSuccess)
        }
    }

    // --- TM: daily request ---

    suspend fun getRequestableMaterials(projectId: Long): List<Pair<ProjectMaterial, Double>> =
        repository.getRequestableMaterials(projectId)

    fun submitDailyRequest(
        projectId: Long,
        requestDate: String,
        requestedBy: String,
        lines: List<Pair<Long, Double>>,
        onDone: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.submitDailyRequest(projectId, requestDate, requestedBy, lines)
            _isBusy.value = false
            _userNotice.value = result.fold(
                onSuccess = { "Request sent to Boss for approval." },
                onFailure = { it.message ?: "Could not submit the request." }
            )
            onDone(result.isSuccess)
        }
    }

    // --- Boss: daily request review ---

    suspend fun getDailyRequestDetails(requestId: Long): Pair<DailyMaterialRequest, List<DailyRequestMaterial>>? =
        repository.getDailyRequestDetails(requestId)

    fun decideDailyRequest(
        requestId: Long,
        projectId: Long,
        decisions: Map<Long, Double>,
        decidedBy: String,
        bossRemarks: String,
        onDone: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.decideDailyRequest(requestId, decisions, decidedBy, bossRemarks)
            _isBusy.value = false
            _userNotice.value = result.fold(
                onSuccess = { "Decision saved." },
                onFailure = { it.message ?: "Could not save the decision." }
            )
            if (result.isSuccess) loadProjectDetails(projectId)
            onDone(result.isSuccess)
        }
    }

    // --- Warehouse Keeper: actual dispatch ---

    suspend fun getDispatchableRequest(requestId: Long): DispatchableRequest? = repository.getDispatchableRequest(requestId)

    fun dispatchApprovedRequest(
        requestId: Long,
        projectId: Long,
        actualQuantities: Map<Long, Double>,
        warehouseKeeperName: String,
        dispatchDate: String,
        remarks: String,
        onDone: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.dispatchApprovedRequest(requestId, actualQuantities, warehouseKeeperName, dispatchDate, remarks)
            _isBusy.value = false
            _userNotice.value = result.fold(
                onSuccess = { "Materials dispatched. Waiting for Gate confirmation." },
                onFailure = { it.message ?: "Could not record the dispatch." }
            )
            if (result.isSuccess) loadProjectDetails(projectId)
            onDone(result.isSuccess)
        }
    }

    // --- Gate/Collector confirmation ---

    suspend fun getPendingGateDispatch(dispatchId: Long): PendingGateDispatch? = repository.getPendingGateDispatch(dispatchId)

    fun confirmGateCollection(
        dispatchId: Long,
        collectorName: String,
        confirmationDate: String,
        confirmationTime: String,
        remarks: String,
        actualCollected: Map<Long, Double>,
        onDone: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.confirmGateCollection(
                dispatchId, collectorName, confirmationDate, confirmationTime, remarks, actualCollected
            )
            _isBusy.value = false
            _userNotice.value = result.fold(
                onSuccess = { message -> message },
                onFailure = { it.message ?: "Could not save the confirmation." }
            )
            onDone(result.isSuccess)
        }
    }
}
