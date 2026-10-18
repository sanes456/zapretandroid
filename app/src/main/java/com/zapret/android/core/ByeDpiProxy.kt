package com.zapret.android.core

import android.util.Log

/**
 * Owns the native ciadpi (byedpi) SOCKS5 server.
 *
 * [nativeRun] blocks for the whole proxy lifetime, so it is always executed
 * on a dedicated thread. [nativeStop] only calls shutdown() on the listening
 * socket; the event loop in proxy.c notices the cancelled listener and exits.
 */
class ByeDpiProxy {

    @Volatile
    private var thread: Thread? = null

    @Volatile
    var isRunning: Boolean = false
        private set

    /**
     * Invoked (from the proxy thread) when the native event loop exits,
     * whatever the reason. Do not perform long work here.
     */
    var onExit: ((Int) -> Unit)? = null

    fun start(args: List<String>) {
        check(thread == null) { "proxy is already running" }

        val argv = (listOf(BINARY_ARGV0) + args).toTypedArray()
        isRunning = true

        val t = Thread({ run(argv) }, "byedpi-proxy")
        thread = t
        t.start()
    }

    fun stop() {
        val t = thread ?: return

        var waited = 0
        while (t.isAlive && waited < STOP_TIMEOUT_MS) {
            nativeStop()
            t.join(JOIN_STEP_MS)
            waited += JOIN_STEP_MS
        }
        if (t.isAlive) {
            Log.w(TAG, "proxy thread did not exit in ${STOP_TIMEOUT_MS}ms")
        }
    }

    private fun run(argv: Array<String>) {
        val rc = try {
            nativeRun(argv)
        } catch (t: Throwable) {
            Log.e(TAG, "native run failed", t)
            -1
        }
        isRunning = false
        thread = null
        onExit?.invoke(rc)
    }

    private external fun nativeRun(args: Array<String>): Int

    private external fun nativeStop()

    companion object {
        private const val TAG = "ByeDpiProxy"
        private const val BINARY_ARGV0 = "ciadpi"
        private const val STOP_TIMEOUT_MS = 5000
        private const val JOIN_STEP_MS = 100L

        init {
            System.loadLibrary("byedpi")
        }
    }
}
