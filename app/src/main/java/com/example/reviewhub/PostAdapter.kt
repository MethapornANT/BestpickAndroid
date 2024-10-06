package com.example.reviewhub

import android.app.Activity
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class PostAdapter(private val postList: List<Post>) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_post, parent, false)
        return PostViewHolder(view, this) // ส่งตัวแปร adapter เข้าไปใน ViewHolder
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = postList[position]
        holder.bind(post)
    }

    override fun getItemCount(): Int = postList.size

    class PostViewHolder(itemView: View, private val adapter: PostAdapter) : RecyclerView.ViewHolder(itemView) { // รับ adapter เป็น parameter
        private val userName: TextView = itemView.findViewById(R.id.user_name)
        private val follower: TextView = itemView.findViewById(R.id.follower)
        private val postTime: TextView = itemView.findViewById(R.id.post_time)
        private val title: TextView = itemView.findViewById(R.id.title)
        private val postContent: TextView = itemView.findViewById(R.id.post_content)
        private val userProfileImage: ImageView = itemView.findViewById(R.id.user_profile_image)
        private val mediaViewPager: ViewPager2 = itemView.findViewById(R.id.media_view_pager)
        private val likeButton: ImageView = itemView.findViewById(R.id.like_button)
        private val shareButton: ImageView = itemView.findViewById(R.id.share_button)
        var isLiked = false
        var isFollowing = false

        fun bind(post: Post) {
            val context = itemView.context
            val baseUrl = context.getString(R.string.root_url)

            // Construct full URLs for media and profile image
            val profileImageUrl = post.userProfileUrl?.let { baseUrl + it }
            val photoUrls = post.photoUrl?.map { Pair(baseUrl + it, "photo") } ?: emptyList()
            val videoUrls = post.videoUrl?.map { Pair(baseUrl + it, "video") } ?: emptyList()
            val mediaUrls = photoUrls + videoUrls
            val displayTime = post.updated ?: post.time

            // Set user details
            postTime.text = formatTime(displayTime)
            userName.text = post.userName
            title.text = post.title
            postContent.text = if (post.content.length > 40) {
                post.content.substring(0, 40) + "....."
            } else {
                post.content
            }

            val sharedPreferences = context.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
            val token = sharedPreferences.getString("TOKEN", null)
            val userId = sharedPreferences.getString("USER_ID", null)



            // Load profile image using the full URL
            Glide.with(context)
                .load(profileImageUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_launcher_background)
                .error(R.drawable.ic_error)
                .into(userProfileImage)

            // Set up ViewPager2 for photo and video slideshow if there is any media
            if (mediaUrls.isNotEmpty()) {
                val adapter = PhotoPagerAdapter(mediaUrls)
                mediaViewPager.adapter = adapter
                mediaViewPager.visibility = View.VISIBLE

                adapter.setOnItemClickListener { position, mediaType ->
                    if (context is FragmentActivity) {
                        val postDetailFragment = PostDetailFragment()
                        val bundle = Bundle()
                        bundle.putInt("POST_ID", post.id)
                        postDetailFragment.arguments = bundle
                        recordInteraction(post.id, "view", null, token!!, context)
                        context.supportFragmentManager.beginTransaction()
                            .replace(R.id.nav_host_fragment, postDetailFragment)
                            .addToBackStack(null)
                            .commit()
                    }
                }
            } else {
                mediaViewPager.visibility = View.GONE
            }



            userProfileImage.setOnClickListener {
                val fragmentManager = (context as? FragmentActivity)?.supportFragmentManager
                if (fragmentManager != null) {
                    val transaction = fragmentManager.beginTransaction()
                    val anotherUserFragment = AnotherUserFragment()

                    // ส่งข้อมูล USER_ID ไปยัง Fragment
                    val bundle = Bundle()
                    bundle.putInt("USER_ID", post.userId)
                    anotherUserFragment.arguments = bundle
                    recordInteraction(post.id, "view_profile", null, token!!, context)
                    // แทนที่ Fragment ปัจจุบันด้วย AnotherUserFragment
                    transaction.replace(R.id.nav_host_fragment, anotherUserFragment)
                    transaction.addToBackStack(null)
                    transaction.commit()
                }
            }







            // เรียก API เพื่อตรวจสอบสถานะการกดไลค์ของโพสต์
            if (token != null && userId != null) {
                checkLikeStatus(post.id, userId.toInt(), token, context)
                checkFollowStatus(post.userId, userId.toInt(), token, context)
            }
            likeButton.setOnClickListener {
                isLiked = !isLiked
                if (isLiked) {
                    likeButton.setImageResource(R.drawable.heartclick)
                } else {
                    likeButton.setImageResource(R.drawable.heart)
                }
                if (token != null && userId != null) {
                    likeUnlikePost(post.id, userId.toInt(), token, context)
                    recordInteraction(post.id, if (isLiked) "like" else "unlike", null, token, context)
                } else {
                    Toast.makeText(context, "Token or UserID not available", Toast.LENGTH_SHORT).show()
                }
            }

            // กำหนดการคลิกปุ่มติดตาม/เลิกติดตาม
            follower.setOnClickListener {
                if (token != null && userId != null) {
                    // ทำการ Follow/Unfollow ผู้ใช้
                    followUnfollowUser(post.userId, userId.toInt(), token, context)
                    // บันทึก Interaction ใหม่ โดยใช้ "follow" หรือ "unfollow" แทน
                    recordInteraction(post.id, if (isFollowing) "follow" else "unfollow", null, token, context)
                } else {
                    Toast.makeText(context, "Token or UserID not available", Toast.LENGTH_SHORT).show()
                }
            }


            shareButton.setOnClickListener {
                sharePost(context, post)
            }
        }

        private fun sharePost(context: Context, post: Post) {
            val shareText = "Check out this post from ${post.userName}:\n${post.content}"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            context.startActivity(Intent.createChooser(intent, "Share Post via"))
        }

        // ฟังก์ชันเรียก API เพื่อตรวจสอบสถานะการกดไลค์
        private fun checkLikeStatus(postId: Int, userId: Int, token: String, context: Context) {
            val client = OkHttpClient()
            val url = "${context.getString(R.string.root_url)}${context.getString(R.string.check_like_status)}$postId/$userId"

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", "Bearer $token") // ส่ง token ใน header
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    (context as? Activity)?.runOnUiThread {
                        Toast.makeText(context, "Failed to check like status: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val jsonResponse = response.body?.string()
                    val isPostLiked = JSONObject(jsonResponse).getBoolean("isLiked")

                    (context as? Activity)?.runOnUiThread {
                        isLiked = isPostLiked
                        likeButton.setImageResource(if (isLiked) R.drawable.heartclick else R.drawable.heart)
                    }
                }
            })
        }

        private fun likeUnlikePost(postId: Int, userId: Int, token: String, context: Context) {
            val client = OkHttpClient()
            val url = context.getString(R.string.root_url) + context.getString(R.string.postlikeorunlike) + postId
            val requestBody = FormBody.Builder()
                .add("user_id", userId.toString())
                .build()

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $token")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    (context as? Activity)?.runOnUiThread {
                        Toast.makeText(context, "Failed to like/unlike post: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val jsonResponse = response.body?.string()
                    val message = JSONObject(jsonResponse).getString("message")

                    response.use {
                        if (!response.isSuccessful) {
                            (context as? Activity)?.runOnUiThread {
                                Toast.makeText(context, "Error: ${response.message}", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            (context as? Activity)?.runOnUiThread {
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            })
        }

        // ฟังก์ชันตรวจสอบสถานะการติดตาม
        private fun checkFollowStatus(followingId: Int, userId: Int, token: String, context: Context) {
            val client = OkHttpClient()
            val url = "${context.getString(R.string.root_url)}/api/users/$userId/follow/$followingId/status"

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", "Bearer $token")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    (context as? Activity)?.runOnUiThread {
                        Toast.makeText(context, "Failed to check follow status: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val jsonResponse = response.body?.string()
                    if (!jsonResponse.isNullOrEmpty()) {
                        val isUserFollowing = JSONObject(jsonResponse).getBoolean("isFollowing")
                        (context as? Activity)?.runOnUiThread {
                            isFollowing = isUserFollowing
                            follower.text = if (isFollowing) "Following" else "Follow"
                        }
                    } else {
                        (context as? Activity)?.runOnUiThread {
                            Toast.makeText(context, "Failed to get follow status", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
        }

        // ฟังก์ชันติดตาม/เลิกติดตาม
        private fun followUnfollowUser(followingId: Int, userId: Int, token: String, context: Context) {
            val client = OkHttpClient()
            val url = "${context.getString(R.string.root_url)}/api/users/$userId/follow/$followingId"
            val requestBody = FormBody.Builder().build()

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $token")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    (context as? Activity)?.runOnUiThread {
                        Toast.makeText(context, "Failed to follow/unfollow user: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val jsonResponse = response.body?.string()
                    if (!jsonResponse.isNullOrEmpty()) {
                        val message = JSONObject(jsonResponse).getString("message")
                        (context as? Activity)?.runOnUiThread {
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            isFollowing = !isFollowing
                            follower.text = if (isFollowing) "Following" else "Follow"

                            adapter.notifyItemChanged(adapterPosition)
                        }
                    } else {
                        (context as? Activity)?.runOnUiThread {
                            Toast.makeText(context, "Failed to update follow status", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
        }


        private fun formatTime(timeString: String): String {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                inputFormat.timeZone = TimeZone.getTimeZone("UTC")
                val date = inputFormat.parse(timeString)
                if (date != null) {
                    val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    outputFormat.format(date)
                } else {
                    timeString
                }
            } catch (e: Exception) {
                timeString
            }
        }

        private fun recordInteraction(postId: Int, actionType: String, content: String? = null, token: String, context: Context) {
            val client = OkHttpClient()
            val url = "${context.getString(R.string.root_url)}${context.getString(R.string.interactions)}"

            // สร้าง body ของ request
            val requestBody = FormBody.Builder()
                .add("post_id", postId.toString())
                .add("action_type", actionType)
                .apply {
                    if (content != null) {
                        add("content", content)
                    }
                }
                .build()

            // สร้าง request พร้อมแนบ token ใน header
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $token")
                .build()

            // ส่ง request ไปยังเซิร์ฟเวอร์
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
}
