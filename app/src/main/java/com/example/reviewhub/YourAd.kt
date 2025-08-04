package com.example.reviewhub

data class YourAd(
    val id: Int,
    val imageUrl: String,
    val caption: String,
    val url: String,
    val status: String, // สถานะ: "pending_approval", "pending_payment", "active", "expired"
    val packageType: String?,
    val duration: Int?,
    val startDate: String?,
    val endDate: String?
)