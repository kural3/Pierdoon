package com.ku.pierdoon

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.ku.pierdoon.data.download.Downloader

/**
 * Google's own sign-in page, for YouTube's cookies: with them yt-dlp downloads as the user's
 * account, and YouTube stops asking it to prove it isn't a bot. Closes itself once signed in.
 */
class YouTubeLoginActivity : ComponentActivity() {
    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Downloader.init(this)
        web = WebView(this).apply {
            fitsSystemWindows = true
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // Google turns embedded browsers away from its sign-in; without the "wv" mark this is
            // just Chrome on the phone.
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    val host = android.net.Uri.parse(url).host.orEmpty()
                    if (!host.endsWith("youtube.com")) return
                    val cookies = CookieManager.getInstance().getCookie(YOUTUBE).orEmpty()
                    if ("SAPISID=" !in cookies && "__Secure-3PAPISID=" !in cookies) return
                    CookieManager.getInstance().flush()
                    Downloader.saveYouTubeCookies(cookies)
                    setResult(RESULT_OK)
                    finish()
                }
            }
            loadUrl(SIGN_IN)
        }
        setContentView(web)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    private companion object {
        const val YOUTUBE = "https://www.youtube.com"
        const val SIGN_IN = "https://accounts.google.com/ServiceLogin?service=youtube&passive=true&continue=https%3A%2F%2Fwww.youtube.com%2F"
    }
}
