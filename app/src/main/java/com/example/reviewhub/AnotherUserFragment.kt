package com.example.reviewhub

import android.annotation.SuppressLint
import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class AnotherUserFragment : Fragment() {

    private lateinit var userProfileImage: ImageView
    private lateinit var usernameTextView: TextView
    private lateinit var followerCountTextView: TextView
    private lateinit var followingCountTextView: TextView
    private lateinit var postCountTextView: TextView
    private lateinit var bioTextView: TextView
    private lateinit var followButton: Button
    private lateinit var recyclerViewPosts: RecyclerView

    private val client = OkHttpClient()

    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_another_user, container, false)

        val bottomNav = activity?.findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav?.visibility = View.GONE

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            bottomNav?.visibility = View.VISIBLE
            parentFragmentManager.popBackStack()
        }

        // เชื่อมโยง View กับ Layout XML
        userProfileImage = view.findViewById(R.id.user_profile_image)
        usernameTextView = view.findViewById(R.id.usernameanother)
        followerCountTextView = view.findViewById(R.id.follower_count)
        followingCountTextView = view.findViewById(R.id.following_count)
        postCountTextView = view.findViewById(R.id.post_count)
        bioTextView = view.findViewById(R.id.bio)
        followButton = view.findViewById(R.id.follower)
        recyclerViewPosts = view.findViewById(R.id.recycler_view_posts)
        val back = view.findViewById<TextView>(R.id.back)

        back.setOnClickListener{
            requireActivity().onBackPressed()
        }

        // กำหนด LayoutManager ให้กับ RecyclerView
        recyclerViewPosts.layoutManager = LinearLayoutManager(requireContext())

        // ดึง userId จาก Arguments
        val userId = arguments?.getInt("USER_ID") ?: -1

        if (userId != -1) {
            fetchUserProfile(userId)
        } else {
            Toast.makeText(requireContext(), "Invalid User ID", Toast.LENGTH_SHORT).show()
        }

        return view
    }

    private fun fetchUserProfile(userId: Int) {
        val sharedPreferences = context?.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences?.getString("TOKEN", null)
        val url = getString(R.string.root_url) + "/api/users/" + userId + "/view-profile"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Failed to load profile: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val jsonResponse = response.body?.string()
                if (jsonResponse != null) {
                    val userProfile = JSONObject(jsonResponse)

                    requireActivity().runOnUiThread {
                        // แสดงข้อมูลผู้ใช้ใน UI
                        displayUserProfile(userProfile)
                    }
                }
            }
        })
    }

    private fun displayUserProfile(userProfile: JSONObject) {
        // ตรวจสอบและดึงข้อมูลฟิลด์อื่น ๆ
        val profileImageUrl = userProfile.optString("profileImageUrl", "")
        val username = userProfile.optString("username", "Unknown User")
        val followerCount = userProfile.optInt("followerCount", 0)
        val followingCount = userProfile.optInt("followingCount", 0)
        val postCount = userProfile.optInt("postCount", 0)
        val bio = userProfile.optString("bio", "No bio available")

        // ตั้งค่าข้อมูลใน View
        usernameTextView.text = username
        followerCountTextView.text = followerCount.toString()
        followingCountTextView.text = followingCount.toString()
        postCountTextView.text = postCount.toString()
        bioTextView.text = bio

        // โหลดรูปโปรไฟล์ด้วย Glide
        Glide.with(requireContext())
            .load(getString(R.string.root_url) + profileImageUrl)
            .placeholder(R.drawable.profiletest2)
            .into(userProfileImage)

        // ตรวจสอบว่ามีฟิลด์ `posts` อยู่ใน JSON หรือไม่
        val posts = userProfile.optJSONArray("posts") ?: return // ถ้าไม่มีฟิลด์ `posts` ให้ return ออกไป

        val postList = mutableListOf<Post>()

        // ถ้ามีฟิลด์ `posts` จึงทำการแปลงข้อมูล
        for (i in 0 until posts.length()) {
            val post = posts.getJSONObject(i)
            postList.add(
                Post(
                    id = post.getInt("post_id"),
                    userName = userProfile.getString("username"),
                    userId = userProfile.getInt("userId"),
                    title = post.getString("content"),
                    time = post.getString("created_at"),
                    updated = post.optString("updated_at", null),
                    content = post.getString("content"),
                    is_liked = post.optBoolean("is_liked", false), // ใช้ optBoolean แทน getBoolean เพื่อป้องกันข้อผิดพลาด
                    userProfileUrl = userProfile.optString("profileImageUrl", null),
                    photoUrl = post.optJSONArray("photos")?.let { jsonArray ->
                        List(jsonArray.length()) { index -> jsonArray.getString(index) }
                    },
                    videoUrl = post.optJSONArray("videos")?.let { jsonArray ->
                        List(jsonArray.length()) { index -> jsonArray.getString(index) }
                    },
                    likeCount = post.optInt("like_count", 0),
                    commentCount = post.optInt("comment_count", 0)
                )
            )
        }

        // กำหนด Adapter ให้กับ RecyclerView
        recyclerViewPosts.adapter = PostAdapter(postList)
    }


}
