package com.epfl.esl.workoutapp.wear.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface HRDao {
    // Implementation of the queries to use to access the database
    @Insert
    suspend fun insert(HREntityLine: HREntity)
    @Query("DELETE FROM heart_rate_values_table")
    suspend fun clear()
    @Query("SELECT * FROM heart_rate_values_table ORDER BY time_stamps")
    suspend fun getAllHRValues(): List<HREntity>
    @Query("SELECT COUNT(*) FROM heart_rate_values_table")
    suspend fun size(): Int
}