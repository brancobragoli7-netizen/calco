package com.cogito.calco

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.io.IOException

/**
 * La interfaz es una página web guardada dentro del APK (assets/index.html).
 * Esta pantalla la muestra, le da acceso a la galería y le permite mandar
 * paquetes terminados a WhatsApp.
 */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        web = WebView(this)
        web.setBackgroundColor(Color.BLACK)
        setContentView(web)

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = true
        web.settings.textZoom = 100

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                serveAsset(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host == HOST) return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, request.url))
                } catch (e: Exception) {
                    // sin app para abrir el enlace
                }
                return true
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: WebChromeClient.FileChooserParams,
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                val types = params.acceptTypes
                    .flatMap { it.split(",") }
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                val pick = Intent(Intent.ACTION_GET_CONTENT)
                pick.addCategory(Intent.CATEGORY_OPENABLE)
                if (types.size == 1) {
                    pick.type = types[0]
                } else {
                    pick.type = "*/*"
                    if (types.isNotEmpty()) pick.putExtra(Intent.EXTRA_MIME_TYPES, types.toTypedArray())
                }
                return try {
                    startActivityForResult(pick, REQ_FILE)
                    true
                } catch (e: ActivityNotFoundException) {
                    fileCallback = null
                    false
                }
            }
        }

        web.addJavascriptInterface(Bridge(), "CalcoNative")
        web.loadUrl("https://$HOST/assets/index.html")
    }

    /** Sirve los archivos de assets/ con una dirección https propia, así la página puede guardar datos. */
    private fun serveAsset(url: Uri): WebResourceResponse? {
        if (url.host != HOST) return null
        val path = url.path ?: return null
        if (!path.startsWith("/assets/")) return null
        val name = path.removePrefix("/assets/")
        if (name.isEmpty() || name.contains("..")) return null
        val mime = when (name.substringAfterLast('.', "").lowercase()) {
            "html" -> "text/html"
            "js" -> "text/javascript"
            "css" -> "text/css"
            "ttf" -> "font/ttf"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "json" -> "application/json"
            else -> "application/octet-stream"
        }
        return try {
            val res = WebResourceResponse(mime, "utf-8", assets.open(name))
            res.responseHeaders = mapOf("Access-Control-Allow-Origin" to "*")
            res
        } catch (e: IOException) {
            null
        }
    }

    private fun js(code: String) {
        runOnUiThread { web.evaluateJavascript(code, null) }
    }

    private fun waResult(status: String, message: String) {
        js("window.calcoWaResult && window.calcoWaResult(${JSONObject.quote(status)}, ${JSONObject.quote(message)})")
    }

    private fun openWhatsApp(pack: StickerStore.Pack) {
        val intent = Intent()
        intent.action = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
        intent.putExtra("sticker_pack_id", pack.id)
        intent.putExtra("sticker_pack_authority", "$packageName.stickercontentprovider")
        intent.putExtra("sticker_pack_name", pack.name)
        try {
            startActivityForResult(intent, REQ_WHATSAPP)
        } catch (e: ActivityNotFoundException) {
            waResult("error", "No encontré WhatsApp en este teléfono.")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_FILE -> {
                val cb = fileCallback
                fileCallback = null
                val uri = data?.data
                cb?.onReceiveValue(if (resultCode == RESULT_OK && uri != null) arrayOf(uri) else null)
            }
            REQ_WHATSAPP -> {
                val error = data?.getStringExtra("validation_error")
                when {
                    resultCode == RESULT_OK -> waResult("ok", "")
                    error != null -> waResult("error", error)
                    else -> waResult("cancel", "")
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        web.evaluateJavascript("(window.calcoBack ? window.calcoBack() : false)") { result ->
            if (result != "true") finish()
        }
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    /** Lo que la página puede pedirle al teléfono. */
    inner class Bridge {
        @JavascriptInterface
        fun version(): String = "1"

        @JavascriptInterface
        fun beginPack(id: String): Boolean = try {
            StickerStore.begin(this@MainActivity, id)
        } catch (e: Exception) {
            false
        }

        @JavascriptInterface
        fun putFile(id: String, name: String, base64: String): Boolean = try {
            StickerStore.put(this@MainActivity, id, name, Base64.decode(base64, Base64.DEFAULT))
            true
        } catch (e: Exception) {
            false
        }

        /** Devuelve "" si salió bien (y abre WhatsApp), o el motivo del error. */
        @JavascriptInterface
        fun commitPack(metaJson: String): String = try {
            val pack = StickerStore.commit(this@MainActivity, JSONObject(metaJson))
            runOnUiThread { openWhatsApp(pack) }
            ""
        } catch (e: Exception) {
            e.message ?: "No se pudo guardar el paquete."
        }
    }

    companion object {
        private const val HOST = "appassets.androidplatform.net"
        private const val REQ_FILE = 41
        private const val REQ_WHATSAPP = 42
    }
}
