package com.example.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.viewmodel.ProjectViewModel

/**
 * Hosts every CFW MaterialFlow project-workflow screen behind one entry point, the same way
 * the existing Kobo Data screen is shown as a full-screen overlay — so the app's existing
 * 5-tab bottom navigation (Dashboard/New Entry/Search/Reports/Settings) is left untouched.
 */
@Composable
fun ProjectWorkflowScreen(onBack: () -> Unit) {
    val projectViewModel: ProjectViewModel = viewModel()
    // 0 Overview, 1 Project Info, 2 Engineer, 3 Boss, 4 TM Request, 5 Warehouse, 6 Gate, 7 Alerts
    var selectedTab by remember { mutableIntStateOf(0) }
    val unreadCount by projectViewModel.unreadNotificationCount.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text("Project Workflow", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 8.dp) {
            TabItem(0, selectedTab, { selectedTab = 0 }, Icons.Default.Dashboard, "Overview")
            TabItem(1, selectedTab, { selectedTab = 1 }, Icons.Default.Info, "Project Info")
            TabItem(2, selectedTab, { selectedTab = 2 }, Icons.Default.Engineering, "Engineer")
            TabItem(3, selectedTab, { selectedTab = 3 }, Icons.Default.Gavel, "Boss")
            TabItem(4, selectedTab, { selectedTab = 4 }, Icons.Default.Send, "TM Request")
            TabItem(5, selectedTab, { selectedTab = 5 }, Icons.Default.LocalShipping, "Warehouse")
            TabItem(6, selectedTab, { selectedTab = 6 }, Icons.Default.CheckCircle, "Gate")
            Tab(
                selected = selectedTab == 7,
                onClick = { selectedTab = 7 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (unreadCount > 0) {
                            BadgedBox(badge = { Badge { Text(unreadCount.toString()) } }) {
                                Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.height(18.dp))
                            }
                        } else {
                            Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.height(18.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Alerts")
                    }
                }
            )
        }

        when (selectedTab) {
            0 -> ProjectOverviewScreen(viewModel = projectViewModel)
            1 -> ProjectDetailsScreen(viewModel = projectViewModel)
            2 -> EngineerEstimateScreen(viewModel = projectViewModel)
            3 -> BossApprovalScreen(viewModel = projectViewModel)
            4 -> DailyRequestScreen(viewModel = projectViewModel)
            5 -> WarehouseDispatchScreen(viewModel = projectViewModel)
            6 -> GateConfirmationScreen(viewModel = projectViewModel)
            7 -> NotificationsScreen(viewModel = projectViewModel)
        }
    }
}

@Composable
private fun TabItem(
    index: Int,
    selectedTab: Int,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Tab(
        selected = selectedTab == index,
        onClick = onClick,
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.height(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(label)
            }
        }
    )
}
