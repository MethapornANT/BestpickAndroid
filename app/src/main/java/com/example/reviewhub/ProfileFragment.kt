package com.example.reviewhub

import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Button
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.tabs.TabLayout
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class ProfileFragment : Fragment() {

    private val client = OkHttpClient()
    private lateinit var recyclerViewPosts: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ดึง token และ userId จาก SharedPreferences
        val sharedPreferences = requireActivity().getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences.getString("TOKEN", null)
        val userId = sharedPreferences.getString("USER_ID", null)

        // อ้างอิง UI Component
        val menuImageView = view.findViewById<ImageView>(R.id.menuImageView)
        val editProfileButton = view.findViewById<Button>(R.id.edit_profile_button)
        recyclerViewPosts = view.findViewById(R.id.recycler_view_posts)

        // ตั้งค่าการแสดงผลของ RecyclerView
        recyclerViewPosts.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewPosts.setHasFixedSize(true)

        // กำหนดการทำงานให้กับปุ่ม Edit Profile
        editProfileButton.setOnClickListener {
            val navController = findNavController()
            navController.navigate(R.id.editprofileFragment)
        }

        // ตั้งค่า PopupMenu สำหรับเมนูเพิ่มเติม
        menuImageView.setOnClickListener {
            val popupMenu = PopupMenu(requireContext(), menuImageView)
            popupMenu.menuInflater.inflate(R.menu.navbar_home, popupMenu.menu)

            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.setting -> true
                    R.id.Theme -> true
                    else -> false
                }
            }
            popupMenu.show()
        }

        // ตรวจสอบค่า Token และ User ID ก่อนดึงข้อมูลโปรไฟล์
        if (userId != null && token != null) {
            fetchUserProfile(view, userId, token)
            val tabLayout = view.findViewById<TabLayout>(R.id.tab_layout)
            tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    when (tab?.position) {
                        0 -> {
                            fetchUserProfile(view, userId, token)
                        }
                        1 -> {

                        }
                    }
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })
        }
    }

    private fun fetchUserProfile(view: View, userId: String, token: String) {
        val rootUrl = getString(R.string.root_url)  // URL หลักของ API จาก resources
        val userProfileEndpoint = "/api/users/"  // กำหนด URL ของ API Profile
        val url = "$rootUrl$userProfileEndpoint$userId/view-profile"  // สร้าง URL เต็ม

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ProfileFragment", "Failed to fetch user profile: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseData = response.body?.string()
                    responseData?.let {
                        try {
                            val userProfile = JSONObject(it)

                            // อัปเดต UI บน Main Thread
                            activity?.runOnUiThread {
                                displayUserProfile(view, userProfile)
                            }
                        } catch (e: Exception) {
                            Log.e("ProfileFragment", "Error parsing JSON: ${e.message}")
                        }
                    }
                } else {
                    Log.e("ProfileFragment", "Server error: ${response.message}")
                }
            }
        })
    }

    private fun displayUserProfile(view: View, userProfile: JSONObject) {
        val username = userProfile.getString("username")
        val profileImageUrl = userProfile.getString("profileImageUrl")
        val followerCount = userProfile.getInt("followerCount")
        val followingCount = userProfile.getInt("followingCount")
        val postCount = userProfile.getInt("postCount")
        val bio = userProfile.getString("bio")

        // กำหนดค่าข้อมูลใน View
        view.findViewById<TextView>(R.id.username)?.text = username
        view.findViewById<TextView>(R.id.back)?.text = username
        view.findViewById<TextView>(R.id.follower_count)?.text = followerCount.toString()
        view.findViewById<TextView>(R.id.following_count)?.text = followingCount.toString()
        view.findViewById<TextView>(R.id.post_count)?.text = postCount.toString()
        view.findViewById<TextView>(R.id.bio)?.text = bio

        // โหลดภาพโปรไฟล์โดยใช้ Glide
        val profileImageView = view.findViewById<ImageView>(R.id.user_profile_image)
        Glide.with(this)
            .load(getString(R.string.root_url) + profileImageUrl)
            .centerCrop()
            .placeholder(R.drawable.ic_launcher_background)
            .into(profileImageView)

        // เรียกฟังก์ชันจัดการข้อมูลโพสต์ของผู้ใช้
        if (userProfile.has("posts")) {
            displayUserPosts(userProfile.getJSONArray("posts"),userProfile)
        }
    }

    private fun displayUserPosts(posts: JSONArray, userProfile: JSONObject) {
        val postList = mutableListOf<Post>()
        val username = userProfile.getString("username")  // ดึง username จาก userProfile
        val userId = userProfile.getInt("userId")  // ดึง userId จาก userProfile

        for (i in 0 until posts.length()) {
            val post = posts.getJSONObject(i)

            postList.add(
                Post(
                    id = post.getInt("post_id"),
                    userName = username,  // ใช้ username จาก userProfile
                    userId = userId,  // ใช้ userId จาก userProfile แทนการดึงจากโพสต์
                    title = post.getString("content"),
                    time = post.getString("created_at"),
                    updated = post.optString("updated_at", null),
                    content = post.getString("content"),
                    is_liked = post.optBoolean("is_liked", false),
                    userProfileUrl = userProfile.optString("profileImageUrl", null),  // ใช้ profileImageUrl จาก userProfile
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

        Log.d("ProfileFragment", "Number of posts displayed: ${postList.size}")

        // ตั้งค่า Adapter สำหรับ RecyclerView
        recyclerViewPosts.adapter = PostAdapter(postList)
    }

}
