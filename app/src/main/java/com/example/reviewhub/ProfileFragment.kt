package com.example.reviewhub

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Button
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import okhttp3.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class ProfileFragment : Fragment() {

    private val client = OkHttpClient()
    private lateinit var recyclerViewPosts: RecyclerView
    private lateinit var followerTextView: TextView // Add this to reference the follower TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Retrieve token and userId from SharedPreferences
        val sharedPreferences = requireActivity().getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences.getString("TOKEN", null)
        val userId = sharedPreferences.getString("USER_ID", null)

        // Reference UI components
        val menuImageView = view.findViewById<ImageView>(R.id.menuImageView)
        val editProfileButton = view.findViewById<Button>(R.id.edit_profile_button)
        followerTextView = view.findViewById(R.id.follower_count) // Correct reference for follower TextView
        recyclerViewPosts = view.findViewById(R.id.recycler_view_posts)

        // Set up RecyclerView
        recyclerViewPosts.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewPosts.setHasFixedSize(true)

        if (userId != null && token != null) {
            fetchUserProfile(view, userId, token)
        } else {
            Toast.makeText(requireContext(), "User ID or token is null", Toast.LENGTH_SHORT).show()
        }

        // Set up Edit Profile button
        editProfileButton.setOnClickListener {
            findNavController().navigate(R.id.editprofileFragment)
        }

        // Set up PopupMenu for additional options
        menuImageView.setOnClickListener {
            val popupMenu = PopupMenu(requireContext(), menuImageView)
            popupMenu.menuInflater.inflate(R.menu.navbar_home, popupMenu.menu)

            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.deleteAccount -> {
                        showDeleteAccountDialog()
                        true
                    }

                    R.id.logout -> {
                        performLogout()
                        true
                    }

                    else -> false
                }
            }
            popupMenu.show()
        }

        // Tab to switch between user posts and bookmarks
        val tabLayout = view.findViewById<TabLayout>(R.id.tab_layout)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (userId != null && token != null) {
                    when (tab?.position) {
                        0 -> fetchUserProfile(view, userId, token) // Fetch user posts
                        1 -> fetchBookmarks(view, userId, token) // Fetch user bookmarks
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun showDeleteAccountDialog() {
        val options = arrayOf("Option 1", "Option 2")
        val dialogBuilder = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setTitle("Delete Account Option")
            .setSingleChoiceItems(options, -1) { dialog, which ->
                // Handle option selection
            }
            .setPositiveButton("Confirm") { dialog, _ ->
                // Handle confirmation
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }

        val alertDialog = dialogBuilder.create()
        alertDialog.show()
    }

    // Fetch user profile API call
    private fun fetchUserProfile(view: View, userId: String, token: String) {
        val rootUrl = getString(R.string.root_url)
        val userProfileEndpoint = "/api/users/"
        val url = "$rootUrl$userProfileEndpoint$userId/view-profile"

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
                            activity?.runOnUiThread {
                                displayUserProfile(view, userProfile)
                            }
                        } catch (e: JSONException) {
                            Log.e("ProfileFragment", "Error parsing JSON: ${e.message}")
                        }
                    }
                } else {
                    Log.e("ProfileFragment", "Server error: ${response.message}")
                }
            }
        })
    }

    // Display user profile data
    private fun displayUserProfile(view: View, userProfile: JSONObject) {
        val username = userProfile.getString("username")
        val profileImageUrl = userProfile.getString("profileImageUrl")
        val followerCount = userProfile.getInt("followerCount")
        val followingCount = userProfile.getInt("followingCount")
        val postCount = userProfile.getInt("postCount")
        val bio = userProfile.getString("bio")

        // Set user profile data
        view.findViewById<TextView>(R.id.username)?.text = username
        view.findViewById<TextView>(R.id.back)?.text = username
        followerTextView.text = followerCount.toString()
        view.findViewById<TextView>(R.id.following_count)?.text = followingCount.toString()
        view.findViewById<TextView>(R.id.post_count)?.text = postCount.toString()
        view.findViewById<TextView>(R.id.bio)?.text = bio

        // Load profile image using Glide
        val profileImageView = view.findViewById<ImageView>(R.id.user_profile_image)
        Glide.with(this)
            .load(getString(R.string.root_url) + profileImageUrl)
            .centerCrop()
            .placeholder(R.drawable.ic_launcher_background)
            .into(profileImageView)

        // Display user's posts
        if (userProfile.has("posts")) {
            displayUserPosts(userProfile.getJSONArray("posts"), userProfile)
        }
    }

    // Display user posts
    private fun displayUserPosts(posts: JSONArray, userProfile: JSONObject) {
        val postList = mutableListOf<Post>()
        val username = userProfile.getString("username")
        val userId = userProfile.getInt("userId")

        for (i in 0 until posts.length()) {
            val post = posts.getJSONObject(i)
            postList.add(
                Post(
                    id = post.getInt("post_id"),
                    userName = username,
                    userId = userId,
                    title = post.getString("content"),
                    time = post.getString("created_at"),
                    updated = post.optString("updated_at", null),
                    content = post.getString("content"),
                    is_liked = post.optBoolean("is_liked", false),
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

        // Set the adapter for RecyclerView
        recyclerViewPosts.adapter = PostAdapter(postList)
    }

    private fun fetchBookmarks(view: View, userId: String, token: String) {
        val rootUrl = getString(R.string.root_url)
        val url = "$rootUrl/api/bookmarks"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ProfileFragment", "Failed to fetch bookmarks: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseData = response.body?.string()
                    responseData?.let {
                        try {
                            val jsonResponse = JSONObject(it)
                            val bookmarks = jsonResponse.getJSONArray("bookmarks")
                            activity?.runOnUiThread {
                                displayBookmarks(bookmarks)
                            }
                        } catch (e: JSONException) {
                            Log.e("ProfileFragment", "Error parsing bookmarks JSON: ${e.message}")
                        }
                    }
                } else {
                    Log.e("ProfileFragment", "Error fetching bookmarks: ${response.message}")
                }
            }
        })
    }
    private fun displayBookmarks(bookmarks: JSONArray) {
        val bookmarkList = mutableListOf<Post>()
        val sharedPreferences = requireContext().getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences.getString("TOKEN", null)
        val userId = sharedPreferences.getString("USER_ID", null)?.toInt()

        for (i in 0 until bookmarks.length()) {
            val post = bookmarks.getJSONObject(i)
            val postId = post.optInt("post_id", -1) // Handle missing post_id
            val author = post.optJSONObject("author") // Extract author object
            val followingId = author?.optInt("user_id", -1) ?: -1 // Get user_id from the author object

            if (postId != -1 && userId != null && token != null) {
                // Create Post object
                val postObject = Post(
                    id = postId,
                    userName = post.optJSONObject("author")?.optString("username", "Unknown") ?: "Unknown",
                    userId = followingId,
                    title = post.optString("content", "No Content"),
                    time = post.optString("created_at", "Unknown Date"),
                    updated = post.optString("updated_at", null),
                    content = post.optString("content", ""),
                    is_liked = post.optBoolean("is_liked", false),
                    userProfileUrl = post.optJSONObject("author")?.optString("profile_image", null),
                    photoUrl = post.optJSONArray("photos")?.let { jsonArray ->
                        List(jsonArray.length()) { index -> jsonArray.getString(index) }
                    },
                    videoUrl = post.optJSONArray("videos")?.let { jsonArray ->
                        List(jsonArray.length()) { index -> jsonArray.getString(index) }
                    },
                    likeCount = post.optInt("like_count", 0),
                    commentCount = post.optInt("comment_count", 0)
                )

                // Add post to the list
                bookmarkList.add(postObject)
            }
        }

        // Set the adapter for RecyclerView
        recyclerViewPosts.adapter = PostAdapter(bookmarkList)
    }


    // Logout functionality
    private fun performLogout() {
        val firebaseAuth = FirebaseAuth.getInstance()
        firebaseAuth.signOut()
        clearLocalData()

        // Start the login activity and finish the current one
        val intent = Intent(requireContext(), LoginActivity::class.java)
        startActivity(intent)
        requireActivity().finish()
    }

    // Clear saved data (e.g., TOKEN, USER_ID) from SharedPreferences
    private fun clearLocalData() {
        if (isAdded) {
            val sharedPreferences: SharedPreferences =
                requireContext().getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
            sharedPreferences.edit().clear().apply()
        }
    }

}
