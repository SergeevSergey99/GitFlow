package com.gitflow.android.data.auth

import retrofit2.Response
import retrofit2.http.*

data class BitbucketOAuthResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Int? = null,
    val refresh_token: String? = null,
    val scopes: String? = null
)

data class BitbucketUser(
    val account_id: String,
    val username: String? = null,
    val display_name: String,
    val links: BitbucketUserLinks? = null
)

data class BitbucketUserLinks(
    val avatar: BitbucketLink? = null
)

data class BitbucketLink(val href: String)

data class BitbucketPagedResponse<T>(
    val values: List<T> = emptyList(),
    val next: String? = null,
    val size: Int? = null
)

data class BitbucketWorkspacePermission(
    val workspace: BitbucketWorkspace
)

data class BitbucketWorkspace(
    val slug: String,
    val name: String
)

data class BitbucketRepository(
    val uuid: String,
    val name: String,
    val full_name: String,
    val description: String? = null,
    val is_private: Boolean = false,
    val links: BitbucketRepoLinks? = null,
    val mainbranch: BitbucketBranch? = null,
    val updated_on: String = "",
    val owner: BitbucketOwner? = null,
    val size: Long? = null,
    val scm: String = "git"
)

data class BitbucketRepoLinks(
    val clone: List<BitbucketCloneLink>? = null,
    val html: BitbucketLink? = null
)

data class BitbucketCloneLink(val name: String, val href: String)

data class BitbucketBranch(val name: String)

data class BitbucketOwner(
    val account_id: String? = null,
    val display_name: String? = null,
    val username: String? = null,
    val links: BitbucketUserLinks? = null
)

interface BitbucketApi {

    @POST("site/oauth2/access_token")
    @FormUrlEncoded
    suspend fun getAccessToken(
        @Header("Authorization") basicAuth: String,
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("code_verifier") codeVerifier: String
    ): Response<BitbucketOAuthResponse>

    @POST("site/oauth2/access_token")
    @FormUrlEncoded
    suspend fun refreshToken(
        @Header("Authorization") basicAuth: String,
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String
    ): Response<BitbucketOAuthResponse>

    @GET("2.0/user")
    suspend fun getCurrentUser(
        @Header("Authorization") authorization: String
    ): Response<BitbucketUser>

    @GET("2.0/user/permissions/workspaces")
    suspend fun getWorkspaces(
        @Header("Authorization") authorization: String,
        @Query("pagelen") pagelen: Int = 100
    ): Response<BitbucketPagedResponse<BitbucketWorkspacePermission>>

    @GET("2.0/repositories/{workspace}")
    suspend fun getRepositories(
        @Header("Authorization") authorization: String,
        @Path("workspace") workspace: String,
        @Query("role") role: String = "member",
        @Query("pagelen") pagelen: Int = 100,
        @Query("sort") sort: String = "-updated_on"
    ): Response<BitbucketPagedResponse<BitbucketRepository>>
}
