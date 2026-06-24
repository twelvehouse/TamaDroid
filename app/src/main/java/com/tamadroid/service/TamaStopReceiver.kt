package com.tamadroid.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** "停止" action on the ongoing notification: stop the service and tear down the engine. */
class TamaStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        TamaService.stop(context)
        TamaRuntime.shutdown()
    }
}
