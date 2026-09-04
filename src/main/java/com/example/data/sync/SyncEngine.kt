package com.example.data.sync

import com.example.data.dao.CfwDao
import com.example.data.model.DispatchRecord
import com.example.data.model.SyncRowData
import com.example.data.preferences.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class SyncEngine(
    private val cfwDao: CfwDao,
    private val userPreferences: UserPreferences
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun syncDispatch(dispatch: DispatchRecord): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val request = cfwDao.getRequestById(dispatch.requestId)
                ?: return@withContext Result.failure(Exception("Material request not found"))
            val beneficiary = cfwDao.getBeneficiaryById(request.beneficiaryId)
                ?: return@withContext Result.failure(Exception("CFW Worker not found"))
            val dispatchMaterials = cfwDao.getDispatchMaterialsForDispatch(dispatch.id)

            if (dispatchMaterials.isEmpty()) {
                return@withContext Result.failure(Exception("No materials in dispatch record"))
            }

            // Build detailed sync row items
            val syncRows = mutableListOf<SyncRowData>()
            val timestamp = "${dispatch.dispatchDate} ${dispatch.time}".trim()

            for (dm in dispatchMaterials) {
                val reqMat = cfwDao.getRequestedMaterialById(dm.materialId) ?: continue
                val totalDispatchedForMat = cfwDao.getTotalDispatchedForMaterial(reqMat.id)
                val remainingQty = maxOf(0.0, reqMat.approvedQuantity - totalDispatchedForMat)

                syncRows.add(
                    SyncRowData(
                        timestamp = timestamp,
                        drrCode = beneficiary.drrCode,
                        cfwName = beneficiary.cfwName,
                        fcnNumber = beneficiary.fcnNumber,
                        recipientName = dispatch.recipientName,
                        recipientFcn = dispatch.recipientFcn,
                        projectId = request.projectId,
                        materialName = reqMat.materialName,
                        approvedQuantity = reqMat.approvedQuantity,
                        dispatchedQuantity = dm.dispatchedQuantity,
                        remainingQuantity = remainingQty,
                        unit = reqMat.unit,
                        dispatchVisitNumber = dispatch.visitNumber,
                        collectorName = dispatch.collectorName,
                        syncStatus = "SYNCED"
                    )
                )
            }

            // 1. Google Sheets Webhook
            val sheetsWebhook = userPreferences.googleWebhookUrl.value
            var sheetsSuccess = true
            var lastError = ""

            if (sheetsWebhook.isNotBlank() && !sheetsWebhook.contains("demo")) {
                try {
                    val sheetsPayload = buildGoogleSheetsPayload(syncRows)
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val body = sheetsPayload.toString().toRequestBody(mediaType)
                    val httpRequest = Request.Builder()
                        .url(sheetsWebhook)
                        .post(body)
                        .build()

                    client.newCall(httpRequest).execute().use { response ->
                        if (!response.isSuccessful) {
                            sheetsSuccess = false
                            lastError = "Sheets HTTP ${response.code}"
                        }
                    }
                } catch (e: Exception) {
                    sheetsSuccess = false
                    lastError = "Sheets error: ${e.message}"
                }
            }

            // 2. KoboToolbox — OpenRosa XML submission
            val koboUsername = userPreferences.koboUsername.value
            val koboBaseUrl = userPreferences.koboServerUrl.value.trimEnd('/')
            val koboSubmissionUrl = "$koboBaseUrl/$koboUsername/submission"
            val koboToken = userPreferences.koboApiToken.value
            var koboSuccess = true

            if (koboToken.isNotBlank() && !koboToken.contains("demo") && koboUsername.isNotBlank()) {
                for (row in syncRows) {
                    try {
                        val instanceId = "uuid:${UUID.randomUUID()}"
                        val xml = buildKoboSubmissionXml(dispatch, beneficiary, row, instanceId)
                        val xmlMediaType = "text/xml".toMediaType()
                        val xmlBody = xml.toRequestBody(xmlMediaType)

                        val multipartBody = MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart(
                                "xml_submission_file",
                                "submission_${dispatch.id}_${row.materialName}.xml",
                                xmlBody
                            )
                            .build()

                        val httpRequest = Request.Builder()
                            .url(koboSubmissionUrl)
                            .header("Authorization", "Token $koboToken")
                            .post(multipartBody)
                            .build()

                        client.newCall(httpRequest).execute().use { response ->
                            // OpenRosa submission success codes are 201 (Created) or 202 (Accepted)
                            if (!response.isSuccessful && response.code != 201 && response.code != 202) {
                                koboSuccess = false
                                val err = "Kobo HTTP ${response.code} (${row.materialName})"
                                lastError = if (lastError.isEmpty()) err else "$lastError; $err"
                            }
                        }
                    } catch (e: Exception) {
                        koboSuccess = false
                        val err = "Kobo error (${row.materialName}): ${e.message}"
                        lastError = if (lastError.isEmpty()) err else "$lastError; $err"
                    }
                }
            }

            // Demo / simulation mode fallback
            if (sheetsWebhook.contains("demo") || koboToken.contains("demo") || (sheetsWebhook.isBlank() && koboToken.isBlank())) {
                kotlinx.coroutines.delay(600)
                cfwDao.updateDispatchSyncStatus(dispatch.id, "SYNCED", null)
                return@withContext Result.success(true)
            }

            if (sheetsSuccess && koboSuccess) {
                cfwDao.updateDispatchSyncStatus(dispatch.id, "SYNCED", null)
                Result.success(true)
            } else {
                cfwDao.updateDispatchSyncStatus(dispatch.id, "FAILED", lastError.ifBlank { "Sync failed" })
                Result.failure(Exception(lastError))
            }
        } catch (e: Exception) {
            cfwDao.updateDispatchSyncStatus(dispatch.id, "FAILED", e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    private fun buildGoogleSheetsPayload(syncRows: List<SyncRowData>): JSONObject {
        val root = JSONObject()
        val rowsArray = JSONArray()

        syncRows.forEach { row ->
            val obj = JSONObject().apply {
                put("Timestamp", row.timestamp)
                put("DRR Code", row.drrCode)
                put("CFW Name (DRR Registrant)", row.cfwName)
                put("FCN (DRR Registrant)", row.fcnNumber)
                put("Recipient Name", row.recipientName)
                put("Recipient FCN", row.recipientFcn)
                put("Project ID", row.projectId)
                put("Material Name", row.materialName)
                put("Approved Quantity", row.approvedQuantity)
                put("Dispatched Quantity", row.dispatchedQuantity)
                put("Remaining Quantity", row.remainingQuantity)
                put("Unit", row.unit)
                put("Dispatch Visit Number", row.dispatchVisitNumber)
                put("Collector Name", row.collectorName)
                put("Sync Status", row.syncStatus)
            }
            rowsArray.put(obj)
        }

        root.put("spreadsheetId", userPreferences.googleSheetsId.value)
        root.put("rows", rowsArray)
        return root
    }

    private fun buildKoboSubmissionXml(
        dispatch: DispatchRecord,
        beneficiary: com.example.data.model.CfwBeneficiary,
        row: SyncRowData,
        instanceId: String
    ): String {
        val formId = userPreferences.koboFormId.value
        val sb = StringBuilder()
        sb.append("<?xml version='1.0' ?>")
        sb.append("<$formId id=\"$formId\">")
        sb.append("<date>${escapeXml(dispatch.dispatchDate)}</date>")
        sb.append("<drr_code>${escapeXml(beneficiary.drrCode)}</drr_code>")
        sb.append("<tm_name>${escapeXml(beneficiary.cfwName)}</tm_name>")
        // CFW_Worker_Name / FCN_Number in the Kobo form represent whoever actually received
        // materials on THIS visit — not the DRR's original registrant, since one DRR project
        // now dispatches to different people over time.
        sb.append("<cfw_name>${escapeXml(dispatch.recipientName)}</cfw_name>")
        sb.append("<fcn>${escapeXml(dispatch.recipientFcn)}</fcn>")
        sb.append("<approved_qty>${row.approvedQuantity}</approved_qty>")
        sb.append("<material_name>${escapeXml(row.materialName)}</material_name>")
        sb.append("<unit>${escapeXml(row.unit)}</unit>")
        sb.append("<dispatched_qty>${row.dispatchedQuantity}</dispatched_qty>")
        sb.append("<remaining_qty>${row.remainingQuantity}</remaining_qty>")
        sb.append("<submitted_by>${escapeXml(dispatch.collectorName)}</submitted_by>")
        sb.append("<meta><instanceID>$instanceId</instanceID></meta>")
        sb.append("</$formId>")
        return sb.toString()
    }

    private fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    fun generateCsvExport(syncRows: List<SyncRowData>): String {
        val sb = java.lang.StringBuilder()
        sb.append("Timestamp,DRR Code,CFW Name (DRR Registrant),FCN (DRR Registrant),Recipient Name,Recipient FCN,Project ID,Material Name,Approved Quantity,Dispatched Quantity,Remaining Quantity,Unit,Dispatch Visit Number,Collector Name,Sync Status\n")

        syncRows.forEach { row ->
            val line = listOf(
                escapeCsv(row.timestamp),
                escapeCsv(row.drrCode),
                escapeCsv(row.cfwName),
                escapeCsv(row.fcnNumber),
                escapeCsv(row.recipientName),
                escapeCsv(row.recipientFcn),
                escapeCsv(row.projectId),
                escapeCsv(row.materialName),
                row.approvedQuantity.toString(),
                row.dispatchedQuantity.toString(),
                row.remainingQuantity.toString(),
                escapeCsv(row.unit),
                row.dispatchVisitNumber.toString(),
                escapeCsv(row.collectorName),
                row.syncStatus
            ).joinToString(",")
            sb.append(line).append("\n")
        }
        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value
    }
}
