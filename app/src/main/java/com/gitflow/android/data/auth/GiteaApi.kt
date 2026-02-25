package com.gitflow.android.data.auth

import retrofit2.Response
import retrofit2.http.*

data class GiteaUser(
    val id: Long,
    val login: String,
    val full_name: String? = null,
    val email: String? = null,
    val avatar_url: String? = null
)

data class GiteaRepository(
    val id: Long,
    val name: String,
    val full_name: String,
    val description: String? = null,
    val private: Boolean = false,
    val clone_url: String,
    val ssh_url: String,
    val html_url: String,
    val default_branch: String = "main",
    val owner: GiteaUser,
    val updated_at: String = "",
    val size: Long? = null
)

data class GiteaSearchResult(
    val data: List<GiteaRepository> = emptyList(),
    val ok: Boolean = true
)

// Gitea requires a dynamic base URL per instance, so methods use @Url for absolute paths
interface GiteaApi {

    @GET
    suspend fun getCurrentUser(
        @Url url: String,
        @Header("Authorization") authorization: String
    ): Response<GiteaUser>

    @GET
    suspend fun searchRepositories(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Query("limit") limit: Int = 50,
        @Query("page") page: Int = 1,
        @Query("sort") sort: String = "newest"
    ): Response<GiteaSearchResult>
}
