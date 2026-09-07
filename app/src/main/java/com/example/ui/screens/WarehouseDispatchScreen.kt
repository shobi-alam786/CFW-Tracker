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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.DailyMaterialRequest
import com.example.data.model.DailyRequestMaterial
import com.example.data.model.Project
import com.example.data.repository.DispatchableRequest
import com.example.ui.viewmodel.ProjectViewModel

@Composable
fun WarehouseDispatchScreen(
    viewModel: ProjectViewModel,
    modifier: Modifier = Modifier
) {
    val readyRequests by viewModel.requestsReadyForDispatch.collectAsState()
    val userNotice by viewModel.userNotice.collectAsState()
    val projectCache = remember { mutableStateMapOf<Long, Project>() }

    LaunchedEffect(readyRequests) {
        for (request in readyRequests) {
            if (!projectCache.containsKey(request.projectId)) {
                viewModel.getProjectById(request.projectId)?.let { projectCache[request.projectId] = it }
            }
        }
    }

    var selectedRequest by remember { mutableStateOf<DailyMaterialRequest?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Warehouse Dispatch", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        val current = selectedRequest
        if (current == null) {
            if (readyRequests.isEmpty()) {
                item { Text("No approved requests waiting to be dispatched.", style = MaterialTheme.typography.bodyMedium) }
            } else {
                items(readyRequests, key = { it.id }) { request ->
                    val proj = projectCache[request.projectId]
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth().clickable { selectedRequest = request }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                proj?.let { "${it.projectType} — ${it.tmName}" } ?: "Project #${request.projectId}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Requested by ${request.requestedBy} on ${request.requestDate} • ${request.status}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            proj?.let { Text("DRR: ${it.drrCode}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        } else {
            item {
                DispatchReviewCard(
                    viewModel = viewModel,
                    request = current,
                    project = projectCache[current.projectId],
                    onDone = { selectedRequest = null }
                )
            }
        }

        userNotice?.let { notice ->
            item { NoticeBanner(notice) { viewModel.clearUserNotice() } }
        }
    }
}

@Composable
private fun DispatchReviewCard(
    viewModel: ProjectViewModel,
    request: DailyMaterialRequest,
    project: Project?,
    onDone: () -> Unit
) {
    var details by remember(request.id) { mutableStateOf<DispatchableRequest?>(null) }
    val actualQtyMap = remember(request.id) { mutableStateMapOf<Long, String>() }
    var warehouseKeeperName by remember(request.id) { mutableStateOf("") }
    var remarks by remember(request.id) { mutableStateOf("") }
    var error by remember(request.id) { mutableStateOf<String?>(null) }
    val isBusy by viewModel.isBusy.collectAsState()
    val dispatchDate = remember { todayDateString() }

    LaunchedEffect(request.id) {
        details = viewModel.getDispatchableRequest(request.id)
        details?.lines?.forEach { line ->
            actualQtyMap[line.id] = cleanQty(line.approvedQuantity ?: 0.0)
        }
    }

    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(project?.let { "${it.projectType} — ${it.tmName}" } ?: "Project", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            project?.let { Text("DRR: ${it.drrCode} • ${it.projectLocation}, Block ${it.block}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("Requested by ${request.requestedBy} on ${request.requestDate}", style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = warehouseKeeperName,
                onValueChange = { warehouseKeeperName = it },
                label = { Text("Your Name (Warehouse Keeper)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text("Approved Materials — confirm the actual quantity sent out", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            details?.lines?.forEach { line ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(line.materialName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Approved: ${cleanQty(line.approvedQuantity ?: 0.0)} ${line.unit}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(
                        value = actualQtyMap[line.id] ?: "",
                        onValueChange = { actualQtyMap[line.id] = it },
                        label = { Text("Actual") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.width(120.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = remarks,
                onValueChange = { remarks = it },
                label = { Text("Remarks (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            error?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    if (warehouseKeeperName.isBlank()) {
                        error = "Enter your name."
                        return@Button
                    }
                    val quantities = (details?.lines ?: emptyList<DailyRequestMaterial>()).mapNotNull { line ->
                        val qty = actualQtyMap[line.id]?.toDoubleOrNull()
                        if (qty != null && qty > 0) line.id to qty else null
                    }.toMap()
                    if (quantities.isEmpty()) {
                        error = "Enter at least one actual dispatched quantity."
                        return@Button
                    }
                    error = null
                    viewModel.dispatchApprovedRequest(
                        requestId = request.id,
                        projectId = request.projectId,
                        actualQuantities = quantities,
                        warehouseKeeperName = warehouseKeeperName,
                        dispatchDate = dispatchDate,
                        remarks = remarks
                    ) { success -> if (success) onDone() }
                },
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Confirm Dispatch", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Back to list") }
        }
    }
}
