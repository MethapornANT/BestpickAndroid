package com.bestpick.reviewhub.models

data class UserAd(
    val id: Int,
    val order_id: Int,
    val title: String,
    val image: String?,
    val status: String,
    val expiration_date: String?,
    val created_at: String
)