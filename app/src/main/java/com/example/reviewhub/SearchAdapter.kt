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
    fun onItemClick(postId: Int)
}

// Adapter สำหรับจัดการแสดงผลใน RecyclerView
class SearchAdapter(
    private val searchResults: List<SearchResult>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<SearchAdapter.ContentViewHolder>() {

    // ViewHolder สำหรับจัดการการแสดงผลของแต่ละ Item
    class ContentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val usernameTextView: TextView = itemView.findViewById(R.id.username)
        val mainImageView: ImageView = itemView.findViewById(R.id.main_image)
        val profileImageView: ImageView = itemView.findViewById(R.id.Imgviewprofilesearch)
        val titleTextView: TextView = itemView.findViewById(R.id.title_text_view)
        val contentTextView: TextView = itemView.findViewById(R.id.content) // เชื่อมโยง `content` ใน XML


        // ฟังก์ชัน bind สำหรับกำหนดข้อมูลใน View แต่ละตัว
        fun bind(result: SearchResult, listener: OnItemClickListener) {
            usernameTextView.text = result.username
            titleTextView.text = result.title
            contentTextView.text = result.content ?: "No Content"


            // กำหนด URL ของรูปภาพโปรไฟล์และรูปภาพโพสต์จาก Resource
            val profileUrl = itemView.context.getString(R.string.root_url) + result.profileImageUrl
            val postUrl = itemView.context.getString(R.string.root_url) + result.imageUrl
            Log.d("SearchAdapter", "Profile URL: $profileUrl")


            // โหลดรูปภาพโปรไฟล์
            Glide.with(itemView.context)
                .load(profileUrl) // URL ของรูปภาพโปรไฟล์
                .placeholder(R.drawable.profiletest2) // รูปภาพที่แสดงก่อนโหลดเสร็จ
                .into(profileImageView)

            // โหลดรูปภาพโพสต์หลัก
            Glide.with(itemView.context)
                .load(postUrl) // URL ของรูปภาพโพสต์หลัก
                .placeholder(R.drawable.testpic) // รูปภาพที่แสดงก่อนโหลดเสร็จ
                .into(mainImageView)

            // ตั้งค่า Click Listener สำหรับโพสต์นี้
            itemView.setOnClickListener {
                result.postId?.let { postId ->
                    listener.onItemClick(postId)
                }
            }
        }
    }

    // กำหนด Layout ของแต่ละ ViewHolder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_searchlayout, parent, false)
        return ContentViewHolder(view)
    }

    // กำหนดการแสดงผลข้อมูลใน ViewHolder
    override fun onBindViewHolder(holder: ContentViewHolder, position: Int) {
        holder.bind(searchResults[position], listener)
    }

    // จำนวนข้อมูลทั้งหมดในรายการ
    override fun getItemCount(): Int {
        return searchResults.size
    }
}
