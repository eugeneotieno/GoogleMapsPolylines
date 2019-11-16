package com.computingforgeeks.trucksend.models

import com.google.gson.annotations.SerializedName

data class Polyline(@SerializedName("points")
                    val points: String = "")