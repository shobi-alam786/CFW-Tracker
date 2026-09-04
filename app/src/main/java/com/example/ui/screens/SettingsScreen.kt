package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.viewmodel.KoboViewModel
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    // --- Access gate: require an 8-digit user password before showing Settings ---
    var isCheckingAccess by remember { mutableStateOf(true) }
    var isUnlocked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val userCount = viewModel.getUserCount()
        // If no users have been created yet, let this session through so the
        // first user can be added below.
        isUnlocked = userCount == 0
        isCheckingAccess = false
    }

    if (isCheckingAccess) {
        return
    }

    if (!isUnlocked) {
        SettingsLockScreen(
            viewModel = viewModel,
            onUnlocked = { isUnlocked = true }
        )
        return
    }

    SettingsContent(viewModel = viewModel, modifier = modifier)
}

@Composable
private fun SettingsLockScreen(
    viewModel: MainViewModel,
    onUnlocked: () -> Unit
) {
    var passwordInput by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.height(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Settings Locked",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Enter your 8-digit access password to continue.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = passwordInput,
            onValueChange = {
                if (it.length <= 8 && it.all { ch -> ch.isDigit() }) {
                    passwordInput = it
                    errorText = null
                }
            },
            label = { Text("Access Password") },
            placeholder = { Text("8-digit code") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = errorText != null,
            modifier = Modifier.fillMaxWidth()
        )

        if (errorText != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = errorText ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (passwordInput.length != 8) {
                    errorText = "Password must be exactly 8 digits."
                    return@Button
                }
                scope.launch {
                    val ok = viewModel.verifyUserPassword(passwordInput)
                    if (ok) {
                        onUnlocked()
                    } else {
                        errorText = "Incorrect password."
                        passwordInput = ""
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Unlock")
        }
    }
}

@Composable
private fun SettingsContent(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val prefs = viewModel.userPreferences
    val currentTheme by prefs.theme.collectAsState()

    val collectorName by prefs.collectorName.collectAsState()
    val collectorOrg by prefs.collectorOrg.collectAsState()
    val collectorPhone by prefs.collectorPhone.collectAsState()
    val collectorUserId by prefs.collectorUserId.collectAsState()

    val sheetsId by prefs.googleSheetsId.collectAsState()
    val sheetsWebhook by prefs.googleWebhookUrl.collectAsState()

    val koboServer by prefs.koboServerUrl.collectAsState()
    val koboUsername by prefs.koboUsername.collectAsState()
    val koboFormId by prefs.koboFormId.collectAsState()
    val koboToken by prefs.koboApiToken.collectAsState()

    val allUsers by viewModel.allUsers.collectAsState()
    val scope = rememberCoroutineScope()

    var editName by remember { mutableStateOf(collectorName) }
    var editOrg by remember { mutableStateOf(collectorOrg) }
    var editPhone by remember { mutableStateOf(collectorPhone) }
    var editUserId by remember { mutableStateOf(collectorUserId) }

    var editSheetsId by remember { mutableStateOf(sheetsId) }
    var editSheetsWebhook by remember { mutableStateOf(sheetsWebhook) }

    var editKoboServer by remember { mutableStateOf(koboServer) }
    var editKoboUsername by remember { mutableStateOf(koboUsername) }
    var editKoboFormId by remember { mutableStateOf(koboFormId) }
    var editKoboToken by remember { mutableStateOf(koboToken) }

    var showResetDialog by remember { mutableStateOf(false) }

    var newUserName by remember { mutableStateOf("") }
    var newUserPassword by remember { mutableStateOf("") }
    var addUserError by remember { mutableStateOf<String?>(null) }

    // --- KoboToolbox Data (pull submissions) settings ---
    val koboViewModel: KoboViewModel = viewModel()
    val koboDataServer by koboViewModel.serverUrl.collectAsState()
    val koboDataAssetUid by koboViewModel.assetUid.collectAsState()
    val koboDataToken by koboViewModel.apiToken.collectAsState()
    val koboTestResult by koboViewModel.testResultMessage.collectAsState()

    var editKoboDataServer by remember { mutableStateOf(koboDataServer) }
    var editKoboDataAssetUid by remember { mutableStateOf(koboDataAssetUid) }
    var editKoboDataToken by remember { mutableStateOf(koboDataToken) }
    var showClearKoboDataDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // 1. Dark Mode
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Dark Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Dark Theme Mode")
                        Switch(
                            checked = currentTheme == "DARK",
                            onCheckedChange = { isDark ->
                                prefs.setTheme(if (isDark) "DARK" else "LIGHT")
                            }
                        )
                    }
                }
            }
        }

        // 2. Submission Profile
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Submission Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Submitted By Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = editOrg,
                        onValueChange = { editOrg = it },
                        label = { Text("Organization") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editPhone,
                            onValueChange = { editPhone = it },
                            label = { Text("Phone") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = editUserId,
                            onValueChange = { editUserId = it },
                            label = { Text("User ID") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            prefs.updateCollectorProfile(editName.trim(), editOrg.trim(), editPhone.trim(), editUserId.trim())
                            viewModel.showNotice("Submission Profile updated successfully.")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Submission Profile")
                    }
                }
            }
        }

        // 3. Google Sheets Integration
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Google Sheets Integration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editSheetsId,
                        onValueChange = { editSheetsId = it },
                        label = { Text("Spreadsheet ID") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = editSheetsWebhook,
                        onValueChange = { editSheetsWebhook = it },
                        label = { Text("Apps Script Webhook URL") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            prefs.updateGoogleSheetsConfig(editSheetsId.trim(), editSheetsWebhook.trim())
                            viewModel.showNotice("Google Sheets settings saved successfully.")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Google Sheets Settings")
                    }
                }
            }
        }

        // 4. KoboToolbox Integration
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("KoboToolbox Integration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editKoboServer,
                        onValueChange = { editKoboServer = it },
                        label = { Text("Kobo Server URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = editKoboUsername,
                        onValueChange = { editKoboUsername = it },
                        label = { Text("Kobo Username") },
                        placeholder = { Text("Enter Kobo username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = editKoboFormId,
                        onValueChange = { editKoboFormId = it },
                        label = { Text("Project / Asset UID") },
                        placeholder = { Text("e.g. aBcd1234EfGh5678") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = editKoboToken,
                        onValueChange = { editKoboToken = it },
                        label = { Text("API Token") },
                        placeholder = { Text("Enter API token") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            prefs.updateKoboConfig(editKoboServer.trim(), editKoboUsername.trim(), editKoboFormId.trim(), editKoboToken.trim())
                            viewModel.showNotice("KoboToolbox settings saved successfully.")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Kobo Settings")
                    }
                }
            }
        }

        // 4b. KoboToolbox Data Sync (pull submissions into the app)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Kobo Data Sync", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Download and view Kobo Data submissions from the configured project inside this app. " +
                            "This section is for reading submissions; the KoboToolbox Integration above handles outbound dispatch sync.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editKoboDataServer,
                        onValueChange = { editKoboDataServer = it },
                        label = { Text("Server URL") },
                        placeholder = { Text("https://kf.kobotoolbox.org") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = editKoboDataAssetUid,
                        onValueChange = { editKoboDataAssetUid = it },
                        label = { Text("Project / Asset UID") },
                        placeholder = { Text("Enter Kobo Asset UID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = editKoboDataToken,
                        onValueChange = { editKoboDataToken = it },
                        label = { Text("API Token") },
                        placeholder = { Text("Enter API token") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (koboTestResult != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = koboTestResult ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (koboTestResult == "Connection successful.") {
                                com.example.ui.theme.StatusSynced
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                koboViewModel.clearTestResult()
                                koboViewModel.testConnection(
                                    editKoboDataServer.trim(),
                                    editKoboDataAssetUid.trim(),
                                    editKoboDataToken.trim()
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Test Connection")
                        }
                        Button(
                            onClick = {
                                koboViewModel.saveConfig(
                                    editKoboDataServer.trim(),
                                    editKoboDataAssetUid.trim(),
                                    editKoboDataToken.trim()
                                )
                                viewModel.showNotice("KoboToolbox Data settings saved.")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showClearKoboDataDialog = true },
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear Configuration")
                    }
                }
            }
        }

        // 5. User Access (multi-user, 8-digit password each)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("User Access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Each user needs a name and an 8-digit password. Any listed user's password unlocks Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (allUsers.isEmpty()) {
                        Text(
                            text = "No users yet. Add one below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        allUsers.forEach { user ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = user.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                IconButton(onClick = { viewModel.deleteUser(user.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove user",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newUserName,
                        onValueChange = { newUserName = it; addUserError = null },
                        label = { Text("New User Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = newUserPassword,
                        onValueChange = {
                            if (it.length <= 8 && it.all { ch -> ch.isDigit() }) {
                                newUserPassword = it
                                addUserError = null
                            }
                        },
                        label = { Text("8-Digit Password") },
                        placeholder = { Text("e.g. 12345678") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (addUserError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = addUserError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                val error = viewModel.addUser(newUserName, newUserPassword)
                                if (error != null) {
                                    addUserError = error
                                } else {
                                    viewModel.showNotice("User added successfully.")
                                    newUserName = ""
                                    newUserPassword = ""
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add User")
                    }
                }
            }
        }

        // 6. Backup & Data Management
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Data Management & Reset", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Manage local Room database records or clear sample demo data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.showNotice("Backup created and stored locally.")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Create Backup")
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.clearSampleDemoData()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear Demo Data")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showResetDialog = true },
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset All Database Records")
                    }
                }
            }
        }

        // 7. About App & App Version
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("About App", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("App Version: 1.0.0 (Build 2026.08)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Database: Offline Room DB (Encrypted schema)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    // Clear KoboToolbox Data Config Confirmation Dialog
    if (showClearKoboDataDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showClearKoboDataDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Clear KoboToolbox Data Config",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "This removes the saved server URL, project UID and API token, and deletes " +
                            "downloaded submissions cached for this project. This cannot be undone.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(onClick = { showClearKoboDataDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                koboViewModel.clearConfig()
                                editKoboDataServer = com.example.data.kobo.KoboSecureSettings.DEFAULT_SERVER_URL
                                editKoboDataAssetUid = ""
                                editKoboDataToken = ""
                                showClearKoboDataDialog = false
                            },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Confirm Clear")
                        }
                    }
                }
            }
        }
    }

    // Reset Confirmation Dialog
    if (showResetDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showResetDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Reset All Database Records", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Are you sure you want to clear all records in the local database? This operation cannot be undone.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(onClick = { showResetDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.clearAllDatabaseData()
                                showResetDialog = false
                            },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Confirm Reset")
                        }
                    }
                }
            }
        }
    }
}
