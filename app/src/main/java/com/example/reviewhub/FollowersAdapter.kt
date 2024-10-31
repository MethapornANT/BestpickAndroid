package com.bestpick.reviewhub

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class FollowersAdapter(private var followerList: MutableList<Follower>) :
    RecyclerView.Adapter<FollowersAdapter.FollowerViewHolder>() {

    class FollowerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageProfile: ImageView = itemView.findViewById(R.id.user_profile_image)
        val textUsername: TextView = itemView.findViewById(R.id.user_name)
        val unfollow: TextView = itemView.findViewById(R.id.unfollow)
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
        val profileImageUrl = "/api" +follower.profileImageUrl

        Log.d("FollowersAdapter", "Full Image URL: $baseUrl$profileImageUrl")
        // ใช้ Glide เพื่อโหลดรูปภาพ
        Glide.with(holder.itemView.context)
            .load(baseUrl + profileImageUrl)
            .centerCrop()
            .placeholder(R.drawable.ic_launcher_background)
            .into(holder.imageProfile)

        holder.imageProfile.setOnClickListener {
            openUserProfile(holder.itemView.context, follower.userId)
        }

        val sharedPreferences = holder.itemView.context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val token = sharedPreferences.getString("TOKEN", null)
        val userId = sharedPreferences.getString("USER_ID", null)?.toIntOrNull()

        if (token != null && userId != null) {
            checkFollowStatus(userId, follower.userId, token, holder.unfollow, holder.itemView.context)
        }

        holder.unfollow.setOnClickListener {
            handleFollowButton(holder.itemView.context, follower.userId, holder.unfollow)
        }
    }

    private fun checkFollowStatus(
        userId: Int,
        followingId: Int,
        token: String,
        unfollow: TextView,
        context: Context
    ) {
        val client = OkHttpClient()
        val url = "${context.getString(R.string.root_url)}/api/users/$userId/follow/$followingId/status"

        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val jsonObject = JSONObject(responseBody)
                        val isFollowing = jsonObject.getBoolean("isFollowing")

                        withContext(Dispatchers.Main) {
                            // อัปเดตข้อความของ `unfollow` ตามสถานะการติดตาม
                            unfollow.text = if (isFollowing) "Unfollow" else "Follow Back"
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                }
            }
        }
    }

    private fun handleFollowButton(context: Context, userId: Int, unfollow: TextView) {
        val sharedPreferences = context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val token = sharedPreferences.getString("TOKEN", null)

        if (token != null) {
            unfollow.isEnabled = false // ปิดการใช้งานเพื่อป้องกันการคลิกซ้ำ
            followUnfollowUser(context, userId, token, unfollow)
        }
    }

    private fun followUnfollowUser(context: Context, followingId: Int, token: String, unfollow: TextView) {
        val sharedPreferences = context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val userIdString = sharedPreferences.getString("USER_ID", null)
        val userId = userIdString?.toIntOrNull() ?: return
        val url = context.getString(R.string.root_url) + "/api/users/$userId/follow/$followingId"
        val requestBody = FormBody.Builder().build()

        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                (context as? Activity)?.runOnUiThread {
                    unfollow.isEnabled = true // เปิดการใช้งานอีกครั้ง
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        val jsonResponse = response.body?.string()
                        val message = JSONObject(jsonResponse).getString("message")
                        (context as? Activity)?.runOnUiThread {
                            unfollow.isEnabled = true
                            unfollow.text = if (unfollow.text == "Unfollow") "Follow" else "Unfollow"
                        }
                    } else {
                        (context as? Activity)?.runOnUiThread {
                        }
                    }
                }
            }
        })
    }

    override fun getItemCount(): Int {
        return followerList.size
    }

    private fun openUserProfile(context: Context, userId: Int) {
        val sharedPreferences = context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val currentUserId = sharedPreferences.getString("USER_ID", null)?.toIntOrNull()
        val token = sharedPreferences.getString("TOKEN", null)

        val fragmentManager = (context as AppCompatActivity).supportFragmentManager
        if (userId == currentUserId) {
            // Open ProfileFragment for the current user
            val profileFragment = ProfileFragment().apply {
                arguments = Bundle().apply {
                    putBoolean("isSelfProfile", true)
                }
            }
            fragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, profileFragment)
                .addToBackStack(null)
                .commit()

        } else {
            // Open AnotherUserFragment for a different user
            val anotherUserFragment = AnotherUserFragment().apply {
                arguments = Bundle().apply {
                    putInt("USER_ID", userId)
                }
            }

            token?.let {
                recordInteraction(null, "view_profile", null, it, context)
            }

            fragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, anotherUserFragment)
                .addToBackStack(null)
                .commit()
        }
    }

    private fun recordInteraction(postId: Int? = null, actionType: String, content: String? = null, token: String, context: Context) {
        val client = OkHttpClient()
        val url = "${context.getString(R.string.root_url)}${context.getString(R.string.interactions)}"

        val requestBodyBuilder = FormBody.Builder()
            .add("action_type", actionType)

        postId?.let {
            requestBodyBuilder.add("post_id", it.toString())
        }

        content?.let {
            requestBodyBuilder.add("content", it)
        }

        val requestBody = requestBodyBuilder.build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                (context as? Activity)?.runOnUiThread {
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        (context as? Activity)?.runOnUiThread {
                        }
                    } else {
                        val jsonResponse = response.body?.string()
                        val message = JSONObject(jsonResponse).getString("message")
                        (context as? Activity)?.runOnUiThread {
                        }
                    }
                }
            }
        })
    }
}