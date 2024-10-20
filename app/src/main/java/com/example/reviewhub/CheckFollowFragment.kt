package com.example.reviewhub

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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
import okhttp3.FormBody
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
    private lateinit var searchEditText: EditText
    private val searchHandler = Handler()
    private var searchRunnable: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_check_follow, container, false)
        val sharedPreferences = requireActivity().getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences.getString("TOKEN", null)
        val userId = sharedPreferences.getString("USER_ID", null)




        searchEditText = view.findViewById(R.id.search_checkfollow)
        backButton = view.findViewById(R.id.back_button)
        recyclerViewFollowing = view.findViewById(R.id.recycler_view_following)
        recyclerViewFollowers = view.findViewById(R.id.recycler_view_followers)
        recyclerViewFollowers.visibility = View.GONE
        recyclerViewFollowing.visibility = View.VISIBLE


        if (token != null && userId != null) {
            fetchListFollowing(view, userId, token)
            fetchUserProfile(view, userId, token)
            setupSearchFunctionality(view, userId, token)
        } else {
            Toast.makeText(activity, "Token or User ID not found", Toast.LENGTH_SHORT).show()
        }

        backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
            findNavController().navigate(R.id.profileFragment)
        }





        return view
    }

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
                Log.e("CheckFollowFragment", "Failed to fetch user profile: ${e.message}")
                activity?.runOnUiThread {
                    Toast.makeText(activity, "Failed to load profile", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        val responseData = response.body?.string()
                        responseData?.let {
                            try {
                                val userProfile = JSONObject(it)
                                activity?.runOnUiThread {
                                    displayUserProfile(view, userProfile)
                                }
                            } catch (e: JSONException) {
                                Log.e("CheckFollowFragment", "Error parsing JSON: ${e.message}")
                                activity?.runOnUiThread {
                                    Toast.makeText(activity, "Failed to parse profile data", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } else {
                        Log.e("CheckFollowFragment", "Server error: ${response.message}")
                        activity?.runOnUiThread {
                            Toast.makeText(activity, "Server error: ${response.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })
    }

    private fun displayUserProfile(view: View, userProfile: JSONObject) {
        val username = userProfile.optString("username", "Unknown user")
        view.findViewById<TextView>(R.id.username_checkprofile)?.text = username
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


    private fun setupSearchFunctionality(view: View, userId: String, token: String) {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()

                if (query.isNotEmpty()) {
                    // เมื่อมีการค้นหา ให้เรียกฟังก์ชันค้นหา
                    searchFollowingOrFollowers(view, userId, token, query)
                } else {
                    // หากไม่มีการค้นหา (ช่องว่าง) ให้แสดงรายการทั้งหมด
                    if (recyclerViewFollowing.visibility == View.VISIBLE) {
                        fetchListFollowing(view, userId, token)
                    } else if (recyclerViewFollowers.visibility == View.VISIBLE) {
                        fetchListFollowers(view, userId, token)
                    }
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }


    private fun searchFollowingOrFollowers(view: View, userId: String, token: String, query: String) {
        if (recyclerViewFollowing.visibility == View.VISIBLE) {
            searchFollowing(view, userId, token, query)
        } else if (recyclerViewFollowers.visibility == View.VISIBLE) {
            searchFollowers(view, userId, token, query)
        }
    }

    private fun searchFollowing(view: View, userId: String, token: String, query: String) {
        // แปลง query เป็นตัวอักษรเล็กทั้งหมดเพื่อตรวจสอบให้ตรงกับ username แบบไม่สนใจตัวเล็ก-ใหญ่
        val searchQuery = query.toLowerCase()
        val url = "${getString(R.string.root_url)}/api/users/search/following?query=$searchQuery"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("CheckFollowFragment", "Failed to search following: ${e.message}")
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
                                val userProfileUrl = followingObject.optString("profileImageUrl", null)


                                if (followingObject.getString("username").toLowerCase().contains(searchQuery)) {
                                    val follower = Following(
                                        followingObject.getInt("userId"),
                                        followingObject.getString("username"),
                                        userProfileUrl
                                    )
                                    followingList.add(follower)
                                }
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

    private fun searchFollowers(view: View, userId: String, token: String, query: String) {
        // แปลง query เป็นตัวอักษรเล็กทั้งหมดเพื่อตรวจสอบให้ตรงกับ username แบบไม่สนใจตัวเล็ก-ใหญ่
        val searchQuery = query.toLowerCase()
        val url = "${getString(R.string.root_url)}/api/users/search/followers?query=$searchQuery"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("CheckFollowFragment", "Failed to search followers: ${e.message}")
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
                                val userProfileUrl = followerObject.optString("profileImageUrl", null)

                                // เปรียบเทียบ query กับชื่อผู้ใช้ โดยไม่สนใจตัวอักษรเล็ก-ใหญ่
                                if (followerObject.getString("username").toLowerCase().contains(searchQuery)) {
                                    val follower = Follower(
                                        followerObject.getInt("userId"),
                                        followerObject.getString("username"),
                                        userProfileUrl
                                    )
                                    followerList.add(follower)
                                }
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


