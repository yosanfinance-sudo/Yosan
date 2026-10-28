package com.example.data.remote

import android.content.Context
import android.os.Build
import com.example.BuildConfig
import com.example.utils.SecureTokenManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val PREFS_NAME = "api_client_prefs"
    private const val KEY_CUSTOM_URL = "custom_backend_url"

    fun getBaseUrl(context: Context? = null): String {
        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val custom = prefs.getString(KEY_CUSTOM_URL, null)
            if (!custom.isNullOrBlank()) {
                val clean = custom.trim()
                if (clean.startsWith("http://") || clean.startsWith("https://")) {
                    return if (clean.endsWith("/")) clean else "$clean/"
                }
            }
        }

        val isEmulator = (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT)

        val defaultOnlineUrl = try {
            com.example.BuildConfig.DEFAULT_BACKEND_URL.trim()
        } catch (_: Exception) {
            ""
        }

        return when {
            defaultOnlineUrl.isNotBlank() -> if (defaultOnlineUrl.endsWith("/")) defaultOnlineUrl else "$defaultOnlineUrl/"
            else -> "https://yosan-xgsd.onrender.com/"
        }
    }

    @Synchronized
    fun setCustomBaseUrl(context: Context, url: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (url.isNullOrBlank()) {
            prefs.edit().remove(KEY_CUSTOM_URL).apply()
        } else {
            prefs.edit().putString(KEY_CUSTOM_URL, url.trim()).apply()
        }
        retrofit = null
    }

    @Volatile
    private var retrofit: Retrofit? = null

    @Synchronized
    fun getAuthApi(context: Context): AuthApi {
        if (retrofit == null) {
            val tokenManager = SecureTokenManager(context)

            val logging = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            }

            val authInterceptor = Interceptor { chain ->
                val original = chain.request()
                val accessToken = tokenManager.getAccessToken()
                val userEmail = tokenManager.getUserEmail()
                val sessionToken = tokenManager.getSessionToken()

                val requestBuilder = original.newBuilder()
                if (!accessToken.isNullOrBlank()) {
                    requestBuilder.header("Authorization", "Bearer $accessToken")
                }
                if (!userEmail.isNullOrBlank()) {
                    requestBuilder.header("X-User-Email", userEmail)
                }
                if (!sessionToken.isNullOrBlank()) {
                    requestBuilder.header("X-Session-Token", sessionToken)
                }
                val response = chain.proceed(requestBuilder.build())

                // Automatic Session Refresh on HTTP 401 Unauthorized
                if (response.code == 401 && !original.url.encodedPath.contains("/auth/refresh") && !original.url.encodedPath.contains("/auth/google")) {
                    val refreshToken = tokenManager.getRefreshToken()
                    if (!refreshToken.isNullOrBlank() && !refreshToken.startsWith("offline_")) {
                        val newToken = synchronized(this) {
                            val currentToken = tokenManager.getAccessToken()
                            if (currentToken != accessToken && !currentToken.isNullOrBlank()) {
                                currentToken
                            } else {
                                refreshAccessTokenSync(context, refreshToken)
                            }
                        }

                        if (!newToken.isNullOrBlank()) {
                            response.close()
                            val retryRequestBuilder = original.newBuilder()
                                .header("Authorization", "Bearer $newToken")
                            if (!userEmail.isNullOrBlank()) {
                                retryRequestBuilder.header("X-User-Email", userEmail)
                            }
                            val currentSession = tokenManager.getSessionToken()
                            if (!currentSession.isNullOrBlank()) {
                                retryRequestBuilder.header("X-Session-Token", currentSession)
                            }
                            return@Interceptor chain.proceed(retryRequestBuilder.build())
                        }
                    }
                }
                response
            }

            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .addInterceptor(logging)
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(getBaseUrl(context))
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
        }
        return retrofit!!.create(AuthApi::class.java)
    }

    /**
     * Sends a non-blocking background request to the health endpoint to wake up
     * the backend if it is asleep (e.g. Render free tier cold start).
     */
    fun prewarmBackend(context: Context? = null) {
        try {
            val url = getBaseUrl(context) + "api/health"
            val request = okhttp3.Request.Builder()
                .url(url)
                .get()
                .build()

            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    // Silent fail: best effort wake-up ping
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.close()
                }
            })
        } catch (_: Exception) {
            // Best effort ignore
        }
    }

    private fun refreshAccessTokenSync(context: Context, refreshToken: String): String? {
        return try {
            val tokenManager = SecureTokenManager(context)
            val refreshUrl = getBaseUrl(context) + "api/auth/refresh"
            val jsonBody = org.json.JSONObject().put("refreshToken", refreshToken).toString()
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val requestBody = jsonBody.toRequestBody(mediaType)
            val request = okhttp3.Request.Builder()
                .url(refreshUrl)
                .post(requestBody)
                .build()

            val rawClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            val res = rawClient.newCall(request).execute()
            if (res.isSuccessful) {
                val resBody = res.body?.string() ?: ""
                val jsonObj = org.json.JSONObject(resBody)
                val newAccess = jsonObj.optString("accessToken", "")
                val newRefresh = jsonObj.optString("refreshToken", refreshToken)
                if (newAccess.isNotBlank()) {
                    tokenManager.saveTokens(newAccess, newRefresh)
                    return newAccess
                }
            } else if (res.code == 401 || res.code == 403) {
                tokenManager.clearSession()
                com.example.utils.AppPreferences(context).isLoggedOut = true
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    @Volatile
    private var googleDriveRetrofit: Retrofit? = null

    @Synchronized
    fun getGoogleDriveApi(): GoogleDriveApi {
        if (googleDriveRetrofit == null) {
            val logging = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            googleDriveRetrofit = Retrofit.Builder()
                .baseUrl("https://www.googleapis.com/")
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
        }
        return googleDriveRetrofit!!.create(GoogleDriveApi::class.java)
    }
}

