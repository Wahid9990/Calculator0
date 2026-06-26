package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.GridCell
import com.example.data.GridColumn
import com.example.data.GridRow
import com.example.data.GridSheet
import com.example.data.GridRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

import com.example.data.ParentSheet
import com.example.data.ProductPreset

data class SheetUiData(
    val sheet: GridSheet,
    val rows: List<GridRow>,
    val columns: List<GridColumn>,
    val cellsMap: Map<Pair<Long, Long>, Int> // (rowId, colId) -> Quantity
)

data class ParentSheetSummary(
    val parentSheet: ParentSheet,
    val totalPieces: Int,
    val grandTotal: Double
)

data class GridMultiUIState(
    val parentSheets: List<ParentSheet> = emptyList(),
    val activeParentSheetId: Long? = null,
    val sheetsData: List<SheetUiData> = emptyList(),
    val summariesByParentSheet: List<ParentSheetSummary> = emptyList(),
    val totalPiecesCombinedAllSheets: Int = 0,
    val grandTotalCombinedAllSheets: Double = 0.0,
    val activeSheetTotalPieces: Int = 0,
    val activeSheetGrandTotal: Double = 0.0
)

class GridViewModel(private val repository: GridRepository) : ViewModel() {

    // Active Parent Sheet ID State
    val activeParentSheetId = MutableStateFlow<Long?>(null)

