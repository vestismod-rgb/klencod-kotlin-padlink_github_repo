package com.padlink.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy
import java.util.concurrent.TimeUnit

/** Sends webMAN MOD web commands (/pad.ps3?...) to the PS3 over HTTP port 80. */
class MainActivity : Activity() {
    private lateinit var web: WebView
    private val exec = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(3), DiscardOldestPolicy()) // in order, drops stale presses
    @Volatile private var base: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.addJavascriptInterface(Bridge(), "PadLink")
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) @Suppress("DEPRECATION") window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun get(url: String, timeoutMs: Int): Int {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = timeoutMs; c.readTimeout = timeoutMs; c.requestMethod = "GET"
            c.responseCode
        } finally { c.disconnect() }
    }

    inner class Bridge {
        private val prefs = getSharedPreferences("padlink", Context.MODE_PRIVATE)

        @JavascriptInterface fun savedIp(): String = prefs.getString("ip", "192.168.1.103") ?: "192.168.1.103"

        /** Shows a popup on the PS3 screen; calls window.onLink(true/false). */
        @JavascriptInterface fun connect(ip: String) {
            prefs.edit().putString("ip", ip).apply()
            exec.execute {
                var ok = false
                try {
                    val u = "http://$ip"
                    ok = get("$u/popup.ps3/PAD%20LINK%20connected", 2500) in 200..399
                    if (ok) base = u
                } catch (e: Exception) { ok = false }
                runOnUiThread { web.evaluateJavascript("window.onLink($ok)", null) }
            }
        }

        /** path example: /pad.ps3?cross_hold   (executed in order) */
        @JavascriptInterface fun send(path: String) {
            val b = base ?: return
            exec.execute { try { get(b + path, 1500) } catch (e: Exception) { } }
        }
    }
}