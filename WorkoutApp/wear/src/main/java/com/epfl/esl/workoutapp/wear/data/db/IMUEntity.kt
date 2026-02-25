package com.epfl.esl.workoutapp.wear.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "imu_values_table")
data class IMUEntity(
    @PrimaryKey(autoGenerate = true)
    var nightID: Long = 0L,
    // Different column for different attributes of the entity IMU
    @ColumnInfo(name = "accX_values")
    var accXValue: Float = 0F,
    @ColumnInfo(name = "accY_values")
    var accYValue: Float = 0F,
    @ColumnInfo(name = "accZ_values")
    var accZValue: Float = 0F,
    @ColumnInfo(name = "gyroX_values")
    var gyroXValue: Float = 0F,
    @ColumnInfo(name = "gyroY_values")
    var gyroYValue: Float = 0F,
    @ColumnInfo(name = "gyroZ_values")
    var gyroZValue: Float = 0F,
    @ColumnInfo(name = "time_stamps")
    val timeMilli: Long = System.currentTimeMillis(),
)