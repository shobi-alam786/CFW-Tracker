package com.example.data.qr

import org.json.JSONObject

/**
 * Extracts only Recipient Name and FCN from a scanned QR value.
 *
 * This parser is intentionally isolated from the existing dispatch/Kobo logic.
 * It accepts common labelled/JSON QR payloads and semicolon-separated payloads
 * where a numeric FCN can be identified next to a name.
 */
data class RecipientQrResult(
    val name: String,
    val fcn: String
)

object RecipientQrParser {

    fun parse(rawValue: String): RecipientQrResult? {
        val raw = rawValue.trim()
        if (raw.isBlank()) return null

        parseJson(raw)?.let { return it }
        parseLabelled(raw)?.let { return it }
        parseDelimited(raw)?.let { return it }

        return null
    }

    private fun parseJson(raw: String): RecipientQrResult? {
        if (!raw.trimStart().startsWith("{")) return null

        return runCatching {
            val json = JSONObject(raw)
            val name = firstJsonValue(json, "name", "recipientName", "recipient_name")
            val fcn = firstJsonValue(json, "fcn", "fcnNumber", "recipientFcn", "recipient_fcn")

            if (name.isNullOrBlank() || fcn.isNullOrBlank()) null
            else RecipientQrResult(name.trim(), fcn.trim())
        }.getOrNull()
    }

    private fun parseLabelled(raw: String): RecipientQrResult? {
        val nameRegex = Regex(
            "(?im)(?:recipient\\s*name|name)\\s*[:=]\\s*([^\\r\\n;|]+)"
        )
        val fcnRegex = Regex(
            "(?im)(?:recipient\\s*fcn(?:\\s*number)?|fcn(?:\\s*number)?)\\s*[:=]\\s*([A-Za-z0-9-]+)"
        )

        val name = nameRegex.find(raw)?.groupValues?.getOrNull(1)?.trim()
        val fcn = fcnRegex.find(raw)?.groupValues?.getOrNull(1)?.trim()

        return if (!name.isNullOrBlank() && !fcn.isNullOrBlank()) {
            RecipientQrResult(name, fcn)
        } else {
            null
        }
    }

    private fun parseDelimited(raw: String): RecipientQrResult? {
        val parts = raw
            .split(';', '|', '\n', '\r', ',')
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (parts.size < 2) return null

        // Look for a single plausible FCN (4–12 digits) and a nearby non-numeric name.
        val fcnCandidates = parts.filter { it.matches(Regex("\\d{4,12}")) }
        if (fcnCandidates.size != 1) return null

        val fcn = fcnCandidates.single()
        val fcnIndex = parts.indexOf(fcn)

        val nearby = listOfNotNull(
            parts.getOrNull(fcnIndex - 1),
            parts.getOrNull(fcnIndex + 1)
        )

        val name = nearby.firstOrNull { candidate ->
            candidate.length >= 2 &&
                !candidate.matches(Regex("\\d+")) &&
                !candidate.contains("=") &&
                !candidate.contains(":")
        } ?: return null

        return RecipientQrResult(name.trim(), fcn.trim())
    }

    private fun firstJsonValue(json: JSONObject, vararg keys: String): String? {
        keys.forEach { key ->
            if (json.has(key) && !json.isNull(key)) {
                val value = json.optString(key).trim()
                if (value.isNotBlank()) return value
            }
        }
        return null
    }
}
