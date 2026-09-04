package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.kobo.KoboErrorType
import com.example.data.kobo.KoboImportEngine
import com.example.data.kobo.KoboRepository
import com.example.data.kobo.KoboSecureSettings
import com.example.data.kobo.KoboSubmission
import com.example.data.kobo.KoboSyncResult
import com.example.data.kobo.KoboTestResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** UI state for the Kobo Data screen. */
sealed class KoboUiState {
    object Loading : KoboUiState()
    object NotConfigured : KoboUiState()
    data class Success(val submissions: List<KoboSubmission>) : KoboUiState()
    object Empty : KoboUiState()
    data class Error(val message: String) : KoboUiState()
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class KoboViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = KoboSecureSettings(application)
    private val koboDao = AppDatabase.getDatabase(application).koboDao()
    private val cfwDao = AppDatabase.getDatabase(application).cfwDao()
    private val repository = KoboRepository(koboDao, settings)
    private val importEngine = KoboImportEngine(cfwDao)

    val serverUrl: StateFlow<String> = settings.serverUrl
    val assetUid: StateFlow<String> = settings.assetUid
    val apiToken: StateFlow<String> = settings.apiToken
    val lastSyncTime: StateFlow<Long> = settings.lastSyncTime
    val isConfigured: Boolean get() = settings.isConfigured

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _isOffline = MutableStateFlow(!isNetworkAvailable(application))
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val _userNotice = MutableStateFlow<String?>(null)
    val userNotice: StateFlow<String?> = _userNotice.asStateFlow()

    private val _testResultMessage = MutableStateFlow<String?>(null)
    val testResultMessage: StateFlow<String?> = _testResultMessage.asStateFlow()

    val totalCachedCount: StateFlow<Int> = assetUid.flatMapLatest { uid ->
        if (uid.isBlank()) kotlinx.coroutines.flow.flowOf(0) else repository.getCachedCount(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val uiState: StateFlow<KoboUiState> = combine(
        assetUid,
        _searchQuery
    ) { uid, query -> uid to query }
        .flatMapLatest { (uid, query) ->
            if (uid.isBlank()) {
                kotlinx.coroutines.flow.flowOf<KoboUiState>(KoboUiState.NotConfigured)
            } else {
                val submissionsFlow = if (query.isBlank()) {
                    repository.getCachedSubmissions(uid)
                } else {
                    repository.searchCachedSubmissions(uid, query)
                }
                kotlinx.coroutines.flow.flow<KoboUiState> {
                    submissionsFlow.collect { list ->
                        emit(if (list.isEmpty()) KoboUiState.Empty else KoboUiState.Success(list))
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), KoboUiState.Loading)

    suspend fun getSubmissionDetail(submissionId: Long): KoboSubmission? =
        repository.getSubmissionDetail(submissionId)

    /**
     * Builds the report for the selected DRR daily request (same DRR + same local calendar day)
     * from the local cache. A new request on a later day is intentionally kept separate — see
     * [com.example.data.kobo.KoboVisitAggregator] for exactly how that's assembled.
     */
    suspend fun getDrrDayReport(current: KoboSubmission): com.example.data.kobo.KoboDrrDayReport {
        val all = repository.getAllCachedSubmissionsOnce(current.assetUid)
        return com.example.data.kobo.KoboVisitAggregator.build(current, all)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun refreshNetworkStatus() {
        _isOffline.value = !isNetworkAvailable(getApplication())
    }

    fun clearNotice() {
        _userNotice.value = null
    }

    fun clearTestResult() {
        _testResultMessage.value = null
    }

    fun saveConfig(serverUrl: String, assetUid: String, apiToken: String) {
        settings.saveConfig(serverUrl, assetUid, apiToken)
        _userNotice.value = "KoboToolbox Data settings saved."
    }

    fun clearConfig() {
        val uidToClear = settings.assetUid.value
        viewModelScope.launch {
            if (uidToClear.isNotBlank()) {
                repository.clearCache(uidToClear)
            }
            settings.clearConfig()
            _userNotice.value = "KoboToolbox Data configuration cleared."
        }
    }

    fun testConnection(serverUrl: String, assetUid: String, apiToken: String) {
        viewModelScope.launch {
            _testResultMessage.value = "Testing connection..."
            when (val result = repository.testConnection(serverUrl, assetUid, apiToken)) {
                is KoboTestResult.Success ->
                    _testResultMessage.value = "Connection successful."
                is KoboTestResult.Failure ->
                    _testResultMessage.value = friendlyErrorMessage(result.type, result.message)
            }
        }
    }

    fun sync() {
        if (_isSyncing.value) return
        refreshNetworkStatus()
        if (_isOffline.value) {
            _userNotice.value = "You're offline. Showing previously downloaded data."
            return
        }
        viewModelScope.launch {
            _isSyncing.value = true
            when (val result = repository.syncSubmissions()) {
                is KoboSyncResult.Success -> {
                    // Push the cached submissions into the app's own Beneficiary/MaterialRequest
                    // tables so Reports, Dashboard, and CSV export reflect the latest Kobo data.
                    val uid = assetUid.value
                    if (uid.isNotBlank()) {
                        importEngine.importAll(repository.getAllCachedSubmissionsOnce(uid))
                    }
                    _userNotice.value = if (result.downloadedCount > 0) {
                        "Synced ${result.downloadedCount} submission(s)."
                    } else {
                        "No submissions found for this project."
                    }
                }
                is KoboSyncResult.NotConfigured ->
                    _userNotice.value = "Please configure KoboToolbox Data settings first."
                is KoboSyncResult.Error ->
                    _userNotice.value = friendlyErrorMessage(result.type, result.message)
            }
            _isSyncing.value = false
        }
    }

    private fun friendlyErrorMessage(type: KoboErrorType, fallback: String): String = when (type) {
        KoboErrorType.NO_NETWORK -> "Network unavailable. Check your connection and try again."
        KoboErrorType.TIMEOUT -> "The request timed out. Check your connection and try again."
        KoboErrorType.INVALID_TOKEN -> "Invalid API token. Please check it in Settings."
        KoboErrorType.INVALID_ASSET -> "Invalid Project/Asset UID. Please check it in Settings."
        KoboErrorType.SERVER_ERROR -> "KoboToolbox server error. Please try again later."
        KoboErrorType.PARSE_ERROR -> "Could not read the data returned by the server."
        KoboErrorType.UNKNOWN -> fallback.ifBlank { "Something went wrong. Please try again." }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }
}
