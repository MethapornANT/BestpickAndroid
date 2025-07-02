package com.bestpick.reviewhub

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatDelegate
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import okhttp3.*
import org.json.JSONException
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
    private lateinit var sendMessageButton: Button
    private lateinit var recyclerViewPosts: RecyclerView
    private var isFollowing = false
    private val client = OkHttpClient()
    private lateinit var loadingIndicator: LottieAnimationView
    private var targetUserId: Int = -1
    private var currentMatchId: Int = -1

    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_another_user, container, false)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        val bottomNav = activity?.findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav?.visibility = View.GONE
        loadingIndicator = view.findViewById(R.id.lottie_loading)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            bottomNav?.visibility = View.VISIBLE
            parentFragmentManager.popBackStack()
        }

        userProfileImage = view.findViewById(R.id.user_profile_image)
        usernameTextView = view.findViewById(R.id.usernameanother)
        followerCountTextView = view.findViewById(R.id.follower_count)
        followingCountTextView = view.findViewById(R.id.following_count)
        postCountTextView = view.findViewById(R.id.post_count)
        bioTextView = view.findViewById(R.id.bio)
        followButton = view.findViewById(R.id.follower)
        sendMessageButton = view.findViewById(R.id.send_message)
        recyclerViewPosts = view.findViewById(R.id.recycler_view_posts)

        // Enable layout transitions for smooth animation
        val buttonContainer = followButton.parent as LinearLayout
        buttonContainer.layoutTransition = LayoutTransition()
        buttonContainer.layoutTransition.enableTransitionType(LayoutTransition.CHANGING)

        val back = view.findViewById<TextView>(R.id.back)
        back.setOnClickListener {
            requireActivity().onBackPressed()
        }

        recyclerViewPosts.layoutManager = LinearLayoutManager(requireContext())

        val userId = arguments?.getInt("USER_ID") ?: -1
        targetUserId = userId
        if (userId != -1) {
            fetchUserProfile(userId)
        }

        followButton.setOnClickListener {
            handleFollowButton(userId)
        }

        sendMessageButton.setOnClickListener {
            navigateToChat()
        }

        return view
    }

    private fun navigateToChat() {
        if (currentMatchId != -1) {
            // ถ้ามี matchID แล้ว ใช้เลย
            val intent = Intent(requireContext(), ChatActivity::class.java).apply {
                putExtra("matchID", currentMatchId)
                putExtra("senderID", getCurrentUserId())
                putExtra("nickname", usernameTextView.text.toString())
            }
            startActivity(intent)
        } else {
            // ถ้ายังไม่มี matchID ให้ดึงจาก API
            findOrCreateMatch()
        }
    }

    private fun findOrCreateMatch() {
        val sharedPreferences = context?.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences?.getString("TOKEN", null)
        val currentUserId = getCurrentUserId()

        // เรียก API create-match-on-follow
        val url = getString(R.string.root_url) + "/api/create-match-on-follow"

        val requestBody = FormBody.Builder()
            .add("followerID", currentUserId.toString())
            .add("followingID", targetUserId.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Failed to open chat: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val jsonResponse = response.body?.string()
                if (response.isSuccessful && !jsonResponse.isNullOrEmpty()) {
                    try {
                        val jsonObject = JSONObject(jsonResponse)
                        val matchId = jsonObject.getInt("matchID")
                        currentMatchId = matchId

                        requireActivity().runOnUiThread {
                            val intent = Intent(requireContext(), ChatActivity::class.java).apply {
                                putExtra("matchID", matchId)
                                putExtra("senderID", currentUserId)
                                putExtra("nickname", usernameTextView.text.toString())
                            }
                            startActivity(intent)
                        }
                    } catch (e: Exception) {
                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else if (response.code == 403) {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "You must follow this user first", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    // Add this import at the top of the file
    private fun getCurrentUserId(): Int {
        val sharedPreferences = context?.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val userIdString = sharedPreferences?.getString("USER_ID", null)
        return userIdString?.toIntOrNull() ?: -1
    }

    private fun handleFollowButton(userId: Int) {
        val sharedPreferences = context?.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences?.getString("TOKEN", null)

        if (token != null) {
            followButton.isEnabled = false // Disable button to prevent multiple clicks
            followUnfollowUser(userId, token)
        }
    }

    private fun updateButtonsUI() {
        if (isFollowing) {
            // User is following - adjust button weights and show message button
            val followParams = followButton.layoutParams as LinearLayout.LayoutParams
            followParams.weight = 0.57f
            followButton.layoutParams = followParams
            followButton.text = "Following"

            sendMessageButton.visibility = View.VISIBLE
        } else {
            // User is not following - hide message button and restore follow button
            val followParams = followButton.layoutParams as LinearLayout.LayoutParams
            followParams.weight = 1f
            followButton.layoutParams = followParams
            followButton.text = "Follow"

            sendMessageButton.visibility = View.GONE
        }
    }

    private fun fetchUserProfile(userId: Int) {
        val sharedPreferences = context?.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences?.getString("TOKEN", null)
        val url = getString(R.string.root_url) + "/api/users/$userId/view-profile"

        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()

        // แสดงโหลดเมื่อเริ่มดึงข้อมูล
        showLoadingIndicator(true)

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // ซ่อนโหลดเมื่อเกิดข้อผิดพลาด
                showLoadingIndicator(false)

                if (isAdded) { // ตรวจสอบว่า Fragment ยังคงเชื่อมต่อกับ Activity
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Failed to load profile: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                // ซ่อนโหลดเมื่อได้รับข้อมูล
                showLoadingIndicator(false)

                val jsonResponse = response.body?.string()
                if (jsonResponse != null) {
                    val userProfile = JSONObject(jsonResponse)

                    if (isAdded) { // ตรวจสอบว่า Fragment ยังคงเชื่อมต่อกับ Activity ก่อนอัปเดต UI
                        requireActivity().runOnUiThread {
                            displayUserProfile(userProfile)
                            checkFollowStatus(userId)
                        }
                    }
                }
            }
        })
    }

    // ฟังก์ชันแสดงหรือซ่อนโหลด
    fun showLoadingIndicator(show: Boolean) {
        if (isAdded) {
            requireActivity().runOnUiThread {
                loadingIndicator.visibility = if (show) View.VISIBLE else View.GONE
            }
        }
    }


    private fun displayUserProfile(userProfile: JSONObject) {
        val profileImageUrl = userProfile.optString("profileImageUrl", "")
        val username = userProfile.optString("username", "Unknown User")
        val followerCount = userProfile.optInt("followerCount", 0)
        val followingCount = userProfile.optInt("followingCount", 0)
        val postCount = userProfile.optInt("postCount", 0)
        val bio = userProfile.optString("bio", "No bio available")

        usernameTextView.text = username
        followerCountTextView.text = followerCount.toString()
        followingCountTextView.text = followingCount.toString()
        postCountTextView.text = postCount.toString()
        bioTextView.text = bio

        Glide.with(requireContext())
            .load(getString(R.string.root_url) + "/api" + profileImageUrl)
            .placeholder(R.drawable.user)
            .into(userProfileImage)

        val posts = userProfile.optJSONArray("posts") ?: return

        val postList = mutableListOf<Any>()
        for (i in 0 until posts.length()) {
            val post = posts.getJSONObject(i)
            postList.add(
                Post(
                    id = post.getInt("post_id"),
                    userName = userProfile.getString("username"),
                    userId = userProfile.getInt("userId"),
                    title = post.getString("title"),
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

        recyclerViewPosts.adapter = PostAdapter(postList)
    }

    private fun followUnfollowUser(followingId: Int, token: String) {
        val sharedPreferences = context?.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val userIdString = sharedPreferences?.getString("USER_ID", null)
        val userId = userIdString?.toIntOrNull() ?: return
        val url = getString(R.string.root_url) + "/api/users/$userId/follow/$followingId"
        val requestBody = FormBody.Builder().build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Failed to follow/unfollow user: ${e.message}", Toast.LENGTH_SHORT).show()
                    followButton.isEnabled = true  // Re-enable the button
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val jsonResponse = response.body?.string()
                if (!jsonResponse.isNullOrEmpty()) {
                    val jsonObject = JSONObject(jsonResponse)
                    val message = jsonObject.getString("message")
                    // เก็บ matchID ถ้ามี
                    if (jsonObject.has("matchID") && !jsonObject.isNull("matchID")) {
                        currentMatchId = jsonObject.getInt("matchID")
                    }

                    requireActivity().runOnUiThread {
                        isFollowing = !isFollowing
                        updateButtonsUI()
                        followButton.isEnabled = true  // Re-enable the button

                        if (isFollowing) {
                            updateFollowerCount(1) // Increment follower count
                            recordInteraction(followingId, "follow", null, token, requireContext())
                        } else {
                            updateFollowerCount(-1) // Decrement follower count
                            recordInteraction(followingId, "unfollow", null, token, requireContext())
                        }
                    }
                }
            }
        })
    }

    private fun updateFollowerCount(change: Int) {
        val currentCount = followerCountTextView.text.toString().toInt()
        followerCountTextView.text = (currentCount + change).toString()
    }

    private fun checkFollowStatus(userId: Int) {
        val sharedPreferences = context?.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences?.getString("TOKEN", null)
        val currentUserId = sharedPreferences?.getString("USER_ID", null)?.toIntOrNull() ?: return

        val url = getString(R.string.root_url) + "/api/users/$currentUserId/follow/$userId/status"

        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                requireActivity().runOnUiThread {

                }
            }

            override fun onResponse(call: Call, response: Response) {
                val jsonResponse = response.body?.string()
                if (!jsonResponse.isNullOrEmpty()) {
                    try {
                        val isUserFollowing = JSONObject(jsonResponse).getBoolean("isFollowing")
                        requireActivity().runOnUiThread {
                            isFollowing = isUserFollowing
                            updateButtonsUI()
                        }
                    } catch (e: JSONException) {
                        requireActivity().runOnUiThread {

                        }
                    }
                }
            }
        })
    }

    private fun recordInteraction(
        postId: Int,
        actionType: String,
        content: String? = null,
        token: String,
        context: Context
    ) {
        val url = "${context.getString(R.string.root_url)}${context.getString(R.string.interactions)}"

        val requestBody = FormBody.Builder()
            .add("post_id", postId.toString())
            .add("action_type", actionType)
            .apply {
                if (content != null) {
                    add("content", content)
                }
            }
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                (context as? Activity)?.runOnUiThread {
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        (context as? Activity)?.runOnUiThread {
                        }
                    } else {
                        val jsonResponse = response.body?.string()
                        val message = JSONObject(jsonResponse).getString("message")
                        (context as? Activity)?.runOnUiThread {
                        }
                    }
                }
            }
        })
    }
}