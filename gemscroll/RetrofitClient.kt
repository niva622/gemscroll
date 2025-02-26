//RetrofitClient.kt
package com.example.gemscroll

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://apilb.stepn.com/") // Базовый URL (будет заменен при использовании @Url)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}