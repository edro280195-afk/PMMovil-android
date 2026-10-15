package com.example.pmmovil.di

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.example.pmmovil.BuildConfig
import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.security.SecurePreferencesFactory
import com.example.pmmovil.data.security.SessionEventBus
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // internal (no private): EntradaScreen.kt y CameraScreen.kt la reutilizan para
    // construir URLs de fotos (Coil), así siempre apuntan al mismo entorno que
    // el resto de las llamadas de Retrofit (dev local / producción según corresponda).
    internal val BASE_URL: String = BuildConfig.PMM_API_BASE_URL

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: android.content.Context): SharedPreferences {
        return SecurePreferencesFactory.create(context)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        prefs: SharedPreferences,
        sessionEventBus: SessionEventBus
    ): OkHttpClient {
        val networkTraceEnabled = BuildConfig.DEBUG || BuildConfig.NETWORK_TRACE_ENABLED
        val logging = HttpLoggingInterceptor().apply {
            level = if (networkTraceEnabled) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val path = request.url.encodedPath
                val token = prefs.getString("AUTH_TOKEN", null)
                val requestBuilder = request.newBuilder()
                if (token != null && request.header("Authorization") == null && !path.contains("Auth/login", ignoreCase = true)) {
                    requestBuilder.addHeader("Authorization", "Bearer $token")
                }
                val authenticatedRequest = requestBuilder.build()
                val response = chain.proceed(authenticatedRequest)
                val currentToken = prefs.getString("AUTH_TOKEN", null)
                if (response.code == 401 && currentToken != null &&
                    authenticatedRequest.header("Authorization") == "Bearer $currentToken" &&
                    !path.contains("Auth/login", ignoreCase = true)) {
                    prefs.edit {
                        remove("AUTH_TOKEN")
                        remove("AUTH_USER")
                        remove("AUTH_NAME")
                        remove("AUTH_LUGAR")
                        remove("AUTH_PLANTA")
                    }
                    sessionEventBus.emitSessionExpired()
                }
                response
            }
            .addInterceptor { chain ->
                val request = chain.request()
                val path = request.url.encodedPath
                val startedAt = System.nanoTime()

                if (networkTraceEnabled) {
                    Log.d("PMM-NETWORK", "--> ${request.method} $path")
                }

                try {
                    val response = chain.proceed(request)
                    if (networkTraceEnabled) {
                        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
                        val message = if (response.code >= 400) {
                            extractResponseMessage(response)
                        } else {
                            null
                        }
                        Log.d(
                            "PMM-NETWORK",
                            "<-- ${response.code} $path ${elapsedMs}ms retryAfter=${response.header("Retry-After") ?: "N/D"} message=${message ?: "N/D"}"
                        )
                    }
                    response
                } catch (exception: Exception) {
                    if (networkTraceEnabled) {
                        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
                        Log.e(
                            "PMM-NETWORK",
                            "!! ${request.method} $path ${elapsedMs}ms exception=${exception::class.java.simpleName}",
                            exception
                        )
                    }
                    throw exception
                }
            }
            .addInterceptor(logging)
            .build()
    }

    private fun extractResponseMessage(response: okhttp3.Response): String? {
        val body = response.peekBody(64 * 1024).string()
        return Regex("\\\"message\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"")
            .find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?.take(200)
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun providePMMovilApi(retrofit: Retrofit): PMMovilApi {
        return retrofit.create(PMMovilApi::class.java)
    }
}
