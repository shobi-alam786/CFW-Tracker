package com.example.data.kobo

import org.json.JSONArray
import org.json.JSONObject

/** A single field/value pair prepared for display, at a given nesting [depth]. */
data class KoboDisplayField(
    val key: String,
    val displayValue: String,
    val depth: Int = 0,
    val isMeta: Boolean = false
)

/**
 * Kobo forms can contain any mix of strings, numbers, booleans, nested groups (objects),
 * and repeat groups (arrays) — and any field can simply be missing. This walks a submission's
 * JSON and turns it into a flat list of (key, value) pairs that a Composable can render with
 * a plain loop, without ever needing to know a form's structure ahead of time or risk a
 * type-cast crash.
 */
object KoboJsonUtils {

    /** Fields KoboToolbox adds itself; shown in a separate "Submission Info" section. */
    private val metaKeys = setOf(
        "_id", "_uuid", "_submission_time", "_validation_status", "_status",
        "_submitted_by", "_notes", "_tags", "formhub/uuid", "meta/instanceID",
        "__version__", "_xform_id_string", "_attachments", "_geolocation"
    )

    fun flatten(json: JSONObject): List<KoboDisplayField> {
        val result = mutableListOf<KoboDisplayField>()
        val keys = json.keys().asSequence().sorted().toList()
        for (key in keys) {
            appendField(result, key, json.opt(key), depth = 0)
        }
        return result
    }

    fun metaFields(fields: List<KoboDisplayField>): List<KoboDisplayField> = fields.filter { it.isMeta }
    fun dataFields(fields: List<KoboDisplayField>): List<KoboDisplayField> = fields.filter { !it.isMeta }

    private fun appendField(out: MutableList<KoboDisplayField>, key: String, value: Any?, depth: Int) {
        val isMeta = depth == 0 && metaKeys.contains(key)
        when (value) {
            null, JSONObject.NULL -> out.add(KoboDisplayField(key, "—", depth, isMeta))

            is JSONObject -> {
                out.add(KoboDisplayField(key, "", depth, isMeta))
                val childKeys = value.keys().asSequence().sorted().toList()
                for (childKey in childKeys) {
                    appendField(out, childKey, value.opt(childKey), depth + 1)
                }
            }

            is JSONArray -> {
                if (value.length() == 0) {
                    out.add(KoboDisplayField(key, "(empty)", depth, isMeta))
                } else {
                    out.add(KoboDisplayField(key, "${value.length()} item(s)", depth, isMeta))
                    for (i in 0 until value.length()) {
                        val item = value.opt(i)
                        appendField(out, "[$i]", item, depth + 1)
                    }
                }
            }

            is Boolean -> out.add(KoboDisplayField(key, if (value) "Yes" else "No", depth, isMeta))

            else -> out.add(KoboDisplayField(key, value.toString(), depth, isMeta))
        }
    }
}
