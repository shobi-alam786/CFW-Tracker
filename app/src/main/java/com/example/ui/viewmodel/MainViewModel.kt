package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.AuditLog
import com.example.data.model.BeneficiaryFullDetails
import com.example.data.model.CfwBeneficiary
import com.example.data.model.DispatchRecord
import com.example.data.preferences.UserPreferences
import com.example.data.repository.CfwRepository
import com.example.data.sync.SyncEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppTab {
    DASHBOARD, NEW_ENTRY, SEARCH, REPORTS, SETTINGS
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val userPreferences = UserPreferences(application)
    private val syncEngine = SyncEngine(db.cfwDao(), userPreferences)
    val repository = CfwRepository(db.cfwDao(), db.auditDao(), db.userDao(), userPreferences, syncEngine)

    init {
        viewModelScope.launch {
            repository.clearSampleDemoData()
        }
    }

    // Active Navigation Tab
    private val _currentTab = MutableStateFlow(AppTab.DASHBOARD)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    // Selected Beneficiary for Profile Screen modal/subview
    private val _selectedBeneficiaryId = MutableStateFlow<Long?>(null)
    val selectedBeneficiaryId: StateFlow<Long?> = _selectedBeneficiaryId.asStateFlow()

    // Search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Dashboard Metrics
    val totalBeneficiariesCount: StateFlow<Int> = repository.totalBeneficiariesCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val totalRequestsCount: StateFlow<Int> = repository.totalRequestsCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val totalDispatchesCount: StateFlow<Int> = repository.totalDispatchesCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val todayDispatchesCount: StateFlow<Int> = repository.getTodayDispatchesCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val pendingRequestsCount: StateFlow<Int> = repository.pendingRequestsCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val completedRequestsCount: StateFlow<Int> = repository.completedRequestsCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val pendingSyncCount: StateFlow<Int> = repository.pendingSyncCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val syncedCount: StateFlow<Int> = repository.syncedCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val allBeneficiaries: StateFlow<List<CfwBeneficiary>> = repository.allBeneficiaries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allDispatches: StateFlow<List<DispatchRecord>> = repository.allDispatches.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val auditLogs: StateFlow<List<AuditLog>> = repository.auditLogs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allUsers: StateFlow<List<com.example.data.model.AppUser>> = repository.allUsers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun addUser(name: String, password: String): String? = repository.addUser(name, password)
    fun deleteUser(id: Long) {
        viewModelScope.launch { repository.deleteUser(id) }
    }
    suspend fun verifyUserPassword(password: String): Boolean = repository.verifyUserPassword(password)
    suspend fun getUserCount(): Int = repository.getUserCount()

    val customMaterials: StateFlow<Set<String>> = userPreferences.customMaterials.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val customUnits: StateFlow<Set<String>> = userPreferences.customUnits.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun addCustomMaterial(name: String) {
        userPreferences.addCustomMaterial(name)
    }

    fun addCustomUnit(unit: String) {
        userPreferences.addCustomUnit(unit)
    }

    // UI Notice / Toast
    private val _userNotice = MutableStateFlow<String?>(null)
    val userNotice: StateFlow<String?> = _userNotice.asStateFlow()

    // Is Syncing State
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // Sync Error Popup State
    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    fun clearSyncError() {
        _syncError.value = null
    }

    fun selectTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun openPersonProfile(beneficiaryId: Long) {
        _selectedBeneficiaryId.value = beneficiaryId
    }

    fun closePersonProfile() {
        _selectedBeneficiaryId.value = null
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearNotice() {
        _userNotice.value = null
    }

    fun postNotice(notice: String) {
        _userNotice.value = notice
    }

    fun showNotice(msg: String) {
        _userNotice.value = msg
    }

    fun registerBeneficiaryAndRequest(
        drrCode: String,
        cfwName: String,
        fcnNumber: String,
        approvedMaterials: List<Pair<String, Pair<Double, String>>>,
        onSuccess: (Long) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val reqId = repository.registerBeneficiaryAndRequest(
                    drrCode = drrCode,
                    cfwName = cfwName,
                    fcnNumber = fcnNumber,
                    approvedMaterials = approvedMaterials
                )
                _userNotice.value = "Registered $cfwName ($drrCode) with Material Request #$reqId!"
                onSuccess(reqId)
            } catch (e: Exception) {
                _userNotice.value = "Registration error: ${e.message}"
            }
        }
    }

    fun createDispatchRecord(
        requestId: Long,
        recipientName: String,
        recipientFcn: String,
        dispatches: List<Pair<Long, Double>>,
        note: String,
        latitude: Double? = null,
        longitude: Double? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.createDispatchRecord(
                requestId = requestId,
                recipientName = recipientName,
                recipientFcn = recipientFcn,
                dispatches = dispatches,
                note = note,
                latitude = latitude,
                longitude = longitude
            )
            res.fold(
                onSuccess = { dispatchId ->
                    _userNotice.value = "Materials successfully dispatched!"
                    onSuccess()
                },
                onFailure = { err ->
                    val msg = err.message ?: "Dispatch validation error"
                    _userNotice.value = msg
                    onError(msg)
                }
            )
        }
    }

    fun triggerSyncAll() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            val summary = repository.syncAllPending()
            _isSyncing.value = false
            when {
                summary.attempted == 0 -> _userNotice.value = "Nothing to sync — all dispatches are already up to date."
                summary.failCount == 0 -> _userNotice.value = "Sync complete. ${summary.successCount} dispatch(es) synced."
                else -> {
                    _userNotice.value = "Sync finished with errors. ${summary.successCount}/${summary.attempted} synced."
                    _syncError.value = summary.lastError ?: "Sync could not reach the server. Check your network or API/webhook settings and try again."
                }
            }
        }
    }

    fun triggerSingleSync(dispatchId: Long) {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            val summary = repository.syncSingleDispatch(dispatchId)
            _isSyncing.value = false
            if (summary.failCount == 0) {
                _userNotice.value = "Dispatch record successfully synced!"
            } else {
                _userNotice.value = "Sync failed. Check API/network settings."
                _syncError.value = summary.lastError ?: "Sync failed. Check your network or API/webhook settings and try again."
            }
        }
    }

    fun deleteBeneficiary(beneficiaryId: Long) {
        viewModelScope.launch {
            repository.deleteBeneficiary(beneficiaryId)
            if (_selectedBeneficiaryId.value == beneficiaryId) {
                _selectedBeneficiaryId.value = null
            }
            _userNotice.value = "TM profile deleted."
        }
    }

    fun updateBeneficiary(beneficiary: CfwBeneficiary) {
        viewModelScope.launch {
            repository.updateBeneficiary(beneficiary)
            _userNotice.value = "TM profile updated."
        }
    }

    fun bulkImportCsv(csvContent: String) {
        viewModelScope.launch {
            try {
                var importedCount = 0
                val lines = csvContent.lines().filter { it.isNotBlank() }
                val defaultApproved = listOf(
                    "Cement" to (5.0 to "Bag"),
                    "Brick" to (500.0 to "Pieces"),
                    "Sand" to (24.0 to "Feet")
                )

                for (i in 1 until lines.size) {
                    val cols = lines[i].split(",").map { it.trim().removeSurrounding("\"") }
                    if (cols.size >= 3) {
                        val drr = cols[0]
                        val name = cols[1]
                        val fcn = cols[2]
                        if (drr.isNotBlank() && name.isNotBlank()) {
                            val existing = repository.getBeneficiaryByDrrCode(drr)
                            if (existing == null) {
                                repository.registerBeneficiaryAndRequest(
                                    drrCode = drr,
                                    cfwName = name,
                                    fcnNumber = fcn,
                                    approvedMaterials = defaultApproved
                                )
                                importedCount++
                            }
                        }
                    }
                }
                _userNotice.value = "Bulk import complete! Added $importedCount new TM profiles."
            } catch (e: Exception) {
                _userNotice.value = "Bulk import error: ${e.message}"
            }
        }
    }

    fun clearSampleDemoData() {
        viewModelScope.launch {
            val removed = repository.clearSampleDemoData()
            _userNotice.value = if (removed > 0) "Removed $removed sample demo records." else "No sample demo records found."
        }
    }

    fun clearAllDatabaseData() {
        viewModelScope.launch {
            repository.clearAllDatabaseData()
            _userNotice.value = "All database records have been reset."
        }
    }
}
