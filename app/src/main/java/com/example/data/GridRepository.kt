package com.example.data

import kotlinx.coroutines.flow.Flow

class GridRepository(private val gridDao: GridDao) {
    val allParentSheets: Flow<List<ParentSheet>> = gridDao.getAllParentSheets()
    val allSheets: Flow<List<GridSheet>> = gridDao.getAllSheets()
    val allCells: Flow<List<GridCell>> = gridDao.getAllCells()
    val allRows: Flow<List<GridRow>> = gridDao.getAllRows()
    val allColumns: Flow<List<GridColumn>> = gridDao.getAllColumns()
    val allProductPresets: Flow<List<ProductPreset>> = gridDao.getAllProductPresets()

    fun getRowsBySheet(sheetId: Long): Flow<List<GridRow>> = gridDao.getRowsBySheet(sheetId)
    fun getColumnsBySheet(sheetId: Long): Flow<List<GridColumn>> = gridDao.getColumnsBySheet(sheetId)

    suspend fun insertParentSheet(parentSheet: ParentSheet): Long = gridDao.insertParentSheet(parentSheet)
    suspend fun updateParentSheet(parentSheet: ParentSheet) = gridDao.updateParentSheet(parentSheet)
    suspend fun deleteParentSheetById(parentSheetId: Long) {
        val sheets = gridDao.getSheetsByParentSheetSync(parentSheetId)
        for (sheet in sheets) {
            gridDao.deleteCellsBySheet(sheet.id)
            gridDao.deleteRowsBySheet(sheet.id)
            gridDao.deleteColumnsBySheet(sheet.id)
        }
        gridDao.deleteSheetsByParentSheet(parentSheetId)
        gridDao.deleteParentSheetById(parentSheetId)
    }

    suspend fun insertSheet(sheet: GridSheet): Long = gridDao.insertSheet(sheet)
    suspend fun updateSheet(sheet: GridSheet) = gridDao.updateSheet(sheet)
    suspend fun deleteSheetById(sheetId: Long) {
        gridDao.deleteCellsBySheet(sheetId)
        gridDao.deleteRowsBySheet(sheetId)
        gridDao.deleteColumnsBySheet(sheetId)
        gridDao.deleteSheetById(sheetId)
    }

    suspend fun insertRow(row: GridRow): Long = gridDao.insertRow(row)
    suspend fun updateRow(row: GridRow) = gridDao.updateRow(row)
    suspend fun deleteRowById(rowId: Long) {
        gridDao.deleteRowById(rowId)
        gridDao.deleteCellsByRow(rowId)
    }

    suspend fun insertColumn(column: GridColumn): Long = gridDao.insertColumn(column)
    suspend fun updateColumn(column: GridColumn) = gridDao.updateColumn(column)
    suspend fun deleteColumnById(columnId: Long) {
        gridDao.deleteColumnById(columnId)
        gridDao.deleteCellsByColumn(columnId)
    }

    suspend fun insertCell(cell: GridCell) = gridDao.insertCell(cell)
    suspend fun insertCells(cells: List<GridCell>) = gridDao.insertCells(cells)
    suspend fun deleteCell(rowId: Long, columnId: Long) = gridDao.deleteCell(rowId, columnId)

    // Product Preset operations
    suspend fun insertProductPreset(preset: ProductPreset): Long = gridDao.insertProductPreset(preset)
    suspend fun updateProductPreset(preset: ProductPreset) = gridDao.updateProductPreset(preset)
    suspend fun deleteProductPresetById(presetId: Long) = gridDao.deleteProductPresetById(presetId)
    suspend fun updateColumnNamesAndRates(oldName: String, newName: String, newRate: Double) = gridDao.updateColumnNamesAndRates(oldName, newName, newRate)

    suspend fun resetAll() = gridDao.resetAll()
}
