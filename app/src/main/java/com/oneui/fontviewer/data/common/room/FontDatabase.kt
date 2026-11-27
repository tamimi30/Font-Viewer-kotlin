package com.oneui.fontviewer.data.common.room

import android.content.Context

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Database(
    entities = [FontEntity::class],
    version = 4,
    exportSchema = false
)
abstract class FontDatabase : RoomDatabase() {

    abstract fun fontDao(): FontDao

    companion object {
        private const val DATABASE_NAME = "oneui_fonts_database"
        @Volatile
        private var INSTANCE: FontDatabase? = null
        private const val NUMBER_OF_THREADS = 4

        val databaseWriteExecutor: ExecutorService =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS)

        fun getInstance(context: Context): FontDatabase {
            var instance = INSTANCE
            if (instance == null) {
                synchronized(FontDatabase::class.java) {
                    instance = INSTANCE
                    if (instance == null) {
                        instance = Room.databaseBuilder(
                            context.applicationContext,
                            FontDatabase::class.java,
                            DATABASE_NAME
                        )
                            .fallbackToDestructiveMigration()
                            .build()
                        INSTANCE = instance
                    }
                }
            }
            return instance!!
        }
    }
}
