package com.zapret.android.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var mode: String
        get() = sp.getString(KEY_MODE, MODE_VPN) ?: MODE_VPN
        set(value) = sp.edit { putString(KEY_MODE, value) }

    var strategyId: String
        get() = sp.getString(KEY_STRATEGY, DEFAULT_STRATEGY) ?: DEFAULT_STRATEGY
        set(value) = sp.edit { putString(KEY_STRATEGY, value) }

    var extraArgs: String
        get() = sp.getString(KEY_EXTRA_ARGS, "") ?: ""
        set(value) = sp.edit { putString(KEY_EXTRA_ARGS, value) }

    var udp: Boolean
        get() = sp.getBoolean(KEY_UDP, true)
        set(value) = sp.edit { putBoolean(KEY_UDP, value) }

    var ipv6: Boolean
        get() = sp.getBoolean(KEY_IPV6, false)
        set(value) = sp.edit { putBoolean(KEY_IPV6, value) }

    var dns: String
        get() = sp.getString(KEY_DNS, DEFAULT_DNS) ?: DEFAULT_DNS
        set(value) = sp.edit { putString(KEY_DNS, value) }

    var startOnBoot: Boolean
        get() = sp.getBoolean(KEY_START_ON_BOOT, false)
        set(value) = sp.edit { putBoolean(KEY_START_ON_BOOT, value) }

    companion object {
        const val MODE_VPN = "vpn"
        const val MODE_PROXY = "proxy"
        const val DEFAULT_STRATEGY = "auto"
        const val DEFAULT_DNS = "1.1.1.1"

        private const val NAME = "zapret_settings"
        private const val KEY_MODE = "mode"
        private const val KEY_STRATEGY = "strategy"
        private const val KEY_EXTRA_ARGS = "extra_args"
        private const val KEY_UDP = "udp"
        private const val KEY_IPV6 = "ipv6"
        private const val KEY_DNS = "dns"
        private const val KEY_START_ON_BOOT = "start_on_boot"
    }
}
