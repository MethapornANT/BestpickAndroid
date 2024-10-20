package com.example.reviewhub

import com.google.gson.annotations.SerializedName

data class Follower(
    val id: Int,
    val username: String,
    val profileImageUrl: String?
)
