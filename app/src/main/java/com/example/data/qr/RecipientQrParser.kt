package com.example.data.qr

import org.json.JSONObject

data class RecipientQrResult(
    val name: String,
    val fcn: String
)

object RecipientQrParser {

    fun parse(rawValue: String): RecipientQrResult? {
        val raw = rawValue.trim()

        if (raw.isBlank()) return null

        // 1. JSON
        parseJson(raw)?.let { return it }

        // 2. Labelled text
        parseLabelled(raw)?.let { return it }

        // 3. Delimited text
        parseDelimited(raw)?.let { return it }

        // 4. Simple fallback:
        //    If the QR contains two or more lines/parts, find an FCN
        //    and use the nearest text value as the recipient name.
        parseFlexible(raw)?.let { return it }

        return null
    }

    private fun parseJson(raw: String): RecipientQrResult? {
        if (!raw.trimStart().startsWith("{")) return null

        return runCatching {
            val json = JSONObject(raw)

            val name = firstJsonValue(
                json,
                "name",
                "recipientName",
                "recipient_name",
                "recipient",
                "Recipient Name"
            )

            val fcn = firstJsonValue(
                json,
                "fcn",
                "fcnNumber",
                "recipientFcn",
                "recipient_fcn",
                "recipientFCN",
                "FCN",
                "FCN Number",
                "Recipient FCN"
            )

            if (name.isNullOrBlank() || fcn.isNullOrBlank()) {
                null
            } else {
                RecipientQrResult(
                    name = name.trim(),
                    fcn = fcn.trim()
                )
            }
        }.getOrNull()
    }

    private fun parseLabelled(raw: String): RecipientQrResult? {

        val nameRegex = Regex(
            pattern = "(?im)" +
                "(?:recipient\\s*name|beneficiary\\s*name|name)" +
                "\\s*[:=]\\s*([^\\r\\n;|,]+)"
        )

        val fcnRegex = Regex(
            pattern = "(?im)" +
                "(?:recipient\\s*fcn(?:\\s*number)?|" +
                "beneficiary\\s*fcn(?:\\s*number)?|" +
                "fcn(?:\\s*number)?)" +
                "\\s*[:=]\\s*([A-Za-z0-9_-]+)"
        )

        val name = nameRegex
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()

        val fcn = fcnRegex
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()

        return if (
            !name.isNullOrBlank() &&
            !fcn.isNullOrBlank()
        ) {
            RecipientQrResult(
                name = name,
                fcn = fcn
            )
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

        val fcnCandidates = parts.filter { isPossibleFcn(it) }

        if (fcnCandidates.isEmpty()) return null

        val fcn = fcnCandidates.first()
        val fcnIndex = parts.indexOf(fcn)

        val nearby = listOfNotNull(
            parts.getOrNull(fcnIndex - 1),
            parts.getOrNull(fcnIndex + 1)
        )

        val name = nearby.firstOrNull {
            isPossibleName(it)
        }

        return if (!name.isNullOrBlank()) {
            RecipientQrResult(
                name = name,
                fcn = fcn
            )
        } else {
            null
        }
    }

    private fun parseFlexible(raw: String): RecipientQrResult? {

        val parts = raw
            .split(
                ';',
                '|',
                '\n',
                '\r',
                ',',
                '\t'
            )
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (parts.size < 2) return null

        var fcnIndex = -1
        var fcnValue: String? = null

        for (index in parts.indices) {
            val cleaned = parts[index]
                .replace(
                    Regex(
                        "(?i)^(recipient\\s*)?fcn(\\s*number)?\\s*[:=]\\s*"
                    ),
                    ""
                )
                .trim()

            if (isPossibleFcn(cleaned)) {
                fcnIndex = index
                fcnValue = cleaned
                break
            }
        }

        if (fcnIndex == -1 || fcnValue.isNullOrBlank()) {
            return null
        }

        val possibleNames = listOfNotNull(
            parts.getOrNull(fcnIndex - 1),
            parts.getOrNull(fcnIndex + 1),
            parts.getOrNull(fcnIndex - 2),
            parts.getOrNull(fcnIndex + 2)
        )

        val name = possibleNames.firstOrNull {
            isPossibleName(it)
        }

        return if (!name.isNullOrBlank()) {
            RecipientQrResult(
                name = name,
                fcn = fcnValue
            )
        } else {
            null
        }
    }

    private fun isPossibleFcn(value: String): Boolean {
        val cleaned = value
            .trim()
            .replace(
                Regex(
                    "(?i)^(recipient\\s*)?fcn(\\s*number)?\\s*[:=]\\s*"
                ),
                ""
            )
            .trim()

        // Normal FCN: 4–12 digits
        if (cleaned.matches(Regex("\\d{4,12}"))) {
            return true
        }

        // Also allow FCNs containing letters, hyphens or underscores.
        // This makes the scanner compatible with existing identifiers
        // such as FCN-12345 or ABC12345.
        return cleaned.matches(
            Regex("[A-Za-z0-9_-]{4,20}")
        ) && cleaned.any { it.isDigit() }
    }

    private fun isPossibleName(value: String): Boolean {

        val candidate = value
            .trim()
            .replace(
                Regex(
                    "(?i)^(recipient\\s*name|beneficiary\\s*name|name)\\s*[:=]\\s*"
                ),
                ""
            )
            .trim()

        if (candidate.length < 2) return false

        if (candidate.matches(Regex("\\d+"))) {
            return false
        }

        if (candidate.contains("=")) return false
        if (candidate.contains(":")) return false

        return true
    }

    private fun firstJsonValue(
        json: JSONObject,
        vararg keys: String
    ): String? {

        keys.forEach { key ->

            if (json.has(key) && !json.isNull(key)) {

                val value = json
                    .optString(key)
                    .trim()

                if (value.isNotBlank()) {
                    return value
                }
            }
        }

        return null
    }
}
