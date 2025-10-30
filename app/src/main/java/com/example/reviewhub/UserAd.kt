package com.bestpick.reviewhub.models

data class UserAd(
    val id: Int,
    val order_id: Int,
    val title: String,
    val content: String,
    val image: String?,
    val status: String,
    val show_at: String?,
    val expiration_date: String?,
    val created_at: String,
    val package_name: String?,
    val package_price: Double?,
    val package_duration: Int?,
    val admin_notes: String? // << Add this line
)