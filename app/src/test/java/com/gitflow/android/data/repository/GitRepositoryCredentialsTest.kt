package com.gitflow.android.data.repository

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gitflow.android.data.auth.AuthManager
import com.gitflow.android.data.models.GitProvider
import com.gitflow.android.data.settings.AppSettingsManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for host matching in [GitRepository.resolveCredentialsProvider] — the security fix
 * from P0.3. A credentials provider must be returned only for a real provider host or its
 * subdomains, never for a look-alike host that merely contains the provider domain as a substring.
 */
@RunWith(RobolectricTestRunner::class)
// Force a stock Application: the real GitFlowApplication.onCreate() starts Koin/WorkManager,
// which throw under Robolectric and are not needed here.
@Config(sdk = [34], application = Application::class)
class GitRepositoryCredentialsTest {

    private lateinit var authManager: AuthManager
    private lateinit var repo: GitRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        authManager = mockk(relaxed = true)
        repo = GitRepository(context, authManager, mockk<AppSettingsManager>(relaxed = true))
    }

    @Test
    fun exactGithubHost_returnsCredentials() = runBlocking {
        every { authManager.getAccessToken(GitProvider.GITHUB) } returns "gh-token"

        assertNotNull(repo.resolveCredentialsProvider("https://github.com/owner/repo.git"))
    }

    @Test
    fun githubSubdomain_returnsCredentials() = runBlocking {
        every { authManager.getAccessToken(GitProvider.GITHUB) } returns "gh-token"

        // "*.github.com" is a legitimate GitHub host and must match.
        assertNotNull(repo.resolveCredentialsProvider("https://api.github.com/owner/repo.git"))
    }

    @Test
    fun lookAlikeGithubHost_returnsNull() = runBlocking {
        // The vulnerability: "github.com.attacker.com" contains "github.com" but is NOT GitHub.
        // The token must NOT be handed to it.
        every { authManager.getAccessToken(GitProvider.GITHUB) } returns "gh-token"

        assertNull(repo.resolveCredentialsProvider("https://github.com.attacker.com/owner/repo.git"))
    }

    @Test
    fun unknownHost_returnsNull() = runBlocking {
        every { authManager.getAccessToken(GitProvider.GITHUB) } returns "gh-token"

        assertNull(repo.resolveCredentialsProvider("https://example.com/owner/repo.git"))
    }

    @Test
    fun embeddedUserInfo_returnsCredentialsWithoutProviderToken() = runBlocking {
        // Credentials embedded in the URL are used directly, regardless of host.
        assertNotNull(repo.resolveCredentialsProvider("https://user:pass@internal.example.com/owner/repo.git"))
    }

    @Test
    fun blankUrl_returnsNull() = runBlocking {
        assertNull(repo.resolveCredentialsProvider("   "))
    }
}
