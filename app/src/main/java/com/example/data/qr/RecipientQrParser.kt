package com.example.data.qr

data class RecipientQrResult(
    val name: String,
    val fcn: String
)

object RecipientQrParser {

    fun parse(rawValue: String): RecipientQrResult? {
        val raw = rawValue.trim()

        if (raw.isBlank()) {
            return null
        }

        // ---------------------------------------------------------
        // Exact Rohingya/recipient QR format:
        //
        // STJ-19C16172;
        // P57-00005892;
        // 600884;
        // Shobi Alam;
        // Male;
        // 01/01/2001;
        // Myanmar:...;
        // 1;
        // 1;
        // 1012
        //
        // FCN = field 3  -> index 2
        // Name = field 4 -> index 3
        // ---------------------------------------------------------

        val fields = raw
            .split(";")
            .map { it.trim() }

        if (fields.size >= 4) {

            val fcn = fields[2]
            val name = fields[3]

            // FCN in this QR format is numeric.
            if (
                fcn.matches(Regex("\\d{4,12}")) &&
                name.isNotBlank()
            ) {
                return RecipientQrResult(
                    name = name,
                    fcn = fcn
                )
            }
        }

        // ---------------------------------------------------------
        // JSON fallback
        // ---------------------------------------------------------

        parseJson(raw)?.let {
            return it
        }

        // ---------------------------------------------------------
        // Labelled text fallback
        // Example:
        //
        // Recipient Name: Shobi Alam
        // FCN: 600884
        // ---------------------------------------------------------

        parseLabelled(raw)?.let {
            return it
        }

        // ---------------------------------------------------------
        // Generic fallback
        // ---------------------------------------------------------

        parseGeneric(raw)?.let {
            return it
        }

        return null
    }

    private fun parseJson(raw: String): RecipientQrResult? {
        if (!raw.trimStart().startsWith("{")) {
            return null
        }

        return try {
            val json = org.json.JSONObject(raw)

            val nameKeys = listOf(
                "name",
                "recipientName",
                "recipient_name",
                "Recipient Name"
            )

            val fcnKeys = listOf(
                "fcn",
                "fcnNumber",
                "recipientFcn",
                "recipient_fcn",
                "recipientFCN",
                "FCN",
                "FCN Number",
                "Recipient FCN"
            )

            var name: String? = null
            var fcn: String? = null

            for (key in nameKeys) {
                if (json.has(key)) {
                    val value = json.optString(key).trim()

                    if (value.isNotBlank()) {
                        name = value
                        break
                    }
                }
            }

            for (key in fcnKeys) {
                if (json.has(key)) {
                    val value = json.optString(key).trim()

                    if (
                        value.matches(
                            Regex("\\d{4,12}")
                        )
                    ) {
                        fcn = value
                        break
                    }
                }
            }

            if (
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

        } catch (_: Exception) {
            null
        }
    }

    private fun parseLabelled(raw: String): RecipientQrResult? {

        val nameRegex = Regex(
            pattern = "(?im)^\\s*(?:recipient\\s+name|name)\\s*[:=]\\s*(.+?)\\s*$"
        )

        val fcnRegex = Regex(
            pattern = "(?im)^\\s*(?:recipient\\s+fcn(?:\\s+number)?|fcn(?:\\s+number)?)\\s*[:=]\\s*(\\d{4,12})\\s*$"
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

    private fun parseGeneric(raw: String): RecipientQrResult? {

        val parts = raw
            .split(
                ";",
                "|",
                "\n",
                "\r",
                ","
            )
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val fcnIndex = parts.indexOfFirst {
            it.matches(Regex("\\d{4,12}"))
        }

        if (fcnIndex < 0) {
            return null
        }

        val fcn = parts[fcnIndex]

        // Prefer a nearby text value as the name.
        val possibleNameIndexes = listOf(
            fcnIndex + 1,
            fcnIndex - 1
        )

        for (index in possibleNameIndexes) {

            if (index !in parts.indices) {
                continue
            }

            val candidate = parts[index]

            if (
                candidate.isNotBlank() &&
                !candidate.matches(Regex("\\d{4,12}")) &&
                !candidate.matches(Regex("(?i)male|female")) &&
                !candidate.matches(Regex("\\d{1,2}/\\d{1,2}/\\d{2,4}"))
            ) {
                return RecipientQrResult(
                    name = candidate,
                    fcn = fcn
                )
            }
        }

        return null
    }
}
