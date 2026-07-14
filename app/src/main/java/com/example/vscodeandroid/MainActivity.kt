package com.example.vscodeandroid

import android.app.DownloadManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.inputmethod.EditorInfo
import android.webkit.DownloadListener
import android.webkit.HttpAuthHandler
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.vscodeandroid.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var webView: WebView
    private val prefs by lazy { getSharedPreferences("vsm", MODE_PRIVATE) }

    // Mobile UA so vscode.dev renders its responsive layout (no "unsupported" screen).
    private val MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    companion object {
        private const val KEY_URL = "url"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        webView = binding.webview

        setupWebView()
        loadStartUrl()
    }

    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mediaPlaybackRequiresUserGesture = false
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.userAgentString = MOBILE_UA
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = true

        webView.webViewClient = VSCodeWebViewClient()
        webView.webChromeClient = VSCodeWebChromeClient()
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            val name = contentDisposition.takeIf { it.isNotBlank() }
                ?.substringAfter("filename=")?.trim('"')
                ?: "dl_${System.currentTimeMillis()}"
            val req = DownloadManager.Request(Uri.parse(url)).apply {
                addRequestHeader("User-Agent", userAgent)
                setMimeType(mimeType)
                setTitle(name)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(this@MainActivity, null, name)
            }
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
        }
    }

    private fun loadStartUrl() {
        val url = prefs.getString(KEY_URL, getString(R.string.default_url))
            ?: getString(R.string.default_url)
        webView.loadUrl(url)
    }

    private inner class VSCodeWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            if (uri.scheme == "http" || uri.scheme == "https") return false
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (_: Exception) {
            }
            return true
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            injectAssets()
        }

        override fun onReceivedHttpAuthRequest(
            view: WebView?,
            handler: HttpAuthHandler?,
            host: String?,
            realm: String?
        ) {
            val input = EditText(this@MainActivity)
            input.inputType = EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
            input.hint = "user:password"
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Auth required ($host)")
                .setView(input)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val parts = input.text.toString().split(":", limit = 2)
                    handler?.proceed(parts[0], parts.getOrElse(1) { "" })
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> handler?.cancel() }
                .show()
        }

        // ponytail: accept all cert errors for self-signed code-server on a trusted LAN.
        // Insecure on public networks — only run code-server on a network you trust.
        override fun onReceivedSslError(
            view: WebView?,
            handler: SslErrorHandler?,
            error: android.net.http.SslError?
        ) {
            handler?.proceed()
        }
    }

    private inner class VSCodeWebChromeClient : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            binding.progress.visibility =
                if (newProgress < 100) android.view.View.VISIBLE else android.view.View.GONE
            binding.progress.progress = newProgress
        }

        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: android.os.Message?
        ): Boolean {
            val pop = WebView(this@MainActivity)
            pop.settings.javaScriptEnabled = true
            pop.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, req: WebResourceRequest): Boolean {
                    webView.loadUrl(req.url.toString())
                    return true
                }
            }
            val transport = resultMsg?.obj as? WebView.WebViewTransport
            transport?.webView = pop
            resultMsg?.sendToTarget()
            return true
        }
    }

    private fun injectAssets() {
        val css = assets.open("mobile.css").bufferedReader().use { it.readText() }
        val js = assets.open("inject.js").bufferedReader().use { it.readText() }
        val b64css = Base64.encodeToString(css.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val b64js = Base64.encodeToString(js.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        // Inline <style> (not data: URI) so VS Code's CSP does not block it; run via
        // evaluateJavascript so it is not subject to CSP at all. Re-applied as the
        // workbench boots asynchronously (the initial page is just a loader).
        val script = """
            (function(){
              var CSS = atob('$b64css');
              var JS = atob('$b64js');
              function apply(){
                var s = document.getElementById('vsm-style');
                if(!s){ s = document.createElement('style'); s.id='vsm-style';
                  (document.head||document.documentElement).appendChild(s); }
                s.textContent = CSS;
              }
              apply();
              try { if (window.MutationObserver) {
                new MutationObserver(function(){ apply(); })
                  .observe(document.documentElement, {childList:true, subtree:true});
              } } catch(e){}
              try { (new Function(JS))(); } catch(e){}
              setTimeout(apply, 800); setTimeout(apply, 2000); setTimeout(apply, 4000);
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_open_url -> {
                showOpenUrlDialog()
                true
            }

            R.id.menu_reload -> {
                webView.reload()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showOpenUrlDialog() {
        val input = EditText(this)
        input.setText(webView.url ?: prefs.getString(KEY_URL, getString(R.string.default_url)))
        input.inputType = EditorInfo.TYPE_TEXT_VARIATION_URI
        AlertDialog.Builder(this)
            .setTitle(R.string.url_dialog_title)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    prefs.edit().putString(KEY_URL, url).apply()
                    webView.loadUrl(url)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
