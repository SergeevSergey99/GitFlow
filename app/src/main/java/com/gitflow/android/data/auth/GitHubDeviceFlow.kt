package com.gitflow.android.data.auth

import kotlinx.coroutines.delay

data class GitHubDeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val intervalSeconds: Int,
    val expiresInSeconds: Int
)

internal const val GITHUB_DEVICE_EXPIRED = "expired_token"
internal const val GITHUB_DEVICE_DENIED = "access_denied"

/** Returns the response carrying the token, or the one with a final error. */
internal suspend fun pollGitHubDeviceToken(
    code: GitHubDeviceCode,
    poll: suspend () -> GitHubDeviceTokenResponse
): GitHubDeviceTokenResponse {
    var intervalSeconds = code.intervalSeconds
    var waitedSeconds = 0
    while (waitedSeconds < code.expiresInSeconds) {
        delay(intervalSeconds * 1000L)
        waitedSeconds += intervalSeconds
        val response = poll()
        when (response.error) {
            "authorization_pending" -> Unit
            // Polling faster than allowed gets the app's requests rejected, so the new interval is kept for good.
            "slow_down" -> intervalSeconds = response.interval ?: (intervalSeconds + 5)
            else -> return response
        }
    }
    return GitHubDeviceTokenResponse(error = GITHUB_DEVICE_EXPIRED)
}
