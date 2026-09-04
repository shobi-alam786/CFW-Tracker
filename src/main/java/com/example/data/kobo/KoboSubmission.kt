package com.example.data.kobo

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.json.JSONObject

/**
 * A single downloaded KoboToolbox submission, cached locally for offline viewing.
 *
 * KoboToolbox forms can have completely different fields from one project to the next,
 * so instead of modeling every possible field as a database column, we store the full
 * submission JSON as-is (rawJson) and pull out only the handful of fields we always need
 * for lists/sorting (submissionId, submissionTime). The Details screen re-parses rawJson
 * dynamically so it can display any field, known or unknown, without crashing.
 */
@Entity(
    tableName = "kobo_submission",
    indices = [Index(value = ["assetUid"])]
)
data class KoboSubmission(
    @PrimaryKey val submissionId: Long,
    val assetUid: String,
    val rawJson: String,
    val submissionTime: String,
    val validationStatus: String = "",
    val downloadedAt: Long = System.currentTimeMillis()
) {
    /** Lazily parsed view of [rawJson]. Never throws — returns an empty object on bad data. */
    fun toJson(): JSONObject = try {
        JSONObject(rawJson)
    } catch (e: Exception) {
        JSONObject()
    }

    /**
     * A short, human-friendly label for list rows, since we don't know which fields
     * a given Kobo form actually has. Picks the first non-metadata text field it finds.
     */
    fun previewLabel(): String {
        val json = toJson()
        val keys = json.keys().asSequence().filter { !it.startsWith("_") && !it.startsWith("meta/") }
        for (key in keys) {
            val value = json.opt(key)
            if (value is String && value.isNotBlank()) {
                return value
            }
        }
        return "Submission #$submissionId"
    }
}
