package com.zapret.android.core

import com.zapret.android.data.Prefs

/**
 * Builds the ciadpi (byedpi) command line from user settings.
 *
 * The proxy always listens on loopback only, because the only client is the
 * local hev-socks5-tunnel instance running inside the same app.
 */
object CiadpiArgs {

    const val PROXY_HOST = "127.0.0.1"
    const val PROXY_PORT = 1080

    fun build(prefs: Prefs): List<String> {
        val args = mutableListOf("-i", PROXY_HOST, "-p", PROXY_PORT.toString())

        args += tokenize(Strategies.byId(prefs.strategyId).args)
        args += tokenize(prefs.extraArgs)

        if (!prefs.udp) {
            args += "-U"
        }
        if (!prefs.ipv6) {
            args += "-X"
        }
        return args
    }

    private fun tokenize(raw: String): List<String> =
        raw.split(Regex("\\s+")).filter { it.isNotBlank() }
}
