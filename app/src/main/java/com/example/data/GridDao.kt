package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GridDao {
    // Parent Sheets
    @Query("SELECT * FROM parent_sheets ORDER BY orderPosition ASC, id ASC")
    fun getAllParentSheets(): Flow<List<ParentSheet>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParentSheet(parentSheet: ParentSheet): Long

    @Update
    suspend fun updateParentSheet(parentSheet: ParentSheet)

    @Query("DELETE FROM parent_sheets WHERE id = :parentSheetId")
    suspend fun deleteParentSheetById(parentSheetId: Long)

    @Query("SELECT * FROM grid_sheets WHERE parentSheetId = :parentSheetId ORDER BY orderPosition ASC, id ASC")
    suspend fun getSheetsByParentSheetSync(parentSheetId: Long): List<GridSheet>

    @Query("DELETE FROM grid_sheets WHERE parentSheetId = :parentSheetId")
    suspend fun deleteSheetsByParentSheet(parentSheetId: Long)

    // Sheets
    @Query("SELECT * FROM grid_sheets ORDER BY orderPosition ASC, id ASC")
    fun getAllSheets(): Flow<List<GridSheet>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSheet(sheet: GridSheet): Long

    @Update
    suspend fun updateSheet(sheet: GridSheet)

    @Delete
    suspend fun deleteSheet(sheet: GridSheet)

    @Query("DELETE FROM grid_sheets WHERE id = :sheetId")
    suspend fun deleteSheetById(sheetId: Long)

    // Rows
    @Query("SELECT * FROM grid_rows WHERE sheetId = :sheetId ORDER BY orderPosition ASC, id ASC")
    fun getRowsBySheet(sheetId: Long): Flow<List<GridRow>>

    @Query("SELECT * FROM grid_rows ORDER BY orderPosition ASC, id ASC")
    fun getAllRows(): Flow<List<GridRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRow(row: GridRow): Long

    @Update
    suspend fun updateRow(row: GridRow)

    @Delete
    suspend fun deleteRow(row: GridRow)

    @Query("DELETE FROM grid_rows WHERE id = :rowId")
    suspend fun deleteRowById(rowId: Long)

    @Query("DELETE FROM grid_rows WHERE sheetId = :sheetId")
    suspend fun deleteRowsBySheet(sheetId: Long)

    // Columns
    @Query("SELECT * FROM grid_columns WHERE sheetId = :sheetId ORDER BY orderPosition ASC, id ASC")
    fun getColumnsBySheet(sheetId: Long): Flow<List<GridColumn>>

    @Query("SELECT * FROM grid_columns ORDER BY orderPosition ASC, id ASC")
    fun getAllColumns(): Flow<List<GridColumn>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertColumn(column: GridColumn): Long

    @Update
    suspend fun updateColumn(column: GridColumn)

    @Delete
    suspend fun deleteColumn(column: GridColumn)

    @Query("DELETE FROM grid_columns WHERE id = :columnId")
    suspend fun deleteColumnById(columnId: Long)

    @Query("DELETE FROM grid_columns WHERE sheetId = :sheetId")
    suspend fun deleteColumnsBySheet(sheetId: Long)

    // Cells
    @Query("SELECT * FROM grid_cells")
    fun getAllCells(): Flow<List<GridCell>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCell(cell: GridCell)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCells(cells: List<GridCell>)

    @Query("DELETE FROM grid_cells WHERE rowId = :rowId")
    suspend fun deleteCellsByRow(rowId: Long)

    @Query("DELETE FROM grid_cells WHERE columnId = :columnId")
    suspend fun deleteCellsByColumn(columnId: Long)

    @Query("DELETE FROM grid_cells WHERE rowId = :rowId AND columnId = :columnId")
    suspend fun deleteCell(rowId: Long, columnId: Long)

    @Query("DELETE FROM grid_cells WHERE rowId IN (SELECT id FROM grid_rows WHERE sheetId = :sheetId)")
    suspend fun deleteCellsBySheet(sheetId: Long)

    // Reset All
    @Query("DELETE FROM grid_rows")
    suspend fun clearAllRows()

    @Query("DELETE FROM grid_columns")
    suspend fun clearAllColumns()

    @Query("DELETE FROM grid_cells")
    suspend fun clearAllCells()

    @Query("DELETE FROM grid_sheets")
    suspend fun clearAllSheets()

    @Query("DELETE FROM parent_sheets")
    suspend fun clearAllParentSheets()

    @Query("DELETE FROM product_presets")
    suspend fun clearAllProductPresets()

    // Product Presets
    @Query("SELECT * FROM product_presets ORDER BY name ASC")
    fun getAllProductPresets(): Flow<List<ProductPreset>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProductPreset(preset: ProductPreset): Long

    @Update
    suspend fun updateProductPreset(preset: ProductPreset)

    @Delete
    suspend fun deleteProductPreset(preset: ProductPreset)

    @Query("DELETE FROM product_presets WHERE id = :presetId")
    suspend fun deleteProductPresetById(presetId: Long)

    @Query("UPDATE grid_columns SET name = :newName, rate = :newRate WHERE name = :oldName")
    suspend fun updateColumnNamesAndRates(oldName: String, newName: String, newRate: Double)

    @Transaction
    suspend fun resetAll() {
        clearAllCells()
        clearAllRows()
        clearAllColumns()
        clearAllSheets()
        clearAllParentSheets()
        clearAllProductPresets()
    }
}
