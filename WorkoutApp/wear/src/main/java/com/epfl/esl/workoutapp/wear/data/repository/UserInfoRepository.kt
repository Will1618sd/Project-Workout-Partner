package com.epfl.esl.workoutapp.wear.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.epfl.esl.workoutapp.wear.domain.WearPaths
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class UserInfo(
    val username: String = "—",
    val photo: Bitmap? = null
)

/**
 * Listens to /userInfo DataItem from mobile and exposes it as StateFlow.
 */
class UserInfoRepository(
    private val appContext: Context
) : DataClient.OnDataChangedListener {

    private val dataClient: DataClient = Wearable.getDataClient(appContext)

    private val _userInfo = MutableStateFlow(UserInfo())
    val userInfo: StateFlow<UserInfo> = _userInfo

    private var listening = false

    fun start() {
        if (listening) return
        listening = true
        dataClient.addListener(this)
    }

    fun stop() {
        if (!listening) return
        listening = false
        dataClient.removeListener(this)
    }

    override fun onDataChanged(events: DataEventBuffer) {
        events
            .filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == WearPaths.USER_INFO }
            .forEach { event ->
                val map = DataMapItem.fromDataItem(event.dataItem).dataMap

                val username = map.getString("username") ?: "—"
                val bytes = map.getByteArray("profileImage")

                val bmp = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }

                _userInfo.value = UserInfo(username = username, photo = bmp)
            }
    }
}