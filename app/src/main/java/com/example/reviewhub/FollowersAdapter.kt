package com.example.reviewhub

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.reviewhub.FollowingAdapter.FollowingViewHolder

class FollowersAdapter(private var followerList: MutableList<Follower>) :
    RecyclerView.Adapter<FollowersAdapter.FollowerViewHolder>() {

    class FollowerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageProfile: ImageView = itemView.findViewById(R.id.user_profile_image)
        val textUsername: TextView = itemView.findViewById(R.id.user_name)
        val follow: TextView = itemView.findViewById(R.id.follow)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FollowerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_checkfollower, parent, false)
        return FollowerViewHolder(view)
    }

    override fun onBindViewHolder(holder: FollowerViewHolder, position: Int) {
        val follower = followerList[position]
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
            .into(holder.imageProfile) // ใส่รูปภาพใน ImageView

        holder.imageProfile.setOnClickListener{
            Toast.makeText(holder.itemView.context, "Clicked on ${follower.username}", Toast.LENGTH_SHORT).show()
        }


    }


    override fun getItemCount(): Int {
        return followerList.size
    }
}

