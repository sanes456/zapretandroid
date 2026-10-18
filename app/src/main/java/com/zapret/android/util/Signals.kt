package com.zapret.android.util

import android.content.Context
import android.content.Intent

object Signals {

    const val ACTION_STATE_CHANGED = "com.zapret.android.action.STATE_CHANGED"

    fun broadcastStateChanged(context: Context) {
        context.sendBroadcast(
            Intent(ACTION_STATE_CHANGED).setPackage(context.packageName)
        )
    }
}
