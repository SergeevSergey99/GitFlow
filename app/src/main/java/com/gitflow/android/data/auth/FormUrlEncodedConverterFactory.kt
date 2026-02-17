package com.gitflow.android.data.auth

import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

class FormUrlEncodedConverterFactory : Converter.Factory() {

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        if (type == GitHubOAuthResponse::class.java) {
            return FormUrlEncodedConverter()
        }
        return null
    }

    private class FormUrlEncodedConverter : Converter<ResponseBody, GitHubOAuthResponse> {
        override fun convert(value: ResponseBody): GitHubOAuthResponse? {
            val responseString = value.string()
            val params = parseUrlEncoded(responseString)

            if (params.containsKey("error")) {
                val error = params["error"]
                val errorDescription = params["error_description"] ?: ""
                throw IllegalArgumentException("GitHub OAuth error: $error - $errorDescription")
            }

            val accessToken = params["access_token"]
            if (accessToken.isNullOrEmpty()) {
                throw IllegalArgumentException("Missing access_token in OAuth response")
            }

            return GitHubOAuthResponse(
                access_token = accessToken,
                token_type = params["token_type"] ?: "bearer",
                scope = params["scope"] ?: ""
            )
        }

        private fun parseUrlEncoded(data: String): Map<String, String> {
            return data.split("&").associate { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) {
                    parts[0] to parts[1]
                } else {
                    parts[0] to ""
                }
            }
        }
    }
}
