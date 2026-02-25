package com.epfl.esl.workoutapp.wear.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [HREntity::class], version = 2, exportSchema = false)
abstract class HRDatabase : RoomDatabase() {
    abstract val heartRateDao: HRDao
    companion object {
        @Volatile
        private var INSTANCE: HRDatabase? = null
        fun getInstance(context: Context): HRDatabase {
            synchronized(this) {
                var instance = INSTANCE
                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        HRDatabase::class.java,
                        "HR_database"
                    )
                        .fallbackToDestructiveMigration()
                        .build()
                    INSTANCE = instance
                }
                return instance
            }
        }
    }
}