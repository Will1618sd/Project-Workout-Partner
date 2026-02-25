package com.epfl.esl.workoutapp.wear.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface IMUDao {
    @Insert
    suspend fun insert(IMUEntityLine: IMUEntity)
    @Query("DELETE FROM imu_values_table")
    suspend fun clear()
    @Query("SELECT * FROM imu_values_table ORDER BY time_stamps")
    suspend fun getAllIMUValues(): List<IMUEntity>
    @Query("SELECT COUNT(*) FROM imu_values_table")
    suspend fun size(): Int
}