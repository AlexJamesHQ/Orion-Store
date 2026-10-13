package com.orion.store.platform

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File

/** Reads the web (Capacitor) Orion Store's zustand stores. Updating over it keeps the package, so its WebView localStorage for
 *  https://localhost is still on disk; a throwaway WebView on that origin hands both stores back. Main thread only. */
object WebMigration {
    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    fun read(ctx: Context, done: (settings: String?, data: String?) -> Unit) {
        if (!File(ctx.dataDir, "app_webview").exists()) return done(null, null) // fresh install: never had a WebView
        val web = WebView(ctx); var over = false
        fun finish(s: String?, d: String?) { if (over) return; over = true; done(s, d); web.destroy() }
        web.settings.javaScriptEnabled = true; web.settings.domStorageEnabled = true
        web.addJavascriptInterface(object { @JavascriptInterface fun post(s: String, d: String) { web.post { finish(s.ifEmpty { null }, d.ifEmpty { null }) } } }, "Mig")
        web.webViewClient = object : WebViewClient() {
            // Same origin as Capacitor's androidScheme https + hostname localhost; nothing ever hits the network
            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest) = WebResourceResponse("text/html", "utf-8",
                "<script>Mig.post(localStorage.getItem('orion-settings-storage')||'',localStorage.getItem('orion-data-storage')||'')</script>".byteInputStream())
        }
        web.loadUrl("https://localhost/")
        web.postDelayed({ finish(null, null) }, 8000)
    }
}
