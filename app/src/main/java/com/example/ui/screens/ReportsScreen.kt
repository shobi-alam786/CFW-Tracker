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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import com.example.data.model.DispatchDetail
import com.example.data.model.MaterialSummaryItem
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
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
    var dispatchDetails by remember { mutableStateOf<List<DispatchDetail>>(emptyList()) }

    LaunchedEffect(beneficiaries) {
        val list = mutableListOf<BeneficiaryFullDetails>()
        for (ben in beneficiaries) {
            val det = viewModel.repository.getBeneficiaryFullDetails(ben.id)
            if (det != null) list.add(det)
        }
        detailsList = list
        // Dispatch details only change when a dispatch is recorded/synced; reload alongside
        // beneficiaries so Reports always reflects the latest data.
        dispatchDetails = viewModel.repository.getAllDispatchDetails()
    }

    // --- Date filter (applies to every tab below) ---
    var fromDate by remember { mutableStateOf<String?>(null) }
    var toDate by remember { mutableStateOf<String?>(null) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    val filteredDetailsList = remember(detailsList, fromDate, toDate) {
        detailsList.filter { det -> isDateInRange(det.request?.requestDate, fromDate, toDate) }
    }
    val filteredDispatches = remember(dispatchDetails, fromDate, toDate) {
        dispatchDetails.filter { isDateInRange(it.dispatchDate, fromDate, toDate) }
    }

    // Material totals calculation (Material Summary tab)
    val materialSummaryMap = remember(filteredDetailsList) {
        val map = mutableMapOf<String, MaterialSummaryItem>()
        filteredDetailsList.forEach { det ->
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

        // Date Range Filter
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = { showFromPicker = true },
                    label = { Text(fromDate?.let { formatDisplayDate(it) } ?: "From date") },
                    leadingIcon = {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
                AssistChip(
                    onClick = { showToPicker = true },
                    label = { Text(toDate?.let { formatDisplayDate(it) } ?: "To date") },
                    leadingIcon = {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
                if (fromDate != null || toDate != null) {
                    IconButton(onClick = { fromDate = null; toDate = null }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear date filter")
                    }
                }
            }
        }

        item {
            when (selectedTab) {
                "Material Summary" -> MaterialSummaryCard(
                    materialSummaryMap = materialSummaryMap,
                    onExport = {
                        scope.launch {
                            val csvData = viewModel.repository.exportAllCsv()
                            shareCsvReport(context, csvData)
                        }
                    }
                )
                "Daily Dispatch" -> DailyDispatchCard(
                    dispatches = filteredDispatches,
                    hasDateFilter = fromDate != null || toDate != null
                )
                "Weekly" -> PeriodSummaryCard(
                    title = "Weekly Dispatch Summary",
                    dispatches = filteredDispatches,
                    groupBy = ::weekGroupInfo
                )
                "Monthly" -> PeriodSummaryCard(
                    title = "Monthly Dispatch Summary",
                    dispatches = filteredDispatches,
                    groupBy = ::monthGroupInfo
                )
                "TM Project Status" -> TmProjectStatusCard(detailsList = filteredDetailsList)
            }
        }
    }

    if (showFromPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = fromDate?.let { dateStrToMillis(it) })
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    fromDate = state.selectedDateMillis?.let { millisToDateStr(it) }
                    showFromPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showFromPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showToPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = toDate?.let { dateStrToMillis(it) })
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    toDate = state.selectedDateMillis?.let { millisToDateStr(it) }
                    showToPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showToPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

// --- Material Summary tab (unchanged content, now date-filtered upstream) ---
@Composable
private fun MaterialSummaryCard(
    materialSummaryMap: List<MaterialSummaryItem>,
    onExport: () -> Unit
) {
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
                onClick = onExport,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export Full CSV / Excel Audit File")
            }
        }
    }
}

