package com.bestpick.reviewhub

data class Notification(
    val id: Int,
    val receiver_id: Int,
    val post_id: Int?,
    val action_type: String,
    val content: String,
    val post_content_snippet: String?,
    var read_status: Int,
    val created_at: String,
    val sender_name: String?,
    val sender_picture: String?,
    val receiver_name: String,
    val comment_content: String?,
    val ads_id: Int? = null
)

