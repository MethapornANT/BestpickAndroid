package com.example.reviewhub

import com.google.gson.annotations.SerializedName

data class Following(
    val userId: Int,
    val username: String,
    val profileImageUrl: String?
)
