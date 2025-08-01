package com.example.reviewhub

data class YourAd(
    val id: Int,
    val imageUrl: String,
    val caption: String,
    val url: String,
    val status: String, // <-- ตัวแปรที่สำคัญที่สุดสำหรับหน้านี้!
    val packageType: String?,
    val duration: Int?,
    val startDate: String?,
    val endDate: String?
)