package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AuditLog
import com.example.ui.viewmodel.ProjectViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectOverviewScreen(
    viewModel: ProjectViewModel,
    modifier: Modifier = Modifier
) {
    val summary by viewModel.dashboardSummary.collectAsState()
    val recentActivity by viewModel.recentActivity.collectAsState()
    val timeFmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    LaunchedEffect(Unit) { viewModel.refreshDashboardSummary() }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Overview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        val currentSummary = summary
        if (currentSummary == null) {
            item { Text("Loading…", style = MaterialTheme.typography.bodyMedium) }
        } else {
            item {
                Text("Projects", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Total", currentSummary.totalProjects.toString(), Modifier.weight(1f))
                    StatCard("Active", currentSummary.activeProjects.toString(), Modifier.weight(1f))
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Completed", currentSummary.completedProjects.toString(), Modifier.weight(1f))
                    StatCard("Pending Requests", currentSummary.pendingRequests.toString(), Modifier.weight(1f))
                }
            }

            item {
                Text("Material Alerts", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(
                        "🔴 Finished Materials",
                        currentSummary.finishedMaterialsCount.toString(),
                        Modifier.weight(1f),
                        valueColor = MaterialTheme.colorScheme.error
                    )
                    StatCard(
                        "🟠 Low Materials",
                        currentSummary.lowMaterialsCount.toString(),
                        Modifier.weight(1f),
                        valueColor = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        item {
            Text("Recent Activity", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        if (recentActivity.isEmpty()) {
            item { Text("No activity yet.", style = MaterialTheme.typography.bodyMedium) }
        } else {
            items(recentActivity.take(30), key = { it.id }) { log ->
                ActivityRow(log, timeFmt.format(Date(log.timestamp)))
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = valueColor)
            Spacer(modifier = Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActivityRow(log: AuditLog, timeLabel: String) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(timeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(log.user, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Text(log.details, style = MaterialTheme.typography.bodySmall)
        }
    }
}
