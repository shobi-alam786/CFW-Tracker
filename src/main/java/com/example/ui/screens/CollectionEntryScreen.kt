package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BeneficiaryFullDetails
import com.example.data.model.CfwBeneficiary
import com.example.data.model.DefaultMaterials
import com.example.ui.theme.StatusPending
import com.example.ui.theme.StatusSynced
import com.example.ui.viewmodel.AppTab
import com.example.ui.viewmodel.MainViewModel

@Composable
fun CollectionEntryScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    initialDrrCode: String = ""
) {
    var selectedTabMode by remember { mutableIntStateOf(if (initialDrrCode.isNotBlank()) 1 else 0) } // 0: Register & Request, 1: Dispatch Materials

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTabMode,
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Tab(
                selected = selectedTabMode == 0,
                onClick = { selectedTabMode = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("1. Register & Request", fontWeight = FontWeight.Bold)
                    }
                }
            )
            Tab(
                selected = selectedTabMode == 1,
                onClick = { selectedTabMode = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("2. Dispatch Materials", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        if (selectedTabMode == 0) {
            RegisterAndRequestTab(
                viewModel = viewModel,
                initialDrrCode = initialDrrCode,
                onNavigateToDispatch = { selectedTabMode = 1 }
            )
        } else {
            DispatchMaterialsTab(
                viewModel = viewModel,
                initialDrrCode = initialDrrCode
            )
        }
    }
}

@Composable
fun RegisterAndRequestTab(
    viewModel: MainViewModel,
    initialDrrCode: String,
    onNavigateToDispatch: () -> Unit
) {
    var drrCode by remember { mutableStateOf(initialDrrCode) }
    var cfwName by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    var existingBeneficiary by remember { mutableStateOf<CfwBeneficiary?>(null) }

    val savedCustomMaterials by viewModel.customMaterials.collectAsState()
    val savedCustomUnits by viewModel.customUnits.collectAsState()

    // Materials the user has chosen for this request — starts empty until they pick some
    val approvedQuantities = remember {
        mutableStateMapOf<String, Pair<String, String>>()
    }

    // Controls for adding custom material row
    var showAddMaterialSection by remember { mutableStateOf(false) }
    var newMaterialName by remember { mutableStateOf("") }
    var newMaterialUnit by remember { mutableStateOf("") }
    var showMultiSelectDialog by remember { mutableStateOf(false) }

    // Combine all available materials for autocomplete/multi-select
    val allPredefinedNames = remember { DefaultMaterials.LIST.map { it.name } }
    val allKnownMaterialNames = remember(allPredefinedNames, savedCustomMaterials) {
        (allPredefinedNames + savedCustomMaterials.toList() + approvedQuantities.keys).distinct().sorted()
    }

    val defaultUnitsList = remember { DefaultMaterials.UNITS.filter { it != "Custom" } }
    val allKnownUnits = remember(defaultUnitsList, savedCustomUnits) {
        (defaultUnitsList + savedCustomUnits.toList()).distinct().sorted()
    }

    // Smart lookup check for existing DRR Code
    LaunchedEffect(drrCode) {
        if (drrCode.isNotBlank()) {
            val found = viewModel.repository.getBeneficiaryByDrrCode(drrCode)
            existingBeneficiary = found
            if (found != null) {
                cfwName = found.cfwName
            } else {
                // No match for this DRR code — clear any previously auto-filled values
                cfwName = ""
            }
        } else {
            existingBeneficiary = null
            cfwName = ""
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Step 1: Project Details & Approved Materials",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Create a project request using a unique DRR Code, TM Name, and approved material quantities.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Beneficiary Info Form Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Project Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (existingBeneficiary != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Existing DRR Code Found!",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "Reusing the existing project profile without creating a duplicate DRR Code.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = drrCode,
                        onValueChange = { drrCode = it.uppercase() },
                        label = { Text("DRR Code (Unique) *") },
                        placeholder = { Text("e.g. C013-A-2") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = cfwName,
                        onValueChange = { cfwName = it },
                        label = { Text("TM Name *") },
                        placeholder = { Text("e.g. John Doe") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Approved Quantities Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Approved Materials",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Row {
                            IconButton(onClick = { showMultiSelectDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = "Multi-Select Materials",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { showAddMaterialSection = !showAddMaterialSection }) {
                                Icon(
                                    imageVector = if (showAddMaterialSection) Icons.Default.Close else Icons.Default.Add,
                                    contentDescription = "Add Material",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Text(
                        text = "Set the approved quantity and unit for each material in this project request.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Expandable section for adding custom material row on the fly
                    AnimatedVisibility(visible = showAddMaterialSection) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Add Custom / Predefined Material",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                // Material Name with Autocomplete / Drop-down Suggestions
                                var matNameExpanded by remember { mutableStateOf(false) }
                                val materialSuggestions = remember(newMaterialName, allKnownMaterialNames) {
                                    if (newMaterialName.isBlank()) emptyList()
                                    else allKnownMaterialNames.filter {
                                        it.contains(newMaterialName, ignoreCase = true) && it != newMaterialName
                                    }.take(5)
                                }

                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = newMaterialName,
                                        onValueChange = { input ->
                                            newMaterialName = input
                                            matNameExpanded = input.isNotBlank()
                                            // Auto-suggest unit if matching predefined material
                                            val matched = DefaultMaterials.LIST.find { it.name.equals(input.trim(), ignoreCase = true) }
                                            if (matched != null && newMaterialUnit.isBlank()) {
                                                newMaterialUnit = matched.defaultUnit
                                            }
                                        },
                                        label = { Text("Material Name *") },
                                        placeholder = { Text("e.g. Solar Panel, Bamboo") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    DropdownMenu(
                                        expanded = matNameExpanded && materialSuggestions.isNotEmpty(),
                                        onDismissRequest = { matNameExpanded = false },
                                        modifier = Modifier.fillMaxWidth(0.9f),
                                        properties = PopupProperties(focusable = false)
                                    ) {
                                        materialSuggestions.forEach { suggestion ->
                                            DropdownMenuItem(
                                                text = { Text(suggestion) },
                                                onClick = {
                                                    newMaterialName = suggestion
                                                    matNameExpanded = false
                                                    val matched = DefaultMaterials.LIST.find { it.name.equals(suggestion, ignoreCase = true) }
                                                    if (matched != null) {
                                                        newMaterialUnit = matched.defaultUnit
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Unit Field with Autocomplete / Drop-down Suggestions
                                var unitExpanded by remember { mutableStateOf(false) }
                                val unitSuggestions = remember(newMaterialUnit, allKnownUnits) {
                                    if (newMaterialUnit.isBlank()) allKnownUnits
                                    else allKnownUnits.filter {
                                        it.contains(newMaterialUnit, ignoreCase = true)
                                    }
                                }

                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = newMaterialUnit,
                                        onValueChange = { input ->
                                            newMaterialUnit = input
                                            unitExpanded = true
                                        },
                                        label = { Text("Unit *") },
                                        placeholder = { Text("e.g. Bag, Pieces, Feet, Roll") },
                                        trailingIcon = {
                                            IconButton(onClick = { unitExpanded = !unitExpanded }) {
                                                Icon(Icons.Default.Add, contentDescription = "Select Unit")
                                            }
                                        },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    DropdownMenu(
                                        expanded = unitExpanded && unitSuggestions.isNotEmpty(),
                                        onDismissRequest = { unitExpanded = false },
                                        modifier = Modifier.fillMaxWidth(0.9f),
                                        properties = PopupProperties(focusable = false)
                                    ) {
                                        unitSuggestions.forEach { unitOpt ->
                                            DropdownMenuItem(
                                                text = { Text(unitOpt) },
                                                onClick = {
                                                    newMaterialUnit = unitOpt
                                                    unitExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "Approved Quantity for new materials starts blank.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = {
                                        newMaterialName = ""
                                        newMaterialUnit = ""
                                        showAddMaterialSection = false
                                    }) {
                                        Text("Cancel")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            val cleanMat = newMaterialName.trim()
                                            val cleanUnit = newMaterialUnit.trim().ifBlank { "Pieces" }
                                            if (cleanMat.isNotBlank()) {
                                                val existingKey = approvedQuantities.keys.find {
                                                    it.equals(cleanMat, ignoreCase = true)
                                                }
                                                if (existingKey != null) {
                                                    // Already present — update unit instead of creating a duplicate row
                                                    approvedQuantities[existingKey] = Pair(
                                                        approvedQuantities[existingKey]?.first ?: "",
                                                        cleanUnit
                                                    )
                                                } else {
                                                    // Quantity starts BLANK as required
                                                    approvedQuantities[cleanMat] = Pair("", cleanUnit)
                                                }
                                                viewModel.addCustomMaterial(cleanMat)
                                                viewModel.addCustomUnit(cleanUnit)
                                                newMaterialName = ""
                                                newMaterialUnit = ""
                                                showAddMaterialSection = false
                                            }
                                        },
                                        enabled = newMaterialName.isNotBlank()
                                    ) {
                                        Text("Add Row")
                                    }
                                }
                            }
                        }
                    }

                    // Approved quantities list display
                    if (approvedQuantities.isEmpty()) {
                        Text(
                            text = "No materials selected yet. Tap the filter icon above to choose materials, or + to add a custom one.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    approvedQuantities.keys.forEach { matName ->
                        val pair = approvedQuantities[matName] ?: Pair("", "Pieces")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = matName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = pair.first,
                                onValueChange = { newQty ->
                                    approvedQuantities[matName] = Pair(newQty, pair.second)
                                },
                                label = { Text("Qty", maxLines = 1) },
                                placeholder = { Text("0") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1.3f)
                            )

                            // Unit box
                            Box(
                                modifier = Modifier
                                    .weight(0.7f)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = pair.second,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Remove row button
                            IconButton(
                                onClick = { approvedQuantities.remove(matName) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove Material",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (validationError != null) {
            item {
                Text(
                    text = validationError ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            Button(
                onClick = {
                    val cleanDrr = drrCode.trim()
                    val cleanName = cfwName.trim()

                    if (cleanDrr.isBlank()) {
                        validationError = "Error: DRR Code is required."
                        return@Button
                    }
                    if (cleanName.isBlank()) {
                        validationError = "Error: TM Name is required."
                        return@Button
                    }

                    val approvedList = approvedQuantities.map { (name, pair) ->
                        name to Pair(pair.first.toDoubleOrNull() ?: 0.0, pair.second)
                    }

                    validationError = null
                    viewModel.registerBeneficiaryAndRequest(
                        drrCode = cleanDrr,
                        cfwName = cleanName,
                        fcnNumber = "",
                        approvedMaterials = approvedList,
                        onSuccess = {
                            onNavigateToDispatch()
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save & Proceed to Dispatch")
            }
        }
    }

    // Multi-select materials modal dialog
    if (showMultiSelectDialog) {
        val allAvailableForSelect = remember(allKnownMaterialNames) {
            allKnownMaterialNames
        }
        val tempSelected = remember {
            mutableStateMapOf<String, Boolean>().apply {
                allAvailableForSelect.forEach { mat ->
                    put(mat, approvedQuantities.containsKey(mat))
                }
            }
        }

        androidx.compose.ui.window.Dialog(onDismissRequest = { showMultiSelectDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Multi-Select Materials",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Check all materials to include in this request.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    ) {
                        items(allAvailableForSelect) { mat ->
                            val isChecked = tempSelected[mat] ?: false
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { tempSelected[mat] = !isChecked }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { tempSelected[mat] = it }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = mat,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showMultiSelectDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            tempSelected.forEach { (mat, selected) ->
                                if (selected && !approvedQuantities.containsKey(mat)) {
                                    val defaultUnit = DefaultMaterials.LIST.find { it.name.equals(mat, ignoreCase = true) }?.defaultUnit ?: "Pieces"
                                    approvedQuantities[mat] = Pair("", defaultUnit)
                                } else if (!selected && approvedQuantities.containsKey(mat)) {
                                    approvedQuantities.remove(mat)
                                }
                            }
                            showMultiSelectDialog = false
                        }) {
                            Text("Apply")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DispatchMaterialsTab(
    viewModel: MainViewModel,
    initialDrrCode: String
) {
    val allBeneficiaries by viewModel.allBeneficiaries.collectAsState()

    var selectedBeneficiaryId by remember { mutableStateOf<Long?>(null) }
    var searchCode by remember { mutableStateOf(initialDrrCode) }
    var beneficiaryDetails by remember { mutableStateOf<BeneficiaryFullDetails?>(null) }

    // Map of material ID -> Quantity To Dispatch string
    val dispatchQtyMap = remember { mutableStateMapOf<Long, String>() }
    var dispatchNote by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    var showRecipientQrScanner by remember { mutableStateOf(false) }

    // Who is actually receiving materials on THIS visit — captured fresh each time since one
    // DRR Code project can dispatch to different people on different days.
    var recipientName by remember { mutableStateOf("") }
    var recipientFcn by remember { mutableStateOf("") }

    // Only projects with a request that isn't fully dispatched yet should show in the picker
    var pendingBeneficiaries by remember { mutableStateOf<List<CfwBeneficiary>>(emptyList()) }
    LaunchedEffect(allBeneficiaries) {
        pendingBeneficiaries = allBeneficiaries.filter { ben ->
            viewModel.repository.getBeneficiaryFullDetails(ben.id)?.isCompleted != true
        }
    }

    // Autoload details when beneficiary ID or search code changes
    LaunchedEffect(searchCode, allBeneficiaries) {
        selectedBeneficiaryId = if (searchCode.isNotBlank()) {
            viewModel.repository.getBeneficiaryByDrrCode(searchCode)?.id
        } else {
            null
        }
    }

    LaunchedEffect(selectedBeneficiaryId) {
        val bId = selectedBeneficiaryId
        if (bId != null) {
            val details = viewModel.repository.getBeneficiaryFullDetails(bId)
            beneficiaryDetails = details
            dispatchQtyMap.clear()
            // Leave the map empty for each remaining item so the field starts blank (with
            // a "0" placeholder shown, not an actual pre-filled value the user has to delete).
            details?.requestedMaterials?.forEach { reqMatStatus ->
                if (reqMatStatus.remainingQuantity > 0) {
                    dispatchQtyMap[reqMatStatus.material.id] = ""
                }
            }
            // Reset recipient fields for the new beneficiary/visit — never carry over the
            // previous person's name into a different DRR's dispatch.
            recipientName = ""
            recipientFcn = ""
        } else {
            beneficiaryDetails = null
        }
    }

    if (showRecipientQrScanner) {
        RecipientQrScannerScreen(
            onBack = {
                showRecipientQrScanner = false
            },
            onRecipientScanned = { name, fcn ->
                recipientName = name
                recipientFcn = fcn
                validationError = null
                showRecipientQrScanner = false
            }
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Step 2: Dispatch Materials",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Select a DRR Code, enter Recipient Information, and dispatch the required materials for this visit.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Beneficiary Lookup Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Select Project to Dispatch",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = searchCode,
                        onValueChange = { searchCode = it.uppercase() },
                        label = { Text("Search by DRR Code") },
                        placeholder = { Text("e.g. DRR-1001") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (pendingBeneficiaries.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Or select a project from the list:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            pendingBeneficiaries.forEach { ben ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (selectedBeneficiaryId == ben.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            selectedBeneficiaryId = ben.id
                                            searchCode = ben.drrCode
                                        }
                                        .padding(horizontal = 10.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = "${ben.drrCode} • TM Name: ${ben.cfwName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selectedBeneficiaryId == ben.id) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Beneficiary Selected Card & Remaining Materials Form
        val details = beneficiaryDetails
        if (details != null) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = details.beneficiary.cfwName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "DRR Code: ${details.beneficiary.drrCode} • TM Name: ${details.beneficiary.cfwName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Box(
                                modifier = Modifier
                                    .background(
                                        if (details.isCompleted) StatusSynced else StatusPending,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (details.isCompleted) "Completed" else "Pending",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            val remainingMaterials = details.requestedMaterials.filter { it.remainingQuantity > 0 }

            if (remainingMaterials.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSynced, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "All approved materials for this request have been fully dispatched!",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Project status is marked as COMPLETED.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Recipient Information",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )

                                IconButton(
                                    onClick = {
                                        showRecipientQrScanner = true
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = "Scan Recipient QR Code",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Enter the person who is receiving the materials for this dispatch visit.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = recipientName,
                                onValueChange = { recipientName = it },
                                label = { Text("Recipient Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = recipientFcn,
                                onValueChange = { recipientFcn = it },
                                label = { Text("Recipient FCN Number") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                item {
                    Text(
                        text = "Materials Available",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(remainingMaterials) { matStatus ->
                    val matId = matStatus.material.id
                    val qtyStr = dispatchQtyMap[matId] ?: ""
                    val qtyVal = qtyStr.toDoubleOrNull() ?: 0.0
                    val isExceeded = qtyVal > matStatus.remainingQuantity + 0.0001

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isExceeded) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isExceeded) 2.dp else 1.dp,
                                color = if (isExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(14.dp)
                            )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = matStatus.material.materialName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Remaining: ${matStatus.remainingQuantity} ${matStatus.material.unit}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Approved: ${matStatus.material.approvedQuantity} ${matStatus.material.unit} • Already Dispatched: ${matStatus.totalDispatched} ${matStatus.material.unit}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = qtyStr,
                                    onValueChange = { dispatchQtyMap[matId] = it },
                                    label = { Text("Quantity to Dispatch") },
                                    placeholder = { Text("0") },
                                    isError = isExceeded,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedButton(
                                    onClick = { dispatchQtyMap[matId] = matStatus.remainingQuantity.toString() }
                                ) {
                                    Text("Max All (${matStatus.remainingQuantity})", fontSize = 11.sp)
                                }
                            }

                            if (isExceeded) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Only ${matStatus.remainingQuantity} ${matStatus.material.unit} of ${matStatus.material.materialName} remain. Dispatch quantity cannot exceed remaining balance.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = dispatchNote,
                        onValueChange = { dispatchNote = it },
                        label = { Text("Visit Remarks / Notes") },
                        placeholder = { Text("e.g. Visit 2 shelter framing distribution") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (validationError != null) {
                    item {
                        Text(
                            text = validationError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                item {
                    val reqId = details.request?.id
                    Button(
                        onClick = {
                            if (reqId == null) {
                                validationError = "Error: No material request found."
                                return@Button
                            }

                            if (recipientName.isBlank()) {
                                validationError = "Error: Please enter the Recipient Name."
                                return@Button
                            }

                            if (recipientFcn.isBlank()) {
                                validationError = "Error: Please enter the Recipient FCN Number."
                                return@Button
                            }

                            val dispatchesToSave = dispatchQtyMap.mapNotNull { (matId, qtyStr) ->
                                val qty = qtyStr.toDoubleOrNull() ?: 0.0
                                if (qty > 0) matId to qty else null
                            }

                            if (dispatchesToSave.isEmpty()) {
                                validationError = "Error: Please enter at least one dispatch quantity > 0."
                                return@Button
                            }

                            // Strict check before submit
                            for ((matId, qty) in dispatchesToSave) {
                                val matStatus = details.requestedMaterials.find { it.material.id == matId }
                                if (matStatus != null && qty > matStatus.remainingQuantity + 0.0001) {
                                    validationError = "Error: Cannot dispatch $qty ${matStatus.material.unit} of ${matStatus.material.materialName}. Maximum remaining is ${matStatus.remainingQuantity}."
                                    return@Button
                                }
                            }

                            validationError = null
                            viewModel.createDispatchRecord(
                                requestId = reqId,
                                recipientName = recipientName,
                                recipientFcn = recipientFcn,
                                dispatches = dispatchesToSave,
                                note = dispatchNote,
                                onSuccess = {
                                    viewModel.selectTab(AppTab.DASHBOARD)
                                },
                                onError = { msg ->
                                    validationError = msg
                                }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(Icons.Default.LocalShipping, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Dispatch Remaining Materials", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (searchCode.isNotBlank()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No project found for '$searchCode'. Create the project in Step 1 first.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}