package com.example.data.preferences

import android.content.Context
import androidx.core.content.edit
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("cfw_prefs", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(prefs.getString("theme", "SYSTEM") ?: "SYSTEM")
    val theme: StateFlow<String> = _theme.asStateFlow()

    private val _collectorName = MutableStateFlow(prefs.getString("collector_name", "Shobi Alam") ?: "Shobi Alam")
    val collectorName: StateFlow<String> = _collectorName.asStateFlow()

    private val _collectorOrg = MutableStateFlow(prefs.getString("collector_org", "") ?: "")
    val collectorOrg: StateFlow<String> = _collectorOrg.asStateFlow()

    private val _collectorPhone = MutableStateFlow(prefs.getString("collector_phone", "+88017") ?: "+88017")
    val collectorPhone: StateFlow<String> = _collectorPhone.asStateFlow()

    private val _collectorUserId = MutableStateFlow(prefs.getString("collector_user_id", "") ?: "")
    val collectorUserId: StateFlow<String> = _collectorUserId.asStateFlow()

    private val _googleSheetsId = MutableStateFlow(prefs.getString("sheets_id", "") ?: "")
    val googleSheetsId: StateFlow<String> = _googleSheetsId.asStateFlow()

    private val _googleWebhookUrl = MutableStateFlow(prefs.getString("sheets_webhook", "") ?: "")
    val googleWebhookUrl: StateFlow<String> = _googleWebhookUrl.asStateFlow()

    private val _koboServerUrl = MutableStateFlow(prefs.getString("kobo_server", "https://kc.kobotoolbox.org") ?: "https://kc.kobotoolbox.org")
    val koboServerUrl: StateFlow<String> = _koboServerUrl.asStateFlow()

    private val _koboUsername = MutableStateFlow(prefs.getString("kobo_username", "") ?: "")
    val koboUsername: StateFlow<String> = _koboUsername.asStateFlow()

    private val _koboFormId = MutableStateFlow(
        prefs.getString("kobo_form_id", "")
            ?.takeUnless { it == "cfw_material_distribution_v1" }
            ?: ""
    )
    val koboFormId: StateFlow<String> = _koboFormId.asStateFlow()

    private val _koboApiToken = MutableStateFlow(
        prefs.getString("kobo_token", "")
            ?.takeUnless { it == "kobo_api_demo_token_12345" }
            ?: ""
    )
    val koboApiToken: StateFlow<String> = _koboApiToken.asStateFlow()

    private val _customMaterials = MutableStateFlow(prefs.getStringSet("custom_materials", emptySet()) ?: emptySet())
    val customMaterials: StateFlow<Set<String>> = _customMaterials.asStateFlow()

    private val _customUnits = MutableStateFlow(prefs.getStringSet("custom_units", emptySet()) ?: emptySet())
    val customUnits: StateFlow<Set<String>> = _customUnits.asStateFlow()

    fun addCustomMaterial(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotBlank()) {
            val updated = _customMaterials.value + trimmed
            prefs.edit {
                putStringSet("custom_materials", updated)
            }
            _customMaterials.value = updated
        }
    }

    fun addCustomUnit(unit: String) {
        val trimmed = unit.trim()
        if (trimmed.isNotBlank()) {
            val updated = _customUnits.value + trimmed
            prefs.edit {
                putStringSet("custom_units", updated)
            }
            _customUnits.value = updated
        }
    }

    fun setTheme(theme: String) {
        prefs.edit {
            putString("theme", theme)
        }
        _theme.value = theme
    }

    fun updateCollectorProfile(name: String, org: String, phone: String, userId: String) {
        prefs.edit {
            putString("collector_name", name)
            putString("collector_org", org)
            putString("collector_phone", phone)
            putString("collector_user_id", userId)
        }
        _collectorName.value = name
        _collectorOrg.value = org
        _collectorPhone.value = phone
        _collectorUserId.value = userId
    }

    fun updateGoogleSheetsConfig(sheetsId: String, webhookUrl: String) {
        prefs.edit {
            putString("sheets_id", sheetsId)
            putString("sheets_webhook", webhookUrl)
        }
        _googleSheetsId.value = sheetsId
        _googleWebhookUrl.value = webhookUrl
    }

    fun updateKoboConfig(serverUrl: String, username: String, formId: String, token: String) {
        prefs.edit {
            putString("kobo_server", serverUrl)
            putString("kobo_username", username)
            putString("kobo_form_id", formId)
            putString("kobo_token", token)
        }
        _koboServerUrl.value = serverUrl
        _koboUsername.value = username
        _koboFormId.value = formId
        _koboApiToken.value = token
    }
}
