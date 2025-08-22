package com.bestpick.reviewhub

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.navigation.Navigation
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class LikeAdapter(private val likeList: MutableList<LikeUser>) : RecyclerView.Adapter<LikeAdapter.LikeViewHolder>() {

    class LikeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageProfile: ImageView = itemView.findViewById(R.id.user_profile_image)
        val textUsername: TextView = itemView.findViewById(R.id.user_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LikeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_like, parent, false)
        return LikeViewHolder(view)
    }

    override fun onBindViewHolder(holder: LikeViewHolder, position: Int) {
        val user = likeList[position]

        holder.textUsername.text = user.username

        // Build full URL safely (handles values like "/api/..." or "/uploads/..." or full http)
        val ctx = holder.itemView.context
        val baseUrl = ctx.getString(R.string.root_url).trimEnd('/')
        val profilePath = user.profileImageUrl ?: ""
        val fullUrl = when {
            profilePath.isBlank() -> ""
            profilePath.startsWith("http") -> profilePath
            profilePath.startsWith("/") -> "$baseUrl$profilePath"
            else -> "$baseUrl/$profilePath"
        }

        if (fullUrl.isNotBlank()) {
            Glide.with(holder.itemView.context)
                .load(fullUrl)
                .centerCrop()
                .placeholder(R.drawable.ic_launcher_background)
                .error(R.drawable.ic_error)
                .into(holder.imageProfile)
        } else {
            holder.imageProfile.setImageResource(R.drawable.ic_launcher_background)
        }

        // เมื่อกดที่ item => ไป AnotherUserFragment ของ user นี้
        holder.itemView.setOnClickListener {
            try {
                val navController = Navigation.findNavController(holder.itemView)
                val bundle = Bundle().apply { putInt("USER_ID", user.userId) }
                navController.navigate(R.id.AnotherUserFragment, bundle)
            } catch (e: Exception) {
                // ถ้า navigation failed, log และอย่า crash
                e.printStackTrace()
            }
        }

        // หากต้องการให้กดที่รูปหรือชื่อแยกได้ ก็เพิ่ม listener ให้ imageProfile/textUsername ด้วย (ผมผูกทั้ง row ไว้แล้ว)
        holder.imageProfile.setOnClickListener { holder.itemView.performClick() }
        holder.textUsername.setOnClickListener { holder.itemView.performClick() }
    }

    override fun getItemCount(): Int = likeList.size

    fun updateList(newList: List<LikeUser>) {
        likeList.clear()
        likeList.addAll(newList)
        notifyDataSetChanged()
    }
}
