package com.example.reviewhub

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class PostDetailFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var dotIndicatorLayout: LinearLayout
    private lateinit var follower: TextView
    private var followingId: Int = -1

    private var isLiked: Boolean = false

    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_post_detail, container, false)

        val bottomNav = activity?.findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav?.visibility = View.GONE

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            bottomNav?.visibility = View.VISIBLE
            parentFragmentManager.popBackStack()
        }


        // เชื่อมโยงกับ LinearLayout สำหรับแสดงจุด Indicator
        dotIndicatorLayout = view.findViewById(R.id.dot_indicator_layout)

        // ตั้งค่า Back Button
        view.findViewById<ImageView>(R.id.back_button).setOnClickListener {
            activity?.onBackPressed()
        }

        val sharedPreferences = requireContext().getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences.getString("TOKEN", null)
        val userId = sharedPreferences.getString("USER_ID", null)
        follower = view.findViewById<TextView>(R.id.follower)
        // ดึง postId จาก arguments
        val postId = arguments?.getInt("POST_ID", -1) ?: -1


        if (postId != -1) {
            if (token != null && userId != null) {
                fetchPostDetails(postId, token, userId.toInt(), view)
            }
        } else {
            Toast.makeText(requireContext(), "Invalid Post ID", Toast.LENGTH_SHORT).show()
        }
        val Imgview = view.findViewById<ImageView>(R.id.Imgview)
        Imgview.setOnClickListener {
            openUserProfile(userId.toString().toInt())
            recordInteraction(postId, "view_profile", null, token!!, requireContext())
        }

        follower.setOnClickListener {
            if (token != null && userId != null) {
                followUser(userId.toInt(), followingId, token)
                val actionType = if (follower.text == "Following") "follow" else "unfollow"
                if (actionType == "follow") {
                    sendNotification(postId, userId.toInt(), "follow", token, requireContext())
                }else{
                    deleteNotification(postId, userId.toInt(), "follow", token, requireContext())
                }
                recordInteraction(postId, actionType, null, token, requireContext())
            }
        }
        val commentButton = view.findViewById<ImageView>(R.id.send_button)
        val commentEditText = view.findViewById<EditText>(R.id.comment_input)
        // กำหนดการทำงานเมื่อคลิกปุ่มส่งคอมเมนต์
        commentButton.setOnClickListener {
            if (token != null && userId != null) {
                val commentContent = commentEditText.text.toString().trim()
                if (commentContent.isNotEmpty()) {
                    postComment(postId, userId.toInt(), commentContent, token)
                    sendNotification(postId, userId.toInt(), "comment", token, requireContext())
                    commentEditText.text.clear() // ล้างข้อมูลหลังส่งคอมเมนต์สำเร็จ
                } else {
                    Toast.makeText(requireContext(), "Comment cannot be empty", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Please login to comment", Toast.LENGTH_SHORT).show()
            }
        }



        return view
    }

    private fun setupPageIndicators(totalPages: Int) {
        val dotSize = 30
        dotIndicatorLayout.removeAllViews()
        for (i in 0 until totalPages) {
            val dot = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                    setMargins(8, 0, 8, 0)
                }
                setImageResource(R.drawable.outline_circle_24)
                scaleX = 1.0f
                scaleY = 1.0f
            }
            dotIndicatorLayout.addView(dot)
        }
    }

    private fun updatePageIndicators(selectedPosition: Int) {
        for (i in 0 until dotIndicatorLayout.childCount) {
            val dot = dotIndicatorLayout.getChildAt(i) as ImageView
            if (i == selectedPosition) {
                animateDot(dot, true)
                dot.setImageResource(R.drawable.baseline_circle_24)
            } else {
                animateDot(dot, false)
                dot.setImageResource(R.drawable.outline_circle_24)
            }
        }
    }

    private fun animateDot(dot: ImageView, isSelected: Boolean) {
        val scale = if (isSelected) 1.4f else 1.0f
        ObjectAnimator.ofFloat(dot, "scaleX", scale).apply {
            duration = 300
            start()
        }
        ObjectAnimator.ofFloat(dot, "scaleY", scale).apply {
            duration = 300
            start()
        }
    }

    // ฟังก์ชันสำหรับเรียก API ติดตาม/เลิกติดตาม
    private fun followUser(userId: Int, followingId: Int, token: String) {
        val client = OkHttpClient()
        val url = "${getString(R.string.root_url)}/api/users/$userId/follow/$followingId"

        val request = Request.Builder()
            .url(url)
            .post(RequestBody.create(null, ByteArray(0))) // ส่ง Body ว่างสำหรับการ POST
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                (requireActivity() as? Activity)?.runOnUiThread {
                    Toast.makeText(requireContext(), "Failed to follow/unfollow user", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        (requireActivity() as? Activity)?.runOnUiThread {
                            Toast.makeText(requireContext(), "Follow status changed successfully", Toast.LENGTH_SHORT).show()
                            checkFollowStatus(userId, followingId, token)
                        }
                    } else {
                        (requireActivity() as? Activity)?.runOnUiThread {
                            Toast.makeText(requireContext(), "Failed: ${response.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })
    }

    // ฟังก์ชันสำหรับเช็คสถานะการติดตามผู้ใช้
    private fun checkFollowStatus(userId: Int, followingId: Int, token: String) {
        val client = OkHttpClient()
        val url = "${getString(R.string.root_url)}/api/users/$userId/follow/$followingId/status"

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
                            // อัปเดตข้อความของ `follower` ตามสถานะการติดตาม
                            follower.text = if (isFollowing) "Following" else "Follow"
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error checking follow status", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchPostDetails(postId: Int, token: String, userId: Int, view: View) {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val url = getString(R.string.root_url) + getString(R.string.postdetail) + postId

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .build()

            try {
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val jsonObject = JSONObject(responseBody)

                        val postContent = jsonObject.getString("content")
                        val title = jsonObject.getString("Title")
                        val likeCount = jsonObject.getInt("like_count")
                        val commentCount = jsonObject.getInt("comment_count")
                        val username = jsonObject.getString("username")
                        followingId = jsonObject.getInt("user_id")
                        val time = jsonObject.getString("updated_at")
                        val profileImage = jsonObject.getString("picture")
                        val profileUrl = getString(R.string.root_url) + profileImage

                        val commentsArray = jsonObject.getJSONArray("comments")
                        val comments = mutableListOf<Comment>()

                        for (i in 0 until commentsArray.length()) {
                            val commentObject = commentsArray.getJSONObject(i)
                            val comment = Comment(
                                id = commentObject.getInt("id"),
                                user_id = commentObject.getInt("user_id"),
                                content = commentObject.getString("content"),
                                username = commentObject.getString("username"),
                                createdAt = commentObject.getString("created_at"),
                                profileImage = commentObject.getString("user_profile")
                            )
                            comments.add(comment)
                        }

                        val postImageUrls = jsonObject.getJSONArray("photo_url")
                        val postVideoUrls = jsonObject.getJSONArray("video_url")

                        val mediaUrls = mutableListOf<Pair<String, String>>()
                        for (i in 0 until postImageUrls.length()) {
                            val innerImageArray = postImageUrls.getJSONArray(i)
                            for (j in 0 until innerImageArray.length()) {
                                val imageUrl = innerImageArray.getString(j)
                                mediaUrls.add(Pair(getString(R.string.root_url) + imageUrl, "photo"))
                            }
                        }

                        for (i in 0 until postVideoUrls.length()) {
                            val innerVideoArray = postVideoUrls.getJSONArray(i)
                            for (j in 0 until innerVideoArray.length()) {
                                val videoUrl = innerVideoArray.getString(j)
                                mediaUrls.add(Pair(getString(R.string.root_url) + videoUrl, "video"))
                            }
                        }

                        withContext(Dispatchers.Main) {
                            view.findViewById<TextView>(R.id.username).text = username
                            view.findViewById<TextView>(R.id.title).text = title
                            view.findViewById<TextView>(R.id.detail).text = postContent
                            view.findViewById<TextView>(R.id.time).text = formatTime(time)
                            view.findViewById<TextView>(R.id.like_count).text = ": $likeCount"
                            view.findViewById<TextView>(R.id.comment_count).text = "$commentCount Comments"
                            checkFollowStatus(userId, followingId, token)

                            if (userId == followingId) {
                                follower.visibility = View.GONE
                            } else {
                                checkFollowStatus(userId, followingId, token)
                            }

                            Glide.with(this@PostDetailFragment)
                                .load(profileUrl)
                                .into(view.findViewById(R.id.Imgview))

                            val viewPager = view.findViewById<ViewPager2>(R.id.ShowImgpost)
                            val adapter = PhotoPagerAdapter(mediaUrls)
                            viewPager.adapter = adapter

                            setupPageIndicators(mediaUrls.size)

                            viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                                override fun onPageSelected(position: Int) {
                                    super.onPageSelected(position)
                                    updatePageIndicators(position)
                                }
                            })

                            val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view_posts)
                            recyclerView.layoutManager = LinearLayoutManager(requireContext())
                            recyclerView.adapter = CommentAdapter(comments)

                            // เรียก `checkLikeStatus` เพื่อตรวจสอบสถานะการกดไลค์
                            checkLikeStatus(postId, userId, token, view)

                            val likeButton = view.findViewById<ImageView>(R.id.like_button)
                            likeButton.setOnClickListener {
                                if (token != null) {
                                    // ตรวจสอบสถานะปัจจุบันของการกดไลค์
                                    if (isLiked) {
                                        // หากปัจจุบันอยู่ในสถานะไลค์ เมื่อคลิกจะเป็นการ unlike
                                        likeUnlikePost(postId, userId, token)
                                        recordInteraction(postId, "unlike", null, token, requireContext())
                                        deleteNotification(postId, userId, "like", token, requireContext())
                                    } else {
                                        // หากยังไม่ไลค์ เมื่อคลิกจะเป็นการ like
                                        likeUnlikePost(postId, userId, token)
                                        sendNotification(postId, userId, "like", token, requireContext())
                                        recordInteraction(postId, "like", null, token, requireContext())
                                    }
                                } else {
                                    Toast.makeText(requireContext(), "Token not available", Toast.LENGTH_SHORT).show()
                                }
                            }

                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Failed to load post details", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("PostDetailFragment", "Error: ${e.message}", e)
                }
            }
        }
    }

    private fun likeUnlikePost(postId: Int, userId: Int?, token: String) {
        val client = OkHttpClient()
        val url = requireContext().getString(R.string.root_url) + requireContext().getString(R.string.postlikeorunlike) + postId
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
                (requireActivity() as? Activity)?.runOnUiThread {
                    Toast.makeText(requireContext(), "Failed to like/unlike post: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        (requireActivity() as? Activity)?.runOnUiThread {
                            Toast.makeText(requireContext(), "Error: ${response.message}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // ดึงข้อมูลไลค์ใหม่จาก JSON response
                        val responseBody = response.body?.string()
                        val jsonObject = responseBody?.let { JSONObject(it) }
                        val newLikeCount = jsonObject?.getInt("likeCount") ?: 0

                        (requireActivity() as? Activity)?.runOnUiThread {
                            // อัปเดตสถานะการไลค์ใน UI
                            checkLikeStatus(postId, userId ?: 0, token, requireView())

                            // อัปเดตจำนวนไลค์ใน TextView
                            val likeCountTextView = requireView().findViewById<TextView>(R.id.like_count)
                            likeCountTextView.text = ": $newLikeCount"
                        }
                    }
                }
            }
        })
    }

    // ฟังก์ชันสำหรับการส่งคอมเมนต์ไปยัง API
    private fun postComment(postId: Int, userId: Int, content: String, token: String) {
        val client = OkHttpClient()
        val url = getString(R.string.root_url) + "/posts/$postId/comment"

        val requestBody = FormBody.Builder()
            .add("content", content)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                (requireActivity() as? Activity)?.runOnUiThread {
                    Toast.makeText(requireContext(), "Failed to post comment: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        (requireActivity() as? Activity)?.runOnUiThread {
                            Toast.makeText(requireContext(), "Comment posted successfully", Toast.LENGTH_SHORT).show()
                            recordInteraction(postId, "comment", content, token, requireContext())
                            fetchPostDetails(postId, token, userId, requireView()) // โหลดข้อมูลโพสต์ใหม่
                        }
                    } else {
                        (requireActivity() as? Activity)?.runOnUiThread {
                            Toast.makeText(requireContext(), "Failed: ${response.message}", Toast.LENGTH_SHORT).show()
                        }
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


    private fun checkLikeStatus(postId: Int, userId: Int, token: String, view: View) {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val url = "${requireContext().getString(R.string.root_url)}${requireContext().getString(R.string.check_like_status)}$postId/$userId"

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", "Bearer $token")
                .build()

            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val jsonObject = JSONObject(responseBody)
                        isLiked = jsonObject.getBoolean("isLiked")

                        withContext(Dispatchers.Main) {
                            val likeButton = view.findViewById<ImageView>(R.id.like_button)
                            likeButton.setImageResource(if (isLiked) R.drawable.heartclick else R.drawable.heart)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to check like status: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    data class Comment(val id: Int,val user_id: Int,val content: String, val username: String, val createdAt: String, val profileImage: String)

    inner class CommentAdapter(private val comments: List<Comment>) :
        RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.comment_postdetail_item, parent, false)
            return CommentViewHolder(view)
        }

        override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
            val comment = comments[position]
            holder.username.text = comment.username
            holder.content.text = comment.content
            holder.createdAt.text = formatTime(comment.createdAt)

            Glide.with(this@PostDetailFragment)
                .load(requireContext().getString(R.string.root_url) + comment.profileImage)
                .into(holder.Imageprofile)


            Log.d("CommentAdapter", "id: $id")
            // กำหนดการคลิกที่โปรไฟล์ของผู้แสดงความคิดเห็น
            holder.Imageprofile.setOnClickListener {
                openUserProfile(comment.user_id)
            }
        }

        override fun getItemCount(): Int {
            return comments.size
        }

        inner class CommentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val username: TextView = view.findViewById(R.id.comment_username)
            val content: TextView = view.findViewById(R.id.comment_content)
            val Imageprofile: ImageView = view.findViewById(R.id.comment_profile_image)
            val createdAt: TextView = view.findViewById(R.id.comment_created_at)
        }
    }

    // ฟังก์ชันสำหรับเปิดหน้าโปรไฟล์ผู้ใช้คนนั้น
    private fun openUserProfile(userId: Int) {
        val fragment = AnotherUserFragment()
        val bundle = Bundle()
        bundle.putInt("USER_ID", userId)
        fragment.arguments = bundle

        // เปลี่ยน Fragment ปัจจุบันเป็น AnotherUserFragment
        parentFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .addToBackStack(null)
            .commit()
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

    private fun recordInteraction(postId: Int? = null, actionType: String, content: String? = null, token: String, context: Context) {
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
