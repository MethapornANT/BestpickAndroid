// HomeFragment.kt

package com.bestpick.reviewhub

import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
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
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import okhttp3.*
import java.io.IOException

class HomeFragment : Fragment() {

    private lateinit var postAdapter: PostAdapter
    private val postList = mutableListOf<Any>()
    private val client = OkHttpClient()
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var noFollowingPostsTextView: TextView
    private lateinit var tabLayout: TabLayout

    private var isForYouLoading = false
    private var isFollowingLoading = false

    private val forYouData = mutableListOf<Any>()
    private val followingData = mutableListOf<Any>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        recyclerView = view.findViewById(R.id.recycler_view_posts)
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout)
        noFollowingPostsTextView = view.findViewById(R.id.no_following_posts)
        tabLayout = view.findViewById(R.id.tab_layout)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        postAdapter = PostAdapter(postList)
        recyclerView.adapter = postAdapter

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val searchEditText = view.findViewById<ImageView>(R.id.searchEditText)
        searchEditText.setOnClickListener {
            val navController = findNavController()
            navController.navigate(R.id.searchFragment)
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        if (forYouData.isEmpty() || forYouData.size != postList.size || !forYouData.containsAll(postList)) {
                            postList.clear()
                            postList.addAll(forYouData)
                            postAdapter.notifyDataSetChanged()
                            Log.d("HomeFragment", "Displaying cached For You data. Size: ${forYouData.size}")
                        }
                        if (forYouData.isEmpty()) {
                            fetchForYouPosts(false)
                        }
                        noFollowingPostsTextView.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                    }
                    1 -> {
                        if (followingData.isEmpty() || followingData.size != postList.size || !followingData.containsAll(postList)) {
                            postList.clear()
                            postList.addAll(followingData)
                            postAdapter.notifyDataSetChanged()
                            Log.d("HomeFragment", "Displaying cached Following data. Size: ${followingData.size}")
                        }
                        if (followingData.isEmpty()) {
                            fetchFollowingPosts(false)
                        }
                        if (followingData.isEmpty()) {
                            noFollowingPostsTextView.visibility = View.VISIBLE
                            recyclerView.visibility = View.GONE
                        } else {
                            noFollowingPostsTextView.visibility = View.GONE
                            recyclerView.visibility = View.VISIBLE
                        }
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}

            override fun onTabReselected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> refreshPosts(forceRefreshForYou = true)
                    1 -> refreshPosts(forceRefreshFollowing = true)
                }
            }
        })

        if (forYouData.isEmpty() && !isForYouLoading) {
            fetchForYouPosts(false)
        }

        // --- CODE ที่แก้ไขแล้ว ---
        // Set up messenger icon listener to navigate to MessageFragment
        val messengerIcon = view.findViewById<ImageView>(R.id.menuImageView) // ใช้ ID เดิมคือ menuImageView
        messengerIcon.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_messageFragment)
        }
        // --- จบส่วนที่แก้ไข ---

        swipeRefreshLayout.setOnRefreshListener {
            val selectedTab = tabLayout.selectedTabPosition
            if (selectedTab == 0) {
                refreshPosts(forceRefreshForYou = true)
            } else {
                refreshPosts(forceRefreshFollowing = true)
            }
        }
    }

    private fun insertAds(posts: List<Post>, ads: List<PostAdapter.Ad>, interval: Int = 5): List<Any> {
        val mixedList = mutableListOf<Any>()
        var adIndex = 0
        for ((index, post) in posts.withIndex()) {
            mixedList.add(post)
            if ((index + 1) % interval == 0 && adIndex < ads.size) {
                mixedList.add(ads[adIndex])
                adIndex++
            }
        }
        return mixedList
    }

    fun refreshPosts(forceRefreshForYou: Boolean = false, forceRefreshFollowing: Boolean = false) {
        recyclerView.smoothScrollToPosition(0)
        val selectedTab = tabLayout.selectedTabPosition
        if (selectedTab == 0) {
            fetchForYouPosts(forceRefreshForYou)
        } else {
            fetchFollowingPosts(forceRefreshFollowing)
        }
    }

    private fun fetchForYouPosts(forceRefresh: Boolean = false) {
        if (isForYouLoading) return
        isForYouLoading = true
        swipeRefreshLayout.isRefreshing = true
        noFollowingPostsTextView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE

        val sharedPreferences = context?.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences?.getString("TOKEN", null) ?: return

        val baseUrl = getString(R.string.root_url2) + "/ai" + "/recommend"
        val url = if (forceRefresh) "$baseUrl?refresh=true" else baseUrl

        val requestBody = FormBody.Builder().build()
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (isAdded) {
                    requireActivity().runOnUiThread {
                        swipeRefreshLayout.isRefreshing = false
                        isForYouLoading = false
                        Toast.makeText(requireContext(), "Failed to load posts: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    if (isAdded) {
                        requireActivity().runOnUiThread {
                            swipeRefreshLayout.isRefreshing = false
                            isForYouLoading = false
                        }
                    }
                    return
                }

                val jsonResponse = response.body?.string()
                jsonResponse?.let {
                    try {
                        val gson = Gson()
                        val postType = object : TypeToken<List<Post>>() {}.type
                        val posts: List<Post> = gson.fromJson(it, postType)

                        fetchRandomAds { ads ->
                            if (isAdded) {
                                requireActivity().runOnUiThread {
                                    val randomSize = (10..15).random()
                                    val randomAds = ads.shuffled().take(randomSize)
                                    val mixedList = insertAds(posts, randomAds, randomSize / 2)

                                    forYouData.clear()
                                    forYouData.addAll(mixedList)

                                    if (tabLayout.selectedTabPosition == 0) {
                                        postList.clear()
                                        postList.addAll(forYouData)
                                        postAdapter.notifyDataSetChanged()
                                    }

                                    if (forYouData.isEmpty()) {
                                        noFollowingPostsTextView.visibility = View.VISIBLE
                                        recyclerView.visibility = View.GONE
                                    } else {
                                        noFollowingPostsTextView.visibility = View.GONE
                                        recyclerView.visibility = View.VISIBLE
                                    }
                                    swipeRefreshLayout.isRefreshing = false
                                    isForYouLoading = false
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (isAdded) {
                            requireActivity().runOnUiThread {
                                swipeRefreshLayout.isRefreshing = false
                                isForYouLoading = false
                            }
                        }
                    }
                }
            }
        })
    }

    private fun fetchFollowingPosts(forceRefresh: Boolean = false) {
        if (isFollowingLoading) return
        isFollowingLoading = true
        swipeRefreshLayout.isRefreshing = true
        noFollowingPostsTextView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE

        val sharedPreferences = context?.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences?.getString("TOKEN", null) ?: return

        val url = getString(R.string.root_url) + "/api/following/posts"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (isAdded) {
                    requireActivity().runOnUiThread {
                        swipeRefreshLayout.isRefreshing = false
                        isFollowingLoading = false
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    if (isAdded) {
                        requireActivity().runOnUiThread {
                            swipeRefreshLayout.isRefreshing = false
                            isFollowingLoading = false
                        }
                    }
                    return
                }

                val responseBody = response.body?.string()
                responseBody?.let {
                    try {
                        val gson = Gson()
                        val jsonObject = gson.fromJson(it, JsonObject::class.java)
                        val postsJsonArray = jsonObject.getAsJsonArray("posts")
                        val postType = object : TypeToken<List<Post>>() {}.type
                        val posts: List<Post> = gson.fromJson(postsJsonArray, postType)

                        if (isAdded) {
                            requireActivity().runOnUiThread {
                                followingData.clear()
                                followingData.addAll(posts)

                                if (tabLayout.selectedTabPosition == 1) {
                                    postList.clear()
                                    postList.addAll(followingData)
                                    postAdapter.notifyDataSetChanged()
                                }

                                if (followingData.isEmpty()) {
                                    recyclerView.visibility = View.GONE
                                    noFollowingPostsTextView.visibility = View.VISIBLE
                                } else {
                                    recyclerView.visibility = View.VISIBLE
                                    noFollowingPostsTextView.visibility = View.GONE
                                }
                                swipeRefreshLayout.isRefreshing = false
                                isFollowingLoading = false
                            }
                        }
                    } catch (e: Exception) {
                        if (isAdded) {
                            requireActivity().runOnUiThread {
                                swipeRefreshLayout.isRefreshing = false
                                isFollowingLoading = false
                            }
                        }
                    }
                }
            }
        })
    }

    private fun fetchRandomAds(callback: (List<PostAdapter.Ad>) -> Unit) {
        val url = getString(R.string.root_url) + "/api/ads/random"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (isAdded) callback(emptyList())
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful || !isAdded) {
                    callback(emptyList())
                    return
                }

                response.body?.string()?.let { jsonResponse ->
                    try {
                        val gson = Gson()
                        val adType = object : TypeToken<List<PostAdapter.Ad>>() {}.type
                        val ads: List<PostAdapter.Ad> = gson.fromJson(jsonResponse, adType)
                        callback(ads)
                    } catch (e: Exception) {
                        callback(emptyList())
                    }
                } ?: callback(emptyList())
            }
        })
    }

    private fun performLogout() {
        FirebaseAuth.getInstance().signOut()
        clearLocalData()
        val intent = Intent(requireContext(), LoginActivity::class.java)
        startActivity(intent)
        requireActivity().finish()
    }

    private fun clearLocalData() {
        if (isAdded) {
            val sharedPreferences: SharedPreferences = requireContext().getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
            sharedPreferences.edit().clear().apply()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tabLayout.clearOnTabSelectedListeners()
    }
}