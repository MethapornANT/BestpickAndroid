package com.example.reviewhub

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.reviewhub.FollowersAdapter.FollowerViewHolder

class FollowingAdapter(private var followingList: MutableList<Following>) :
    RecyclerView.Adapter<FollowingAdapter.FollowingViewHolder>() {

    class FollowingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageProfile: ImageView = itemView.findViewById(R.id.user_profile_image) // เปลี่ยนชื่อเป็น imageProfile
        val textUsername: TextView = itemView.findViewById(R.id.user_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FollowingViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_checkfollow, parent, false)
        return FollowingViewHolder(view)
    }

    override fun onBindViewHolder(holder: FollowingViewHolder, position: Int) {
        val follower = followingList[position]
        holder.textUsername.text = follower.username

        // ดึง baseUrl จาก context และเชื่อมต่อกับ profileImageUrl
        val baseUrl = holder.itemView.context.getString(R.string.root_url)
        val profileImageUrl = follower.profileImageUrl

        Log.d("FollowersAdapter", "Full Image URL: $baseUrl$profileImageUrl")

        // ใช้ Glide เพื่อโหลดรูปภาพ
        Glide.with(holder.itemView.context) // ใช้ context จาก itemView
            .load(baseUrl + profileImageUrl) // เชื่อมต่อ baseUrl กับ profileImageUrl
            .centerCrop()
            .placeholder(R.drawable.ic_launcher_background)
            .error(R.drawable.ic_error) // เพิ่มรูป error หากโหลดรูปไม่ได้
            .into(holder.imageProfile) // ใส่รูปภาพใน ImageView


    }

    override fun getItemCount(): Int {
        return followingList.size
    }
}




