package com.example.data.kobo

import org.json.JSONArray
import org.json.JSONObject

/** A single labeled value, ready to render as-is — no raw JSON, no array-index keys. */
data class KoboField(
    val label: String,
    val value: String
)

/**
 * The result of classifying one submission's fields into business-meaningful groups.
 *
 * This is purely a *display* concern — it reads the same [KoboSubmission.rawJson] the old
 * flat viewer used, it just organizes it more usefully. No sync, caching, search, or
 * pagination logic is touched by this file.
 *
 * Each Kobo submission represents ONE material dispatched to ONE recipient on ONE visit —
 * [materialInfo] here covers only that single submission's material. When a recipient
 * receives several materials in one visit, the app dispatches them as several sibling
 * submissions sharing the same DRR Code + Recipient + date; aggregating those into one
 * "Materials Information" view with multiple cards is handled by the caller (see
 * [KoboSubmissionDetailScreen]), not by this classifier.
 */
data class KoboCategorizedSubmission(
    val tmName: String?,
    val drrCode: String?,
    val recipientName: String?,
    val recipientFcn: String?,
    val materialName: String?,
    val quantityText: String?,
    val submittedBy: String?,
    val projectInfo: List<KoboField>,
    val recipientInfo: List<KoboField>,
    val materialInfo: List<KoboField>,
    val submissionInfo: List<KoboField>,
    val additionalInfo: List<KoboField>,
    val technicalInfo: List<KoboField>
)

/**
 * KoboToolbox forms are dynamic — field names vary per project. Rather than hardcoding one
 * form's schema, this recognizes *common naming patterns* (e.g. any leaf key containing
 * "material", "recipient", "drr", "qty"/"quantity") to sort fields into sections a field
 * worker cares about, and routes anything Kobo-internal (ids, uuids, geolocation,
 * attachments) into a separate technical bucket instead of letting it dominate the screen.
 * Anything that doesn't match a known pattern still gets shown — just under "Additional
 * Information" — so unknown/custom forms remain fully readable, nothing is ever silently
 * dropped.
 */
object KoboFieldClassifier {

    private val technicalLeafKeys = setOf(
        "_id", "_uuid", "_submission_time", "_validation_status", "_status",
        "_submitted_by", "_notes", "_tags", "uuid", "instanceid", "__version__",
        "_xform_id_string", "_attachments", "_geolocation", "_bamboo_dataset_id",
        "_deprecatedid", "_edited", "deviceid", "phonenumber", "audit", "meta",
        "start", "end", "today", "simserial", "subscriberid", "imei"
    )

    private fun leaf(key: String): String = key.substringAfterLast('/').lowercase()

    private fun isTechnical(key: String): Boolean {
        val l = leaf(key)
        return key.startsWith("_") ||
            technicalLeafKeys.contains(l) ||
            l.contains("uuid") ||
            l == "meta"
    }

    private fun matchesAll(leafKey: String, vararg needles: String) = needles.all { leafKey.contains(it) }
    private fun matchesAny(leafKey: String, vararg needles: String) = needles.any { leafKey.contains(it) }

