package com.computingforgeeks.trucksend.models

import com.google.gson.annotations.SerializedName

data class Distance(@SerializedName("text")
                    val text: String = "",
                    @SerializedName("value")
                    val value: Int = 0)