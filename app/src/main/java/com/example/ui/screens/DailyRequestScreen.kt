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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.Project
import com.example.data.model.ProjectMaterial
import com.example.ui.viewmodel.ProjectViewModel
import kotlinx.coroutines.launch

@Composable
fun DailyRequestScreen(
    viewModel: ProjectViewModel,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val isBusy by viewModel.isBusy.collectAsState()
    val userNotice by viewModel.userNotice.collectAsState()

    var drrCode by remember { mutableStateOf("") }
    var project by remember { mutableStateOf<Project?>(null) }
    var lookupError by remember { mutableStateOf<String?>(null) }
    var isLookingUp by remember { mutableStateOf(false) }

    var requestedBy by remember { mutableStateOf("") }
    val requestDate = remember { todayDateString() }
    var validationError by remember { mutableStateOf<String?>(null) }

    // ProjectMaterial.id -> still-available quantity (approved - already committed)
    var requestable by remember { mutableStateOf<List<Pair<ProjectMaterial, Double>>>(emptyList()) }
    val qtyByMaterialId = remember { mutableStateMapOf<Long, String>() }

    fun loadRequestable(projectId: Long) {
        scope.launch {
            requestable = viewModel.getRequestableMaterials(projectId)
            qtyByMaterialId.clear()
        }
    }

    fun lookupProject() {
        val code = drrCode.trim()
        if (code.isBlank()) {
            lookupError = "Enter a DRR Code first."
            return
        }
        isLookingUp = true
        lookupError = null
        scope.launch {
            val found = viewModel.repository.getProjectByDrrCode(code)
            isLookingUp = false
            if (found == null) {
                project = null
                lookupError = "No project found for DRR '$code'."
            } else if (found.status != "MATERIALS_APPROVED" && found.status != "IN_PROGRESS") {
                project = null
                lookupError = "Materials for this project haven't been approved yet (status: ${found.status})."
            } else {
                project = found
                loadRequestable(found.id)
            }
        }
    }

    // Refresh available balances whenever the notice changes after a successful submit,
    // so re-visiting the same project shows the updated remaining amounts.
    LaunchedEffect(userNotice) {
        project?.let { if (userNotice != null) loadRequestable(it.id) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Daily Material Request", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Find Project", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = drrCode,
                            onValueChange = { drrCode = it },
                            label = { Text("DRR Code") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = { lookupProject() }, enabled = !isLookingUp) {
                            if (isLookingUp) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    lookupError?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    project?.let { p ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("${p.projectType} — ${p.tmName}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Requesting for: $requestDate", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        val currentProject = project
        if (currentProject != null) {
            item {
                OutlinedTextField(
                    value = requestedBy,
                    onValueChange = { requestedBy = it },
                    label = { Text("Your Name (TM)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (requestable.isEmpty()) {
                item {
                    Text("No approved materials on this project yet.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                items(requestable, key = { it.first.id }) { (material, available) ->
                    val qtyStr = qtyByMaterialId[material.id] ?: ""
                    val qty = qtyStr.toDoubleOrNull() ?: 0.0
                    val isExceeded = qty > available + 0.0001

                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(material.materialName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "Available: ${cleanQty(available)} ${material.unit}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = qtyStr,
                                onValueChange = { qtyByMaterialId[material.id] = it },
                                label = { Text("Request Quantity") },
                                placeholder = { Text("0") },
                                isError = isExceeded,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (isExceeded) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "Only ${cleanQty(available)} ${material.unit} ${material.materialName} remaining.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                validationError?.let {
                    item {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }

                item {
                    Button(
                        onClick = {
                            if (requestedBy.isBlank()) {
                                validationError = "Enter your name."
                                return@Button
                            }
                            val lines = requestable.mapNotNull { (material, available) ->
                                val qty = qtyByMaterialId[material.id]?.toDoubleOrNull() ?: 0.0
                                if (qty > 0) material.id to qty else null
                            }
                            if (lines.isEmpty()) {
                                validationError = "Enter at least one quantity greater than zero."
                                return@Button
                            }
                            for ((materialId, qty) in lines) {
                                val (material, available) = requestable.first { it.first.id == materialId }
                                if (qty > available + 0.0001) {
                                    validationError = "Only ${cleanQty(available)} ${material.unit} ${material.materialName} remaining."
                                    return@Button
                                }
                            }
                            validationError = null
                            viewModel.submitDailyRequest(
                                projectId = currentProject.id,
                                requestDate = requestDate,
                                requestedBy = requestedBy,
                                lines = lines
                            ) { success ->
                                if (success) qtyByMaterialId.clear()
                            }
                        },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text("Send Material Request", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        userNotice?.let { notice ->
            item { NoticeBanner(notice) { viewModel.clearUserNotice() } }
        }
    }
}
