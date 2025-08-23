package com.bestpick.reviewhub

import com.google.gson.annotations.SerializedName

data class Post(
    val id: Int,
    val userName: String,
    val userId: Int,
    val title: String,
    val time: String,
    val updated: String? = null,
    val content: String,
    val is_liked: Boolean = false,
    val userProfileUrl: String? = null,

    @SerializedName("photo_url")
    val photoUrl: List<String>? = null,

    @SerializedName("video_url")
    val videoUrl: List<String>? = null,

    // counts: ให้ default = 0 เผื่อที่ไหนเรียก constructor แบบเก่า (ไม่ส่งค่า)
    @SerializedName("like_count")
    val likeCount: Int = 0,

    @SerializedName("comment_count")
    val commentCount: Int = 0,

    // เพิ่ม 2 ฟิลด์ที่หายไป — default = 0 จะป้องกัน error เวลาที่โค้ดเรียกไม่ครบ
    @SerializedName("bookmark_count")
    val bookmarkCount: Int = 0,

    @SerializedName("share_count")
    val shareCount: Int = 0,
)
