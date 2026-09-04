package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.kobo.KoboDateUtils
import com.example.data.kobo.KoboFieldClassifier
import com.example.data.kobo.KoboSubmission
import com.example.ui.theme.StatusFailed
import com.example.ui.theme.StatusPending
import com.example.ui.theme.StatusSynced
import com.example.ui.viewmodel.KoboUiState
import com.example.ui.viewmodel.KoboViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun KoboDataScreen(
    viewModel: KoboViewModel,
    onOpenSubmission: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val totalCached by viewModel.totalCachedCount.collectAsState()
    val userNotice by viewModel.userNotice.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshNetworkStatus()
    }

    LaunchedEffect(userNotice) {
        if (userNotice != null) {
            delay(3000)
            viewModel.clearNotice()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Kobo Data",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Kobo Settings")
            }
        }

        if (uiState is KoboUiState.NotConfigured) {
            KoboNotConfiguredCard(onOpenSettings)
            return@Column
        }

        if (userNotice != null) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text(
                    text = userNotice ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Compact summary card: count, last sync, status, sync button
        KoboSummaryCard(
            totalCached = totalCached,
            lastSyncTime = lastSyncTime,
            isSyncing = isSyncing,
            isOffline = isOffline,
            onSync = { viewModel.sync() }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Compact search
        KoboSearchField(
            query = searchQuery,
            onQueryChange = { viewModel.setSearchQuery(it) },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        when (val state = uiState) {
            is KoboUiState.Loading -> KoboLoadingState()
            is KoboUiState.NotConfigured -> Unit // handled above
            is KoboUiState.Empty -> KoboEmptyState(isOffline = isOffline, onSync = { viewModel.sync() })
            is KoboUiState.Error -> KoboErrorState(message = state.message, onRetry = { viewModel.sync() })
            is KoboUiState.Success -> {
                // Submissions arrive newest-first. Reassemble material-level Kobo records
                // into one TM/DRR daily project row before displaying the list.
                val visitGroups = remember(state.submissions) {
                    groupSubmissionsIntoVisits(state.submissions)
                }
                val grouped = remember(visitGroups) {
                    visitGroups.groupBy { KoboDateUtils.groupLabel(it.primary.submissionTime) }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    grouped.forEach { (dateLabel, visitsForDate) ->
                        item(key = "header_$dateLabel") {
                            Text(
                                text = dateLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                            )
                        }
                        items(visitsForDate, key = { it.primary.submissionId }) { visit ->
                            KoboSubmissionRow(
                                visit = visit,
                                onClick = { onOpenSubmission(visit.primary.submissionId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KoboSummaryCard(
    totalCached: Int,
    lastSyncTime: Long,
    isSyncing: Boolean,
    isOffline: Boolean,
    onSync: () -> Unit
) {
    val statusText: String
    val statusColor: Color
    val statusIcon: ImageVector
    when {
        isSyncing -> {
            statusText = "Syncing…"
            statusColor = MaterialTheme.colorScheme.primary
            statusIcon = Icons.Default.Sync
        }
        isOffline -> {
            statusText = "Offline — showing cached data"
            statusColor = StatusPending
            statusIcon = Icons.Default.CloudOff
        }
        lastSyncTime <= 0L -> {
            statusText = "Not synced yet"
            statusColor = MaterialTheme.colorScheme.onSurfaceVariant
            statusIcon = Icons.Default.HourglassTop
        }
        else -> {
            statusText = "Synced successfully"
            statusColor = StatusSynced
            statusIcon = Icons.Default.CheckCircle
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = totalCached.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Submissions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Last synced • ${formatSyncTime(lastSyncTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (isSyncing) {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
                }
            } else {
                FilledIconButton(
                    onClick = onSync,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync now",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun KoboSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search submissions", style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
    )
}

@Composable
private fun KoboNotConfiguredCard(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "KoboToolbox Data isn't configured yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Add your server URL, project/asset UID, and API token in Settings to start downloading submissions.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onOpenSettings) {
            Text("Open Kobo Settings")
        }
    }
}

@Composable
private fun KoboLoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun KoboEmptyState(isOffline: Boolean, onSync: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Inbox,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (isOffline) "No cached submissions yet" else "No submissions found",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isOffline) {
                "You're offline. Connect to the internet and sync to download data."
            } else {
                "Tap sync to download submissions from this KoboToolbox project."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (!isOffline) {
            Button(onClick = onSync) { Text("Sync Now") }
        }
    }
}

@Composable
private fun KoboErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = StatusFailed,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

/**
 * Compact submission row. Prioritizes business fields (recipient, material count, quantity, DRR code)
 * over the Kobo submission ID, which is no longer the headline – it's just a small internal
 * detail visible on the Details screen. Falls back to the old previewLabel() logic for forms
 * where no recognizable material field exists, so unknown forms still render sensibly.
 */
/**
 * One dispatch visit — a recipient receiving one or more materials under one DRR Code on one
 * day. Your Kobo form submits one record per material, so a single visit with several
 * materials arrives as several sibling submissions; this collapses them back into one row.
 */
private data class KoboVisitGroup(
    val primary: KoboSubmission,
    val title: String,
    val drrCode: String?,
    val summaryText: String?
)

/**
 * Groups Kobo records back into one daily project request.
 *
 * A single Kobo submission may represent one material for one recipient. Therefore,
 * several records can belong to the same DRR project request. The Kobo Data list should
 * show the TM Name only once for the same DRR Code on the same calendar day, while the
 * Submission Details screen expands that row into all recipients and materials.
 *
 * A later date is a new daily request, so the same DRR Code is intentionally shown again
 * when it belongs to a different calendar day.
 */
private fun groupSubmissionsIntoVisits(submissions: List<KoboSubmission>): List<KoboVisitGroup> {
    data class Classified(
        val submission: KoboSubmission,
        val summary: com.example.data.kobo.KoboCategorizedSubmission
    )

    val classified = submissions.map {
        Classified(it, KoboFieldClassifier.classify(it.toJson()))
    }

    val groups = LinkedHashMap<String, MutableList<Classified>>()
    val fallbackGroups = mutableListOf<Classified>()

    classified.forEach { item ->
        val drr = item.summary.drrCode
        if (drr.isNullOrBlank()) {
            // Without a DRR Code there is no safe project key, so keep the record separate.
            fallbackGroups += item
        } else {
            val dayKey = item.submission.submissionTime
                .let { KoboDateUtils.parse(it)?.let { date ->
                    java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
                } ?: it.take(10) }
            val key = "${drr.trim()}|$dayKey"
            groups.getOrPut(key) { mutableListOf() }.add(item)
        }
    }

    val result = mutableListOf<KoboVisitGroup>()

    fun toGroup(items: List<Classified>): KoboVisitGroup {
        val first = items.first()
        val materialNames = items.mapNotNull { it.summary.materialName }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.getDefault()) }
        val recipientNames = items.mapNotNull { it.summary.recipientName }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.getDefault()) }

        val summary = when {
            recipientNames.size > 1 && materialNames.size > 1 ->
                "${recipientNames.size} recipients • ${materialNames.size} materials"
            recipientNames.size > 1 ->
                "${recipientNames.size} recipients • ${materialNames.size.coerceAtLeast(1)} material"
            materialNames.size > 1 ->
                "${materialNames.size} materials"
            else -> first.summary.quantityText
        }

        return KoboVisitGroup(
            primary = first.submission,
            title = first.summary.tmName ?: first.summary.drrCode ?: first.submission.previewLabel(),
            drrCode = first.summary.drrCode,
            summaryText = summary
        )
    }

    // LinkedHashMap preserves the newest-first order supplied by the DAO.
    groups.values.forEach { result += toGroup(it) }
    fallbackGroups.forEach { result += toGroup(listOf(it)) }
    return result
}

@Composable
private fun KoboSubmissionRow(
    visit: KoboVisitGroup,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
            ) {
                Text(
                    text = visit.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                visit.summaryText?.let { summary ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (visit.drrCode != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = visit.drrCode,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                    Text(
                        text = KoboDateUtils.formatTimeOnly(visit.primary.submissionTime),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

private fun formatSyncTime(timestamp: Long): String {
    if (timestamp <= 0L) return "Never"
    return try {
        SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(Date(timestamp))
    } catch (_: Exception) {
        "Unknown"
    }
}
