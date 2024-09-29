package com.example.reviewhub

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_post_detail, container, false)

        // เชื่อมโยงกับ LinearLayout สำหรับแสดงจุด Indicator
        dotIndicatorLayout = view.findViewById(R.id.dot_indicator_layout)

        // ตั้งค่า Back Button
        view.findViewById<ImageView>(R.id.back_button).setOnClickListener {
            activity?.onBackPressed()
        }

        val sharedPreferences = requireContext().getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences.getString("TOKEN", null)
        val userId = sharedPreferences.getString("USER_ID", null)

        // ดึง postId จาก arguments
        val postId = arguments?.getInt("POST_ID", -1) ?: -1

        if (postId != -1) {
            if (token != null && userId != null) {
                fetchPostDetails(postId, token, userId.toInt(), view)
            }
        } else {
            Toast.makeText(requireContext(), "Invalid Post ID", Toast.LENGTH_SHORT).show()
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
                        val likeCount = jsonObject.getInt("like_count")
                        val commentCount = jsonObject.getInt("comment_count")
                        val username = jsonObject.getString("username")
                        val profileImage = jsonObject.getString("picture")
                        val profileUrl = getString(R.string.root_url) + profileImage

                        val commentsArray = jsonObject.getJSONArray("comments")
                        val comments = mutableListOf<Comment>()

                        for (i in 0 until commentsArray.length()) {
                            val commentObject = commentsArray.getJSONObject(i)
                            val comment = Comment(
                                id = commentObject.getInt("id"),
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
                            view.findViewById<TextView>(R.id.post_content_detail).text = postContent
                            view.findViewById<TextView>(R.id.like_count).text = "Likes: $likeCount"
                            view.findViewById<TextView>(R.id.comment_count).text = "$commentCount Comments"

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

                            // ตั้งค่า Like Button
                            val likeButton = view.findViewById<ImageView>(R.id.like_button)
                            likeButton.setOnClickListener {
                                if (token != null) {
                                    likeUnlikePost(postId, userId, token)
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
                        checkLikeStatus(postId, userId ?: 0, token, requireView())
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
                        val isLiked = jsonObject.getBoolean("isLiked")

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

    data class Comment(val id: Int, val content: String, val username: String, val createdAt: String, val profileImage: String)

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
}
