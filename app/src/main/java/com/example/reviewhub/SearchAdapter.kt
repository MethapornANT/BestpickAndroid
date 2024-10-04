package com.example.reviewhub

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// สร้าง Data Class สำหรับข้อมูล
data class SearchResult(
    val userId: Int,
    val username: String,
    val postId: Int? = null,  // postId เป็น optional
    val content: String? = null // content เป็น optional
)

// กำหนด Interface สำหรับจัดการการคลิก
interface OnItemClickListener {
    fun onItemClick(postId: Int) // แก้ไขเป็นการส่ง postId แทน
}
class SearchAdapter(
    private val searchResults: List<SearchResult>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_CONTENT = 1
    }

    class UsernameViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val usernameTextView: TextView = itemView.findViewById(R.id.username_text_view)
    }

    class ContentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val contentTextView: TextView = itemView.findViewById(R.id.content_text_view)

        // กำหนดฟังก์ชันสำหรับคลิกที่โพสต์
        fun bind(content: String, postId: Int, listener: OnItemClickListener) {
            contentTextView.text = content
            if(content == "No Content"){
                itemView.setOnClickListener(null)
                itemView.isClickable = false
                itemView.alpha = 0.5f
            }
            else {
                itemView.isClickable = true
                itemView.alpha = 1.0f
                itemView.setOnClickListener {
                    listener.onItemClick(postId)
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (searchResults[position].postId == null) TYPE_USER else TYPE_CONTENT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_USER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_username, parent, false)
                UsernameViewHolder(view)
            }
            TYPE_CONTENT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_content, parent, false)
                ContentViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val searchResult = searchResults[position]
        if (holder is ContentViewHolder) {
            holder.bind(searchResult.content ?: "No Content", searchResult.postId ?: -1, listener)
        } else if (holder is UsernameViewHolder) {
            holder.usernameTextView.text = searchResult.username
        }
    }

    override fun getItemCount(): Int {
        return searchResults.size
    }
}




