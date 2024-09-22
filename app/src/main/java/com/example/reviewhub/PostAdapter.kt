package com.example.reviewhub

import android.app.Activity
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import okhttp3.*
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class PostAdapter(private val postList: List<Post>) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_post, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = postList[position]
        holder.bind(post)
    }

    override fun getItemCount(): Int {
        return postList.size
    }

    class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userName: TextView = itemView.findViewById(R.id.user_name)
        private val postTime: TextView = itemView.findViewById(R.id.post_time)
        private val postContent: TextView = itemView.findViewById(R.id.post_content)
        private val userProfileImage: ImageView = itemView.findViewById(R.id.user_profile_image)
        private val mediaViewPager: ViewPager2 = itemView.findViewById(R.id.media_view_pager)
        private val likeButton: ImageView = itemView.findViewById(R.id.like_button)
        private val shareButton: ImageView = itemView.findViewById(R.id.share_button)

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
            postContent.text = post.content

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
            } else {
                mediaViewPager.visibility = View.GONE
            }

            // Fetch token and userId from SharedPreferences
            val sharedPreferences = context.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
            val token = sharedPreferences.getString("TOKEN", null)
            val userId = sharedPreferences.getString("USER_ID", null)

            // Handle like button click
            likeButton.setOnClickListener {
                if (token != null && userId != null) {
                    likeUnlikePost(post.id, userId.toInt(), token, context)
                } else {
                    Toast.makeText(context, "Token or UserID not available", Toast.LENGTH_SHORT).show()
                }
            }

            shareButton.setOnClickListener {
                sharePost(context, post)
            }

        }

        // Function to share the post content
        private fun sharePost(context: Context, post: Post) {
            val shareText = "Check out this post from ${post.userName}:\n${post.content}"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            context.startActivity(Intent.createChooser(intent, "Share Post via"))
        }

        // Function to like or unlike a post
        private fun likeUnlikePost(postId: Int, userId: Int, token: String, context: Context) {
            val client = OkHttpClient()
            val url = context.getString(R.string.root_url) + context.getString(R.string.postlikeorunlike) + postId
            val requestBody = FormBody.Builder()
                .add("user_id", userId.toString())
                .build()

            // Build the request
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $token")
                .build()

            // Execute the request
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    (context as? Activity)?.runOnUiThread {
                        Toast.makeText(context, "Failed to like/unlike post: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            (context as? Activity)?.runOnUiThread {
                                Toast.makeText(context, "Error: ${response.message}", Toast.LENGTH_SHORT).show()
                            }



                        } else {
                            val message = response.body?.string() ?: "Success"
                            (context as? Activity)?.runOnUiThread {
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                // Optionally, toggle like button state (e.g., change icon)
                            }
                        }
                    }
                }
            })
        }

        // Convert time string to a readable format
        private fun formatTime(timeString: String): String {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                inputFormat.timeZone = TimeZone.getTimeZone("UTC")
                val date = inputFormat.parse(timeString)
                if (date != null) {
                    val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    outputFormat.format(date)
                } else {
                    timeString // Return original string if parsing fails
                }
            } catch (e: Exception) {
                timeString // Return the original string if parsing fails
            }
        }
    }
}
