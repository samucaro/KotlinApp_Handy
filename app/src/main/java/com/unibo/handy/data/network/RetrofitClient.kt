package com.unibo.handy.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "http://10.0.2.2:8000" // Sostituire con l'URL del server se ci sarà
    // Opzionale, mi aiuta solo a inserire i timeout o i log
    val sharedHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(sharedHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    val retrofitService : ServiceAPI by lazy {
        retrofit.create(ServiceAPI::class.java)
    }
}