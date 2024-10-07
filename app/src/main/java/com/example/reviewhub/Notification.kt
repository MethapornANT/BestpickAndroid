package com.example.reviewhub

data class Notification(
    val id: Int,
    val receiver_id: Int,
    val post_id: Int,
    val action_type: String,
    val content: String,
    val read_status: Int,
    val created_at: String,
    val sender_name: String,
    val sender_picture: String?, // สามารถเป็น null ได้
    val receiver_name: String,
    val comment_content: String? // สามารถเป็น null ได้
)
