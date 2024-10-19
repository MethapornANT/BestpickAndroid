package com.example.reviewhub

import android.app.AlertDialog
import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.tabs.TabLayout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class CheckFollowFragment : Fragment() {
    private val client = OkHttpClient()
    private lateinit var recyclerViewFollowing: RecyclerView
    private lateinit var recyclerViewFollowers: RecyclerView
    private lateinit var backButton: ImageView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_check_follow, container, false)
        val sharedPreferences = requireActivity().getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences.getString("TOKEN", null)
        val userId = sharedPreferences.getString("USER_ID", null)

        backButton = view.findViewById(R.id.back_button)
        recyclerViewFollowing = view.findViewById(R.id.recycler_view_following)
        recyclerViewFollowers = view.findViewById(R.id.recycler_view_followers)

        // เริ่มต้นซ่อน RecyclerViewFollowers และแสดง RecyclerViewFollowing
        recyclerViewFollowers.visibility = View.GONE
        recyclerViewFollowing.visibility = View.VISIBLE

        if (token != null && userId != null) {
            fetchListFollowing(view, userId, token) // Default to load "Following" tab first
        } else {
            Toast.makeText(activity, "Token or User ID not found", Toast.LENGTH_SHORT).show()
        }

        backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
            findNavController().navigate(R.id.profileFragment)
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sharedPreferences = requireActivity().getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences.getString("TOKEN", null)
        val userId = sharedPreferences.getString("USER_ID", null)

        val tabLayout = view.findViewById<TabLayout>(R.id.tab_layout)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        recyclerViewFollowers.visibility = View.GONE
                        recyclerViewFollowing.visibility = View.VISIBLE
                        if (token != null && userId != null) {
                            fetchListFollowing(view, userId, token)
                        }
                    }
                    1 -> {
                        recyclerViewFollowing.visibility = View.GONE
                        recyclerViewFollowers.visibility = View.VISIBLE
                        if (token != null && userId != null) {
                            fetchListFollowers(view, userId, token)
                        }
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun fetchListFollowing(view: View, userId: String, token: String) {
        val url = getString(R.string.root_url) + "/api/users/following/$userId"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("CheckFollowFragment", "Failed to fetch following list: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseData = response.body?.string()
                    responseData?.let {
                        try {
                            val jsonArray = JSONArray(it)
                            val followingList = mutableListOf<Following>()

                            for (i in 0 until jsonArray.length()) {
                                val followingObject = jsonArray.getJSONObject(i)

                                // ตรวจสอบว่ามี key "userProfileUrl" หรือไม่
                                val userProfileUrl = if (followingObject.has("profileImageUrl")) {
                                    followingObject.getString("profileImageUrl")
                                } else {
                                    null // ถ้าไม่มีให้เป็น null
                                }

                                val follower = Following(
                                    followingObject.getInt("userId"),
                                    followingObject.getString("username"),
                                    userProfileUrl
                                )
                                followingList.add(follower)
                            }

                            activity?.runOnUiThread {
                                recyclerViewFollowing.layoutManager = LinearLayoutManager(context)
                                recyclerViewFollowing.adapter = FollowingAdapter(followingList)
                            }

                        } catch (e: JSONException) {
                            Log.e("CheckFollowFragment", "Error parsing JSON: ${e.message}")
                        }
                    }
                } else {
                    Log.e("CheckFollowFragment", "Server error: ${response.message}")
                }
            }
        })
    }

    private fun fetchListFollowers(view: View, userId: String, token: String) {
        val url = getString(R.string.root_url) + "/api/users/followers/$userId"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("CheckFollowFragment", "Failed to fetch followers list: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseData = response.body?.string()
                    responseData?.let {
                        try {
                            val jsonArray = JSONArray(it)
                            val followerList = mutableListOf<Follower>()

                            for (i in 0 until jsonArray.length()) {
                                val followerObject = jsonArray.getJSONObject(i)


                                val userProfileUrl = if (followerObject.has("profileImageUrl")) {
                                    followerObject.getString("profileImageUrl")
                                } else {
                                    null
                                }

                                val follower = Follower(
                                    followerObject.getInt("userId"),
                                    followerObject.getString("username"),
                                    userProfileUrl
                                )
                                followerList.add(follower)
                            }

                            activity?.runOnUiThread {
                                recyclerViewFollowers.layoutManager = LinearLayoutManager(context)
                                recyclerViewFollowers.adapter = FollowersAdapter(followerList)
                            }

                        } catch (e: JSONException) {
                            Log.e("CheckFollowFragment", "Error parsing JSON: ${e.message}")
                        }

                    }
                } else {
                    Log.e("CheckFollowFragment", "Server error: ${response.message}")
                }
            }
        })
    }
}


