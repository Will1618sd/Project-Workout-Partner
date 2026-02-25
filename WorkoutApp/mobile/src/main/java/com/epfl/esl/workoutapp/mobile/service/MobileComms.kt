package com.epfl.esl.workoutapp.mobile.service
import kotlinx.coroutines.tasks.await

//class MobileComms(
//    private val dataClient: com.google.android.gms.wearable.DataClient
//) {
//    suspend fun sendOutdoorMetrics(packet: OutdoorMetricsPacket) {
//        val req = com.google.android.gms.wearable.PutDataMapRequest
//            .create(WearPaths.OUTDOOR_METRICS)
//            .apply {
//                dataMap.putInt(OutdoorState.TIME_SEC, packet.timeSec)
//                dataMap.putInt(OutdoorState.DIST_M, packet.distanceM)
//                dataMap.putFloat(OutdoorState.SPEED_KPH, packet.speedKph)
//                dataMap.putBoolean(OutdoorState.STARTED, packet.started)
//                dataMap.putBoolean(OutdoorState.PAUSED, packet.isPaused)
//                dataMap.putLong(OutdoorState.TS, System.currentTimeMillis())
//            }
//            .asPutDataRequest()
//            .setUrgent()
//
//        // Tasks -> suspend
//        kotlinx.coroutines.tasks.await(dataClient.putDataItem(req))
//    }
//}

//data class OutdoorMetricsPacket(
//    val timeSec: Int,
//    val distanceM: Int,
//    val speedKph: Float,
//    val started: Boolean,
//    val isPaused: Boolean
//)