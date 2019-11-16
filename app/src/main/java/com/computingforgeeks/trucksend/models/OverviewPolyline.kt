package com.computingforgeeks.trucksend.models

import com.google.gson.annotations.SerializedName

data class OverviewPolyline(@SerializedName("points")
                            val points: String = "")