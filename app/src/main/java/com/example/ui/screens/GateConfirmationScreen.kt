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
import com.example.data.model.DispatchRecord
import com.example.data.repository.GateMaterialLine
import com.example.data.repository.PendingGateDispatch
import com.example.ui.viewmodel.ProjectViewModel

@Composable
fun GateConfirmationScreen(
    viewModel: ProjectViewModel,
    modifier: Modifier = Modifier
) {
    val pendingDispatches by viewModel.dispatchesPendingGateConfirmation.collectAsState()
    val userNotice by viewModel.userNotice.collectAsState()
    var selectedDispatch by remember { mutableStateOf<DispatchRecord?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Gate Collection Confirmation", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        val current = selectedDispatch
        if (current == null) {
            if (pendingDispatches.isEmpty()) {
                item { Text("No dispatches waiting for Gate confirmation.", style = MaterialTheme.typography.bodyMedium) }
            } else {
                items(pendingDispatches, key = { it.id }) { dispatch ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth().clickable { selectedDispatch = dispatch }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Dispatched to: ${dispatch.recipientName.ifBlank { "Unknown TM" }}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Dispatched by ${dispatch.collectorName} on ${dispatch.dispatchDate}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            item {
                GateReviewCard(
                    viewModel = viewModel,
                    dispatch = current,
                    onDone = { selectedDispatch = null }
                )
            }
        }

        userNotice?.let { notice ->
            item { NoticeBanner(notice) { viewModel.clearUserNotice() } }
        }
    }
}

@Composable
private fun GateReviewCard(
    viewModel: ProjectViewModel,
    dispatch: DispatchRecord,
    onDone: () -> Unit
) {
    var details by remember(dispatch.id) { mutableStateOf<PendingGateDispatch?>(null) }
    val collectedQtyMap = remember(dispatch.id) { mutableStateMapOf<Long, String>() }
    var collectorName by remember(dispatch.id) { mutableStateOf("") }
    var remarks by remember(dispatch.id) { mutableStateOf("") }
    var error by remember(dispatch.id) { mutableStateOf<String?>(null) }
    val isBusy by viewModel.isBusy.collectAsState()
    val confirmationDate = remember { todayDateString() }

    LaunchedEffect(dispatch.id) {
        details = viewModel.getPendingGateDispatch(dispatch.id)
        // Default to "matches what was dispatched" — the Gate/Collector only needs to type a
        // different number if what they actually see differs (spec section 16).
        details?.materials?.forEach { line ->
            collectedQtyMap[line.dispatchMaterial.id] = cleanQty(line.dispatchMaterial.dispatchedQuantity)
        }
    }

    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Dispatched to: ${dispatch.recipientName.ifBlank { "Unknown TM" }}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Dispatched by ${dispatch.collectorName} on ${dispatch.dispatchDate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (dispatch.note.isNotBlank()) {
                Text("Note: ${dispatch.note}", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = collectorName,
                onValueChange = { collectorName = it },
                label = { Text("Your Name (Gate/Collector)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text("Materials Dispatched — confirm what actually left", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            details?.materials?.forEach { line ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(line.materialName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Dispatched: ${cleanQty(line.dispatchMaterial.dispatchedQuantity)} ${line.unit}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value = collectedQtyMap[line.dispatchMaterial.id] ?: "",
                        onValueChange = { collectedQtyMap[line.dispatchMaterial.id] = it },
                        label = { Text("Collected") },
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
                    if (collectorName.isBlank()) {
                        error = "Enter your name."
                        return@Button
                    }
                    val collected = (details?.materials ?: emptyList<GateMaterialLine>()).associate { line ->
                        val dm = line.dispatchMaterial
                        dm.id to (collectedQtyMap[dm.id]?.toDoubleOrNull() ?: dm.dispatchedQuantity)
                    }
                    error = null
                    viewModel.confirmGateCollection(
                        dispatchId = dispatch.id,
                        collectorName = collectorName,
                        confirmationDate = confirmationDate,
                        confirmationTime = "",
                        remarks = remarks,
                        actualCollected = collected
                    ) { success -> if (success) onDone() }
                },
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Confirm Collection", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Back to list") }
        }
    }
}
