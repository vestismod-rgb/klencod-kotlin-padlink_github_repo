package com.padlink.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

class MainActivity : Activity() {
    companion object { const val PORT = 8080 }

    private lateinit var web: WebView
    private val exec = Executors.newSingleThreadExecutor()
    @Volatile private var sock: DatagramSocket? = null
    @Volatile private var addr: InetAddress? = null

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

    inner class Bridge {
        private val prefs = getSharedPreferences("padlink", Context.MODE_PRIVATE)

        @JavascriptInterface fun savedIp(): String = prefs.getString("ip", "") ?: ""

        /** Sends HELLO and waits for OK from the PS3 plugin, then calls window.onLink(true/false). */
        @JavascriptInterface fun connect(ip: String) {
            prefs.edit().putString("ip", ip).apply()
            exec.execute {
                var ok = false
                try {
                    sock?.close(); sock = null
                    val s = DatagramSocket(); s.soTimeout = 1500
                    val a = InetAddress.getByName(ip)
                    val hello = "HELLO".toByteArray()
                    s.send(DatagramPacket(hello, hello.size, a, PORT))
                    val r = DatagramPacket(ByteArray(16), 16); s.receive(r)
                    ok = String(r.data, 0, r.length).startsWith("OK")
                    if (ok) { s.soTimeout = 0; sock = s; addr = a } else s.close()
                } catch (e: Exception) { ok = false }
                runOnUiThread { web.evaluateJavascript("window.onLink($ok)", null) }
            }
        }

        /** Messages: "E,<code>,<1|0>" press/release   "S,<L3|R3>,<x>,<y>" stick -120..120 */
        @JavascriptInterface fun send(msg: String) {
            exec.execute {
                try { val d = msg.toByteArray(); sock?.send(DatagramPacket(d, d.size, addr, PORT)) } catch (e: Exception) {}
            }
        }
    }
}