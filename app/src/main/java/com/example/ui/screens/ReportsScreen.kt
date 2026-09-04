package com.example.ui.screens

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.BeneficiaryFullDetails
import com.example.data.model.MaterialSummaryItem
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun ReportsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val beneficiaries by viewModel.allBeneficiaries.collectAsState()

    var selectedTab by remember { mutableStateOf("Material Summary") }
    val reportTabs = listOf("Material Summary", "Daily Dispatch", "Weekly", "Monthly", "TM Project Status")

    var detailsList by remember { mutableStateOf<List<BeneficiaryFullDetails>>(emptyList()) }

    LaunchedEffect(beneficiaries) {
        val list = mutableListOf<BeneficiaryFullDetails>()
        for (ben in beneficiaries) {
            val det = viewModel.repository.getBeneficiaryFullDetails(ben.id)
            if (det != null) list.add(det)
        }
        detailsList = list
    }

    // Material totals calculation
    val materialSummaryMap = remember(detailsList) {
        val map = mutableMapOf<String, MaterialSummaryItem>()
        detailsList.forEach { det ->
            det.requestedMaterials.forEach { reqMatStatus ->
                val name = reqMatStatus.material.materialName
                val unit = reqMatStatus.material.unit
                val current = map[name] ?: MaterialSummaryItem(name, 0.0, 0.0, 0.0, unit)
                map[name] = MaterialSummaryItem(
                    materialName = name,
                    approvedQty = current.approvedQty + reqMatStatus.material.approvedQuantity,
                    dispatchedQty = current.dispatchedQty + reqMatStatus.totalDispatched,
                    remainingQty = current.remainingQty + reqMatStatus.remainingQuantity,
                    unit = unit
                )
            }
        }
        map.values.toList().sortedByDescending { it.approvedQty }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Material Analytics & Reports",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        scope.launch {
                            val csvData = viewModel.repository.exportAllCsv()
                            shareCsvReport(context, csvData)
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share Report",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Report Type Filter Chips
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(reportTabs) { tab ->
                    FilterChip(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = { Text(tab) }
                    )
                }
            }
        }

        // Material Consumption Table Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Material Balance Breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (materialSummaryMap.isEmpty()) {
                        Text("No material request records available.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        // Table Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Material Name", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.15f))
                            Text("Approved Quantity", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.05f))
                            Text("Dispatch Qty", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.9f))
                            Text("Remaining Quantity", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.05f))
                            Text("Unit", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
                        }
                        HorizontalDivider()

                        materialSummaryMap.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.materialName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.15f))
                                Text("${item.approvedQty.toInt()}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.05f))
                                Text("${item.dispatchedQty.toInt()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.9f))
                                Text("${item.remainingQty.toInt()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.05f))
                                Text(item.unit, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.7f))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val csvData = viewModel.repository.exportAllCsv()
                                shareCsvReport(context, csvData)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export Full CSV / Excel Audit File")
                    }
                }
            }
        }
    }
}

private fun shareCsvReport(context: Context, csvContent: String) {
    val sendIntent: Intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, "TM Project Material Request & Multi-Dispatch Audit Report:\n\n$csvContent")
        type = "text/csv"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Export & Share CFW Material Report")
    context.startActivity(shareIntent)
}
