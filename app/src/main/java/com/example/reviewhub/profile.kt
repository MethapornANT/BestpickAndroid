package com.example.reviewhub

import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class Profile : AppCompatActivity() {
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_profile)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Call the function to fetch user profile
        val id = intent.getStringExtra("id")
        fetchUserProfile(id.toString()) // Pass the user ID as needed
    }

    private fun fetchUserProfile(userId: String) {
        val url = getString(R.string.root_url) + getString(R.string.userprofile) + userId + "/profile"
        val token = intent.getStringExtra("TOKEN")

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token") // Add Authorization header
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ProfileActivity", "Failed to fetch user profile: ${e.message}")
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

                            val Imgprofile = getString(R.string.root_url) + profileImageUrl
                            // Update UI elements on the main thread
                            runOnUiThread {
                                // Set username
                                findViewById<TextView>(R.id.username).text = username

                                // Set follower, following, and post counts
                                findViewById<TextView>(R.id.follower_count).text = followerCount.toString()
                                findViewById<TextView>(R.id.following_count).text = followingCount.toString()
                                findViewById<TextView>(R.id.post_count).text = postCount.toString()

                                // Load the profile image using Glide
                                val profileImageView = findViewById<ImageView>(R.id.user_profile_image)
                                Glide.with(this@Profile)
                                    .load(Imgprofile)
                                    .centerCrop()
                                    .placeholder(R.drawable.ic_launcher_background) // Placeholder image
                                    .into(profileImageView)
                            }
                        } catch (e: Exception) {
                            Log.e("ProfileActivity", "Error parsing JSON: ${e.message}")
                        }
                    }
                } else {
                    Log.e("ProfileActivity", "Server error: ${response.message}")
                }
            }
        })
    }
}
