package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.BulkImportDialog
import com.example.ui.screens.CollectionEntryScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.KoboDataScreen
import com.example.ui.screens.KoboSubmissionDetailScreen
import com.example.ui.screens.PersonProfileScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CfwTrackerTheme
import com.example.ui.viewmodel.AppTab
import com.example.ui.viewmodel.KoboViewModel
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val currentTheme by mainViewModel.userPreferences.theme.collectAsState()

            CfwTrackerTheme(darkTheme = currentTheme == "DARK") {
                CfwAppMainContent(viewModel = mainViewModel)
            }
        }
    }
}

data class NavItem(
    val tab: AppTab,
    val title: String,
    val icon: ImageVector
)

@Composable
fun CfwAppMainContent(viewModel: MainViewModel) {
    val currentTab by viewModel.currentTab.collectAsState()
    val selectedBeneficiaryId by viewModel.selectedBeneficiaryId.collectAsState()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsState()
    val userNotice by viewModel.userNotice.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    var showBulkImportDialog by remember { mutableStateOf(false) }
    var initialEntryDrrCode by remember { mutableStateOf("") }

    // KoboToolbox Data feature: shown as a full-screen overlay, same pattern as the
    // beneficiary Person Profile screen below, so it doesn't disturb the bottom nav tabs.
    val koboViewModel: KoboViewModel = viewModel()
    var showKoboData by remember { mutableStateOf(false) }
    var selectedKoboSubmissionId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(userNotice) {
        userNotice?.let { notice ->
            snackbarHostState.showSnackbar(notice)
            viewModel.clearNotice()
        }
    }

    val navItems = listOf(
        NavItem(AppTab.DASHBOARD, "Dashboard", Icons.Default.Dashboard),
        NavItem(AppTab.NEW_ENTRY, "New Entry", Icons.Default.AddBox),
        NavItem(AppTab.SEARCH, "Search", Icons.Default.Search),
        NavItem(AppTab.REPORTS, "Reports", Icons.Default.BarChart),
        NavItem(AppTab.SETTINGS, "Settings", Icons.Default.Settings)
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (selectedBeneficiaryId == null && !showKoboData) {
                NavigationBar {
                    navItems.forEach { item ->
                        val isSelected = currentTab == item.tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { viewModel.selectTab(item.tab) },
                            icon = {
                                if (item.tab == AppTab.DASHBOARD && pendingSyncCount > 0) {
                                    BadgedBox(badge = { Badge { Text(pendingSyncCount.toString()) } }) {
                                        Icon(imageVector = item.icon, contentDescription = item.title)
                                    }
                                } else {
                                    Icon(imageVector = item.icon, contentDescription = item.title)
                                }
                            },
                            label = {
                                Text(item.title)
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (showKoboData) {
                if (selectedKoboSubmissionId != null) {
                    KoboSubmissionDetailScreen(
                        submissionId = selectedKoboSubmissionId!!,
                        viewModel = koboViewModel,
                        onBack = { selectedKoboSubmissionId = null }
                    )
                } else {
                    KoboDataScreen(
                        viewModel = koboViewModel,
                        onOpenSubmission = { id -> selectedKoboSubmissionId = id },
                        onOpenSettings = {
                            showKoboData = false
                            viewModel.selectTab(AppTab.SETTINGS)
                        },
                        onBack = { showKoboData = false }
                    )
                }
            } else if (selectedBeneficiaryId != null) {
                PersonProfileScreen(
                    cfwId = selectedBeneficiaryId!!,
                    viewModel = viewModel,
                    onBack = { viewModel.closePersonProfile() },
                    onAddNewCollection = { drr ->
                        viewModel.closePersonProfile()
                        initialEntryDrrCode = drr
                        viewModel.selectTab(AppTab.NEW_ENTRY)
                    }
                )
            } else {
                when (currentTab) {
                    AppTab.DASHBOARD -> DashboardScreen(
                        viewModel = viewModel,
                        onOpenBulkImport = { showBulkImportDialog = true },
                        onOpenKoboData = {
                            selectedKoboSubmissionId = null
                            showKoboData = true
                        }
                    )
                    AppTab.NEW_ENTRY -> CollectionEntryScreen(
                        viewModel = viewModel,
                        initialDrrCode = initialEntryDrrCode
                    )
                    AppTab.SEARCH -> SearchScreen(
                        viewModel = viewModel
                    )
                    AppTab.REPORTS -> ReportsScreen(
                        viewModel = viewModel
                    )
                    AppTab.SETTINGS -> SettingsScreen(
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    if (showBulkImportDialog) {
        BulkImportDialog(
            onDismiss = { showBulkImportDialog = false },
            onImportCsv = { csv ->
                viewModel.bulkImportCsv(csv)
            }
        )
    }
}
