//Apiservice.kt
package com.example.gemscroll

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

interface ApiService {
    @GET
    suspend fun getStonePrice(@Url url: String): Response<StonePriceResponse>
}