    fun classify(json: JSONObject): KoboCategorizedSubmission {
        val projectInfo = mutableListOf<KoboField>()
        val recipientInfo = mutableListOf<KoboField>()
        val materialInfo = mutableListOf<KoboField>()
        val submissionInfo = mutableListOf<KoboField>()
        val additionalInfo = mutableListOf<KoboField>()
        val technicalInfo = mutableListOf<KoboField>()

        var tmName: String? = null
        var drrCode: String? = null
        var recipientName: String? = null
        var recipientFcn: String? = null
        var materialName: String? = null
        var approvedQty: String? = null
        var unit: String? = null

        val keys = json.keys().asSequence().toList().sorted()
        for (key in keys) {
            val leafKey = leaf(key)
            val value = json.opt(key)
            val displayValue = formatValue(value)
            val label = humanizeLabel(leafKey)

            if (isTechnical(key)) {
                technicalInfo.add(KoboField(label, displayValue))
                continue
            }

            when {
                // TM Name (the DRR project's original registrant) must be checked before the
                // generic material/name rules, since "tm_name" would otherwise fall through.
                matchesAll(leafKey, "tm", "name") -> {
                    if (tmName == null) tmName = displayValue
                    // placed into projectInfo below, after DRR Code, in the fixed order requested
                }
                matchesAny(leafKey, "drr") -> {
                    if (drrCode == null) drrCode = displayValue
                }
                matchesAny(leafKey, "material") && !matchesAny(leafKey, "qty", "quantity", "unit") -> {
                    materialInfo.add(KoboField("Material Name", displayValue))
                    if (materialName == null) materialName = displayValue
                }
                leafKey == "unit" || leafKey.endsWith("_unit") || leafKey.endsWith("unit") -> {
                    materialInfo.add(KoboField("Unit", displayValue))
                    if (unit == null) unit = displayValue
                }
                matchesAll(leafKey, "approved") && matchesAny(leafKey, "qty", "quantity") -> {
                    materialInfo.add(KoboField("Approved Quantity", displayValue))
                    if (approvedQty == null) approvedQty = displayValue
                }
                matchesAll(leafKey, "dispatch") && matchesAny(leafKey, "qty", "quantity") ->
                    materialInfo.add(KoboField("Dispatched Quantity", displayValue))
                matchesAll(leafKey, "remain") && matchesAny(leafKey, "qty", "quantity") ->
                    materialInfo.add(KoboField("Remaining Quantity", displayValue))
                matchesAll(leafKey, "recipient", "fcn") || leafKey == "fcn" || matchesAny(leafKey, "fcn") -> {
                    recipientInfo.add(KoboField("Recipient FCN", displayValue))
                    if (recipientFcn == null) recipientFcn = displayValue
                }
                matchesAll(leafKey, "recipient", "name") || matchesAll(leafKey, "cfw", "name") || matchesAll(leafKey, "worker", "name") -> {
                    recipientInfo.add(KoboField("Recipient Name", displayValue))
                    if (recipientName == null) recipientName = displayValue
                }
                matchesAny(leafKey, "submitted_by", "submittedby") ->
                    submissionInfo.add(KoboField("Submitted By", displayValue))
                leafKey == "status" || leafKey.endsWith("_status") ->
                    submissionInfo.add(KoboField("Status", displayValue))
                else ->
                    additionalInfo.add(KoboField(label, displayValue))
            }
        }

        // Project Details: TM Name first, then DRR Code — fixed order regardless of the
        // order fields happen to appear in the submission's JSON.
        if (tmName != null) projectInfo.add(KoboField("TM Name", tmName))
        if (drrCode != null) projectInfo.add(KoboField("DRR Code", drrCode))

        // Kobo always provides these regardless of form — surface them consistently.
        val submissionIdVal = json.optLong("_id", -1L)
        val submissionTimeRaw = json.optString("_submission_time", "")
        val submittedByAccount = json.optString("_submitted_by", "").ifBlank { null }
        val statusFromKobo = json.optJSONObject("_validation_status")?.optString("label", "")?.ifBlank { null }

        // A form can also have its own "submitted by" / "status" style field (e.g. a role or
        // designation question). When it does, prefer that value over Kobo's raw account
        // username so the same label doesn't appear twice with two different values.
        val formSubmittedBy = submissionInfo.firstOrNull { it.label == "Submitted By" }
        val formStatus = submissionInfo.firstOrNull { it.label == "Status" }
        val remainingFormFields = submissionInfo.filterNot { it === formSubmittedBy || it === formStatus }

        val resolvedSubmittedBy = formSubmittedBy?.value ?: submittedByAccount
        val resolvedStatus = formStatus?.value ?: statusFromKobo

        val finalSubmissionInfo = mutableListOf<KoboField>()
        if (submissionIdVal >= 0) finalSubmissionInfo.add(KoboField("Submission ID", submissionIdVal.toString()))
        if (submissionTimeRaw.isNotBlank()) {
            finalSubmissionInfo.add(KoboField("Submission Date", KoboDateUtils.formatDateOnly(submissionTimeRaw)))
        }
        if (resolvedSubmittedBy != null) finalSubmissionInfo.add(KoboField("Submitted By", resolvedSubmittedBy))
        if (resolvedStatus != null) finalSubmissionInfo.add(KoboField("Status", resolvedStatus))
        finalSubmissionInfo.addAll(remainingFormFields)

        val quantityText = when {
            approvedQty != null && unit != null -> "$approvedQty $unit"
            approvedQty != null -> approvedQty
            else -> null
        }

        return KoboCategorizedSubmission(
            tmName = tmName,
            drrCode = drrCode,
            recipientName = recipientName,
            recipientFcn = recipientFcn,
            materialName = materialName,
            quantityText = quantityText,
            submittedBy = resolvedSubmittedBy,
            projectInfo = projectInfo,
            recipientInfo = recipientInfo,
            materialInfo = materialInfo,
            submissionInfo = finalSubmissionInfo,
            additionalInfo = additionalInfo,
            technicalInfo = technicalInfo
        )
    }

    private fun formatValue(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "—"
        is Boolean -> if (value) "Yes" else "No"
        is JSONArray -> formatArray(value)
        is JSONObject -> formatObject(value)
        else -> value.toString().ifBlank { "—" }
    }

    /** Arrays of plain values become a short comma list; arrays of groups become a count. */
    private fun formatArray(array: JSONArray): String {
        if (array.length() == 0) return "None"
        val items = (0 until array.length()).map { array.opt(it) }
        val allPrimitive = items.all { it !is JSONObject && it !is JSONArray }
        return if (allPrimitive) {
            items.joinToString(", ") { formatValue(it) }
        } else {
            "${array.length()} ${if (array.length() == 1) "entry" else "entries"}"
        }
    }

    /** Nested groups are summarized inline as "Label: value, Label: value" rather than raw JSON. */
    private fun formatObject(obj: JSONObject): String {
        val keys = obj.keys().asSequence().toList()
        if (keys.isEmpty()) return "—"
        return keys.joinToString(", ") { k -> "${humanizeLabel(leaf(k))}: ${formatValue(obj.opt(k))}" }
    }

    private fun humanizeLabel(leafKey: String): String {
        val spaced = leafKey
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
            .trim()
        if (spaced.isBlank()) return leafKey
        return spaced.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
    }
}
