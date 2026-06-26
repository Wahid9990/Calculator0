package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import android.content.Context
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ProductPreset
import com.example.utils.Exporters
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridCalculatorScreen(
    viewModel: GridViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val multiState by viewModel.gridState.collectAsStateWithLifecycle()

    // Selections for row/col deletion and highlighting
    val selectedRowId by viewModel.selectedRowId.collectAsStateWithLifecycle()
    val selectedColId by viewModel.selectedColId.collectAsStateWithLifecycle()
    val selectedRowSheetId by viewModel.selectedRowSheetId.collectAsStateWithLifecycle()
    val selectedColSheetId by viewModel.selectedColSheetId.collectAsStateWithLifecycle()

    // Dialog state variables
    var showAddColForSheetId by remember { mutableStateOf<Long?>(null) }
    var showAddRowForSheetId by remember { mutableStateOf<Long?>(null) }
    var showEditColState by remember { mutableStateOf<Pair<Long, Double>?>(null) } // ColId, Rate
    var showEditRowState by remember { mutableStateOf<Long?>(null) } // RowId
    var activeCellEditState by remember { mutableStateOf<Pair<Long, Long>?>(null) } // RowId, ColId
    var showTableSettingsId by remember { mutableStateOf<Long?>(null) }

    // Product and Price presets collected from the room database
    val productPresets by viewModel.productPresets.collectAsStateWithLifecycle()
    var showRatePresetsDialog by remember { mutableStateOf(false) }
    var showEditColPriceDialogState by remember { mutableStateOf<Long?>(null) } // ColId
    var editingPresetState by remember { mutableStateOf<ProductPreset?>(null) }
    var showPresetUpdateConfirmationState by remember { mutableStateOf<Pair<ProductPreset, String>?>(null) } // (New Preset state, Old Name)

    // Level 1 Sheets dialogs
    var showAddParentSheetDialog by remember { mutableStateOf(false) }
    var showRenameParentSheetDialog by remember { mutableStateOf<Long?>(null) }
    var showDeleteParentSheetDialog by remember { mutableStateOf<Long?>(null) }

    // Level 2 Tables dialogs
    var showAddSheetDialog by remember { mutableStateOf(false) }
    var showRenameSheetDialog by remember { mutableStateOf<Long?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }

    // Export PDF Selection state
    var showExportPdfDialog by remember { mutableStateOf(false) }
    var selectedPdfTableIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    // Tools & Combined Summary overlay state
    var showToolsMenuSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Multi-Table Billing Grid",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Connected Production Spreadsheets",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                },
                actions = {
                    // Combined Tools and Summary menu trigger
                    IconButton(
                        onClick = { showToolsMenuSheet = true },
                        modifier = Modifier.testTag("menu_sheet_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (multiState.sheetsData.size > 1) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ) {
                                        Text(multiState.sheetsData.size.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Open Tools & Aggregate Summary",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // Global Price / Rate Presets Settings button
                    IconButton(
                        onClick = { showRatePresetsDialog = true },
                        modifier = Modifier.testTag("appbar_presets_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Manage Predefined Prices",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF7F9FA))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Horizontal sticky Parent Sheets tab selector row (Level 1)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        multiState.parentSheets.forEach { parent ->
                            val isSelected = parent.id == multiState.activeParentSheetId

                            Surface(
                                onClick = { viewModel.selectParentSheet(parent.id) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(
                                    width = 1.6.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                ),
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = parent.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (isSelected) {
                                        IconButton(
                                            onClick = { showRenameParentSheetDialog = parent.id },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Rename",
                                                modifier = Modifier.size(12.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        if (multiState.parentSheets.size > 1) {
                                            IconButton(
                                                onClick = { showDeleteParentSheetDialog = parent.id },
                                                modifier = Modifier.size(18.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    modifier = Modifier.size(12.dp),
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = { showAddParentSheetDialog = true },
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add New Sheet",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                if (multiState.sheetsData.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TableChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(72.dp)
                            )
                            Text(
                                text = "No Tables in this Sheet",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Button(
                                onClick = { showAddSheetDialog = true }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Create First Table")
                            }
                        }
                    }
                } else {
                // Vertical list of all active, connected tables on the spreadsheet screen canvas
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
                ) {
                    item {
                        val activeParentSheet = remember(multiState.parentSheets, multiState.activeParentSheetId) {
                            multiState.parentSheets.find { it.id == multiState.activeParentSheetId }
                        }
                        val activeSheetName = activeParentSheet?.name ?: "Sheet"

                        // Active Sheet Summary Dashboard Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "$activeSheetName Summary",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "Aggregate summary of all tables",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
 
                                    // Small visual counts badge for active sheet
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ) {
                                        Text(
                                            text = "${multiState.sheetsData.size} Tables",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
 
                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                Spacer(modifier = Modifier.height(14.dp))
 
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Total Tables",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${multiState.sheetsData.size}",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
 
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "Net Grand Total",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = String.format("%.2f", multiState.activeSheetGrandTotal),
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    items(multiState.sheetsData, key = { it.sheet.id }) { sData ->
                        val sheet = sData.sheet
                        val columns = sData.columns
                        val rows = sData.rows
                        val cellsMap = sData.cellsMap

                        // Local individual totals calculations for this sheet
                        var totalPiecesLocal = 0
                        var grandTotalLocal = 0.0
                        rows.forEach { row ->
                            columns.forEach { col ->
                                val qty = cellsMap[Pair(row.id, col.id)] ?: 0
                                totalPiecesLocal += qty
                                grandTotalLocal += qty * col.rate
                            }
                        }

                        // Sheet Card container
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                // 1. Local Table Header Banner
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.TableChart,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = sheet.name,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 17.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Local edits quick triggers
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        IconButton(
                                            onClick = { showRenameSheetDialog = sheet.id },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Rename Table",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        if (multiState.sheetsData.size > 1) {
                                            IconButton(
                                                onClick = { viewModel.deleteSheet(sheet.id) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Table",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // 2. Local Table Actions ribbon (Add Row, Column)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { showAddRowForSheetId = sheet.id },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(34.dp)
                                            .testTag("add_row_${sheet.id}")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Add Row", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = { showAddColForSheetId = sheet.id },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(34.dp)
                                            .testTag("add_column_${sheet.id}")
                                    ) {
                                        Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Add Column", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // Selection Context Actions for Row Deletion
                                    AnimatedVisibility(
                                        visible = (selectedRowId != null && selectedRowSheetId == sheet.id) || 
                                                (selectedColId != null && selectedColSheetId == sheet.id)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                if (selectedRowId != null) {
                                                    viewModel.deleteSelectedRow()
                                                } else {
                                                    viewModel.deleteSelectedColumn()
                                                }
                                            },
                                            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteForever,
                                                contentDescription = "Delete Selection",
                                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // 3. The Spreadsheet Scrollable Grid
                                if (rows.isEmpty() && columns.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .border(
                                                1.dp,
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(20.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Empty Table. Add rows or columns to calculate.",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.outline,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .border(
                                                width = 1.5.dp,
                                                color = MaterialTheme.colorScheme.outline,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clip(RoundedCornerShape(8.dp))
                                    ) {
                                        Column {
                                            // --- Table Header row ---
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                // Top corner cell
                                                Box(
                                                    modifier = Modifier
                                                        .width(120.dp)
                                                        .height(50.dp)
                                                        .background(MaterialTheme.colorScheme.primary)
                                                        .border(1.dp, MaterialTheme.colorScheme.outline)
                                                        .padding(8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "Row Date \\ Prices",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                }
 
                                                // Column rate headers
                                                columns.forEach { col ->
                                                    val isColSelected = selectedColId == col.id && selectedColSheetId == sheet.id
                                                    Box(
                                                        modifier = Modifier
                                                            .width(105.dp)
                                                            .height(50.dp)
                                                            .background(
                                                                if (isColSelected) MaterialTheme.colorScheme.secondaryContainer
                                                                else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                                            )
                                                            .border(
                                                                width = if (isColSelected) 1.5.dp else 1.dp,
                                                                color = if (isColSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                                            )
                                                            .clickable {
                                                                // Open Price Presets Select Menu directly on tap of the column cell in top row
                                                                showEditColPriceDialogState = col.id
                                                            }
                                                            .padding(4.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                            Text(
                                                                text = col.name,
                                                                fontWeight = FontWeight.Black,
                                                                fontSize = 11.sp,
                                                                textAlign = TextAlign.Center,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                                            )
                                                            Text(
                                                                text = col.rate.toString(),
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Black,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                    }
                                                }
 
                                                // Row cumulative total header cell on right
                                                Box(
                                                    modifier = Modifier
                                                        .width(110.dp)
                                                        .height(50.dp)
                                                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                                                        .border(1.dp, MaterialTheme.colorScheme.outline)
                                                        .padding(8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "Row Cash Sum",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                                    )
                                                }
                                            }
 
                                            // --- Table Data rows ---
                                            rows.forEach { row ->
                                                val isRowSelected = selectedRowId == row.id && selectedRowSheetId == sheet.id
                                                var rowSum = 0.0
 
                                                Row(
                                                    modifier = Modifier.background(
                                                        if (isRowSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                                        else Color.Transparent
                                                    ),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Date Item row header cell
                                                    Box(
                                                        modifier = Modifier
                                                            .width(120.dp)
                                                            .height(44.dp)
                                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                                            .border(
                                                                width = if (isRowSelected) 1.5.dp else 1.dp,
                                                                color = if (isRowSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                                            )
                                                            .clickable {
                                                                // Select row
                                                                viewModel.selectRow(sheet.id, row.id)
                                                            }
                                                            .padding(horizontal = 6.dp),
                                                        contentAlignment = Alignment.CenterStart
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = if (row.dateString.isBlank()) "Select Date" else row.dateString,
                                                                fontSize = 12.sp,
                                                                fontWeight = if (row.dateString.isBlank()) FontWeight.Normal else FontWeight.SemiBold,
                                                                color = if (row.dateString.isBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            IconButton(
                                                                onClick = { showEditRowState = row.id },
                                                                modifier = Modifier.size(24.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.EditCalendar,
                                                                    contentDescription = "Edit Date",
                                                                    modifier = Modifier.size(12.dp),
                                                                    tint = MaterialTheme.colorScheme.outline
                                                                )
                                                            }
                                                        }
                                                    }

                                                    // Cells quantities inputs
                                                    columns.forEach { col ->
                                                        val qty = cellsMap[Pair(row.id, col.id)] ?: 0
                                                        rowSum += qty * col.rate

                                                        val isHighlighted = qty > 0
                                                        val cellBgColor = if (isHighlighted) Color(0xFFDCFCE7) else Color.White

                                                        Box(
                                                            modifier = Modifier
                                                                .width(105.dp)
                                                                .height(44.dp)
                                                                .background(cellBgColor)
                                                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                                                .clickable {
                                                                    activeCellEditState = Pair(row.id, col.id)
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = if (qty > 0) qty.toString() else "-",
                                                                fontWeight = if (isHighlighted) FontWeight.ExtraBold else FontWeight.Medium,
                                                                fontSize = 13.sp,
                                                                color = if (isHighlighted) Color(0xFF15803D) else Color(0xFF94A3B8)
                                                            )
                                                        }
                                                    }

                                                    // Row accum total numeric cell
                                                    Box(
                                                        modifier = Modifier
                                                            .width(110.dp)
                                                            .height(44.dp)
                                                            .background(Color(0xFFFEF08A))
                                                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                                            .padding(horizontal = 8.dp),
                                                        contentAlignment = Alignment.CenterEnd
                                                    ) {
                                                        Text(
                                                            text = String.format("%.2f", rowSum),
                                                            fontWeight = FontWeight.ExtraBold,
                                                            fontSize = 12.sp,
                                                            color = Color(0xFF854D0E)
                                                        )
                                                    }
                                                }
                                            }

                                            // --- Table Totals block ---
                                            // 2. Total Price Row (Table grand totals in the corner)
                                            Row(
                                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(120.dp)
                                                        .height(40.dp)
                                                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                                        .padding(horizontal = 6.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    Text("Total Cash", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color(0xFF1E293B))
                                                }

                                                columns.forEach { col ->
                                                    var colPrice = 0.0
                                                    rows.forEach { r ->
                                                        val q = cellsMap[Pair(r.id, col.id)] ?: 0
                                                        colPrice += q * col.rate
                                                    }

                                                    Box(
                                                        modifier = Modifier
                                                            .width(105.dp)
                                                            .height(40.dp)
                                                            .background(Color(0xFFF0FDF4))
                                                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                                            .padding(4.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = String.format("%.2f", colPrice),
                                                            fontWeight = FontWeight.ExtraBold,
                                                            fontSize = 11.sp,
                                                            color = Color(0xFF166534)
                                                        )
                                                    }
                                                }

                                                // Individual sheet grand total price
                                                Box(
                                                    modifier = Modifier
                                                        .width(110.dp)
                                                        .height(40.dp)
                                                        .background(Color(0xFFBBF7D0))
                                                        .border(1.dp, MaterialTheme.colorScheme.outline)
                                                        .padding(horizontal = 8.dp),
                                                    contentAlignment = Alignment.CenterEnd
                                                ) {
                                                    Text(
                                                        text = String.format("%.2f", grandTotalLocal),
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 13.sp,
                                                        color = Color(0xFF14532D)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Bottom action to copy/add row or other tools can go here
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = { showAddSheetDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 4.dp)
                                .testTag("add_table_below_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add New Table Below",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Add New Table Below",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
                }
            }
        }
    }

    // --- OVERLAY: TOOLS & MULTI-TABLE ACCOUNT COMBINED SUMMARY MENU ---
    if (showToolsMenuSheet) {
        ModalBottomSheet(
            onDismissRequest = { showToolsMenuSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header of overlay
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tools & Active Summary",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                    }

                    IconButton(onClick = { showToolsMenuSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close Menu")
                    }
                }

                // --- 1. COMBINED CONNECTED OVERALL METRICS ---
                // "ان کی ٹوٹل ایک جگہ پر دکھائے گا... دو جگہ ان کا نتیجہ ہوگا"
                val activeParentSheet = remember(multiState.parentSheets, multiState.activeParentSheetId) {
                    multiState.parentSheets.find { it.id == multiState.activeParentSheetId }
                }
                val activeSheetName = activeParentSheet?.name ?: "Sheet"

                Text(
                    text = "$activeSheetName Summary Results",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Tables", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                                }
                                Text(multiState.sheetsData.size.toString(), fontSize = 18.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Net Grand Total",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = String.format("%.2f", multiState.activeSheetGrandTotal),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            // Share / Export tools row inside bottom sheet
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = {
                                        Exporters.exportToExcel(context, multiState.sheetsData)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E7145)), // Excel green
                                    modifier = Modifier.testTag("export_excel_button")
                                ) {
                                    Icon(Icons.Default.TableChart, contentDescription = "Export Excel", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Excel", fontSize = 11.sp)
                                }

                                Button(
                                    onClick = {
                                        // Auto-select all available tables/sheets under the active parent sheet initially
                                        selectedPdfTableIds = multiState.sheetsData.map { it.sheet.id }.toSet()
                                        showExportPdfDialog = true
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB30B0B)), // PDF red
                                    modifier = Modifier.testTag("export_pdf_button")
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("PDF", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // --- 2. TABLES MANAGEMENT LIST ---
                Text(
                    text = "Manage Active Tables",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                multiState.sheetsData.forEach { data ->
                    val sheet = data.sheet
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.TableChart, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(sheet.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = { showRenameSheetDialog = sheet.id },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Rename", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                }

                                if (multiState.sheetsData.size > 1) {
                                    IconButton(
                                        onClick = { viewModel.deleteSheet(sheet.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }

                // Global trigger
                Button(
                    onClick = { showAddSheetDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add New Table")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Erase Master Reset trigger
                Button(
                    onClick = { showResetDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear All Data & Reset")
                }
            }
        }
    }

    // --- STANDARD MODALS & DIALOGS ---

    // 1. Prompt for Sheet Name (Creation Dialog with Clone Option)
    if (showAddSheetDialog) {
        var sheetNameInput by remember { mutableStateOf("") }
        var isNameError by remember { mutableStateOf(false) }
        var copyMode by remember { mutableStateOf(false) } // false = Brand New, true = Clone Existing
        var selectedSourceSheetId by remember { mutableStateOf<Long?>(null) }
        
        // Auto-select the first available table to clone if any exists
        val availableSheets = multiState.sheetsData
        LaunchedEffect(availableSheets) {
            if (selectedSourceSheetId == null && availableSheets.isNotEmpty()) {
                selectedSourceSheetId = availableSheets.first().sheet.id
            }
        }

        AlertDialog(
            onDismissRequest = { showAddSheetDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GridOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create Connected Table", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Enter a unique name for this table (e.g., Block accounts, Cotton segment):", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = sheetNameInput,
                        onValueChange = {
                            sheetNameInput = it
                            isNameError = it.isBlank()
                        },
                        label = { Text("Table Name") },
                        isError = isNameError,
                        modifier = Modifier.fillMaxWidth().testTag("sheet_name_input_dialog"),
                        singleLine = true
                    )
                    
                    if (availableSheets.isNotEmpty()) {
                        Text(
                            text = "Choose Table Type:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        // Type 1: Brand New Table
                        Surface(
                            onClick = { copyMode = false },
                            shape = RoundedCornerShape(8.dp),
                            color = if (!copyMode) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            border = BorderStroke(
                                width = 1.5.dp,
                                color = if (!copyMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = !copyMode,
                                    onClick = { copyMode = false }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Brand New (Empty Table)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Starts with no columns or rows.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        
                        // Type 2: Clone Existing Table Structure
                        Surface(
                            onClick = { copyMode = true },
                            shape = RoundedCornerShape(8.dp),
                            color = if (copyMode) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            border = BorderStroke(
                                width = 1.5.dp,
                                color = if (copyMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = copyMode,
                                    onClick = { copyMode = true }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Generate Similar to Existing Table", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Copies column titles, prices/rates, and row structure, but clears cell values and dates.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        
                        if (copyMode) {
                            Text(
                                text = "Select Table to Copy From:",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 140.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                    .padding(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(availableSheets) { item ->
                                    val isSelected = selectedSourceSheetId == item.sheet.id
                                    Surface(
                                        onClick = { selectedSourceSheetId = item.sheet.id },
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f) else Color.Transparent,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = item.sheet.name,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 13.sp
                                            )
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (sheetNameInput.isNotBlank()) {
                            if (copyMode && selectedSourceSheetId != null) {
                                viewModel.cloneSheetStructure(sheetNameInput.trim(), selectedSourceSheetId!!)
                            } else {
                                viewModel.addSheet(sheetNameInput.trim())
                            }
                            showAddSheetDialog = false
                        } else {
                            isNameError = true
                        }
                    },
                    modifier = Modifier.testTag("confirm_add_sheet_button")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSheetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 2. Rename Sheet Dialog
    showRenameSheetDialog?.let { sheetId ->
        var renameInput by remember { mutableStateOf("") }
        var isError by remember { mutableStateOf(false) }
        val targetSheet = remember(multiState.sheetsData, sheetId) {
            multiState.sheetsData.map { it.sheet }.find { it.id == sheetId }
        }

        LaunchedEffect(sheetId) {
            renameInput = targetSheet?.name ?: ""
        }

        AlertDialog(
            onDismissRequest = { showRenameSheetDialog = null },
            title = { Text("Rename Table") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = {
                            renameInput = it
                            isError = it.isBlank()
                        },
                        label = { Text("New Name") },
                        isError = isError,
                        modifier = Modifier.fillMaxWidth().testTag("rename_sheet_input_dialog"),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            viewModel.renameSheet(sheetId, renameInput)
                            showRenameSheetDialog = null
                        } else {
                            isError = true
                        }
                    },
                    modifier = Modifier.testTag("confirm_rename_sheet_button")
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameSheetDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- LEVEL 1 PARENT CONNECTIONS DIALOGS ---

    // Add Parent Sheet Dialog
    if (showAddParentSheetDialog) {
        var nameInput by remember { mutableStateOf("") }
        var isError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddParentSheetDialog = false },
            title = { Text("Create Connected Sheet") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a unique name for this parent sheet (e.g., Sheet 1, Sheet 2):", fontSize = 13.sp)
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = {
                            nameInput = it
                            isError = it.isBlank()
                        },
                        label = { Text("Sheet Name") },
                        isError = isError,
                        modifier = Modifier.fillMaxWidth().testTag("add_parent_sheet_input_dialog"),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nameInput.isNotBlank()) {
                            viewModel.addParentSheet(nameInput)
                            showAddParentSheetDialog = false
                        } else {
                            isError = true
                        }
                    },
                    modifier = Modifier.testTag("confirm_add_parent_sheet_button")
                ) {
                    Text("Create Sheet")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddParentSheetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename Parent Sheet Dialog
    showRenameParentSheetDialog?.let { parentId ->
        var nameInput by remember { mutableStateOf("") }
        var isError by remember { mutableStateOf(false) }
        val targetParent = remember(multiState.parentSheets, parentId) {
            multiState.parentSheets.find { it.id == parentId }
        }

        LaunchedEffect(parentId) {
            nameInput = targetParent?.name ?: ""
        }

        AlertDialog(
            onDismissRequest = { showRenameParentSheetDialog = null },
            title = { Text("Rename Connected Sheet") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = {
                            nameInput = it
                            isError = it.isBlank()
                        },
                        label = { Text("Sheet Name") },
                        isError = isError,
                        modifier = Modifier.fillMaxWidth().testTag("rename_parent_sheet_input_dialog"),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nameInput.isNotBlank()) {
                            viewModel.renameParentSheet(parentId, nameInput)
                            showRenameParentSheetDialog = null
                        } else {
                            isError = true
                        }
                    },
                    modifier = Modifier.testTag("confirm_rename_parent_sheet_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameParentSheetDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Parent Sheet Dialog
    showDeleteParentSheetDialog?.let { parentId ->
        val targetParent = remember(multiState.parentSheets, parentId) {
            multiState.parentSheets.find { it.id == parentId }
        }

        AlertDialog(
            onDismissRequest = { showDeleteParentSheetDialog = null },
            title = { Text("Delete Connected Sheet?") },
            text = {
                Text(
                    text = "Are you sure you want to delete '${targetParent?.name}'? " +
                            "This will also delete all tables, columns, rows, and data entries within it.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteParentSheet(parentId)
                        showDeleteParentSheetDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_parent_sheet_button")
                ) {
                    Text("Delete Sheet")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteParentSheetDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 3. Add Row Dialog (Editable Date Selector)
    showAddRowForSheetId?.let { sId ->
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        var dateStrInput by remember { mutableStateOf(sdf.format(Date())) }

        AlertDialog(
            onDismissRequest = { showAddRowForSheetId = null },
            title = { Text("Add Table Row") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Specify a date in YYYY-MM-DD format for this entry row:", fontSize = 13.sp)
                    OutlinedTextField(
                        value = dateStrInput,
                        onValueChange = { dateStrInput = it },
                        label = { Text("Date") },
                        modifier = Modifier.fillMaxWidth().testTag("row_date_input_dialog"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = { dateStrInput = sdf.format(Date()) }) {
                            Text("Today")
                        }
                        TextButton(
                            onClick = {
                                val cal = Calendar.getInstance()
                                cal.add(Calendar.DAY_OF_YEAR, -1)
                                dateStrInput = sdf.format(cal.time)
                            }
                        ) {
                            Text("Yesterday")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (dateStrInput.isNotBlank()) {
                            viewModel.addRow(sId, dateStrInput)
                            showAddRowForSheetId = null
                        }
                    },
                    modifier = Modifier.testTag("confirm_row_button")
                ) {
                    Text("Apply Row")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRowForSheetId = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 4. Add Column Dialog (Select Predefined Product)
    showAddColForSheetId?.let { sId ->
        AlertDialog(
            onDismissRequest = { showAddColForSheetId = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Product to Add", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (productPresets.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
                                Text("No pre-saved products found.", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Please add products and their rates in settings first.", textAlign = TextAlign.Center, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        showAddColForSheetId = null
                                        showRatePresetsDialog = true
                                    }
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add Product & Price")
                                }
                            }
                        }
                    } else {
                        Text("Select a pre-saved product and price to add as a table column:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(productPresets) { preset ->
                                Surface(
                                    onClick = {
                                        viewModel.addColumn(sId, preset.name, preset.rate)
                                        showAddColForSheetId = null
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(preset.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                            Text("Price/Rate: " + String.format("%.2f", preset.rate), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                        }
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Add Column",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddColForSheetId = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 5. Edit Column Dialog
    showEditColState?.let { (colId, currentRate) ->
        var colNameInput by remember { mutableStateOf("") }
        var colRateInput by remember { mutableStateOf(currentRate.toString()) }
        var isNameError by remember { mutableStateOf(false) }

        // Fetch corresponding column info automatically
        val allSheetsData = multiState.sheetsData
        val targetColumn = remember(allSheetsData, colId) {
            allSheetsData.flatMap { it.columns }.find { it.id == colId }
        }

        LaunchedEffect(colId) {
            colNameInput = targetColumn?.name ?: ""
        }

        AlertDialog(
            onDismissRequest = { showEditColState = null },
            title = { Text("Edit Column Properties") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = colNameInput,
                        onValueChange = {
                            colNameInput = it
                            isNameError = it.isBlank()
                        },
                        label = { Text("Item Name") },
                        isError = isNameError,
                        modifier = Modifier.fillMaxWidth().testTag("edit_col_name_input_dialog"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = colRateInput,
                        onValueChange = { colRateInput = it },
                        label = { Text("Unit Billing Rate") },
                        modifier = Modifier.fillMaxWidth().testTag("edit_col_rate_input_dialog"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedRate = colRateInput.toDoubleOrNull() ?: currentRate
                        if (colNameInput.isNotBlank()) {
                            viewModel.updateColumn(colId, colNameInput, parsedRate)
                            showEditColState = null
                        } else {
                            isNameError = true
                        }
                    },
                    modifier = Modifier.testTag("confirm_edit_col_button")
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditColState = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 6. Edit Row Date Dialog
    showEditRowState?.let { rowId ->
        var dateStrInput by remember { mutableStateOf("") }
        val targetRow = remember(multiState.sheetsData, rowId) {
            multiState.sheetsData.flatMap { it.rows }.find { it.id == rowId }
        }

        LaunchedEffect(rowId) {
            val original = targetRow?.dateString ?: ""
            if (original.isBlank()) {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                dateStrInput = sdf.format(Date())
            } else {
                dateStrInput = original
            }
        }

        AlertDialog(
            onDismissRequest = { showEditRowState = null },
            title = { Text("Modify Row Date") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dateStrInput,
                        onValueChange = { dateStrInput = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (dateStrInput.isNotBlank()) {
                            viewModel.updateRowDate(rowId, dateStrInput)
                            showEditRowState = null
                        }
                    }
                ) {
                    Text("Update Date")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditRowState = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 7. Interactive grid cell value editing modal
    activeCellEditState?.let { (rowId, colId) ->
        var quantityInput by remember { mutableStateOf("") }
        val focusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current
 
        // Lookup current state info
        val currentQty = remember(multiState.sheetsData, rowId, colId) {
            multiState.sheetsData.flatMap { it.cellsMap.entries }
                .find { it.key.first == rowId && it.key.second == colId }?.value ?: 0
        }
 
        LaunchedEffect(rowId, colId) {
            quantityInput = if (currentQty > 0) currentQty.toString() else ""
            delay(150)
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
            } catch (e: Exception) {
                // Ignore
            }
        }

        AlertDialog(
            onDismissRequest = { activeCellEditState = null },
            title = { Text("Input Piece Units") },
            text = {
                Column {
                    Text("Enter numeric count (only numbers are acceptable, blank or zeroes clears the cell):", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = quantityInput,
                        onValueChange = { text ->
                            // Clean only numeric constraints
                            val cleaned = text.filter { it.isDigit() }
                            quantityInput = cleaned
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("Piece Quantity") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .testTag("cell_quantity_input_dialog"),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedQty = quantityInput.toIntOrNull() ?: 0
                        viewModel.setCellValue(rowId, colId, parsedQty)
                        activeCellEditState = null
                    },
                    modifier = Modifier.testTag("confirm_cell_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeCellEditState = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 8. Global Wipe confirmation dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Are you absolutely sure?") },
            text = { Text("This action will destroy all sheets, definitions, columns, rows, and cell inputs forever. It cannot be undone.", fontSize = 14.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetAll()
                        showResetDialog = false
                        showToolsMenuSheet = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_reset_button")
                ) {
                    Text("Wipe Database")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 9. Table Column Prices Settings Dialog
    showTableSettingsId?.let { sheetId ->
        val targetSheet = remember(multiState.sheetsData, sheetId) {
            multiState.sheetsData.find { it.sheet.id == sheetId }
        }
        val cols = targetSheet?.columns ?: emptyList()
 
        // Create local mutable states to hold edited names and rates
        val editedCols = remember(cols) {
            cols.map { col -> col.id to (col.name to col.rate.toString()) }.toMutableStateList()
        }
 
        AlertDialog(
            onDismissRequest = { showTableSettingsId = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Rate Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                if (cols.isEmpty()) {
                    Text("No columns exist in this table yet.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)
                    ) {
                        itemsIndexed(editedCols) { index, pair ->
                            val colId = pair.first
                            val (name, rateStr) = pair.second
 
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = name,
                                        onValueChange = { newName ->
                                            editedCols[index] = colId to (newName to rateStr)
                                        },
                                        label = { Text("Item Name", fontSize = 11.sp) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = rateStr,
                                        onValueChange = { newRate ->
                                            editedCols[index] = colId to (name to newRate)
                                        },
                                        label = { Text("Rate / Price", fontSize = 11.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Save all column edits
                        editedCols.forEach { (colId, info) ->
                            val (name, rateStr) = info
                            val parsedRate = rateStr.toDoubleOrNull() ?: 1.0
                            if (name.isNotBlank()) {
                                viewModel.updateColumn(colId, name, parsedRate)
                            }
                        }
                        showTableSettingsId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTableSettingsId = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 10. Rate Presets Manager Dialog
    if (showRatePresetsDialog) {
        var newPresetName by remember { mutableStateOf("") }
        var newPresetRate by remember { mutableStateOf("") }
        var isNameError by remember { mutableStateOf(false) }
        var isRateError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showRatePresetsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Product & Price Presets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Add products and rates below. They will be available across all tables and sheets automatically.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    // Form
                    OutlinedTextField(
                        value = newPresetName,
                        onValueChange = {
                            newPresetName = it
                            isNameError = false
                        },
                        label = { Text("Product Name") },
                        isError = isNameError,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newPresetRate,
                            onValueChange = {
                                newPresetRate = it
                                isRateError = false
                            },
                            label = { Text("Price/Rate") },
                            isError = isRateError,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val rate = newPresetRate.toDoubleOrNull()
                                if (newPresetName.isBlank()) {
                                    isNameError = true
                                } else if (rate == null || rate < 0.0) {
                                    isRateError = true
                                } else {
                                    viewModel.addProductPreset(newPresetName.trim(), rate)
                                    newPresetName = ""
                                    newPresetRate = ""
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Text("Add")
                        }
                    }

                    // Presets list
                    Text("Pre-saved Products:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (productPresets.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                    Text("No pre-saved products.", color = MaterialTheme.colorScheme.outline, fontSize = 12.sp)
                                }
                            }
                        } else {
                            items(productPresets) { preset ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                                            Text(preset.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text("Price: " + String.format("%.2f", preset.rate), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                        }
                                        Row {
                                            IconButton(
                                                onClick = {
                                                    editingPresetState = preset
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            IconButton(
                                                onClick = {
                                                    viewModel.deleteProductPreset(preset.id)
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showRatePresetsDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    // Edit Product Preset sub-dialog
    editingPresetState?.let { originalPreset ->
        var editPresetName by remember(originalPreset) { mutableStateOf(originalPreset.name) }
        var editPresetRate by remember(originalPreset) { mutableStateOf(originalPreset.rate.toString()) }
        var isEditNameError by remember { mutableStateOf(false) }
        var isEditRateError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { editingPresetState = null },
            title = { Text("Edit Product Preset", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editPresetName,
                        onValueChange = {
                            editPresetName = it
                            isEditNameError = false
                        },
                        label = { Text("Product Name") },
                        isError = isEditNameError,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editPresetRate,
                        onValueChange = {
                            editPresetRate = it
                            isEditRateError = false
                        },
                        label = { Text("Price/Rate") },
                        isError = isEditRateError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val rate = editPresetRate.toDoubleOrNull()
                        if (editPresetName.isBlank()) {
                            isEditNameError = true
                        } else if (rate == null || rate < 0.0) {
                            isEditRateError = true
                        } else {
                            val updatedPreset = originalPreset.copy(name = editPresetName.trim(), rate = rate)
                            editingPresetState = null
                            // Trigger Confirmation Dialog for propagation
                            showPresetUpdateConfirmationState = Pair(updatedPreset, originalPreset.name)
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingPresetState = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation dialog for applying price updates to old tables
    showPresetUpdateConfirmationState?.let { (updatedPreset, oldName) ->
        AlertDialog(
            onDismissRequest = { showPresetUpdateConfirmationState = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QuestionMark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Update Existing Tables?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    text = "Do you want to update the name and price for this product in your old tables too? \n\n" +
                           "If yes, all existing columns matching the name '$oldName' will be updated to '${updatedPreset.name}' at rate ${String.format("%.2f", updatedPreset.rate)} automatically, and their total shares will be recalculated in real-time.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateProductPreset(updatedPreset, updateExistingTables = true, oldName = oldName)
                        showPresetUpdateConfirmationState = null
                    }
                ) {
                    Text("Yes, Update All Tables")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        viewModel.updateProductPreset(updatedPreset, updateExistingTables = false, oldName = oldName)
                        showPresetUpdateConfirmationState = null
                    }
                ) {
                    Text("No, Only Future Tables")
                }
            }
        )
    }

    // 11. Select Predefined Rate Menu Dialog (When tapping on cell in top row)
    showEditColPriceDialogState?.let { colId ->
        val allSheetsData = multiState.sheetsData
        val targetCol = remember(allSheetsData, colId) {
            allSheetsData.flatMap { it.columns }.find { it.id == colId }
        }

        if (targetCol != null) {
            AlertDialog(
                onDismissRequest = { showEditColPriceDialogState = null },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select Predefined Price", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Choose a predefined product price to apply to column '${targetCol.name}':",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (productPresets.isEmpty()) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        Text("No products pre-defined in settings.", color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            } else {
                                items(productPresets) { preset ->
                                    val isCurrent = targetCol.rate == preset.rate
                                    Surface(
                                        onClick = {
                                            viewModel.updateColumn(colId, targetCol.name, preset.rate)
                                            showEditColPriceDialogState = null
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = BorderStroke(
                                            width = if (isCurrent) 1.5.dp else 1.dp,
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(preset.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                Text(
                                                    text = String.format("%.2f", preset.rate),
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 15.sp,
                                                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            if (isCurrent) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = {
                                showEditColPriceDialogState = null
                                showRatePresetsDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Manage Price Presets")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showEditColPriceDialogState = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    if (showExportPdfDialog) {
        AlertDialog(
            onDismissRequest = { showExportPdfDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = Color(0xFFB30B0B)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Select Tables to Export",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Select one or more tables under the active sheet to generate a combined PDF report:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (multiState.sheetsData.isEmpty()) {
                        Text(
                            text = "No tables available to export.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        // Select All Toggle Row
                        val allSelected = selectedPdfTableIds.size == multiState.sheetsData.size
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (allSelected) {
                                        selectedPdfTableIds = emptySet()
                                    } else {
                                        selectedPdfTableIds = multiState.sheetsData.map { it.sheet.id }.toSet()
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = allSelected,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        selectedPdfTableIds = multiState.sheetsData.map { it.sheet.id }.toSet()
                                    } else {
                                        selectedPdfTableIds = emptySet()
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Select All Tables",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(multiState.sheetsData) { data ->
                                val isSelected = selectedPdfTableIds.contains(data.sheet.id)
                                Surface(
                                    onClick = {
                                        if (isSelected) {
                                            selectedPdfTableIds = selectedPdfTableIds - data.sheet.id
                                        } else {
                                            selectedPdfTableIds = selectedPdfTableIds + data.sheet.id
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { checked ->
                                                    if (checked) {
                                                        selectedPdfTableIds = selectedPdfTableIds + data.sheet.id
                                                    } else {
                                                        selectedPdfTableIds = selectedPdfTableIds - data.sheet.id
                                                    }
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = data.sheet.name,
                                                    fontWeight = FontWeight.SemiBold,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                Text(
                                                    text = "${data.rows.size} rows, ${data.columns.size} columns",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val filteredSheets = multiState.sheetsData.filter { selectedPdfTableIds.contains(it.sheet.id) }
                        if (filteredSheets.isNotEmpty()) {
                            Exporters.exportToPdf(context, filteredSheets)
                            showExportPdfDialog = false
                        }
                    },
                    enabled = selectedPdfTableIds.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB30B0B))
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export (${selectedPdfTableIds.size})")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportPdfDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
