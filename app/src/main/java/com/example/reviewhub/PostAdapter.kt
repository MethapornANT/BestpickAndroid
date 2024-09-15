package com.example.reviewhub

import PhotoPagerAdapter
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
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

        fun bind(post: Post) {
            // Set user details
            val displayTime = post.updated_at ?: post.time
            postTime.text = formatTime(displayTime)
            userName.text = post.userName
            postContent.text = post.content

            // Load profile image
            Glide.with(itemView.context)
                .load(post.userProfileUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_launcher_background)
                .error(R.drawable.ic_error)
                .into(userProfileImage)

            // Set up ViewPager2 for photo slideshow
            post.photoUrl?.let { photoUrls ->
                val adapter = PhotoPagerAdapter(photoUrls)
                mediaViewPager.adapter = adapter
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
                    timeString // Return original string if parsing fails
                }
            } catch (e: Exception) {
                timeString // Return the original string if parsing fails
            }
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
                    timeString // Return original string if parsing fails
                }
            } catch (e: Exception) {
                timeString // Return the original string if parsing fails
            }
        }
    }