// --- Daily Dispatch tab: every dispatch visit, grouped by day, newest first ---
@Composable
private fun DailyDispatchCard(
    dispatches: List<DispatchDetail>,
    hasDateFilter: Boolean
) {
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val grouped = remember(dispatches) {
        dispatches
            .sortedWith(compareByDescending<DispatchDetail> { it.dispatchDate }.thenByDescending { it.time })
            .groupBy { it.dispatchDate }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Daily Dispatch", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (!hasDateFilter) {
                Text(
                    "Showing all recorded dispatch visits. Use the date filter above to narrow this down.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (grouped.isEmpty()) {
                Text("No dispatch records for this period.", style = MaterialTheme.typography.bodyMedium)
            } else {
                grouped.forEach { (date, dayDispatches) ->
                    val label = try {
                        dateFmt.format(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date) ?: Date())
                    } catch (e: Exception) { date }

                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    dayDispatches.forEach { d ->
                        Column(modifier = Modifier.padding(bottom = 10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${d.recipientName} (DRR: ${d.drrCode})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                if (d.time.isNotBlank()) {
                                    Text(d.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            val materialsText = d.materials.joinToString(", ") { "${it.materialName}: ${cleanQty(it.quantity)} ${it.unit}" }
                            Text(
                                text = materialsText.ifBlank { "No materials recorded" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Collected by ${d.collectorName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(bottom = 10.dp))
                }
            }
        }
    }
}

// --- Weekly / Monthly tab: dispatches grouped into periods, material totals per period ---
private data class GroupInfo(val key: String, val label: String)

@Composable
private fun PeriodSummaryCard(
    title: String,
    dispatches: List<DispatchDetail>,
    groupBy: (String) -> GroupInfo
) {
    data class PeriodTotals(val label: String, val totals: Map<String, Pair<Double, String>>, val visitCount: Int)

    val periods = remember(dispatches) {
        val byKey = LinkedHashMap<String, MutableList<DispatchDetail>>()
        val labelByKey = mutableMapOf<String, String>()
        dispatches.forEach { d ->
            val info = groupBy(d.dispatchDate)
            byKey.getOrPut(info.key) { mutableListOf() }.add(d)
            labelByKey[info.key] = info.label
        }
        byKey.entries
            .sortedByDescending { it.key }
            .map { (key, list) ->
                val totals = LinkedHashMap<String, Pair<Double, String>>()
                list.forEach { d ->
                    d.materials.forEach { m ->
                        val current = totals[m.materialName]?.first ?: 0.0
                        totals[m.materialName] = (current + m.quantity) to m.unit
                    }
                }
                PeriodTotals(labelByKey[key] ?: key, totals, list.size)
            }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            if (periods.isEmpty()) {
                Text("No dispatch records for this period.", style = MaterialTheme.typography.bodyMedium)
            } else {
                periods.forEach { period ->
                    Text(
                        text = period.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${period.visitCount} dispatch visit(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    period.totals.forEach { (materialName, qtyUnit) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(materialName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(
                                "${cleanQty(qtyUnit.first)} ${qtyUnit.second}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                }
            }
        }
    }
}

// --- TM Project Status tab: one row per DRR / beneficiary ---
@Composable
private fun TmProjectStatusCard(detailsList: List<BeneficiaryFullDetails>) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("TM Project Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            if (detailsList.isEmpty()) {
                Text("No TM projects for this period.", style = MaterialTheme.typography.bodyMedium)
            } else {
                detailsList
                    .sortedBy { it.beneficiary.cfwName }
                    .forEach { det ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${det.beneficiary.cfwName} (${det.beneficiary.drrCode})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Approved ${cleanQty(det.totalApproved)} • Dispatched ${cleanQty(det.totalDispatched)} • Remaining ${cleanQty(det.totalRemaining)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                det.request?.requestDate?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            val isCompleted = det.status == "COMPLETED"
                            Text(
                                text = if (isCompleted) "Completed" else "Pending",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )
                        }
                        HorizontalDivider()
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

// --- Date filter / grouping helpers ---

/** True when [date] (YYYY-MM-DD) falls within [from]..[to] inclusive. A null bound is open. */
private fun isDateInRange(date: String?, from: String?, to: String?): Boolean {
    if (date.isNullOrBlank()) return from == null && to == null
    if (from != null && date < from) return false
    if (to != null && date > to) return false
    return true
}

private fun weekGroupInfo(dateStr: String): GroupInfo {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val date = fmt.parse(dateStr) ?: return GroupInfo(dateStr, dateStr)
    val cal = Calendar.getInstance().apply { time = date }
    // Normalize to Monday of this calendar week regardless of device locale's first day.
    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // Sunday=1 ... Saturday=7
    val offsetFromMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
    cal.add(Calendar.DAY_OF_YEAR, -offsetFromMonday)
    val weekStart = cal.time
    val key = fmt.format(weekStart)
    cal.add(Calendar.DAY_OF_YEAR, 6)
    val weekEnd = cal.time
    val dayLabelFmt = SimpleDateFormat("MMM d", Locale.getDefault())
    val yearFmt = SimpleDateFormat("yyyy", Locale.getDefault())
    val label = "${dayLabelFmt.format(weekStart)} - ${dayLabelFmt.format(weekEnd)}, ${yearFmt.format(weekEnd)}"
    return GroupInfo(key, label)
}

private fun monthGroupInfo(dateStr: String): GroupInfo {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val date = fmt.parse(dateStr) ?: return GroupInfo(dateStr, dateStr)
    val key = dateStr.take(7) // yyyy-MM
    val label = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(date)
    return GroupInfo(key, label)
}

private fun formatDisplayDate(dateStr: String): String = try {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val out = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    out.format(fmt.parse(dateStr) ?: Date())
} catch (e: Exception) {
    dateStr
}

/** DatePicker millis are UTC midnight of the selected day; parse/format in UTC to avoid off-by-one days. */
private fun millisToDateStr(millis: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    fmt.timeZone = TimeZone.getTimeZone("UTC")
    return fmt.format(Date(millis))
}

private fun dateStrToMillis(dateStr: String): Long? {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    fmt.timeZone = TimeZone.getTimeZone("UTC")
    return try { fmt.parse(dateStr)?.time } catch (e: Exception) { null }
}

/** Strips a pointless ".0" from whole numbers. */
private fun cleanQty(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
