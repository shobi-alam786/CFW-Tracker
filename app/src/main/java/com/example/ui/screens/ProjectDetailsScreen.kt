package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DailyMaterialRequest
import com.example.data.model.Project
import com.example.data.model.ProjectFullDetails
import com.example.data.model.ProjectMaterialStatus
import com.example.data.repository.MaterialHistoryEntry
import com.example.ui.viewmodel.ProjectViewModel
import kotlinx.coroutines.launch

private val GreenAvailable = Color(0xFF2E7D32)

@Composable
fun ProjectDetailsScreen(
    viewModel: ProjectViewModel,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var drrCode by remember { mutableStateOf("") }
    var project by remember { mutableStateOf<Project?>(null) }
    var lookupError by remember { mutableStateOf<String?>(null) }
    var isLookingUp by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<ProjectFullDetails?>(null) }
    var requests by remember { mutableStateOf<List<DailyMaterialRequest>>(emptyList()) }

    val expandedMaterialId = remember { mutableStateOf<Long?>(null) }
    val historyByMaterialId = remember { mutableStateMapOf<Long, List<MaterialHistoryEntry>>() }

    fun lookup() {
        val code = drrCode.trim()
        if (code.isBlank()) {
            lookupError = "Enter a DRR Code first."
            return
        }
        isLookingUp = true
        lookupError = null
        scope.launch {
            val found = viewModel.repository.getProjectByDrrCode(code)
            if (found == null) {
                project = null
                details = null
                requests = emptyList()
                lookupError = "No project found for DRR '$code'."
            } else {
                project = found
                details = viewModel.repository.getProjectFullDetails(found.id)
                requests = viewModel.repository.getRequestsForProject(found.id)
                expandedMaterialId.value = null
                historyByMaterialId.clear()
            }
            isLookingUp = false
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Project Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = drrCode,
                            onValueChange = { drrCode = it },
                            label = { Text("DRR Code") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = { lookup() }, enabled = !isLookingUp) {
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
                }
            }
        }

        val currentProject = project
        val currentDetails = details
        if (currentProject != null && currentDetails != null) {
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(currentProject.projectType, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("TM: ${currentProject.tmName}", style = MaterialTheme.typography.bodyMedium)
                        Text("DRR-Code: ${currentProject.drrCode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Location: ${currentProject.projectLocation}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Block: ${currentProject.block}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Status: ${currentProject.status}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            item {
                Text("Approved Materials", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }

            if (currentDetails.materials.isEmpty()) {
                item { Text("No approved materials yet.", style = MaterialTheme.typography.bodyMedium) }
            } else {
                items(currentDetails.materials, key = { it.material.id }) { status ->
                    MaterialBalanceCard(
                        status = status,
                        isExpanded = expandedMaterialId.value == status.material.id,
                        history = historyByMaterialId[status.material.id],
                        onToggle = {
                            val id = status.material.id
                            if (expandedMaterialId.value == id) {
                                expandedMaterialId.value = null
                            } else {
                                expandedMaterialId.value = id
                                if (!historyByMaterialId.containsKey(id)) {
                                    scope.launch {
                                        historyByMaterialId[id] = viewModel.getMaterialHistory(id)
                                    }
                                }
                            }
                        }
                    )
                }
            }

            item {
                Text("Request History", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            if (requests.isEmpty()) {
                item { Text("No daily requests yet.", style = MaterialTheme.typography.bodyMedium) }
            } else {
                items(requests, key = { it.id }) { request ->
                    RequestHistoryRow(request)
                }
            }
        }
    }
}

@Composable
private fun MaterialBalanceCard(
    status: ProjectMaterialStatus,
    isExpanded: Boolean,
    history: List<MaterialHistoryEntry>?,
    onToggle: () -> Unit
) {
    val (label, color) = when (status.statusLevel) {
        "FINISHED" -> "🔴 FINISHED" to MaterialTheme.colorScheme.error
        "LOW" -> "🟠 LOW" to MaterialTheme.colorScheme.tertiary
        else -> "🟢 AVAILABLE" to GreenAvailable
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(status.material.materialName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
            }
            if (status.isOverDispatched) {
                Text("🔴 OVER-DISPATCH WARNING", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Approved: ${cleanQty(status.material.approvedQuantity)} ${status.material.unit}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Dispatched: ${cleanQty(status.totalDispatched)} ${status.material.unit}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Remaining: ${cleanQty(status.remainingQuantity)} ${status.material.unit}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = color
            )

            if (isExpanded) {
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Text("History", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                when {
                    history == null -> Text("Loading…", style = MaterialTheme.typography.bodySmall)
                    history.isEmpty() -> Text("No dispatches recorded yet.", style = MaterialTheme.typography.bodySmall)
                    else -> history.forEach { entry ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(entry.date, style = MaterialTheme.typography.bodySmall)
                            Text("${cleanQty(entry.quantity)} ${status.material.unit} dispatched", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (!history.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total dispatched", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Text(
                            "${cleanQty(history.sumOf { it.quantity })} ${status.material.unit}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestHistoryRow(request: DailyMaterialRequest) {
    val (label, color) = when (request.status) {
        "APPROVED" -> "Approved" to GreenAvailable
        "PARTIALLY_APPROVED" -> "Partially Approved" to MaterialTheme.colorScheme.tertiary
        "REJECTED" -> "Rejected" to MaterialTheme.colorScheme.error
        "DISPATCHED" -> "Dispatched" to MaterialTheme.colorScheme.primary
        "COMPLETED" -> "Completed" to GreenAvailable
        else -> "Pending" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Requested by ${request.requestedBy}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(request.requestDate, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
