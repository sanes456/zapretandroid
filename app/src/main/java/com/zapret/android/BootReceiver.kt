package com.zapret.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.zapret.android.data.Prefs

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = Prefs(context)
        if (!prefs.startOnBoot) return

        try {
            when (prefs.mode) {
                Prefs.MODE_PROXY -> ProxyModeService.start(context)
                else -> {
                    if (VpnService.prepare(context) == null) {
                        ZapretVpnService.start(context)
                    } else {
                        Log.w(TAG, "VPN consent is missing, skipping autostart")
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "autostart failed", t)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
