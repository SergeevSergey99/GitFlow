package com.gitflow.android.data.auth

import com.gitflow.android.data.models.GitRemoteRepository
import com.gitflow.android.data.models.GitUser
import retrofit2.Response
import retrofit2.http.*

data class GitHubOAuthResponse(
    val access_token: String,
    val token_type: String,
    val scope: String
)

data class GitHubUser(
    val id: Long,
    val login: String,
    val name: String?,
    val email: String?,
    val avatar_url: String?
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
    
    @POST("login/oauth/access_token")
    @Headers("Accept: application/json")
    suspend fun getAccessToken(
        @Body request: Map<String, String>
    ): Response<GitHubOAuthResponse>
    
    @GET("user")
    suspend fun getCurrentUser(
        @Header("Authorization") authorization: String
    ): Response<GitHubUser>
    
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
}

data class GitHubOrganization(
    val id: Long,
    val login: String,
    val description: String?,
    val avatar_url: String?
)
