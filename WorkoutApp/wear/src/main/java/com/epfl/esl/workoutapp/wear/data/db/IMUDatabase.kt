package com.epfl.esl.workoutapp.wear.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [IMUEntity::class], version = 2, exportSchema = false)
abstract class IMUDatabase : RoomDatabase() {
    abstract val imuDao: IMUDao
    companion object {
        @Volatile
        private var INSTANCE: IMUDatabase? = null
        fun getInstance(context: Context): IMUDatabase {
            synchronized(this) {
                var instance = INSTANCE
                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        IMUDatabase::class.java,
                        "IMU_database"
                    ).fallbackToDestructiveMigration().build()
                    INSTANCE = instance
                }
                return instance
            }
        }
    }
}