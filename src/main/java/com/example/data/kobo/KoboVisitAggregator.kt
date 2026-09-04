package com.example.data.kobo

/** One row in a materials table — used for "Approved", "Received", and "Total Summary" tables. */
data class KoboMaterialRow(
    val materialName: String,
    val unit: String,
    val approvedQty: String? = null,
    val dispatchQty: String? = null,
    val remainingQty: String? = null
)

/** One recipient's materials for this DRR daily request. */
data class KoboVisitEntry(
    val recipientName: String,
    val recipientFcn: String,
    val materials: List<KoboMaterialRow>
)

data class KoboDrrDayReport(
    val tmName: String?,
    val drrCode: String?,
    val approvedMaterials: List<KoboMaterialRow>,
    val visits: List<KoboVisitEntry>,
    val totalSummary: List<KoboMaterialRow>
)

/**
 * Builds the report for the ONE daily request represented by the tapped submission.
 *
 * A DRR can legitimately have a new request on every project day, for example:
 *
 *   DRR C013 → Aug 30 Request #1 → Brick approved 500
 *          → Aug 31 Request #2 → Brick approved 500
 *
 * Therefore, submissions from different days must NEVER be combined. A single Kobo form
 * submission carries one material for one recipient, so this method reassembles all submissions
 * sharing the same DRR AND the same local calendar day as the tapped submission:
 *
 * - "Approved Materials": distinct materials approved for THIS daily request.
 * - "Recipients and Materials Received": every recipient who received something in THIS daily
 *   request, with only their dispatched quantities.
 * - "Total Summary": per-material TM approved quantity, total dispatch across all recipients in
 *   THIS daily request, and remaining = approved - total dispatch.
 *
 * This only reads already-downloaded local data; it never triggers a new sync.
 */
object KoboVisitAggregator {

    fun build(current: KoboSubmission, allForAsset: List<KoboSubmission>): KoboDrrDayReport {
        val currentClassified = KoboFieldClassifier.classify(current.toJson())
        val tmName = currentClassified.tmName
        val drr = currentClassified.drrCode

        if (drr == null) {
            return KoboDrrDayReport(
                tmName = tmName,
                drrCode = null,
                approvedMaterials = emptyList(),
                visits = emptyList(),
                totalSummary = emptyList()
            )
        }

        data class Classified(val sub: KoboSubmission, val c: KoboCategorizedSubmission)

        val projectSubs = allForAsset
            .map { Classified(it, KoboFieldClassifier.classify(it.toJson())) }
            .filter { it.c.drrCode == drr }

        // IMPORTANT: a DRR can have a separate request on each project day. The selected
        // submission identifies which daily request the user wants to inspect. Everything
        // below therefore uses only submissions from the same DRR + same local calendar day.
        val daySubs = projectSubs
            .filter { KoboDateUtils.isSameDay(it.sub.submissionTime, current.submissionTime) }
            .sortedBy { KoboDateUtils.parse(it.sub.submissionTime)?.time ?: 0L }

        fun unitOf(c: KoboCategorizedSubmission): String =
            c.materialInfo.firstOrNull { it.label == "Unit" }?.value?.takeIf { it != "—" } ?: ""

        fun fieldOf(c: KoboCategorizedSubmission, label: String): String? =
            c.materialInfo.firstOrNull { it.label == label }?.value?.takeIf { it != "—" }

        // --- Approved Materials: distinct materials for THIS daily request only ---
        val approvedMap = LinkedHashMap<String, KoboMaterialRow>()
        for (item in daySubs) {
            val name = item.c.materialName ?: continue
            if (!approvedMap.containsKey(name)) {
                approvedMap[name] = KoboMaterialRow(
                    materialName = name,
                    unit = unitOf(item.c),
                    approvedQty = cleanNumber(fieldOf(item.c, "Approved Quantity"))
                )
            }
        }

        // --- Total Summary: sum dispatched qty across ALL recipients in THIS daily request ---
        val dispatchedSum = LinkedHashMap<String, Double>()
        for (item in daySubs) {
            val name = item.c.materialName ?: continue
            val dispatched = fieldOf(item.c, "Dispatched Quantity")?.toDoubleOrNull() ?: 0.0
            dispatchedSum[name] = (dispatchedSum[name] ?: 0.0) + dispatched
        }
        val totalSummary = approvedMap.values.map { row ->
            val totalDispatched = dispatchedSum[row.materialName] ?: 0.0
            val approvedVal = row.approvedQty?.toDoubleOrNull() ?: 0.0
            row.copy(
                dispatchQty = cleanNumber(totalDispatched.toString()),
                remainingQty = cleanNumber(maxOf(0.0, approvedVal - totalDispatched).toString())
            )
        }

        // --- Recipients and Materials Received: everyone in THIS daily request ---

        data class RecipientKey(val name: String, val fcn: String)
        val byRecipient = LinkedHashMap<RecipientKey, MutableList<Classified>>()
        for (item in daySubs) {
            val name = item.c.recipientName ?: continue
            val fcn = item.c.recipientFcn ?: ""
            byRecipient.getOrPut(RecipientKey(name, fcn)) { mutableListOf() }.add(item)
        }

        val visits = byRecipient.map { (key, items) ->
            KoboVisitEntry(
                recipientName = key.name,
                recipientFcn = key.fcn,
                materials = items.mapNotNull { item ->
                    val name = item.c.materialName ?: return@mapNotNull null
                    KoboMaterialRow(
                        materialName = name,
                        unit = unitOf(item.c),
                        dispatchQty = cleanNumber(fieldOf(item.c, "Dispatched Quantity"))
                    )
                }
            )
        }

        return KoboDrrDayReport(
            tmName = tmName,
            drrCode = drr,
            approvedMaterials = approvedMap.values.toList(),
            visits = visits,
            totalSummary = totalSummary
        )
    }

    /** Strips a pointless ".0" from whole numbers (Kobo/our own XML always sends Doubles). */
    private fun cleanNumber(raw: String?): String? {
        if (raw == null) return null
        val value = raw.toDoubleOrNull() ?: return raw
        return if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    }
}
