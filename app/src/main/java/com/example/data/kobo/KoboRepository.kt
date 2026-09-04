package com.example.data.kobo

import kotlinx.coroutines.flow.Flow

sealed class KoboSyncResult {
    data class Success(val downloadedCount: Int) : KoboSyncResult()
    object NotConfigured : KoboSyncResult()
    data class Error(val type: KoboErrorType, val message: String) : KoboSyncResult()
}

sealed class KoboTestResult {
    object Success : KoboTestResult()
    data class Failure(val type: KoboErrorType, val message: String) : KoboTestResult()
}

/**
 * Repository for the KoboToolbox "Data" feature (pulling submissions into the app).
 *
 * Kept separate from [com.example.data.repository.CfwRepository] and [com.example.data.sync.SyncEngine]
 * because this is a distinct, additive feature (reading Kobo submissions) rather than the
 * existing feature (pushing dispatch records to Kobo/Sheets). This mirrors the existing
 * app's Repository pattern: ViewModel -> Repository -> (remote API + local Room DAO).
 */
class KoboRepository(
    private val koboDao: KoboDao,
    private val settings: KoboSecureSettings
) {
    private val apiClient = KoboApiClient()

    fun getCachedSubmissions(assetUid: String): Flow<List<KoboSubmission>> =
        koboDao.getSubmissionsForAsset(assetUid)

    fun searchCachedSubmissions(assetUid: String, query: String): Flow<List<KoboSubmission>> =
        koboDao.searchSubmissions(assetUid, query)

    fun getCachedCount(assetUid: String): Flow<Int> =
        koboDao.getSubmissionCount(assetUid)

    suspend fun getSubmissionDetail(submissionId: Long): KoboSubmission? =
        koboDao.getSubmissionById(submissionId)

    suspend fun getAllCachedSubmissionsOnce(assetUid: String): List<KoboSubmission> =
        koboDao.getAllSubmissionsForAssetOnce(assetUid)

    suspend fun testConnection(serverUrl: String, assetUid: String, apiToken: String): KoboTestResult {
        return when (val result = apiClient.testConnection(serverUrl, assetUid, apiToken)) {
            is KoboApiResult.Success -> KoboTestResult.Success
            is KoboApiResult.Error -> KoboTestResult.Failure(result.type, result.message)
        }
    }

    /**
     * Downloads all submissions for the configured asset, page by page, following
     * KoboToolbox's own "next" pagination links so we don't have to guess offsets.
     * Every downloaded submission is upserted (insert-or-replace) by its Kobo `_id`,
     * so re-syncing updates changed records instead of creating duplicates.
     */
    suspend fun syncSubmissions(pageSize: Int = 30): KoboSyncResult {
        if (!settings.isConfigured) return KoboSyncResult.NotConfigured

        val serverUrl = settings.serverUrl.value
        val assetUid = settings.assetUid.value
        val apiToken = settings.apiToken.value

        var nextUrl: String? = null
        var start = 0
        var totalDownloaded = 0
        var firstPage = true

        while (firstPage || nextUrl != null) {
            firstPage = false

            val result = apiClient.fetchSubmissionsPage(
                serverUrl = serverUrl,
                assetUid = assetUid,
                apiToken = apiToken,
                pageUrl = nextUrl,
                limit = pageSize,
                start = start
            )

            when (result) {
                is KoboApiResult.Success -> {
                    if (result.submissions.isEmpty()) break

                    val entities = result.submissions.mapNotNull { obj ->
                        val id = obj.optLong("_id", -1L)
                        if (id < 0) return@mapNotNull null
                        KoboSubmission(
                            submissionId = id,
                            assetUid = assetUid,
                            rawJson = obj.toString(),
                            submissionTime = obj.optString("_submission_time", ""),
                            validationStatus = obj.optJSONObject("_validation_status")
                                ?.optString("label", "") ?: ""
                        )
                    }
                    koboDao.upsertSubmissions(entities)
                    totalDownloaded += entities.size

                    nextUrl = result.nextPageUrl
                    start += pageSize
                }
                is KoboApiResult.Error -> {
                    // If we already downloaded some pages before hitting an error (e.g. the
                    // connection dropped midway), report what we got instead of discarding it.
                    return if (totalDownloaded > 0) {
                        settings.updateLastSyncTime(System.currentTimeMillis())
                        KoboSyncResult.Success(totalDownloaded)
                    } else {
                        KoboSyncResult.Error(result.type, result.message)
                    }
                }
            }
        }

        settings.updateLastSyncTime(System.currentTimeMillis())
        return KoboSyncResult.Success(totalDownloaded)
    }

    suspend fun clearCache(assetUid: String) {
        koboDao.clearSubmissionsForAsset(assetUid)
    }
}
