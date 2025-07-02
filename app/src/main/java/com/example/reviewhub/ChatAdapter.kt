package com.bestpick.reviewhub

import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

class ChatAdapter(private val currentUserID: Int) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var messages: List<ChatMessage> = listOf()

    companion object {
        private const val VIEW_TYPE_LEFT = 1
        private const val VIEW_TYPE_RIGHT = 2
    }

    fun setMessages(newMessages: List<ChatMessage>) {
        messages = newMessages
        notifyDataSetChanged() // พิจารณาใช้ DiffUtil.Callback เพื่อประสิทธิภาพที่ดีขึ้นในแอปขนาดใหญ่
        Log.d("ChatAdapter", "Chat messages updated. New size: ${messages.size}")
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        val viewType = if (message.senderID == currentUserID) {
            VIEW_TYPE_RIGHT
        } else {
            VIEW_TYPE_LEFT
        }
        Log.v("ChatAdapter", "Message at position $position (senderID: ${message.senderID}) is VIEW_TYPE: $viewType")
        return viewType
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_RIGHT) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message_right, parent, false)
            Log.d("ChatAdapter", "Created RightChatViewHolder.")
            RightChatViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message_left, parent, false)
            Log.d("ChatAdapter", "Created LeftChatViewHolder.")
            LeftChatViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        if (holder is RightChatViewHolder) {
            holder.bind(message)
            Log.v("ChatAdapter", "Bound RightChatViewHolder for message: ${message.message}")
        } else if (holder is LeftChatViewHolder) {
            holder.bind(message)
            Log.v("ChatAdapter", "Bound LeftChatViewHolder for message: ${message.message} from ${message.nickname}")
        }
    }

    override fun getItemCount(): Int = messages.size

    inner class LeftChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val profileImage: ImageView = itemView.findViewById(R.id.profile_image)
        private val messageText: TextView = itemView.findViewById(R.id.message_text)

        fun bind(chatMessage: ChatMessage) {
            messageText.text = chatMessage.message
            Log.d("ChatAdapter", "Loading profile picture for senderID: ${chatMessage.senderID}, URL: '${chatMessage.profilePicture}'")

            // เพิ่มการจัดการสำหรับ profilePicture ที่อาจเป็น "null" หรือว่างเปล่า
            if (chatMessage.profilePicture.isNotEmpty() && chatMessage.profilePicture != "null") {
                Glide.with(itemView.context)
                    .load(chatMessage.profilePicture)
                    .diskCacheStrategy(DiskCacheStrategy.ALL) // Cache รูปภาพ
                    .placeholder(R.drawable.default_profile_picture) // รูปภาพ Placeholder
                    .error(R.drawable.error_loading_image) // รูปภาพเมื่อเกิดข้อผิดพลาดในการโหลด
                    .into(profileImage)
            } else {
                Log.w("ChatAdapter", "profilePicture is empty or 'null' for senderID: ${chatMessage.senderID}. Using default image.")
                profileImage.setImageResource(R.drawable.default_profile_picture) // ใช้รูปภาพ default
            }

            // กดที่รูปเพื่อไปหน้าโปรไฟล์
            profileImage.setOnClickListener {
                Log.d("ChatAdapter", "Clicked on profile image of user: ${chatMessage.senderID} (Nickname: ${chatMessage.nickname})")
                // ตรวจสอบว่า AnotherUserFragment สามารถรับ Intent ได้หรือไม่ หรือควรใช้ Navigation Component
                // สมมติว่า AnotherUserFragment เป็น Activity หรือเป็น Fragment ที่เปิดผ่าน Activity
                val intent = Intent(itemView.context, AnotherUserFragment::class.java) // เปลี่ยน AnotherUserFragment เป็น Activity ที่คุณต้องการ
                intent.putExtra("userID", chatMessage.senderID)  // ส่ง userID ของผู้ส่งไป
                itemView.context.startActivity(intent)
            }
        }
    }

    inner class RightChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.message_text)

        fun bind(chatMessage: ChatMessage) {
            messageText.text = chatMessage.message
            Log.v("ChatAdapter", "Right chat message: ${chatMessage.message}")
        }
    }
}