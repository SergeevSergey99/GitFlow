package com.gitflow.android.data.auth

import com.gitflow.android.data.models.GitRemoteRepository
import com.gitflow.android.data.models.GitUser
import retrofit2.Response
import retrofit2.http.*


data class GitHubDeviceCodeResponse(
    val device_code: String,
    val user_code: String,
    val verification_uri: String,
    val expires_in: Int,
    val interval: Int
)

/** While the user has not confirmed yet GitHub answers 200 with [error] set instead of a token. */
data class GitHubDeviceTokenResponse(
    val access_token: String? = null,
    val token_type: String? = null,
    val scope: String? = null,
    val error: String? = null,
    val error_description: String? = null,
    val interval: Int? = null
)

data class GitHubUser(
    val id: Long,
    val login: String,
    val name: String?,
    val email: String?,
    val avatar_url: String?
)

data class GitHubEmail(
    val email: String,
    val primary: Boolean,
    val verified: Boolean
)

data class GitHubRepository(
    val id: Long,
    val name: String,
    val full_name: String,
    val description: String?,
    val private: Boolean,
    val clone_url: String,
    val ssh_url: String,
    val html_url: String,
    val default_branch: String,
    val owner: GitHubUser,
    val updated_at: String,
    val size: Int? = null
)

interface GitHubApi {
    
    @POST("login/device/code")
    @FormUrlEncoded
    @Headers("Accept: application/json")
    suspend fun requestDeviceCode(
        @Field("client_id") clientId: String,
        @Field("scope") scope: String
    ): Response<GitHubDeviceCodeResponse>

    @POST("login/oauth/access_token")
    @FormUrlEncoded
    @Headers("Accept: application/json")
    suspend fun pollDeviceToken(
        @Field("client_id") clientId: String,
        @Field("device_code") deviceCode: String,
        @Field("grant_type") grantType: String = "urn:ietf:params:oauth:grant-type:device_code"
    ): Response<GitHubDeviceTokenResponse>
    
    @GET("user")
    suspend fun getCurrentUser(
        @Header("Authorization") authorization: String
    ): Response<GitHubUser>

    @GET("user/emails")
    suspend fun getUserEmails(
        @Header("Authorization") authorization: String
    ): Response<List<GitHubEmail>>
    
    @GET("user/repos")
    suspend fun getUserRepositories(
        @Header("Authorization") authorization: String,
        @Query("type") type: String = "all",
        @Query("sort") sort: String = "updated",
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1
    ): Response<List<GitHubRepository>>

    @GET("repos/{owner}/{repo}")
    suspend fun getRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Authorization") authorization: String? = null
    ): Response<GitHubRepository>
    
    @GET("user/orgs")
    suspend fun getUserOrganizations(
        @Header("Authorization") authorization: String
    ): Response<List<GitHubOrganization>>
    
    @GET("orgs/{org}/repos")
    suspend fun getOrganizationRepositories(
        @Header("Authorization") authorization: String,
        @Path("org") organization: String,
        @Query("type") type: String = "all",
        @Query("sort") sort: String = "updated",
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1
    ): Response<List<GitHubRepository>>

    @GET("repos/{owner}/{repo}/pulls")
    suspend fun getPullRequests(
        @Header("Authorization") authorization: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open",
        @Query("per_page") perPage: Int = 50
    ): Response<List<GitHubPullRequest>>
}

data class GitHubPullRequest(
    val number: Long,
    val title: String,
    val user: GitHubUser?,
    val head: GitHubPullRequestRef?,
    val base: GitHubPullRequestRef?,
    val draft: Boolean? = null,
    val updated_at: String?,
    val html_url: String?
)

data class GitHubPullRequestRef(
    val ref: String?
)

data class GitHubOrganization(
    val id: Long,
    val login: String,
    val description: String?,
    val avatar_url: String?
)
