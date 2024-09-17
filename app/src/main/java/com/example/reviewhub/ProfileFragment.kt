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
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class ProfileFragment : Fragment() {

    private val client = OkHttpClient()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Retrieve token passed through fragment arguments
        val sharedPreferences = requireActivity().getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences.getString("TOKEN", null)
        val userId = sharedPreferences.getString("USER_ID", null)


        Log.d("ProfileFragment", "Token: $token")
        Log.d("ProfileFragment", "User ID: $userId")

        // Fetch user profile using token and userId
        if (userId != null) {
            fetchUserProfile(view, userId, token)
        }
    }

    private fun fetchUserProfile(view: View, userId: String, token: String?) {
        val rootUrl = getString(R.string.root_url)
        val userProfileEndpoint = getString(R.string.userprofile)
        val url = "$rootUrl$userProfileEndpoint$userId/profile"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token") // Add Authorization header
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
                            val jsonObject = JSONObject(it)
                            val username = jsonObject.getString("username")
                            val profileImageUrl = jsonObject.getString("profileImageUrl")
                            val followerCount = jsonObject.getInt("followerCount")
                            val followingCount = jsonObject.getInt("followingCount")
                            val postCount = jsonObject.getInt("postCount")

                            val imgProfileUrl = rootUrl + profileImageUrl

                            // Update UI elements on the main thread
                            activity?.runOnUiThread {
                                // Set username
                                view.findViewById<TextView>(R.id.username)?.text = username
                                view.findViewById<TextView>(R.id.usernametop)?.text = username

                                // Set follower, following, and post counts
                                view.findViewById<TextView>(R.id.follower_count)?.text = followerCount.toString()
                                view.findViewById<TextView>(R.id.following_count)?.text = followingCount.toString()
                                view.findViewById<TextView>(R.id.post_count)?.text = postCount.toString()

                                // Load the profile image using Glide
                                val profileImageView = view.findViewById<ImageView>(R.id.user_profile_image)
                                Glide.with(this@ProfileFragment)
                                    .load(imgProfileUrl)
                                    .centerCrop()
                                    .placeholder(R.drawable.ic_launcher_background) // Placeholder image
                                    .into(profileImageView)
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
}
