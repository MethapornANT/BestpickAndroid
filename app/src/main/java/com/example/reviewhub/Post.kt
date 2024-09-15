package com.example.reviewhub

import com.google.gson.annotations.SerializedName

data class Post(
    val id: Int,
    val userName: String,
    val time: String,
    val updated: String?, // This field maps to the "updated_at" JSON key
    val content: String,
    val userProfileUrl: String?,

    @SerializedName("photo_url")
    val photoUrl: List<String>?,

    @SerializedName("video_url")
    val videoUrl: List<String>?
)



