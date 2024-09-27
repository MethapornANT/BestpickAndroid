package com.example.reviewhub

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class PostDetailFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_post_detail, container, false)

        // ตั้งค่า Back Button
        view.findViewById<ImageView>(R.id.back_button).setOnClickListener {
            activity?.onBackPressed()
        }

        // ดึง postId จาก arguments
        val postId = arguments?.getInt("POST_ID", -1) ?: -1

        if (postId != -1) {
            fetchPostDetails(postId, view)
        } else {
            Toast.makeText(context, "Invalid Post ID", Toast.LENGTH_SHORT).show()
        }
        return view
    }

    // ฟังก์ชันสำหรับการดึงข้อมูลโพสต์และคอมเมนต์
    private fun fetchPostDetails(postId: Int, view: View) {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val url = getString(R.string.root_url) + getString(R.string.postdetail) + postId

            val request = Request.Builder()
                .url(url)
                .build()

            try {
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val jsonObject = JSONObject(responseBody)

                        // ดึงข้อมูลโพสต์และคอมเมนต์จาก JSON
                        val postContent = jsonObject.getString("content")
                        val likeCount = jsonObject.getInt("like_count")
                        val commentCount = jsonObject.getInt("comment_count")
                        val username = jsonObject.getString("username")
                        val profileImage = jsonObject.getString("picture")
                        val profileUrl = getString(R.string.root_url) + profileImage

                        // ดึงข้อมูลคอมเมนต์
                        val commentsArray = jsonObject.getJSONArray("comments")
                        val comments = mutableListOf<Comment>()

                        for (i in 0 until commentsArray.length()) {
                            val commentObject = commentsArray.getJSONObject(i)
                            val comment = Comment(
                                id = commentObject.getInt("id"),
                                content = commentObject.getString("content"),
                                username = commentObject.getString("username"),
                                createdAt = commentObject.getString("created_at")
                            )
                            comments.add(comment)
                        }

                        // ดึงข้อมูลรูปภาพและวิดีโอ
                        val postImageUrls = jsonObject.getJSONArray("photo_url")
                        val postVideoUrls = jsonObject.getJSONArray("video_url")

                        val mediaUrls = mutableListOf<Pair<String, String>>() // รายการ URL และประเภทของสื่อ

                        // ดึง URL รูปภาพจาก photo_url โดยแยก JSON Array ชั้นในสุด
                        for (i in 0 until postImageUrls.length()) {
                            val innerImageArray = postImageUrls.getJSONArray(i)
                            for (j in 0 until innerImageArray.length()) {
                                val imageUrl = innerImageArray.getString(j)
                                mediaUrls.add(Pair(getString(R.string.root_url) + imageUrl, "photo"))
                            }
                        }

                        // ดึง URL วิดีโอจาก video_url โดยแยก JSON Array ชั้นในสุด
                        for (i in 0 until postVideoUrls.length()) {
                            val innerVideoArray = postVideoUrls.getJSONArray(i)
                            for (j in 0 until innerVideoArray.length()) {
                                val videoUrl = innerVideoArray.getString(j)
                                mediaUrls.add(Pair(getString(R.string.root_url) + videoUrl, "video"))
                            }
                        }

                        // อัพเดต UI บน Main Thread
                        withContext(Dispatchers.Main) {
                            view.findViewById<TextView>(R.id.username).text = username
                            view.findViewById<TextView>(R.id.post_content_detail).text = postContent
                            view.findViewById<TextView>(R.id.like_count).text = "Likes: $likeCount"
                            view.findViewById<TextView>(R.id.comment_count).text = "$commentCount Comments"

                            Glide.with(this@PostDetailFragment)
                                .load(profileUrl)
                                .into(view.findViewById(R.id.Imgview))

                            // ตั้งค่า ViewPager2 สำหรับแสดงรูปภาพและวิดีโอ
                            val viewPager = view.findViewById<ViewPager2>(R.id.ShowImgpost)
                            val adapter = PhotoPagerAdapter(mediaUrls)
                            viewPager.adapter = adapter


                            // ตั้งค่า RecyclerView สำหรับคอมเมนต์
                            val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view_posts)
                            recyclerView.layoutManager = LinearLayoutManager(context)
                            recyclerView.adapter = CommentAdapter(comments)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to load post details", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("PostDetailFragment", "Error: ${e.message}", e)
                }
            }
        }
    }

    // ข้อมูลคอมเมนต์
    data class Comment(val id: Int, val content: String, val username: String, val createdAt: String)

    // Adapter สำหรับแสดงรายการคอมเมนต์
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
            holder.createdAt.text = comment.createdAt
        }

        override fun getItemCount(): Int {
            return comments.size
        }

        inner class CommentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val username: TextView = view.findViewById(R.id.comment_username)
            val content: TextView = view.findViewById(R.id.comment_content)
            val createdAt: TextView = view.findViewById(R.id.comment_created_at)
        }
    }
}
