package com.gitflow.android.ui.auth

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gitflow.android.R
import com.gitflow.android.data.models.GitProvider
import com.gitflow.android.ui.theme.GitFlowTheme
import timber.log.Timber

/**
 * Hosts the OAuth authorization step in a Custom Tab (the device's real, fully-trusted
 * browser) instead of an embedded WebView. This removes the whole class of WebView-trust
 * issues (host allowlisting, JS/SSL settings, embedded-browser detection by providers) since
 * the user authenticates in the actual browser they already trust, with its own address bar
 * and certificate handling.
 *
 * Declared `launchMode="singleTask"` in the manifest, and reused for the OAuth redirect via
 * the `.OAuthCallbackActivity` alias (`gitflow://oauth/...`, exported, BROWSABLE). This is the
 * standard pattern for Custom-Tabs-based OAuth (also used by AppAuth): the same Activity
 * instance that launched the tab is revisited via [onNewIntent] when the browser redirects
 * back, rather than a new instance being created — so it can still fulfil the
 * `startActivityForResult` contract [AuthViewModel]/[AuthScreen] are waiting on.
 */
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
    }

    private var authUrl: String = ""
    private var redirectUri: String = ""
    private var expectedState: String = ""
    private var provider: GitProvider = GitProvider.GITHUB

    private var customTabLaunched = false
    private var redirectHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        provider = intent.getStringExtra(EXTRA_PROVIDER)?.let {
            runCatching { GitProvider.valueOf(it) }.getOrNull()
        } ?: return finishCancelled()
        authUrl = intent.getStringExtra(EXTRA_AUTH_URL) ?: return finishCancelled()
        redirectUri = intent.getStringExtra(EXTRA_REDIRECT_URI) ?: return finishCancelled()
        expectedState = intent.getStringExtra(EXTRA_STATE) ?: return finishCancelled()

        setContent {
            GitFlowTheme {
                OAuthWaitingScreen(
                    providerName = providerDisplayName(provider),
                    onCancel = { finishCancelled() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!customTabLaunched) {
            // First time this Activity becomes visible: open the Custom Tab. Doing this here
            // (rather than in onCreate) keeps it tied to actual visibility across OEM skins.
            customTabLaunched = true
            launchCustomTab()
        } else if (!redirectHandled) {
            // We're back on screen after the tab was shown, but onNewIntent never fired —
            // the user backed out of the browser without completing sign-in.
            finishCancelled()
        }
    }

    private fun launchCustomTab() {
        try {
            CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(authUrl))
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "No browser available to launch OAuth Custom Tab")
            finishWithError(getString(R.string.oauth_error_no_browser))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        redirectHandled = true

        // Guard defensively: the intent-filter only scopes on scheme+host, not the exact
        // path, so make sure this is really our expected redirect before handling it —
        // otherwise the Activity would be left stuck on the waiting screen forever.
        val url = intent.data?.toString()
        if (url == null || !url.startsWith(redirectUri)) return finishCancelled()
        checkForAuthCode(
            context = this,
            url = url,
            redirectUri = redirectUri,
            expectedState = expectedState,
            onCodeReceived = { code, state -> finishSuccess(code, state) },
            onError = { message -> finishWithError(message) }
        )
    }

    private fun finishSuccess(code: String, state: String) {
        setResult(RESULT_OK, Intent().apply {
            putExtra(RESULT_CODE, code)
            putExtra(RESULT_STATE, state)
        })
        finish()
    }

    private fun finishWithError(message: String) {
        setResult(RESULT_CANCELED, Intent().apply { putExtra(RESULT_ERROR, message) })
        finish()
    }

    private fun finishCancelled() {
        setResult(RESULT_CANCELED)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OAuthWaitingScreen(providerName: String, onCancel: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.oauth_title, providerName)) },
            actions = {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.auth_close))
                }
            }
        )
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.oauth_waiting_browser),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun checkForAuthCode(
    context: Context,
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
            onError(context.getString(R.string.oauth_error_auth, errorDescription))
            return
        }

        val state = uri.getQueryParameter("state")
        if (state != expectedState) {
            onError("OAuth state mismatch - possible CSRF attack")
            return
        }

        val code = uri.getQueryParameter("code")
        if (code != null) {
            onCodeReceived(code, state)
        } else {
            onError(context.getString(R.string.oauth_error_no_code))
        }
    }
}
