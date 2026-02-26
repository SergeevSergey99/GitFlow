package com.gitflow.android.data.auth

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

data class AzureProfile(
    val id: String,
    val displayName: String,
    val emailAddress: String? = null,
    val publicAlias: String? = null
)

data class AzureProject(
    val id: String,
    val name: String,
    val description: String? = null
)

data class AzureRepository(
    val id: String,
    val name: String,
    val project: AzureProjectRef,
    val remoteUrl: String? = null,
    val sshUrl: String? = null,
    val webUrl: String? = null,
    val defaultBranch: String? = null,
    val size: Long? = null
)

data class AzureProjectRef(
    val id: String,
    val name: String
)

data class AzurePagedResponse<T>(
    val value: List<T> = emptyList(),
    val count: Int = 0
)

// Azure DevOps uses dynamic org URLs, methods use @Url for absolute paths
interface AzureDevOpsApi {

    @GET
    suspend fun getProjects(
        @Url url: String,
        @Header("Authorization") authorization: String
    ): Response<AzurePagedResponse<AzureProject>>

    @GET
    suspend fun getRepositories(
        @Url url: String,
        @Header("Authorization") authorization: String
    ): Response<AzurePagedResponse<AzureRepository>>
}

// Azure DevOps profile API (separate base URL)
interface AzureDevOpsProfileApi {

    @GET
    suspend fun getProfile(
        @Url url: String,
        @Header("Authorization") authorization: String
    ): Response<AzureProfile>
}

/** Used to fetch the user's avatar image bytes from Azure DevOps. */
interface AzureDevOpsIdentityApi {
    @GET
    @Headers("Accept: image/png")
    suspend fun getIdentityImage(
        @Url url: String,
        @Header("Authorization") authorization: String
    ): Response<ResponseBody>
}
