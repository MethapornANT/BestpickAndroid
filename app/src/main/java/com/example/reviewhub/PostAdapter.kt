package com.example.reviewhub

import android.app.Activity
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import android.widget.PopupMenu
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class PostAdapter(private val postList: MutableList<Post>) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

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
        private val reportButton: ImageView = itemView.findViewById(R.id.report)
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
                post.content.substring(0, 40) + " See more.."
            } else {
                post.content
            }

            val sharedPreferences = context.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
            val token = sharedPreferences.getString("TOKEN", null)
            val userId = sharedPreferences.getString("USER_ID", null)
            val isUserPost = userId?.toInt() == post.userId
            // ตรวจสอบว่าโพสต์นี้เป็นของผู้ใช้เองหรือไม่
            if (userId?.toInt() == post.userId) {
                follower.visibility = View.GONE // ซ่อนปุ่ม "Follow" หากเป็นโพสต์ของผู้ใช้เอง
            } else {
                follower.visibility = View.VISIBLE // แสดงปุ่ม "Follow" หากเป็นโพสต์ของผู้อื่น
            }

            reportButton.setOnClickListener {
                showReportMenu(itemView.context, reportButton, post.id ,isUserPost)
            }

            title.setOnClickListener {
                // Check if the token is not null
                token?.let {
                    // Create the PostDetailFragment and pass the post ID
                    val postDetailFragment = PostDetailFragment()
                    val bundle = Bundle().apply {
                        putInt("POST_ID", post.id)
                    }
                    postDetailFragment.arguments = bundle

                    // Record the interaction
                    recordInteraction(post.id, "view", null, it, context)

                    // Navigate to the PostDetailFragment
                    (context as? FragmentActivity)?.supportFragmentManager?.beginTransaction()
                        ?.replace(R.id.nav_host_fragment, postDetailFragment)
                        ?.addToBackStack(null)
                        ?.commit()
                } ?: run {
                    // Handle the case when token is null
                    Toast.makeText(context, "User not authenticated", Toast.LENGTH_SHORT).show()
                }
            }

            postContent.setOnClickListener {
                // Check if the token is not null
                token?.let {
                    // Create the PostDetailFragment and pass the post ID
                    val postDetailFragment = PostDetailFragment()
                    val bundle = Bundle().apply {
                        putInt("POST_ID", post.id)
                    }
                    postDetailFragment.arguments = bundle

                    // Record the interaction
                    recordInteraction(post.id, "view", null, it, context)

                    // Navigate to the PostDetailFragment
                    (context as? FragmentActivity)?.supportFragmentManager?.beginTransaction()
                        ?.replace(R.id.nav_host_fragment, postDetailFragment)
                        ?.addToBackStack(null)
                        ?.commit()
                } ?: run {
                    // Handle the case when token is null
                    Toast.makeText(context, "User not authenticated", Toast.LENGTH_SHORT).show()
                }
            }




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
                    if (token != null && userId != null) {
                        likeUnlikePost(post.id, userId.toInt(), token, context)
                        sendNotification(post.id, userId.toInt(), "like", token, context)
                        recordInteraction(post.id, "like", null, token, context)
                    }
                } else {
                    likeButton.setImageResource(R.drawable.heart)
                    if (token != null && userId != null) {
                        likeUnlikePost(post.id, userId.toInt(), token, context)
                        deleteNotification(post.id, userId.toInt(), "like", token, context) // เพิ่มฟังก์ชันลบแจ้งเตือน
                        recordInteraction(post.id, "unlike", null, token, context)
                    }
                }
            }

            // กำหนดการคลิกปุ่มติดตาม/เลิกติดตาม
            follower.setOnClickListener {
                if (token != null && userId != null) {
                    // ทำการ Follow/Unfollow ผู้ใช้
                    followUnfollowUser(post.userId, userId.toInt(), token, context)
                    if (!isFollowing) {
                        sendNotification(post.id, userId.toInt(), "follow", token, context)
                        recordInteraction(post.id, "follow", null, token, context)
                    } else {
                        deleteNotification(post.id, userId.toInt(), "follow", token, context) // เพิ่มฟังก์ชันลบแจ้งเตือน
                        recordInteraction(post.id, "unfollow", null, token, context)
                    }
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
                    // Log the JSON response to check its content
                    Log.d("FollowStatusResponse", jsonResponse ?: "Empty response")

                    // Proceed only if jsonResponse is not null or empty
                    if (!jsonResponse.isNullOrEmpty()) {
                        try {
                            val isUserFollowing = JSONObject(jsonResponse).getBoolean("isFollowing")
                            (context as? Activity)?.runOnUiThread {
                                isFollowing = isUserFollowing
                                follower.text = if (isFollowing) "Following" else "Follow"
                            }
                        } catch (e: JSONException) {
                            (context as? Activity)?.runOnUiThread {
                                Toast.makeText(context, "Error parsing follow status: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
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
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("UTC") // ตั้งค่า inputFormat เป็น UTC
                }
                val outputFormat = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("Asia/Bangkok") // ตั้งค่า outputFormat เป็น Asia/Bangkok
                }
                val date = inputFormat.parse(timeString ?: "")
                date?.let { outputFormat.format(it) } ?: "N/A"

            } catch (e: Exception) {
                timeString
            }
        }

        private fun deleteNotification(postId: Int, userId: Int, actionType: String, token: String, context: Context) {
            val client = OkHttpClient()
            val url = "${context.getString(R.string.root_url)}/api/notifications" // URL API ของการลบ Notification

            val requestBody = FormBody.Builder()
                .add("user_id", userId.toString())
                .add("post_id", postId.toString())
                .add("action_type", actionType)
                .build()

            val request = Request.Builder()
                .url(url)
                .delete(requestBody)
                .addHeader("Authorization", "Bearer $token")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    (context as? Activity)?.runOnUiThread {
                        Toast.makeText(context, "Failed to delete notification: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val jsonResponse = response.body?.string()
                    (context as? Activity)?.runOnUiThread {
                        if (!response.isSuccessful) {
                            Toast.makeText(context, "Error: ${response.message}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Notification deleted successfully", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
        }



        private fun sendNotification(postId: Int, userId: Int, actionType: String, token: String, context: Context) {
            val client = OkHttpClient()
            val url = "${context.getString(R.string.root_url)}/api/notifications"

            // สร้าง Body ของ Request สำหรับสร้าง Notification
            val requestBody = FormBody.Builder()
                .add("user_id", userId.toString())
                .add("post_id", postId.toString())
                .add("action_type", actionType)
                .add("content", "User ${userId} performed action: $actionType on post $postId")
                .build()

            // สร้าง Request พร้อมแนบ Header ของ Token
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $token")
                .build()

            // ส่ง Request ไปยังเซิร์ฟเวอร์
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    (context as? Activity)?.runOnUiThread {
                        Toast.makeText(context, "Failed to send notification: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val jsonResponse = response.body?.string()
                    (context as? Activity)?.runOnUiThread {
                        if (!response.isSuccessful) {
                            Toast.makeText(context, "Error: ${response.message}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Notification sent successfully", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
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

        private fun showReportMenu(context: Context, anchorView: View, postId: Int, isUserPost: Boolean) {
            val popupMenu = PopupMenu(context, anchorView)
            popupMenu.menuInflater.inflate(R.menu.menu_report, popupMenu.menu)

            // Show edit and delete options only for user's own posts
            popupMenu.menu.findItem(R.id.edit_post).isVisible = isUserPost
            popupMenu.menu.findItem(R.id.delete_post).isVisible = isUserPost

            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.report -> {
                        Toast.makeText(context, "Reported as spam", Toast.LENGTH_SHORT).show()
                        // Handle spam report action here
                        true
                    }
                    R.id.edit_post -> {
                        Toast.makeText(context, "Edit Post selected", Toast.LENGTH_SHORT).show()
                        // Handle Edit Post action here (e.g., navigate to Edit screen)
                        true
                    }
                    R.id.delete_post -> {
                        deletePost(postId, context)
                        true
                    }
                    else -> false
                }
            }

            popupMenu.show()
        }

        private fun deletePost(postId: Int, context: Context) {
            val client = OkHttpClient()
            val sharedPreferences = context.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
            val token = sharedPreferences.getString("TOKEN", null)
            val userId = sharedPreferences.getString("USER_ID", null)

            if (token != null && userId != null) {
                val url = "${context.getString(R.string.root_url)}/posts/$postId"
                val requestBody = FormBody.Builder()
                    .add("user_id", userId)
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .delete(requestBody) // ส่ง `user_id` ใน request body สำหรับการลบโพสต์
                    .addHeader("Authorization", "Bearer $token") // เพิ่ม token ใน header
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        (context as? Activity)?.runOnUiThread {
                            Toast.makeText(context, "Failed to delete post: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val jsonResponse = response.body?.string()
                        (context as? Activity)?.runOnUiThread {
                            if (!response.isSuccessful) {
                                val errorMessage = JSONObject(jsonResponse ?: "{}").optString("error", "Failed to delete post")
                                Toast.makeText(context, "Error: $errorMessage", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Post deleted successfully", Toast.LENGTH_SHORT).show()
                                adapter.postList.removeAt(adapterPosition)
                                adapter.notifyItemRemoved(adapterPosition)
                            }
                        }
                    }
                })
            } else {
                Toast.makeText(context, "User not authenticated", Toast.LENGTH_SHORT).show()
            }
        }





    }
}
