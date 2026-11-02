package com.unitrix.sentinela

import android.os.Bundle
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import java.io.ByteArrayInputStream

/** Bundled, offline demonstration. No JavaScript bridge and no access to the real app services. */
class DemoActivity : ComponentActivity() {
    private lateinit var demo: WebView
    private val origin = "https://appassets.androidplatform.net"
    private val assetsAllowed = mapOf("demo-app.html" to "text/html", "demo-app.css" to "text/css", "demo-app.js" to "text/javascript", "demo-store.mjs" to "text/javascript", "demo-Manrope.ttf" to "font/ttf", "demo-InstrumentSerif.ttf" to "font/ttf")
    @Suppress("SetJavaScriptEnabled", "DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SecurityStore(this).demoActive = true
        demo = WebView(this)
        demo.setBackgroundColor(android.graphics.Color.rgb(250, 250, 248))
        with(demo.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            blockNetworkLoads = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mediaPlaybackRequiresUserGesture = true
            setGeolocationEnabled(false)
        }
        listOf("searchBoxJavaBridge_", "accessibility", "accessibilityTraversal").forEach { demo.removeJavascriptInterface(it) }
        demo.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) { callback?.invoke(origin, false, false) }
        }
        demo.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return true
                if (uri.scheme == "https" && uri.host == "appassets.androidplatform.net" && uri.path in setOf("/", "/panel.html")) { finish(); return true }
                return uri.scheme != "https" || uri.host != "appassets.androidplatform.net" || uri.path != "/demo/demo-app.html"
            }
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse {
                val uri = request?.url
                val name = uri?.path?.removePrefix("/demo/")
                val mime = assetsAllowed[name]
                if (uri?.scheme == "https" && uri.host == "appassets.androidplatform.net" && uri.path == "/demo/$name" && mime != null && request?.method == "GET") {
                    return try {
                        WebResourceResponse(mime, if (mime == "font/ttf") null else "UTF-8", 200, "OK", mapOf("Content-Security-Policy" to "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; font-src 'self'; img-src 'self' data:; connect-src 'none'; media-src blob:; object-src 'none'; base-uri 'none'; form-action 'none'", "X-Content-Type-Options" to "nosniff", "Cache-Control" to "no-cache"), assets.open("demo/$name"))
                    } catch (_: Exception) { blocked(404, "Not found") }
                }
                return blocked(403, "Forbidden")
            }
        }
        val layout = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL; fitsSystemWindows = true }
        val exit = android.widget.Button(this).apply {
            text = "Sair da demonstração · voltar ao aplicativo real"
            isAllCaps = false
            setTextColor(android.graphics.Color.rgb(66, 110, 96))
            setBackgroundColor(android.graphics.Color.rgb(250, 250, 248))
            setOnClickListener { finish() }
        }
        layout.addView(exit, android.widget.LinearLayout.LayoutParams(-1, -2))
        layout.addView(demo, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(layout)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { if (demo.canGoBack()) demo.goBack() else finish() } })
        demo.loadUrl("$origin/demo/demo-app.html")
    }
    private fun blocked(status: Int, reason: String) = WebResourceResponse("text/plain", "UTF-8", status, reason, emptyMap(), ByteArrayInputStream("Offline demonstration: external access disabled".toByteArray()))
    override fun onDestroy() { demo.stopLoading(); demo.loadUrl("about:blank"); demo.destroy(); super.onDestroy() }
}
