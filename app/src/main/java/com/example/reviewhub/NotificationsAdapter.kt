package com.example.reviewhub

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.navigation.Navigation
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.*


class NotificationsAdapter(
    private val notificationList: List<Notification>,
    private val onNotificationClick: (Notification) -> Unit  // เพิ่ม Listener สำหรับคลิก Notification
) : RecyclerView.Adapter<NotificationsAdapter.NotificationViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_follow_you, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notificationList[position]
        holder.bind(notification, onNotificationClick)
    }

    override fun getItemCount(): Int = notificationList.size

    class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val profileImage: ImageView = itemView.findViewById(R.id.comment_profile_image)
        private val userName: TextView = itemView.findViewById(R.id.comment_username)
        private val actionText: TextView = itemView.findViewById(R.id.comment_created_at)
        private val notificationContent: TextView = itemView.findViewById(R.id.comment_content)
        private val notificationContainer: LinearLayout =
            itemView.findViewById(R.id.notification_container)

        fun bind(notification: Notification, onNotificationClick: (Notification) -> Unit) {
            notificationContent.text = notification.content
            // ตั้งค่าชื่อผู้ส่งการแจ้งเตือน
            userName.text = notification.sender_name ?: "Unknown User"

            // ฟอร์แมตวันที่แสดงผล
            actionText.text = formatTime(notification.created_at)

            // กำหนดเนื้อหาของการแจ้งเตือนตามประเภท
            notificationContent.text = when (notification.action_type) {
                "like" -> "liked your post"
                "follow" -> "started following you"
                "comment" -> {
                    val commentContent = notification.comment_content
                    if (commentContent!!.length > 10) {
                        "commented: ${commentContent.take(10)}..."
                    } else {
                        "commented: $commentContent"
                    }
                }
                else -> ""
            }


            // โหลดรูปโปรไฟล์ผู้ส่งการแจ้งเตือน ถ้าไม่มีแสดงรูปเริ่มต้น
            val rootUrl = itemView.context.getString(R.string.root_url) + "/api"
            val senderProfileImageUrl = if (!notification.sender_picture.isNullOrEmpty()) {
                "$rootUrl${notification.sender_picture}"
            } else {
                R.drawable.profiletest2 // รูปภาพเริ่มต้น
            }

            // โหลดรูปโปรไฟล์โดยใช้ Glide
            Glide.with(itemView.context)
                .load(senderProfileImageUrl)
                .circleCrop()
                .placeholder(R.drawable.testpic)
                .into(profileImage)

            // ตรวจสอบสถานะการอ่าน
            if (notification.read_status == 1) {
                notificationContainer.setBackgroundColor(itemView.context.getColor(R.color.gray_light))
            } else {
                notificationContainer.setBackgroundColor(itemView.context.getColor(R.color.white))
            }

            itemView.setOnClickListener {
                onNotificationClick(notification) // เรียก onNotificationClick เมื่อผู้ใช้คลิก

                // ใช้ NavController เพื่อจัดการการนำทาง
                val navController = Navigation.findNavController(itemView)
                val bundle = Bundle().apply {
                    putInt("POST_ID", notification.post_id)
                }
                navController.navigate(R.id.action_to_postdetailFragment, bundle)
            }



        }

        // ฟอร์แมตวันที่เพื่อให้แสดงผลอย่างถูกต้อง
        private fun formatTime(createdAt: String?): String {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("UTC") // ตั้งค่า inputFormat เป็น UTC
                }
                val outputFormat = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("Asia/Bangkok") // ตั้งค่า outputFormat เป็น Asia/Bangkok
                }
                val date = inputFormat.parse(createdAt ?: "")
                date?.let { outputFormat.format(it) } ?: "N/A"
            } catch (e: Exception) {
                "N/A"
            }
        }

    }
}
