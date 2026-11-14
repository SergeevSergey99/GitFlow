package com.gitflow.android.data.auth

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GitHubDeviceFlowTest {

    private val code = GitHubDeviceCode(
        deviceCode = "device",
        userCode = "ABCD-1234",
        verificationUri = "https://github.com/login/device",
        intervalSeconds = 5,
        expiresInSeconds = 900
    )

    private fun pending() = GitHubDeviceTokenResponse(error = "authorization_pending")

    @Test
    fun pendingThenToken_returnsTokenAfterWaitingEachInterval() = runTest {
        val replies = ArrayDeque(listOf(pending(), pending(), GitHubDeviceTokenResponse(access_token = "token")))

        val result = pollGitHubDeviceToken(code) { replies.removeFirst() }

        assertEquals("token", result.access_token)
        assertEquals(15_000L, currentTime)
    }

    @Test
    fun slowDown_keepsTheNewIntervalForLaterPolls() = runTest {
        val replies = ArrayDeque(
            listOf(
                GitHubDeviceTokenResponse(error = "slow_down", interval = 10),
                pending(),
                GitHubDeviceTokenResponse(access_token = "token")
            )
        )

        pollGitHubDeviceToken(code) { replies.removeFirst() }

        assertEquals(5_000L + 10_000L + 10_000L, currentTime)
    }

    @Test
    fun neverConfirmed_endsAsExpiredWhenTheCodeLifetimeRunsOut() = runTest {
        var polls = 0

        val result = pollGitHubDeviceToken(code) { polls++; pending() }

        assertEquals(GITHUB_DEVICE_EXPIRED, result.error)
        assertEquals(900 / 5, polls)
    }

    @Test
    fun declinedOnGitHub_stopsPollingImmediately() = runTest {
        var polls = 0

        val result = pollGitHubDeviceToken(code) { polls++; GitHubDeviceTokenResponse(error = GITHUB_DEVICE_DENIED) }

        assertEquals(GITHUB_DEVICE_DENIED, result.error)
        assertEquals(1, polls)
    }
}
