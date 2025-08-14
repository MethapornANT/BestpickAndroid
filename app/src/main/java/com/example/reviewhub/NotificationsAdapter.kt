package com.bestpick.reviewhub

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.*

class NotificationsAdapter(
    private val notificationList: List<Notification>,
    private val onNotificationClick: (Notification) -> Unit
) : RecyclerView.Adapter<NotificationsAdapter.NotificationViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_follow_you, parent, false)
        return NotificationViewHolder(v)
    }

    override fun onBindViewHolder(h: NotificationViewHolder, pos: Int) {
        h.bind(notificationList[pos], onNotificationClick)
    }

    override fun getItemCount() = notificationList.size

    class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val profileImage: ImageView = itemView.findViewById(R.id.comment_profile_image)
        private val userName: TextView = itemView.findViewById(R.id.comment_username)
        private val timeText: TextView = itemView.findViewById(R.id.comment_created_at)
        private val contentText: TextView = itemView.findViewById(R.id.comment_content)
        private val container: View = itemView.findViewById(R.id.notification_container)

        fun bind(n: Notification, onClick: (Notification) -> Unit) {
            userName.text = n.sender_name?.takeIf { it.isNotBlank() } ?: "Bestpick"
            timeText.text = formatTime(n.created_at)

            val msg = when (n.action_type) {
                "like" -> "${n.sender_name} liked your post"
                "follow" -> "${n.sender_name} started following you"
                "comment" -> "${n.sender_name} commented: ${n.comment_content.orEmpty().trim()}"
                else -> n.content
            }
            contentText.text = msg

            val rootUrl = itemView.context.getString(R.string.root_url) + "/api"
            val img: Any = if (!n.sender_picture.isNullOrEmpty()) "$rootUrl${n.sender_picture}" else R.drawable.profiletest2
            Glide.with(itemView).load(img).circleCrop().placeholder(R.drawable.testpic).into(profileImage)

            val bg = if (n.read_status == 1) R.color.gray_light else R.color.white
            container.setBackgroundColor(ContextCompat.getColor(itemView.context, bg))

            itemView.setOnClickListener { onClick(n) }
        }

        private fun formatTime(createdAt: String?): String {
            if (createdAt.isNullOrBlank()) return "N/A"
            val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd HH:mm:ss")
            for (p in patterns) try {
                val inFmt = SimpleDateFormat(p, Locale.getDefault()).apply {
                    if (p.contains("'Z'")) timeZone = TimeZone.getTimeZone("UTC")
                }
                val outFmt = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("Asia/Bangkok")
                }
                inFmt.parse(createdAt)?.let { return outFmt.format(it) }
            } catch (_: Exception) {}
            return createdAt
        }
    }
}
