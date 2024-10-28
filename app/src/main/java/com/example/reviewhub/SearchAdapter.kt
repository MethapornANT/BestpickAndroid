package com.example.reviewhub

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

// Data class สำหรับเก็บข้อมูลการค้นหา
data class SearchResult(
    val userId: Int,
    val username: String,
    val postId: Int? = null,
    val content: String? = null,
    val title: String? = null,
    val profileImageUrl: String = "",
    val imageUrl: String = ""
)

// Interface สำหรับคลิกที่ Item ใน RecyclerView
interface OnItemClickListener {
    fun onItemClick(postId: Int?, userId: Int)
}

// Adapter สำหรับจัดการแสดงผลใน RecyclerView
// Adapter สำหรับจัดการแสดงผลใน RecyclerView
class SearchAdapter(
    private val searchResults: List<SearchResult>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_USER_NO_POST = 0
        private const val VIEW_TYPE_USER_WITH_POST = 1
    }

    // ViewHolder สำหรับผู้ใช้ที่ไม่มีโพสต์
    class UserNoPostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImageView: ImageView = itemView.findViewById(R.id.profile_image)
        val usernameTextView: TextView = itemView.findViewById(R.id.username)

        fun bind(result: SearchResult, listener: OnItemClickListener) { // เพิ่ม listener เป็นพารามิเตอร์
            usernameTextView.text = result.username
            val baseUrl = itemView.context.getString(R.string.root_url) + "/api"

            // โหลดรูปภาพโปรไฟล์
            Glide.with(itemView.context)
                .load(baseUrl + result.profileImageUrl)
                .placeholder(R.drawable.profile) // รูปภาพที่แสดงก่อนโหลดเสร็จ
                .into(profileImageView)

            // ตั้งค่า Click Listener สำหรับผู้ใช้ที่ไม่มีโพสต์
            itemView.setOnClickListener {
                listener.onItemClick(null, result.userId) // ส่ง userId และ null สำหรับ postId
            }
        }
    }


    // ViewHolder สำหรับผู้ใช้ที่มีโพสต์
    class UserWithPostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val usernameTextView: TextView = itemView.findViewById(R.id.usernamesearch)
        val mainImageView: ImageView = itemView.findViewById(R.id.main_image)
        val profileImageView: ImageView = itemView.findViewById(R.id.profile_image)
        val titleTextView: TextView = itemView.findViewById(R.id.title_text_view)
        val contentTextView: TextView = itemView.findViewById(R.id.content)

        // ฟังก์ชัน bind สำหรับกำหนดข้อมูลใน View แต่ละตัว
        fun bind(result: SearchResult, listener: OnItemClickListener) {
            usernameTextView.text = result.username
            titleTextView.text = result.title ?: "No Title"
            contentTextView.text = result.content ?: "No Content"

            // กำหนด URL ของรูปภาพโปรไฟล์และรูปภาพโพสต์จาก Resource
            val baseUrl = itemView.context.getString(R.string.root_url) + "/api"
            val profileUrl =result.profileImageUrl

            // โหลดรูปภาพโปรไฟล์
            Glide.with(itemView.context)
                .load(baseUrl + profileUrl) // URL ของรูปภาพโปรไฟล์
                .placeholder(R.drawable.user) // รูปภาพที่แสดงก่อนโหลดเสร็จ
                .into(profileImageView)

            // เช็คว่ามีโพสต์หรือไม่ ถ้ามีจึงจะแสดงรูปภาพและข้อมูลโพสต์
            if (result.postId != null) {
                val postUrl = result.imageUrl
                Glide.with(itemView.context)
                    .load(baseUrl + postUrl) // URL ของรูปภาพโพสต์หลัก
                    .placeholder(R.drawable.testpic) // รูปภาพที่แสดงก่อนโหลดเสร็จ
                    .into(mainImageView)

                // ตั้งค่า Click Listener สำหรับโพสต์นี้
                itemView.setOnClickListener {
                    listener.onItemClick(result.postId, result.userId)
                }
            } else {
                // ถ้าไม่มีโพสต์ ซ่อน ImageView ที่แสดงรูปโพสต์
                mainImageView.visibility = View.GONE

                // ปิดการคลิกถ้าไม่มีโพสต์
                itemView.setOnClickListener(null)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (searchResults[position].postId == null) {
            VIEW_TYPE_USER_NO_POST
        } else {
            VIEW_TYPE_USER_WITH_POST
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_USER_NO_POST -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user_search, parent, false)
                UserNoPostViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_searchlayout, parent, false)
                UserWithPostViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val result = searchResults[position]

        when (holder.itemViewType) {
            VIEW_TYPE_USER_NO_POST -> (holder as UserNoPostViewHolder).bind(result, listener) // ส่ง listener เข้าไปใน bind
            VIEW_TYPE_USER_WITH_POST -> (holder as UserWithPostViewHolder).bind(result, listener) // ส่ง listener เข้าไปใน bind
        }
    }


    override fun getItemCount(): Int {
        return searchResults.size
    }
}

