package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "parent_sheets")
data class ParentSheet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val orderPosition: Int = 0
)

@Entity(tableName = "grid_sheets")
data class GridSheet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val orderPosition: Int = 0,
    val parentSheetId: Long = 1
)

@Entity(tableName = "grid_rows")
data class GridRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sheetId: Long = 1,
    val dateString: String,
    val orderPosition: Int = 0
)

@Entity(tableName = "grid_columns")
data class GridColumn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sheetId: Long = 1,
    val name: String,
    val rate: Double,
    val orderPosition: Int = 0
)

@Entity(
    tableName = "grid_cells",
    primaryKeys = ["rowId", "columnId"]
)
data class GridCell(
    val rowId: Long,
    val columnId: Long,
    val quantity: Int
)

@Entity(tableName = "product_presets")
data class ProductPreset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val rate: Double
)
