package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.kobo.KoboCategorizedSubmission
import com.example.data.kobo.KoboDateUtils
import com.example.data.kobo.KoboDrrDayReport
import com.example.data.kobo.KoboField
import com.example.data.kobo.KoboFieldClassifier
import com.example.data.kobo.KoboSubmission
import com.example.data.kobo.KoboVisitEntry
import com.example.ui.theme.StatusSynced
import com.example.ui.viewmodel.KoboViewModel

@Composable
fun KoboSubmissionDetailScreen(
    submissionId: Long,
    viewModel: KoboViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var submission by remember { mutableStateOf<KoboSubmission?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var report by remember { mutableStateOf<KoboDrrDayReport?>(null) }

    LaunchedEffect(submissionId) {
        isLoading = true
        val current = viewModel.getSubmissionDetail(submissionId)
        submission = current
        isLoading = false
        if (current != null) {
            report = viewModel.getDrrDayReport(current)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Submission Details",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        when {
            isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            submission == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "This submission is no longer available offline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {
                val current = submission!!
                val categorized = remember(current.submissionId, current.rawJson) {
                    KoboFieldClassifier.classify(current.toJson())
                }
                val currentReport = report

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { KoboHeroCard(submission = current, categorized = categorized) }

                    // 1. Project Details
                    if (categorized.projectInfo.isNotEmpty()) {
                        item {
                            KoboFieldGrid(
                                title = "Project Details",
                                icon = Icons.AutoMirrored.Filled.Assignment,
                                fields = categorized.projectInfo
                            )
                        }
                    }

                    if (currentReport == null) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    } else {
                        // 2. Materials Approved — materials approved for this daily request
                        if (currentReport.approvedMaterials.isNotEmpty()) {
                            item {
                                KoboMaterialsTable(
                                    title = "Approved Materials",
                                    icon = Icons.Default.Inventory2,
                                    headers = listOf("Material Name", "Approved Quantity", "Unit"),
                                    weights = listOf(0.42f, 0.34f, 0.24f),
                                    rows = currentReport.approvedMaterials.map {
                                        listOf(it.materialName, it.approvedQty ?: "—", it.unit.ifBlank { "—" })
                                    }
                                )
                            }
                        }

                        // 3. Recipients and Materials Received — recipients in this daily request
                        if (currentReport.visits.isNotEmpty()) {
                            item { KoboRecipientsCard(visits = currentReport.visits) }
                        }

                        // 4. Total Summary — approved vs. total dispatched in this daily request vs. remaining
                        if (currentReport.totalSummary.isNotEmpty()) {
                            item {
                                KoboMaterialsTable(
                                    title = "Total Summary",
                                    icon = Icons.Default.BarChart,
                                    headers = listOf("Material Name", "Approved Quantity", "Dispatch Qty", "Remaining Quantity", "Unit"),
                                    weights = listOf(0.28f, 0.17f, 0.19f, 0.20f, 0.16f),
                                    rows = currentReport.totalSummary.map {
                                        listOf(it.materialName, it.approvedQty ?: "—", it.dispatchQty ?: "—", it.remainingQty ?: "—", it.unit.ifBlank { "—" })
                                    },
                                    highlightColumnIndex = 3
                                )
                            }
                        }
                    }

                    // 5. Submission Information
                    if (categorized.submissionInfo.isNotEmpty()) {
                        item {
                            KoboFieldGrid(
                                title = "Submission Information",
                                icon = Icons.Default.Info,
                                fields = categorized.submissionInfo
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Hero header: recipient name (or a sensible fallback), date/time, and who submitted it. */
@Composable
private fun KoboHeroCard(submission: KoboSubmission, categorized: KoboCategorizedSubmission) {
    val title = categorized.tmName ?: categorized.materialName ?: submission.previewLabel()

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = KoboDateUtils.formatFull(submission.submissionTime),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                if (categorized.submittedBy != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Submitted by ${categorized.submittedBy}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/**
 * Fields laid out two-per-row (label above value) instead of one full-width row with a
 * divider after every field – far fewer dividers, and the grid keeps the card compact.
 */
@Composable
private fun KoboFieldGrid(
    title: String,
    icon: ImageVector?,
    fields: List<KoboField>
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            KoboSectionHeader(title, icon)
            Spacer(modifier = Modifier.height(12.dp))

            val rows = fields.chunked(2)
            rows.forEachIndexed { rowIndex, rowFields ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    rowFields.forEach { field ->
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = field.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = field.value,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (rowFields.size == 1) {
                        Column(modifier = Modifier.weight(1f)) {}
                    }
                }
                if (rowIndex != rows.lastIndex) {
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }
    }
}

@Composable
private fun KoboSectionHeader(title: String, icon: ImageVector?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

/** A compact data table: tinted header row, then data rows with thin separators. */
@Composable
private fun KoboMaterialsTable(
    title: String,
    icon: ImageVector,
    headers: List<String>,
    weights: List<Float>,
    rows: List<List<String>>,
    highlightColumnIndex: Int? = null
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            KoboSectionHeader(title, icon)
            Spacer(modifier = Modifier.height(12.dp))
            KoboTableBody(headers, weights, rows, highlightColumnIndex)
        }
    }
}

@Composable
private fun KoboTableBody(
    headers: List<String>,
    weights: List<Float>,
    rows: List<List<String>>,
    highlightColumnIndex: Int? = null
) {
    if (rows.isEmpty()) {
        Text(
            text = "No data available.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        headers.forEachIndexed { i, header ->
            Text(
                text = header,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(weights[i])
            )
        }
    }

    rows.forEachIndexed { rowIndex, row ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            row.forEachIndexed { i, cell ->
                Text(
                    text = cell,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (highlightColumnIndex == i) StatusSynced else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(weights[i])
                )
            }
        }
        if (rowIndex != rows.lastIndex) {
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
        }
    }
}

/** All recipients who received materials under this request code today, each with their own table. */
@Composable
private fun KoboRecipientsCard(
    visits: List<KoboVisitEntry>
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Blue recipients section header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(
                        horizontal = 12.dp,
                        vertical = 10.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.People,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(
                    modifier = Modifier.width(8.dp)
                )

                Text(
                    text = "Recipients and Materials Received",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            visits.forEachIndexed { index, visit ->
                // Recipient information row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(
                            horizontal = 10.dp,
                            vertical = 10.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )

                    Text(
                        text = "${index + 1}. ${visit.recipientName} " +
                                "(FCN: ${visit.recipientFcn.ifBlank { "—" }})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(
                    modifier = Modifier.height(6.dp)
                )

                // Material Name | Dispatch Qty | Unit
                KoboTableBody(
                    headers = listOf(
                        "Material Name",
                        "Dispatch Qty",
                        "Unit"
                    ),
                    weights = listOf(
                        0.42f,
                        0.34f,
                        0.24f
                    ),
                    rows = visit.materials.map {
                        listOf(
                            it.materialName,
                            it.dispatchQty ?: "—",
                            it.unit.ifBlank { "—" }
                        )
                    }
                )

                if (index != visits.lastIndex) {
                    Spacer(
                        modifier = Modifier.height(16.dp)
                    )
                }
            }
        }
    }
}

