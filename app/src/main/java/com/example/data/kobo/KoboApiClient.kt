package com.example.data.kobo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** Why a Kobo API call failed, so the UI can show a clear, specific message. */
enum class KoboErrorType {
    NO_NETWORK,
    TIMEOUT,
    INVALID_TOKEN,
    INVALID_ASSET,
    SERVER_ERROR,
    PARSE_ERROR,
    UNKNOWN
}

sealed class KoboApiResult {
    /**
     * @param submissions raw submission objects for this page, newest fields untouched
     * @param nextPageUrl full URL Kobo gave us for the next page, or null if this was the last page
     * @param totalCount total submissions available on the server for this asset
     */
    data class Success(
        val submissions: List<JSONObject>,
        val nextPageUrl: String?,
        val totalCount: Int
    ) : KoboApiResult()

    data class Error(val type: KoboErrorType, val message: String) : KoboApiResult()
}

/**
 * Talks to the official KoboToolbox v2 REST API:
 * GET {server}/api/v2/assets/{assetUid}/data/?format=json&limit=..&start=..
 * Authenticated with `Authorization: Token <token>`.
 *
 * This uses OkHttp + org.json directly (the same libraries [com.example.data.sync.SyncEngine]
 * already uses to talk to KoboToolbox) instead of introducing Retrofit, so the new read path
 * stays consistent with the app's existing networking approach.
 */
class KoboApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Fetches one page of submissions. Pass [pageUrl] to follow a "next" link Kobo already gave us. */
    suspend fun fetchSubmissionsPage(
        serverUrl: String,
        assetUid: String,
        apiToken: String,
        pageUrl: String? = null,
        limit: Int = 30,
        start: Int = 0
    ): KoboApiResult = withContext(Dispatchers.IO) {
        val url = pageUrl ?: "${serverUrl.trimEnd('/')}/api/v2/assets/$assetUid/data/" +
            "?format=json&limit=$limit&start=$start"

        try {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Token $apiToken")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()

                when {
                    response.code == 401 || response.code == 403 ->
                        KoboApiResult.Error(KoboErrorType.INVALID_TOKEN, "The API token was rejected. Please check it in Settings.")

                    response.code == 404 ->
                        KoboApiResult.Error(KoboErrorType.INVALID_ASSET, "Project/Asset UID not found. Please check it in Settings.")

                    response.code >= 500 ->
                        KoboApiResult.Error(KoboErrorType.SERVER_ERROR, "KoboToolbox server error (HTTP ${response.code}). Please try again later.")

                    !response.isSuccessful ->
                        KoboApiResult.Error(KoboErrorType.UNKNOWN, "Request failed (HTTP ${response.code}).")

                    else -> parseSubmissionsBody(body)
                }
            }
        } catch (e: UnknownHostException) {
            KoboApiResult.Error(KoboErrorType.NO_NETWORK, "No internet connection. Check your network and try again.")
        } catch (e: SocketTimeoutException) {
            KoboApiResult.Error(KoboErrorType.TIMEOUT, "The request timed out. Check your connection and try again.")
        } catch (e: IOException) {
            KoboApiResult.Error(KoboErrorType.NO_NETWORK, "Network unavailable. Check your connection and try again.")
        } catch (e: Exception) {
            KoboApiResult.Error(KoboErrorType.UNKNOWN, e.message ?: "Unknown error while contacting KoboToolbox.")
        }
    }

    private fun parseSubmissionsBody(body: String): KoboApiResult {
        return try {
            val json = JSONObject(body)
            val resultsArray = json.optJSONArray("results") ?: org.json.JSONArray()
            val submissions = mutableListOf<JSONObject>()
            for (i in 0 until resultsArray.length()) {
                submissions.add(resultsArray.getJSONObject(i))
            }
            val nextUrl = json.optString("next", "").ifBlank { null }
            val totalCount = json.optInt("count", submissions.size)
            KoboApiResult.Success(submissions, nextUrl, totalCount)
        } catch (e: Exception) {
            KoboApiResult.Error(KoboErrorType.PARSE_ERROR, "Could not read the data returned by the server.")
        }
    }

    /** A lightweight single-record fetch used by the "Test Connection" button in Settings. */
    suspend fun testConnection(serverUrl: String, assetUid: String, apiToken: String): KoboApiResult =
        fetchSubmissionsPage(serverUrl = serverUrl, assetUid = assetUid, apiToken = apiToken, limit = 1, start = 0)
}
