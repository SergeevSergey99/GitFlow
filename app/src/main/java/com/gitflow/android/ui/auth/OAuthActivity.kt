package com.gitflow.android.ui.auth

import android.content.Intent
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.gitflow.android.data.models.GitProvider
import com.gitflow.android.ui.theme.GitFlowTheme

@OptIn(ExperimentalMaterial3Api::class)
class OAuthActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PROVIDER = "provider"
        const val EXTRA_AUTH_URL = "auth_url"
        const val EXTRA_REDIRECT_URI = "redirect_uri"
        const val EXTRA_STATE = "state"

        const val RESULT_CODE = "code"
        const val RESULT_STATE = "state"
        const val RESULT_ERROR = "error"

        private val ALLOWED_HOSTS = setOf(
            "github.com",
            "gitlab.com"
        )

        internal fun isHostAllowed(host: String): Boolean {
            return ALLOWED_HOSTS.any { host == it || host.endsWith(".$it") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val provider = intent.getStringExtra(EXTRA_PROVIDER)?.let {
            GitProvider.valueOf(it)
        } ?: run {
            finish()
            return
        }

        val authUrl = intent.getStringExtra(EXTRA_AUTH_URL) ?: run {
            finish()
            return
        }

        val redirectUri = intent.getStringExtra(EXTRA_REDIRECT_URI) ?: run {
            finish()
            return
        }

        val expectedState = intent.getStringExtra(EXTRA_STATE) ?: run {
            finish()
            return
        }

        setContent {
            GitFlowTheme {
                OAuthScreen(
                    provider = provider,
                    authUrl = authUrl,
                    redirectUri = redirectUri,
                    expectedState = expectedState,
                    onCodeReceived = { code, state ->
                        val resultIntent = Intent().apply {
                            putExtra(RESULT_CODE, code)
                            putExtra(RESULT_STATE, state)
                        }
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    },
                    onError = { error ->
                        val resultIntent = Intent().apply {
                            putExtra(RESULT_ERROR, error)
                        }
                        setResult(RESULT_CANCELED, resultIntent)
                        finish()
                    },
                    onClose = {
                        setResult(RESULT_CANCELED)
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OAuthScreen(
    provider: GitProvider,
    authUrl: String,
    redirectUri: String,
    expectedState: String,
    onCodeReceived: (code: String, state: String) -> Unit,
    onError: (String) -> Unit,
    onClose: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        onDispose { }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text("Авторизация ${if (provider == GitProvider.GITHUB) "GitHub" else "GitLab"}")
            },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                }
            },
            actions = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть")
                }
            }
        )

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString() ?: return false

                                // Always allow redirect URI
                                if (url.startsWith(redirectUri)) {
                                    return false
                                }

                                val host = request.url?.host ?: return true
                                if (!OAuthActivity.isHostAllowed(host)) {
                                    return true // Block navigation to unknown hosts
                                }
                                return false
                            }

                            override fun onPageStarted(
                                view: WebView?,
                                url: String?,
                                favicon: android.graphics.Bitmap?
                            ) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true

                                url?.let {
                                    checkForAuthCode(it, redirectUri, expectedState, onCodeReceived, onError)
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                errorCode: Int,
                                description: String?,
                                failingUrl: String?
                            ) {
                                super.onReceivedError(view, errorCode, description, failingUrl)
                                isLoading = false
                                onError("Ошибка загрузки: $description")
                            }

                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: SslErrorHandler?,
                                error: SslError?
                            ) {
                                handler?.cancel()
                                onError("SSL error - connection not secure")
                            }
                        }

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            allowFileAccess = false
                            allowContentAccess = false
                        }

                        loadUrl(authUrl)
                    }
                }
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Загрузка...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

private fun checkForAuthCode(
    url: String,
    redirectUri: String,
    expectedState: String,
    onCodeReceived: (code: String, state: String) -> Unit,
    onError: (String) -> Unit
) {
    if (url.startsWith(redirectUri)) {
        val uri = Uri.parse(url)

        val error = uri.getQueryParameter("error")
        if (error != null) {
            val errorDescription = uri.getQueryParameter("error_description") ?: error
            onError("Ошибка авторизации: $errorDescription")
            return
        }

        val state = uri.getQueryParameter("state")
        if (state != expectedState) {
            onError("OAuth state mismatch - possible CSRF attack")
            return
        }

        val code = uri.getQueryParameter("code")
        if (code != null && state != null) {
            onCodeReceived(code, state)
        } else {
            onError("Не удалось получить код авторизации")
        }
    }
}
