package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ParentSheet::class, GridSheet::class, GridRow::class, GridColumn::class, GridCell::class, ProductPreset::class],
    version = 4,
    exportSchema = false
)
abstract class GridDatabase : RoomDatabase() {
    abstract fun gridDao(): GridDao

    companion object {
        @Volatile
        private var INSTANCE: GridDatabase? = null

        fun getDatabase(context: Context): GridDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GridDatabase::class.java,
                    "dynamic_grid_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
