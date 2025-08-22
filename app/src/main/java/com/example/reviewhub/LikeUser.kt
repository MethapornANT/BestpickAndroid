package com.bestpick.reviewhub

// Simple model for a user who liked
data class LikeUser(
    val userId: Int,
    val username: String,
    val profileImageUrl: String?
)
