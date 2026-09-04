package com.example.data.kobo

import com.example.data.dao.CfwDao
import com.example.data.model.CfwBeneficiary
import com.example.data.model.DispatchMaterial
import com.example.data.model.DispatchRecord
import com.example.data.model.MaterialRequest
import com.example.data.model.RequestedMaterial
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Converts downloaded [KoboSubmission] records into the app's own local tables
 * (CfwBeneficiary / MaterialRequest / RequestedMaterial / DispatchRecord / DispatchMaterial)
 * so that Kobo data feeds into Reports, Dashboard, and CSV export the same way data entered
 * through "New Entry" does.
 *
 * Without this step, syncing on the Kobo Data screen only fills the kobo_submission cache
 * (used for browsing raw submissions) — it never reaches the tables ReportsScreen reads from.
 *
 * One [MaterialRequest] is created per DRR Code per calendar day, matching the same rule the
 * "New Entry" flow already uses (see CfwDao.getRequestForBeneficiaryOnDate). Re-running the
 * import for a day that was already imported wipes and rebuilds that day's materials/dispatches
 * from the latest Kobo data, so re-syncing never creates duplicate rows.
 */
class KoboImportEngine(private val cfwDao: CfwDao) {

    /** Groups all cached submissions by DRR Code + calendar day, then imports each group. */
    suspend fun importAll(submissions: List<KoboSubmission>) {
        data class DayKey(val drrCode: String, val day: String)

        val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val groups = LinkedHashMap<DayKey, MutableList<KoboSubmission>>()

        for (sub in submissions) {
            val classified = KoboFieldClassifier.classify(sub.toJson())
            val drrCode = classified.drrCode?.trim()?.takeIf { it.isNotBlank() } ?: continue
            val day = KoboDateUtils.parse(sub.submissionTime)?.let { dayFormat.format(it) }
                ?: sub.submissionTime.take(10)
            groups.getOrPut(DayKey(drrCode, day)) { mutableListOf() }.add(sub)
        }

        for ((key, subsInGroup) in groups) {
            val representative = subsInGroup.first()
            val report = KoboVisitAggregator.build(representative, submissions)
            importDayReport(report, key.day)
        }
    }

    /** Imports one DRR's single-day report into the CFW tables. */
    private suspend fun importDayReport(report: KoboDrrDayReport, dispatchDate: String) {
        val drrCode = report.drrCode ?: return
        if (report.approvedMaterials.isEmpty() && report.visits.isEmpty()) return

        // 1. Beneficiary — one row per DRR Code, updated in place on re-import.
        val tmName = report.tmName?.takeIf { it.isNotBlank() } ?: drrCode
        val existingBeneficiary = cfwDao.getBeneficiaryByDrrCode(drrCode)
        val beneficiaryId = if (existingBeneficiary != null) {
            cfwDao.updateBeneficiary(
                existingBeneficiary.copy(cfwName = tmName, updatedAt = System.currentTimeMillis())
            )
            existingBeneficiary.id
        } else {
            cfwDao.insertBeneficiary(
                CfwBeneficiary(drrCode = drrCode, cfwName = tmName, fcnNumber = "")
            )
        }

        // 2. Material request for this DRR on this specific day.
        val existingRequest = cfwDao.getRequestForBeneficiaryOnDate(beneficiaryId, dispatchDate)
        val requestId = if (existingRequest != null) {
            // Re-importing this day: clear old materials/dispatches first so re-syncing
            // never piles up duplicate rows — rebuild fresh from the latest Kobo data.
            cfwDao.deleteDispatchMaterialsForRequest(existingRequest.id)
            cfwDao.deleteDispatchesForRequest(existingRequest.id)
            cfwDao.deleteRequestedMaterialsForRequest(existingRequest.id)
            existingRequest.id
        } else {
            cfwDao.insertMaterialRequest(
                MaterialRequest(beneficiaryId = beneficiaryId, requestDate = dispatchDate)
            )
        }

        // 3. Approved materials for this request.
        if (report.approvedMaterials.isNotEmpty()) {
            cfwDao.insertRequestedMaterials(
                report.approvedMaterials.map { row ->
                    RequestedMaterial(
                        requestId = requestId,
                        materialName = row.materialName,
                        approvedQuantity = row.approvedQty?.toDoubleOrNull() ?: 0.0,
                        unit = row.unit
                    )
                }
            )
        }

        // Need each RequestedMaterial's generated id to link DispatchMaterial rows below.
        val materialIdByName = cfwDao.getRequestedMaterialsForRequest(requestId)
            .associateBy { it.materialName.lowercase(Locale.getDefault()) }

        // 4. One DispatchRecord + its DispatchMaterial rows per recipient visit.
        for (visit in report.visits) {
            val dispatchId = cfwDao.insertDispatchRecord(
                DispatchRecord(
                    requestId = requestId,
                    dispatchDate = dispatchDate,
                    collectorName = "Kobo Import",
                    recipientName = visit.recipientName,
                    recipientFcn = visit.recipientFcn,
                    syncStatus = "SYNCED"
                )
            )

            val dispatchMaterials = visit.materials.mapNotNull { row ->
                val reqMat = materialIdByName[row.materialName.lowercase(Locale.getDefault())]
                    ?: return@mapNotNull null
                DispatchMaterial(
                    dispatchId = dispatchId,
                    materialId = reqMat.id,
                    dispatchedQuantity = row.dispatchQty?.toDoubleOrNull() ?: 0.0
                )
            }
            if (dispatchMaterials.isNotEmpty()) {
                cfwDao.insertDispatchMaterials(dispatchMaterials)
            }
        }
    }
}
