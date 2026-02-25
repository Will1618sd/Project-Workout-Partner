package com.epfl.esl.workoutapp.mobile.ui.activity

import android.content.Context
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

class WearNotifier(private val context: Context) {
    suspend fun showSummaryOnWear(sessionId: String, activityType: String) {
        val messageClient = Wearable.getMessageClient(context)
        val nodeClient = Wearable.getNodeClient(context)

        val nodes = nodeClient.connectedNodes.await()
        if (nodes.isEmpty()) return

        val payload = """{"sessionId": "$sessionId", "activityType": "$activityType"}""".toByteArray()

        nodes.forEach { node ->
            messageClient.sendMessage(node.id, "/show_summary", payload).await()
        }
""
    }
}