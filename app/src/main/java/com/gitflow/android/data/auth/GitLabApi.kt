package com.gitflow.android.data.auth

import com.gitflow.android.data.models.GitRemoteRepository
import com.gitflow.android.data.models.GitUser
import retrofit2.Response
import retrofit2.http.*

data class GitLabOAuthResponse(
    val access_token: String,
    val token_type: String,
    val refresh_token: String?,
    val expires_in: Int?,
    val scope: String
)

data class GitLabUser(
    val id: Long,
    val username: String,
    val name: String?,
    val email: String?,
    val avatar_url: String?
)

data class GitLabRepository(
    val id: Long,
    val name: String,
    val name_with_namespace: String,
    val description: String?,
    val visibility: String, // "private", "internal", "public"
    val http_url_to_repo: String,
    val ssh_url_to_repo: String,
    val web_url: String,
    val default_branch: String?,
    val owner: GitLabUser?,
    val namespace: GitLabNamespace,
    val last_activity_at: String
)

data class GitLabNamespace(
    val id: Long,
    val name: String,
    val path: String,
    val kind: String, // "user" or "group"
    val full_path: String,
    val avatar_url: String?
)

data class GitLabGroup(
    val id: Long,
    val name: String,
    val path: String,
    val description: String?,
    val visibility: String,
    val avatar_url: String?
)

interface GitLabApi {
    
    @POST("oauth/token")
    @FormUrlEncoded
    suspend fun getAccessToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("code") code: String,
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("redirect_uri") redirectUri: String
    ): Response<GitLabOAuthResponse>
    
    @GET("api/v4/user")
    suspend fun getCurrentUser(
        @Header("Authorization") authorization: String
    ): Response<GitLabUser>
    
    @GET("api/v4/projects")
    suspend fun getUserProjects(
        @Header("Authorization") authorization: String,
        @Query("membership") membership: Boolean = true,
        @Query("order_by") orderBy: String = "last_activity_at",
        @Query("sort") sort: String = "desc",
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1
    ): Response<List<GitLabRepository>>
    
    @GET("api/v4/groups")
    suspend fun getUserGroups(
        @Header("Authorization") authorization: String,
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1
    ): Response<List<GitLabGroup>>
    
    @GET("api/v4/groups/{id}/projects")
    suspend fun getGroupProjects(
        @Header("Authorization") authorization: String,
        @Path("id") groupId: Long,
        @Query("order_by") orderBy: String = "last_activity_at",
        @Query("sort") sort: String = "desc",
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1
    ): Response<List<GitLabRepository>>
}
