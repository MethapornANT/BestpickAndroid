package com.example.reviewhub

import com.google.gson.annotations.SerializedName

data class Follower(
    val userId: Int,
    val username: String,
    val profileImageUrl: String?
)
