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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // Initialize view elements
        recyclerView = view.findViewById(R.id.recycler_view_posts)
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout)
        noFollowingPostsTextView = view.findViewById(R.id.no_following_posts)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        postAdapter = PostAdapter(postList)
        recyclerView.adapter = postAdapter

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up click listener for search
        val searchEditText = view.findViewById<ImageView>(R.id.searchEditText)
        searchEditText.setOnClickListener {
            val navController = findNavController()
            val bottomNavigationView = activity?.findViewById<BottomNavigationView>(R.id.bottom_navigation)
            navController.navigate(R.id.searchFragment)
            bottomNavigationView?.menu?.findItem(R.id.messages)?.isChecked = true
        }

        // Setup TabLayout with a listener for tab selection
        val tabLayout = view.findViewById<TabLayout>(R.id.tab_layout)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> fetchForYouPosts()
                    1 -> fetchFollowingPosts()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Default: Load data for the initial tab (assuming FORYOU is the default tab)
        loadForYouData()

        // Set up menu listener
        val menuImageView = view.findViewById<ImageView>(R.id.menuImageView)
        menuImageView.setOnClickListener {
            val popupMenu = PopupMenu(ContextThemeWrapper(requireContext(), R.style.CustomPopupMenuHomepage), menuImageView)
            popupMenu.menuInflater.inflate(R.menu.navbar_home, popupMenu.menu)
            popupMenu.menu.findItem(R.id.deleteAccount).isVisible = false
            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.logout -> {
                        performLogout()
                        true
                    }
                    else -> false
                }
            }
            popupMenu.show()
        }

        // Pull-to-refresh functionality
        swipeRefreshLayout.setOnRefreshListener {
            val selectedTab = tabLayout.selectedTabPosition
            if (selectedTab == 0) fetchForYouPosts() else fetchFollowingPosts()
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

    fun refreshPosts() {
        view?.findViewById<RecyclerView>(R.id.recycler_view_posts)?.smoothScrollToPosition(0)
        val selectedTab = view?.findViewById<TabLayout>(R.id.tab_layout)?.selectedTabPosition
        if (selectedTab == 0) fetchForYouPosts() else fetchFollowingPosts()
    }

    private fun loadForYouData() {
        fetchForYouPosts()
    }

    private fun fetchForYouPosts() {
        swipeRefreshLayout.isRefreshing = true
        noFollowingPostsTextView.visibility = View.GONE
        val sharedPreferences = context?.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences?.getString("TOKEN", null)

        val url = getString(R.string.root_url2) + "/ai" + "/recommend"
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
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    if (isAdded) {
                        requireActivity().runOnUiThread {
                            swipeRefreshLayout.isRefreshing = false
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
                                    postList.clear()
                                    postList.addAll(mixedList)
                                    postAdapter.notifyDataSetChanged()

                                    if (posts.isEmpty()) {
                                        noFollowingPostsTextView.visibility = View.VISIBLE
                                        recyclerView.visibility = View.GONE
                                    } else {
                                        noFollowingPostsTextView.visibility = View.GONE
                                        recyclerView.visibility = View.VISIBLE
                                    }
                                    swipeRefreshLayout.isRefreshing = false
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (isAdded) {
                            requireActivity().runOnUiThread {
                                swipeRefreshLayout.isRefreshing = false
                            }
                        }
                    }
                } ?: run {
                    if (isAdded) {
                        requireActivity().runOnUiThread {
                            swipeRefreshLayout.isRefreshing = false
                        }
                    }
                }
            }
        })
    }

    private fun fetchFollowingPosts() {
        swipeRefreshLayout.isRefreshing = true
        noFollowingPostsTextView.visibility = View.GONE
        val sharedPreferences = context?.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences?.getString("TOKEN", null)
        val url = getString(R.string.root_url) + "/api/following/posts"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (isAdded) {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        swipeRefreshLayout.isRefreshing = false
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    if (isAdded) {
                        requireActivity().runOnUiThread {
                            swipeRefreshLayout.isRefreshing = false
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
                                postList.clear()
                                if (posts.isEmpty()) {
                                    recyclerView.visibility = View.GONE
                                    noFollowingPostsTextView.visibility = View.VISIBLE
                                } else {
                                    postList.addAll(posts)
                                    postAdapter.notifyDataSetChanged()
                                    recyclerView.visibility = View.VISIBLE
                                    noFollowingPostsTextView.visibility = View.GONE
                                }
                                swipeRefreshLayout.isRefreshing = false
                            }
                        }
                    } catch (e: Exception) {
                        if (isAdded) {
                            requireActivity().runOnUiThread {
                                swipeRefreshLayout.isRefreshing = false
                            }
                        }
                    }
                } ?: run {
                    if (isAdded) {
                        requireActivity().runOnUiThread {
                            swipeRefreshLayout.isRefreshing = false
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
        val firebaseAuth = FirebaseAuth.getInstance()
        firebaseAuth.signOut()
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
}
