package com.example.reviewhub

import com.google.gson.annotations.SerializedName

data class Post(
    val id: Int,
    val userName: String,
    val userId: Int,
    val title : String,
    val time: String,
    val updated: String?,
    val content: String,
    val is_liked: Boolean,
    val userProfileUrl: String?,

    @SerializedName("photo_url")
    val photoUrl: List<String>?,

    @SerializedName("video_url")
    val videoUrl: List<String>?,
    val likeCount: Int,
    val commentCount: Int,
)



