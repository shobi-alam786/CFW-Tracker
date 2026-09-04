package com.example.data.kobo

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface KoboDao {

    @Query("SELECT * FROM kobo_submission WHERE assetUid = :assetUid ORDER BY submissionTime DESC")
    fun getSubmissionsForAsset(assetUid: String): Flow<List<KoboSubmission>>

    @Query(
        "SELECT * FROM kobo_submission WHERE assetUid = :assetUid " +
            "AND rawJson LIKE '%' || :query || '%' ORDER BY submissionTime DESC"
    )
    fun searchSubmissions(assetUid: String, query: String): Flow<List<KoboSubmission>>

    @Query("SELECT * FROM kobo_submission WHERE submissionId = :id LIMIT 1")
    suspend fun getSubmissionById(id: Long): KoboSubmission?

    // One-shot (non-Flow) fetch of everything cached for this asset, used to find sibling
    // submissions that belong to the same dispatch visit (same DRR Code + Recipient + date)
    // when a recipient received more than one material in a single visit.
    @Query("SELECT * FROM kobo_submission WHERE assetUid = :assetUid")
    suspend fun getAllSubmissionsForAssetOnce(assetUid: String): List<KoboSubmission>

    @Query("SELECT COUNT(*) FROM kobo_submission WHERE assetUid = :assetUid")
    fun getSubmissionCount(assetUid: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubmissions(items: List<KoboSubmission>)

    @Query("DELETE FROM kobo_submission WHERE assetUid = :assetUid")
    suspend fun clearSubmissionsForAsset(assetUid: String)

    @Query("SELECT MAX(submissionId) FROM kobo_submission WHERE assetUid = :assetUid")
    suspend fun getHighestSubmissionId(assetUid: String): Long?
}
