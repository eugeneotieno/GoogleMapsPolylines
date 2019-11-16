package com.computingforgeeks.trucksend.networking

import com.computingforgeeks.trucksend.models.Directions
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface RetrofitCalls {

    @GET("directions/json")
    fun getDirections(
            @Query("origin") origin: String,
            @Query("destination") destination: String,
            @Query("mode") mode: String,
            @Query("key") key: String
    ): Call<Directions>
}