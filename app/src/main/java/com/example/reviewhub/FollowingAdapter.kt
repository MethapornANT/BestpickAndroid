package com.example.reviewhub

import android.app.Activity
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class FollowingAdapter(private var followingList: MutableList<Following>) :
    RecyclerView.Adapter<FollowingAdapter.FollowingViewHolder>() {

    class FollowingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageProfile: ImageView = itemView.findViewById(R.id.user_profile_image)
        val textUsername: TextView = itemView.findViewById(R.id.user_name)
        val unfollow: TextView = itemView.findViewById(R.id.unfollow)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FollowingViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_checkfollow, parent, false)
        return FollowingViewHolder(view)
    }

    override fun onBindViewHolder(holder: FollowingViewHolder, position: Int) {
        val follower = followingList[position]
        holder.textUsername.text = follower.username

        val baseUrl = holder.itemView.context.getString(R.string.root_url)
        val profileImageUrl = follower.profileImageUrl
        Log.d("FollowingAdapter", "Full Image URL: $baseUrl$profileImageUrl")

        Glide.with(holder.itemView.context)
            .load(baseUrl + profileImageUrl)
            .centerCrop()
            .placeholder(R.drawable.ic_launcher_background)
            .error(R.drawable.ic_error)
            .into(holder.imageProfile)

        holder.imageProfile.setOnClickListener {
            openUserProfile(holder.itemView.context, follower.userId)
        }

        holder.unfollow.setOnClickListener {
            handleFollowButton(holder.itemView.context, follower.userId, holder.unfollow)
        }
    }

    override fun getItemCount(): Int {
        return followingList.size
    }

    private fun handleFollowButton(context: Context, userId: Int, unfollow: TextView) {
        val sharedPreferences = context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val token = sharedPreferences.getString("TOKEN", null)

        if (token != null) {
            unfollow.isEnabled = false // ปิดการใช้งานเพื่อป้องกันการคลิกซ้ำ
            followUnfollowUser(context, userId, token, unfollow)
        } else {
            Toast.makeText(context, "Token not available", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(context, "Failed to follow/unfollow user: ${e.message}", Toast.LENGTH_SHORT).show()
                    unfollow.isEnabled = true // เปิดการใช้งานอีกครั้ง
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        val jsonResponse = response.body?.string()
                        val message = JSONObject(jsonResponse).getString("message")
                        (context as? Activity)?.runOnUiThread {
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            unfollow.isEnabled = true
                            unfollow.text = if (unfollow.text == "Unfollow") "Follow" else "Unfollow"
                        }
                    } else {
                        (context as? Activity)?.runOnUiThread {
                            Toast.makeText(context, "Error: ${response.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })
    }

    private fun openUserProfile(context: Context, userId: Int) {
        val sharedPreferences = context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val currentUserId = sharedPreferences.getString("USER_ID", null)?.toIntOrNull()
        val token = sharedPreferences.getString("TOKEN", null)

        val fragmentManager = (context as AppCompatActivity).supportFragmentManager
        if (userId == currentUserId) {
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

        postId?.let { requestBodyBuilder.add("post_id", it.toString()) }
        content?.let { requestBodyBuilder.add("content", it) }

        val requestBody = requestBodyBuilder.build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                (context as? Activity)?.runOnUiThread {
                    Toast.makeText(context, "Failed to record interaction: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        (context as? Activity)?.runOnUiThread {
                            Toast.makeText(context, "Failed to record interaction: ${response.message}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val jsonResponse = response.body?.string()
                        val message = JSONObject(jsonResponse).getString("message")
                        (context as? Activity)?.runOnUiThread {
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })
    }
}

