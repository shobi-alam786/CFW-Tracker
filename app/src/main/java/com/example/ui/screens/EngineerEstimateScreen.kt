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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.Project
import com.example.data.repository.MaterialLine
import com.example.ui.viewmodel.ProjectViewModel
import kotlinx.coroutines.launch

/** Fields are mutableStateOf-backed (not plain vars) so editing one row's TextField reliably recomposes just that row. */
private class EstimateMaterialRow(val rowId: Long) {
    var name by mutableStateOf("")
    var qty by mutableStateOf("")
    var unit by mutableStateOf("")
}

@Composable
fun EngineerEstimateScreen(
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

    var engineerName by remember { mutableStateOf("") }
    var visitDate by remember { mutableStateOf(todayDateString()) }
    var measurementDetails by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    var rowCounter by remember { mutableStateOf(0L) }
    val materialRows = remember { mutableStateListOf(EstimateMaterialRow(rowCounter++)) }

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
                lookupError = "No project found for DRR '$code'. It may not be synced from Kobo yet."
            } else {
                project = found
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Engineer Estimate", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Find Project", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
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
                        Text("${p.projectLocation}, Block ${p.block}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Status: ${p.status}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        val currentProject = project
        if (currentProject != null) {
            item {
                OutlinedTextField(
                    value = engineerName,
                    onValueChange = { engineerName = it },
                    label = { Text("Engineer Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = visitDate,
                    onValueChange = { visitDate = it },
                    label = { Text("Visit Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = measurementDetails,
                    onValueChange = { measurementDetails = it },
                    label = { Text("Measurement Details") },
                    placeholder = { Text("e.g. Wall 40ft x 6ft, foundation depth 3ft") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Remarks (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text("Estimated Materials", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            items(materialRows, key = { it.rowId }) { row ->
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = row.name,
                                onValueChange = { row.name = it },
                                label = { Text("Material") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { if (materialRows.size > 1) materialRows.remove(row) }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove material")
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row {
                            OutlinedTextField(
                                value = row.qty,
                                onValueChange = { row.qty = it },
                                label = { Text("Quantity") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = row.unit,
                                onValueChange = { row.unit = it },
                                label = { Text("Unit") },
                                placeholder = { Text("pc, bag, ft³...") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = { materialRows.add(EstimateMaterialRow(rowCounter++)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Material")
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
                        val lines = materialRows.mapNotNull { r ->
                            val qty = r.qty.toDoubleOrNull()
                            if (r.name.isNotBlank() && qty != null && qty > 0 && r.unit.isNotBlank()) {
                                MaterialLine(r.name, qty, r.unit)
                            } else null
                        }
                        if (engineerName.isBlank()) {
                            validationError = "Enter the engineer's name."
                            return@Button
                        }
                        if (lines.isEmpty()) {
                            validationError = "Add at least one material with a name, quantity, and unit."
                            return@Button
                        }
                        validationError = null
                        viewModel.submitEngineerEstimate(
                            projectId = currentProject.id,
                            engineerName = engineerName,
                            visitDate = visitDate,
                            measurementDetails = measurementDetails,
                            notes = notes,
                            materials = lines
                        ) { success ->
                            if (success) {
                                materialRows.clear()
                                materialRows.add(EstimateMaterialRow(rowCounter++))
                                measurementDetails = ""
                                notes = ""
                            }
                        }
                    },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("Submit Estimate to Boss", fontWeight = FontWeight.Bold)
                }
            }
        }

        userNotice?.let { notice ->
            item { NoticeBanner(notice) { viewModel.clearUserNotice() } }
        }
    }
}
