package com.epfl.esl.workoutapp.wear.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "heart_rate_values_table")
data class HREntity(
// Primary key to access the row of SQLite table for the entity HR
    @PrimaryKey(autoGenerate = true)
    var nightID: Long = 0L,
// Different column for different attributes of the entity HR
    @ColumnInfo(name = "heart_rate_values")
    var HRValue: Int = 0,
    @ColumnInfo(name = "time_stamps")
    val timeMilli: Long = System.currentTimeMillis(),
)