    // Product Presets Flow
    val productPresets: StateFlow<List<ProductPreset>> = repository.allProductPresets
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Unified Reactive flow mapping all tables and parent sheets automatically in real-time
    val gridState: StateFlow<GridMultiUIState> = combine(
        repository.allParentSheets,
        activeParentSheetId,
        repository.allSheets,
        repository.allRows,
        repository.allColumns,
        repository.allCells
    ) { arrayOfFlows ->
        @Suppress("UNCHECKED_CAST")
        val parentSheets = arrayOfFlows[0] as List<ParentSheet>
        val activeParentId = arrayOfFlows[1] as Long?
        @Suppress("UNCHECKED_CAST")
        val sheets = arrayOfFlows[2] as List<GridSheet>
        @Suppress("UNCHECKED_CAST")
        val rows = arrayOfFlows[3] as List<GridRow>
        @Suppress("UNCHECKED_CAST")
        val columns = arrayOfFlows[4] as List<GridColumn>
        @Suppress("UNCHECKED_CAST")
        val cells = arrayOfFlows[5] as List<GridCell>

        // 1. Map all sheets (Tables) to their contents
        val allSheetsDataList = sheets.map { sheet ->
            val sheetRows = rows.filter { it.sheetId == sheet.id }
            val sheetCols = columns.filter { it.sheetId == sheet.id }
            val sheetCellMap = cells.filter { cell ->
                sheetRows.any { it.id == cell.rowId } && sheetCols.any { it.id == cell.columnId }
            }.associate { Pair(it.rowId, it.columnId) to it.quantity }

            SheetUiData(
                sheet = sheet,
                rows = sheetRows,
                columns = sheetCols,
                cellsMap = sheetCellMap
            )
        }

        // 2. Compute summaries for each ParentSheet
        val summaries = parentSheets.map { parent ->
            val parentSheetsData = allSheetsDataList.filter { it.sheet.parentSheetId == parent.id }
            var pcs = 0
            var price = 0.0
            parentSheetsData.forEach { sData ->
                sData.rows.forEach { r ->
                    sData.columns.forEach { col ->
                        val qty = sData.cellsMap[Pair(r.id, col.id)] ?: 0
                        pcs += qty
                        price += qty * col.rate
                    }
                }
            }
            ParentSheetSummary(
                parentSheet = parent,
                totalPieces = pcs,
                grandTotal = price
            )
        }

        // 3. Combined total across all parent sheets
        var totalPiecesCombinedAll = 0
        var grandTotalCombinedAll = 0.0
        summaries.forEach { s ->
            totalPiecesCombinedAll += s.totalPieces
            grandTotalCombinedAll += s.grandTotal
        }

        // 4. Currently focused parent sheet ID
        var finalActiveParentId = activeParentId
        if (finalActiveParentId == null && parentSheets.isNotEmpty()) {
            finalActiveParentId = parentSheets.first().id
        }

        // 5. Tables belonging to active parent sheet
        val activeSheetsData = allSheetsDataList.filter { it.sheet.parentSheetId == finalActiveParentId }

        val activeSummary = summaries.find { it.parentSheet.id == finalActiveParentId }
        val activeSheetPieces = activeSummary?.totalPieces ?: 0
        val activeSheetTotal = activeSummary?.grandTotal ?: 0.0

        GridMultiUIState(
            parentSheets = parentSheets,
            activeParentSheetId = finalActiveParentId,
            sheetsData = activeSheetsData,
            summariesByParentSheet = summaries,
            totalPiecesCombinedAllSheets = totalPiecesCombinedAll,
            grandTotalCombinedAllSheets = grandTotalCombinedAll,
            activeSheetTotalPieces = activeSheetPieces,
            activeSheetGrandTotal = activeSheetTotal
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GridMultiUIState()
    )

    // Selection States for deletion context visual tracking
    val selectedRowId = MutableStateFlow<Long?>(null)
    val selectedColId = MutableStateFlow<Long?>(null)

    // Selected column sheet binding to prevent cross-sheet deletion confusion
    val selectedRowSheetId = MutableStateFlow<Long?>(null)
    val selectedColSheetId = MutableStateFlow<Long?>(null)

    // Search query
    val searchQuery = MutableStateFlow("")

    init {
        // Ensure there is at least one active parent sheet and default table under it
        viewModelScope.launch {
            repository.allParentSheets.collect { list ->
                if (list.isEmpty()) {
                    val defaultParentId = repository.insertParentSheet(ParentSheet(name = "Sheet 1"))
                    activeParentSheetId.value = defaultParentId
                    repository.insertSheet(GridSheet(name = "Table 1", parentSheetId = defaultParentId))
                } else if (activeParentSheetId.value == null || !list.any { it.id == activeParentSheetId.value }) {
                    activeParentSheetId.value = list.first().id
                }
            }
        }
    }

    // --- Parent Sheet Management (Level 1) ---
    fun selectParentSheet(parentId: Long) {
        activeParentSheetId.value = parentId
        clearSelections()
    }

    fun addParentSheet(name: String) {
        viewModelScope.launch {
            val list = repository.allParentSheets.first()
            val nextPos = if (list.isEmpty()) 0 else list.maxByOrNull { it.orderPosition }!!.orderPosition + 1
            val newParentId = repository.insertParentSheet(ParentSheet(name = name, orderPosition = nextPos))
            // Auto add Table 1 under it
            repository.insertSheet(GridSheet(name = "Table 1", parentSheetId = newParentId))
            activeParentSheetId.value = newParentId
        }
    }

    fun renameParentSheet(parentId: Long, newName: String) {
        viewModelScope.launch {
            val list = repository.allParentSheets.first()
            val parent = list.find { it.id == parentId }
            if (parent != null) {
                repository.updateParentSheet(parent.copy(name = newName))
            }
        }
    }

    fun deleteParentSheet(parentId: Long) {
        viewModelScope.launch {
            repository.deleteParentSheetById(parentId)
            val list = repository.allParentSheets.first().filter { it.id != parentId }
            if (list.isNotEmpty()) {
                activeParentSheetId.value = list.first().id
            } else {
                activeParentSheetId.value = null
            }
            clearSelections()
        }
    }

    // --- Sheet / Connected Tables Management (Level 2) ---
    fun selectSheet(sheetId: Long) {
        // Not used heavily now as all tables of active parent sheet are visible together
    }

    fun addSheet(name: String) {
        viewModelScope.launch {
            val currentParentId = activeParentSheetId.value
            if (currentParentId != null) {
                val list = repository.allSheets.first().filter { it.parentSheetId == currentParentId }
                val nextPos = if (list.isEmpty()) 0 else list.maxByOrNull { it.orderPosition }!!.orderPosition + 1
                repository.insertSheet(GridSheet(name = name, parentSheetId = currentParentId, orderPosition = nextPos))
            }
        }
    }

    fun cloneSheetStructure(newName: String, sourceSheetId: Long) {
        viewModelScope.launch {
            val currentParentId = activeParentSheetId.value ?: return@launch
            val list = repository.allSheets.first().filter { it.parentSheetId == currentParentId }
            val nextPos = if (list.isEmpty()) 0 else list.maxByOrNull { it.orderPosition }!!.orderPosition + 1
            
            // 1. Insert new sheet/table
            val newSheetId = repository.insertSheet(GridSheet(name = newName, parentSheetId = currentParentId, orderPosition = nextPos))
            
            // 2. Copy columns (names and rates)
            val sourceColumns = repository.getColumnsBySheet(sourceSheetId).first()
            sourceColumns.forEach { col ->
                repository.insertColumn(GridColumn(sheetId = newSheetId, name = col.name, rate = col.rate, orderPosition = col.orderPosition))
            }
            
            // 3. Copy rows with blank date strings (not copying cells/quantities, so they will be empty)
            val sourceRows = repository.getRowsBySheet(sourceSheetId).first()
            sourceRows.forEach { row ->
                repository.insertRow(GridRow(sheetId = newSheetId, dateString = "", orderPosition = row.orderPosition))
            }
        }
    }

    fun renameSheet(sheetId: Long, newName: String) {
        viewModelScope.launch {
            val sheet = repository.allSheets.first().find { it.id == sheetId }
            if (sheet != null) {
                repository.updateSheet(sheet.copy(name = newName))
            }
        }
    }

    fun deleteSheet(sheetId: Long) {
        viewModelScope.launch {
            repository.deleteSheetById(sheetId)
            clearSelections()
        }
    }

    // --- Column Management ---
    fun selectColumn(sheetId: Long, colId: Long) {
        if (selectedColId.value == colId) {
            clearSelections()
        } else {
            selectedColId.value = colId
            selectedColSheetId.value = sheetId
            selectedRowId.value = null
            selectedRowSheetId.value = null
        }
    }

    fun addColumn(sheetId: Long, name: String, rate: Double) {
        viewModelScope.launch {
            val list = repository.getColumnsBySheet(sheetId).first()
            val nextPos = if (list.isEmpty()) 0 else list.maxByOrNull { it.orderPosition }!!.orderPosition + 1
            repository.insertColumn(GridColumn(sheetId = sheetId, name = name, rate = rate, orderPosition = nextPos))
        }
    }

    fun updateColumn(colId: Long, name: String, rate: Double) {
        viewModelScope.launch {
            val allCols = repository.allColumns.first()
            val col = allCols.find { it.id == colId }
            if (col != null) {
                repository.updateColumn(col.copy(name = name, rate = rate))
            }
        }
    }

    fun deleteSelectedColumn() {
        val colId = selectedColId.value
        if (colId != null) {
            viewModelScope.launch {
                repository.deleteColumnById(colId)
                clearSelections()
            }
        }
    }

    // --- Row Management ---
    fun selectRow(sheetId: Long, rowId: Long) {
        if (selectedRowId.value == rowId) {
            clearSelections()
        } else {
            selectedRowId.value = rowId
            selectedRowSheetId.value = sheetId
            selectedColId.value = null
            selectedColSheetId.value = null
        }
    }

    fun addRow(sheetId: Long, dateString: String) {
        viewModelScope.launch {
            val list = repository.getRowsBySheet(sheetId).first()
            val nextPos = if (list.isEmpty()) 0 else list.maxByOrNull { it.orderPosition }!!.orderPosition + 1
            repository.insertRow(GridRow(sheetId = sheetId, dateString = dateString, orderPosition = nextPos))
        }
    }

    fun updateRowDate(rowId: Long, newDate: String) {
        viewModelScope.launch {
            val allRows = repository.allRows.first()
            val row = allRows.find { it.id == rowId }
            if (row != null) {
                repository.updateRow(row.copy(dateString = newDate))
            }
        }
    }

    fun deleteSelectedRow() {
        val rowId = selectedRowId.value
        if (rowId != null) {
            viewModelScope.launch {
                repository.deleteRowById(rowId)
                clearSelections()
            }
        }
    }

    // --- Cell Value Management ---
    fun setCellValue(rowId: Long, colId: Long, quantity: Int) {
        viewModelScope.launch {
            if (quantity <= 0) {
                repository.deleteCell(rowId, colId)
            } else {
                repository.insertCell(GridCell(rowId = rowId, columnId = colId, quantity = quantity))
            }
        }
    }

    fun clearSelections() {
        selectedRowId.value = null
        selectedRowSheetId.value = null
        selectedColId.value = null
        selectedColSheetId.value = null
    }

    // --- Product Preset Management (Pre-defined Rates/Prices) ---
    fun addProductPreset(name: String, rate: Double) {
        viewModelScope.launch {
            repository.insertProductPreset(ProductPreset(name = name, rate = rate))
        }
    }

    fun updateProductPreset(preset: ProductPreset, updateExistingTables: Boolean, oldName: String) {
        viewModelScope.launch {
            repository.updateProductPreset(preset)
            if (updateExistingTables) {
                repository.updateColumnNamesAndRates(oldName = oldName, newName = preset.name, newRate = preset.rate)
            }
        }
    }

    fun deleteProductPreset(presetId: Long) {
        viewModelScope.launch {
            repository.deleteProductPresetById(presetId)
        }
    }

    // --- Global Reset ---
    fun resetAll() {
        viewModelScope.launch {
            repository.resetAll()
            clearSelections()
            searchQuery.value = ""
        }
    }
}

class GridViewModelFactory(private val repository: GridRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GridViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GridViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
