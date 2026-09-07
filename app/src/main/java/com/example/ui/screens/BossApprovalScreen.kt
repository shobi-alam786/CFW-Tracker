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
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.DailyMaterialRequest
import com.example.data.model.DailyRequestMaterial
import com.example.data.model.EngineerEstimate
import com.example.data.model.EstimateMaterial
import com.example.data.model.Project
import com.example.data.repository.MaterialLine
import com.example.ui.viewmodel.ProjectViewModel
import kotlinx.coroutines.launch

@Composable
fun BossApprovalScreen(
    viewModel: ProjectViewModel,
    modifier: Modifier = Modifier
) {
    var subTab by remember { mutableStateOf(0) } // 0 = Estimates, 1 = Daily Requests
    var bossName by remember { mutableStateOf("") }

    val pendingEstimates by viewModel.pendingEstimates.collectAsState()
    val pendingRequests by viewModel.pendingDailyRequests.collectAsState()
    val userNotice by viewModel.userNotice.collectAsState()

    val scope = rememberCoroutineScope()
    val projectCache = remember { mutableStateMapOf<Long, Project>() }

    LaunchedEffect(pendingEstimates, pendingRequests) {
        val ids = (pendingEstimates.map { it.projectId } + pendingRequests.map { it.projectId }).distinct()
        for (id in ids) {
            if (!projectCache.containsKey(id)) {
                viewModel.getProjectById(id)?.let { projectCache[id] = it }
            }
        }
    }

    var selectedEstimate by remember { mutableStateOf<EngineerEstimate?>(null) }
    var selectedRequest by remember { mutableStateOf<DailyMaterialRequest?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = subTab == 0,
                onClick = { subTab = 0; selectedRequest = null },
                label = { Text("Estimates (${pendingEstimates.size})") }
            )
            FilterChip(
                selected = subTab == 1,
                onClick = { subTab = 1; selectedEstimate = null },
                label = { Text("Daily Requests (${pendingRequests.size})") }
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = bossName,
                    onValueChange = { bossName = it },
                    label = { Text("Your Name (Boss)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (subTab == 0) {
                if (selectedEstimate == null) {
                    if (pendingEstimates.isEmpty()) {
                        item { Text("No estimates waiting for approval.", style = MaterialTheme.typography.bodyMedium) }
                    } else {
                        items(pendingEstimates, key = { it.id }) { estimate ->
                            val proj = projectCache[estimate.projectId]
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth().clickable { selectedEstimate = estimate }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        proj?.let { "${it.projectType} — ${it.tmName}" } ?: "Project #${estimate.projectId}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Engineer: ${estimate.engineerName} • Visit: ${estimate.visitDate}",
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
                        EstimateReviewCard(
                            viewModel = viewModel,
                            estimate = selectedEstimate!!,
                            project = projectCache[selectedEstimate!!.projectId],
                            bossName = bossName,
                            onDone = { selectedEstimate = null }
                        )
                    }
                }
            } else {
                if (selectedRequest == null) {
                    if (pendingRequests.isEmpty()) {
                        item { Text("No daily requests waiting for approval.", style = MaterialTheme.typography.bodyMedium) }
                    } else {
                        items(pendingRequests, key = { it.id }) { request ->
                            val proj = projectCache[request.projectId]
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedRequest = request
                                    viewModel.loadProjectDetails(request.projectId)
                                }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        proj?.let { "${it.projectType} — ${it.tmName}" } ?: "Project #${request.projectId}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Requested by ${request.requestedBy} on ${request.requestDate}",
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
                        DailyRequestReviewCard(
                            viewModel = viewModel,
                            request = selectedRequest!!,
                            project = projectCache[selectedRequest!!.projectId],
                            bossName = bossName,
                            onDone = { selectedRequest = null }
                        )
                    }
                }
            }

            userNotice?.let { notice ->
                item { NoticeBanner(notice) { viewModel.clearUserNotice() } }
            }
        }
    }
}

@Composable
private fun EstimateReviewCard(
    viewModel: ProjectViewModel,
    estimate: EngineerEstimate,
    project: Project?,
    bossName: String,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var materials by remember(estimate.id) { mutableStateOf<List<EstimateMaterial>>(emptyList()) }
    val approvedQtyMap = remember(estimate.id) { mutableStateMapOf<Long, String>() }
    var remarks by remember(estimate.id) { mutableStateOf("") }
    var error by remember(estimate.id) { mutableStateOf<String?>(null) }
    val isBusy by viewModel.isBusy.collectAsState()

    LaunchedEffect(estimate.id) {
        materials = viewModel.getEstimateMaterials(estimate.id)
        materials.forEach { approvedQtyMap[it.id] = it.estimatedQuantity.let(::cleanQty) }
    }

    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(project?.let { "${it.projectType} — ${it.tmName}" } ?: "Project", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            project?.let { Text("DRR: ${it.drrCode} • ${it.projectLocation}, Block ${it.block}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("Engineer: ${estimate.engineerName} • Visit: ${estimate.visitDate}", style = MaterialTheme.typography.bodySmall)
            if (estimate.measurementDetails.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text("Measurements: ${estimate.measurementDetails}", style = MaterialTheme.typography.bodySmall)
            }
            if (estimate.notes.isNotBlank()) {
                Text("Notes: ${estimate.notes}", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Estimated Materials (edit to change approved quantity)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            materials.forEach { mat ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(mat.materialName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Estimated: ${cleanQty(mat.estimatedQuantity)} ${mat.unit}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(
                        value = approvedQtyMap[mat.id] ?: "",
                        onValueChange = { approvedQtyMap[mat.id] = it },
                        label = { Text("Approved") },
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { selectedEstimateAction(viewModel, estimate, bossName, remarks, isReject = true, onError = { error = it }, onDone = onDone) },
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f)
                ) { Text("Reject") }

                Button(
                    onClick = {
                        if (bossName.isBlank()) {
                            error = "Enter your name before deciding."
                            return@Button
                        }
                        val lines = materials.mapNotNull { mat ->
                            val qty = approvedQtyMap[mat.id]?.toDoubleOrNull()
                            if (qty != null && qty > 0) MaterialLine(mat.materialName, qty, mat.unit) else null
                        }
                        if (lines.isEmpty()) {
                            error = "Approve at least one material with a quantity."
                            return@Button
                        }
                        error = null
                        viewModel.approveEngineerEstimate(estimate.id, estimate.projectId, lines, bossName, remarks) { success ->
                            if (success) onDone()
                        }
                    },
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f)
                ) { Text("Approve") }
            }

            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Back to list") }
        }
    }
}

private fun selectedEstimateAction(
    viewModel: ProjectViewModel,
    estimate: EngineerEstimate,
    bossName: String,
    remarks: String,
    isReject: Boolean,
    onError: (String) -> Unit,
    onDone: () -> Unit
) {
    if (bossName.isBlank()) {
        onError("Enter your name before deciding.")
        return
    }
    if (isReject) {
        viewModel.rejectEngineerEstimate(estimate.id, estimate.projectId, bossName, remarks) { success ->
            if (success) onDone()
        }
    }
}

@Composable
private fun DailyRequestReviewCard(
    viewModel: ProjectViewModel,
    request: DailyMaterialRequest,
    project: Project?,
    bossName: String,
    onDone: () -> Unit
) {
    var lines by remember(request.id) { mutableStateOf<List<DailyRequestMaterial>>(emptyList()) }
    val decisionQtyMap = remember(request.id) { mutableStateMapOf<Long, String>() }
    var remarks by remember(request.id) { mutableStateOf("") }
    var error by remember(request.id) { mutableStateOf<String?>(null) }
    val isBusy by viewModel.isBusy.collectAsState()
    val projectDetails by viewModel.selectedProjectDetails.collectAsState()

    LaunchedEffect(request.id) {
        lines = viewModel.getDailyRequestDetails(request.id)?.second ?: emptyList()
        lines.forEach { decisionQtyMap[it.id] = cleanQty(it.requestedQuantity) }
    }

    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(project?.let { "${it.projectType} — ${it.tmName}" } ?: "Project", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            project?.let { Text("DRR: ${it.drrCode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("Requested by ${request.requestedBy} on ${request.requestDate}", style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.height(12.dp))
            Text("Project Balance", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            projectDetails?.materials?.forEach { status ->
                Text(
                    "${status.material.materialName}: Approved ${cleanQty(status.material.approvedQuantity)} • " +
                        "Dispatched ${cleanQty(status.totalDispatched)} • Remaining ${cleanQty(status.remainingQuantity)} ${status.material.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Requested Materials (edit to partially approve)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            lines.forEach { line ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(line.materialName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Requested: ${cleanQty(line.requestedQuantity)} ${line.unit}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(
                        value = decisionQtyMap[line.id] ?: "",
                        onValueChange = { decisionQtyMap[line.id] = it },
                        label = { Text("Approved") },
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        if (bossName.isBlank()) {
                            error = "Enter your name before deciding."
                            return@OutlinedButton
                        }
                        error = null
                        viewModel.decideDailyRequest(request.id, request.projectId, emptyMap(), bossName, remarks) { success ->
                            if (success) onDone()
                        }
                    },
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f)
                ) { Text("Reject All") }

                Button(
                    onClick = {
                        if (bossName.isBlank()) {
                            error = "Enter your name before deciding."
                            return@Button
                        }
                        val decisions = lines.mapNotNull { line ->
                            val qty = decisionQtyMap[line.id]?.toDoubleOrNull()
                            if (qty != null && qty > 0) line.id to qty else null
                        }.toMap()
                        if (decisions.isEmpty()) {
                            error = "Approve at least one material, or use Reject All."
                            return@Button
                        }
                        error = null
                        viewModel.decideDailyRequest(request.id, request.projectId, decisions, bossName, remarks) { success ->
                            if (success) onDone()
                        }
                    },
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f)
                ) { Text("Save Decision") }
            }

            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Back to list") }
        }
    }
}
