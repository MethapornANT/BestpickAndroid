package com.example.reviewhub

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.net.Uri
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
import androidx.navigation.findNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class PostAdapter(private val postList: MutableList<Any>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_POST = 0
    private val TYPE_AD = 1
    override fun getItemViewType(position: Int): Int {
        return if (postList[position] is Ad) TYPE_AD else TYPE_POST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_AD) {
            Log.d("PostAdapter", "Posts added to postList: $postList")
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ad, parent, false)
            AdViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_post, parent, false)
            PostViewHolder(view, this)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is AdViewHolder) {
            val ad = postList[position] as Ad
            holder.bind(ad)
        } else if (holder is PostViewHolder) {
            val post = postList[position] as Post
            holder.bind(post)
        }
    }

    override fun getItemCount(): Int = postList.size

    fun updateData(newData: List<Any>) {
        postList.clear()
        postList.addAll(newData)
        notifyDataSetChanged()
    }

    // ViewHolder for ads
    class AdViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val adImage: ImageView = itemView.findViewById(R.id.adImage)
        private val adTitle: TextView = itemView.findViewById(R.id.adTitle)
        private val adDescription: TextView = itemView.findViewById(R.id.adDescription)
        private val adLink: TextView = itemView.findViewById(R.id.adLink)

        fun bind(ad: Ad) {
            val baseUrl = itemView.context.getString(R.string.root_url) // Use itemView.context
            adTitle.text = ad.title
            adDescription.text = ad.content
            adLink.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ad.link))
                itemView.context.startActivity(intent)
            }
            Glide.with(itemView.context)
                .load(baseUrl + "/api" + ad.image)
                .into(adImage)
        }

    }

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
        private val bookmarkButton: ImageView = itemView.findViewById(R.id.bookmark_button)

        var isLiked = false
        var isFollowing = false
        var isBookmark = false


        fun bind(post: Post) {
            val context = itemView.context
            val baseUrl = context.getString(R.string.root_url) +"/api"

            // Construct full URLs for media and profile image
            val profileImageUrl = post.userProfileUrl?.let { baseUrl + it }
            val photoUrls = post.photoUrl?.map { Pair(baseUrl + it,"photo") } ?: emptyList()
            Log.d("PhotoUrls", "Photo URLs: $photoUrls")
            val videoUrls = post.videoUrl?.map { Pair(baseUrl + it,"video") } ?: emptyList()
            Log.d("VideoUrls", "Video URLs: $videoUrls")
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
                val bundle = Bundle().apply {
                    putInt("POST_ID", post.id)
                    putString("SOURCE", "HomeFragment")
                }
                Toast.makeText(context, "Clicked on post content", Toast.LENGTH_SHORT).show()
                val navController = itemView.findNavController()
                navController.navigate(R.id.action_postListFragment_to_postDetailFragment, bundle)
            }

            postContent.setOnClickListener {
                val bundle = Bundle().apply {
                    putInt("POST_ID", post.id)
                    putString("SOURCE", "HomeFragment")
                }
                Toast.makeText(context, "Clicked on post content", Toast.LENGTH_SHORT).show()
                val navController = itemView.findNavController()
                navController.navigate(R.id.action_postListFragment_to_postDetailFragment, bundle)
            }


            // Load profile image using the full URL
            Glide.with(context)
                .load("/api" +profileImageUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_launcher_background)
                .error(R.drawable.ic_error)
                .into(userProfileImage)

            // Set up ViewPager2 for photo and video slideshow if there is any media
            if (mediaUrls.isNotEmpty()) {
                val adapter = PhotoPagerAdapter(mediaUrls)
                mediaViewPager.adapter = adapter
                mediaViewPager.visibility = View.VISIBLE

            } else {
                mediaViewPager.visibility = View.GONE
            }



            userProfileImage.setOnClickListener {
                val fragmentManager = (context as? FragmentActivity)?.supportFragmentManager
                if (fragmentManager != null) {
                    val transaction = fragmentManager.beginTransaction()
                    val anotherUserFragment = AnotherUserFragment()
                    val sharedPreferences = context.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
                    val userid = sharedPreferences.getString("USER_ID", null)

                    if (userid != null && userid.toInt() == post.userId) {
                        // นำทางไปยังโปรไฟล์ของผู้ใช้เอง
                        val navController = (context as? FragmentActivity)?.findNavController(R.id.nav_host_fragment)
                        val bottomNavigationView = (context as? Activity)?.findViewById<BottomNavigationView>(R.id.bottom_navigation)
                        bottomNavigationView?.menu?.findItem(R.id.profile)?.isChecked = true
                        navController?.navigate(R.id.profileFragment)
                        return@setOnClickListener
                    }

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

            bookmarkButton.setOnClickListener {
                isBookmark = !isBookmark
                if (isBookmark) {
                    bookmarkButton.setImageResource(R.drawable.bookmarkclick)
                    if (token != null && userId != null) {
                        // Call the bookmark API to add the post to bookmarks
                        bookmarkPost(post.id, token, context)
                    }
                } else {
                    bookmarkButton.setImageResource(R.drawable.bookmark)
                    if (token != null && userId != null) {
                        // Call the unbookmark API to remove the post from bookmarks
                        bookmarkPost(post.id, token, context)
                    }
                }
            }

// Initial check for bookmark status when the view is created
            if (token != null) {
                checkBookmarkStatus(post.id, token, context) { isBookmarked ->
                    isBookmark = isBookmarked
                    bookmarkButton.setImageResource(if (isBookmarked) R.drawable.bookmarkclick else R.drawable.bookmark)
                }
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
            // สร้างลิงก์ภายในแอปพลิเคชันโดยใช้ Custom URI Scheme
            val postDetailUrl = "reviewhub://post/${post.id}"

            // สร้างข้อความที่จะแชร์ รวมทั้งลิงก์ไปยังรายละเอียดโพสต์
            val shareText = "Check out this post from ${post.userName}: \n\n$postDetailUrl"

            // ใช้ Intent เพื่อแชร์ข้อความ
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }

            // เปิดหน้าต่างแชร์
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
                    response.body?.string()?.let { jsonResponse ->
                        try {
                            // สร้าง JSONObject เพียงครั้งเดียวและตรวจสอบว่ามีคีย์ "isLiked" หรือไม่
                            val jsonObject = JSONObject(jsonResponse)

                            if (jsonObject.has("isLiked")) {
                                val isLiked = jsonObject.getBoolean("isLiked")
                                Log.d("CheckLikeStatus", "isLiked: $isLiked")

                                // อัปเดต UI ใน Main Thread
                                (context as? Activity)?.runOnUiThread {
                                    this@PostViewHolder.isLiked = isLiked
                                    likeButton.setImageResource(if (isLiked) R.drawable.heartclick else R.drawable.heart)
                                }
                            } else {
                                Log.e("CheckLikeStatus", "Key 'isLiked' not found in response")
                            }
                        } catch (e: JSONException) {
                            Log.e("CheckLikeStatus", "JSON Parsing Error: ${e.message}")
                        }
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
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("UTC") // The time is in UTC
                }
                val outputFormat = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("Asia/Bangkok") // Convert to Asia/Bangkok time
                }
                val date = inputFormat.parse(timeString)
                date?.let { outputFormat.format(it) } ?: "N/A"
            } catch (e: Exception) {
                timeString // Return original string if parsing fails
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

            popupMenu.menu.findItem(R.id.edit_post).isVisible = isUserPost
            popupMenu.menu.findItem(R.id.delete_post).isVisible = isUserPost
            popupMenu.menu.findItem(R.id.report).isVisible = !isUserPost

            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.report -> {
                        val sharedPreferences = context.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
                        val token = sharedPreferences.getString("TOKEN", null)
                        val userId = sharedPreferences.getString("USER_ID", null)?.toIntOrNull()

                        if (token != null && userId != null) {
                            val reportOptions = arrayOf(
                                "Inappropriate Content",
                                "Copyright Violation",
                                "Scam or Spam",
                                "Violence or Threats",
                                "Misinformation or False Information",
                                "Fraud or Malicious Intent"
                            )

                            // Create an AlertDialog to show the options
                            val builder = AlertDialog.Builder(context, R.style.CustomAlertDialog)
                            builder.setTitle("Report Post")
                            builder.setSingleChoiceItems(reportOptions, -1) { dialog, which ->
                                val selectedReason = reportOptions[which]
                                reportPost(postId, userId, selectedReason, token, context)
                                dialog.dismiss() // Close the dialog after selection
                            }
                            builder.setNegativeButton("Cancel") { dialog, _ ->
                                dialog.dismiss()
                            }
                            builder.show() // Display the dialog
                        } else {
                            Toast.makeText(context, "User not authenticated", Toast.LENGTH_SHORT).show()
                        }

                        true
                    }

                    R.id.edit_post -> {
                        Toast.makeText(context, "Edit Post selected", Toast.LENGTH_SHORT).show()
                        val sharedPreferences = context.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
                        val token = sharedPreferences.getString("TOKEN", null)

                        token?.let {
                            // Create the PostDetailFragment and pass the post ID
                            val EditpostFragment = EditPostFragment()
                            val bundle = Bundle().apply {
                                putInt("POST_ID", postId)
                                putString("From", "post")
                            }
                            EditpostFragment.arguments = bundle
                            // Navigate to the PostDetailFragment
                            val bottomNavigationView = (context as? Activity)?.findViewById<BottomNavigationView>(R.id.bottom_navigation)
                            bottomNavigationView!!.visibility = View.GONE
                            (context as? FragmentActivity)?.supportFragmentManager?.beginTransaction()
                                ?.replace(R.id.nav_host_fragment, EditpostFragment)
                                ?.addToBackStack(null)
                                ?.commit()
                        } ?: run {
                            // Handle the case when token is null
                            Toast.makeText(context, "User not authenticated", Toast.LENGTH_SHORT).show()
                        }
                        true
                    }

                    R.id.delete_post -> {
                        // Show confirmation dialog before deleting the post
                        val confirmDeleteBuilder = AlertDialog.Builder(context)
                        confirmDeleteBuilder.setTitle("Confirm Deletion")
                        confirmDeleteBuilder.setMessage("Are you sure you want to delete this post?")

                        confirmDeleteBuilder.setPositiveButton("Yes") { dialog, _ ->
                            deletePost(postId, context)
                            dialog.dismiss()
                        }

                        confirmDeleteBuilder.setNegativeButton("Cancel") { dialog, _ ->
                            dialog.dismiss() // Close the dialog if user cancels
                        }

                        confirmDeleteBuilder.show() // Display the confirmation dialog
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
                val url = "${context.getString(R.string.root_url)}/api/posts/$postId"
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

        private fun bookmarkPost(postId: Int, token: String, context: Context) {
            val client = OkHttpClient()
            val url = "${context.getString(R.string.root_url)}/api/posts/$postId/bookmark" // Endpoint to bookmark a post

            val request = Request.Builder()
                .url(url)
                .post(FormBody.Builder().build()) // Send a POST request
                .addHeader("Authorization", "Bearer $token") // Attach token in the header
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    (context as? Activity)?.runOnUiThread {
                        Toast.makeText(context, "Failed to bookmark post: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            (context as? Activity)?.runOnUiThread {
                                Toast.makeText(context, "Error bookmarking post: ${response.message}", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            (context as? Activity)?.runOnUiThread {
                                Toast.makeText(context, "Post bookmarked successfully", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            })
        }

        private fun reportPost(postId: Int, userId: Int, reason: String, token: String, context: Context) {
            val client = OkHttpClient()
            val url = "${context.getString(R.string.root_url)}/api/posts/$postId/report"

            val requestBody = FormBody.Builder()
                .add("user_id", userId.toString())
                .add("reason", reason) // Send the reason selected by the user
                .build()

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $token")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    (context as? Activity)?.runOnUiThread {
                        Toast.makeText(context, "Failed to report post: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    (context as? Activity)?.runOnUiThread {
                        if (response.isSuccessful) {
                            Toast.makeText(context, "Post reported successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Error reporting post: ${response.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
        }


        private fun checkBookmarkStatus(postId: Int, token: String, context: Context, callback: (Boolean) -> Unit) {
            val client = OkHttpClient()
            val url = "${context.getString(R.string.root_url)}/api/posts/$postId/bookmark/status"

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", "Bearer $token") // Add token in header
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    (context as? Activity)?.runOnUiThread {
                        Toast.makeText(context, "Failed to check bookmark status: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.body?.string()?.let { jsonResponse ->
                        try {
                            val jsonObject = JSONObject(jsonResponse)
                            val isBookmarked = jsonObject.getBoolean("isBookmarked")

                            // Update the UI in Main Thread
                            (context as? Activity)?.runOnUiThread {
                                callback(isBookmarked) // Return bookmark status to caller
                            }
                        } catch (e: JSONException) {
                            (context as? Activity)?.runOnUiThread {
                                Toast.makeText(context, "Error parsing bookmark status: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            })
        }
    }
    data class Ad(
        val id: Int,
        val title: String,
        val content: String,
        val image: String,
        val link: String
    )

}
