package com.example.reviewhub

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
    private val postList = mutableListOf<Post>()
    private val client = OkHttpClient()
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var noFollowingPostsTextView: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

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
            bottomNavigationView?.menu?.findItem(R.id.search)?.isChecked = true
        }

        // Setup TabLayout with a listener for tab selection
        val tabLayout = view.findViewById<TabLayout>(R.id.tab_layout)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        Log.d("TabSwitch", "Loading FORYOU data")
                        loadForYouData()
                    }
                    1 -> {
                        Log.d("TabSwitch", "Loading FOLLOW data")
                        loadFollowingData()
                    }
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

        // Fetch data from the API for the default tab
        fetchPosts(showLoading = true)

        // Pull-to-refresh functionality
        swipeRefreshLayout.setOnRefreshListener {
            fetchPosts(showLoading = false)
        }
    }

    // Function to refresh posts when Home is double clicked
    fun refreshPosts() {
        Toast.makeText(requireContext(), "Refreshing posts...", Toast.LENGTH_SHORT).show()
        view?.findViewById<RecyclerView>(R.id.recycler_view_posts)?.smoothScrollToPosition(0)
        fetchPosts(showLoading = true)
    }

    private fun fetchPosts(showLoading: Boolean) {
        swipeRefreshLayout.isRefreshing = false
        noFollowingPostsTextView.visibility = View.GONE // Hide the message initially

        val sharedPreferences = context?.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences?.getString("TOKEN", null)

        if (token == null) {
            Toast.makeText(requireContext(), "Token not found. Please login again.", Toast.LENGTH_SHORT).show()
            return
        }

        val url = getString(R.string.root_url) + getString(R.string.Allpost)

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Failed to fetch posts: ${response.message}", Toast.LENGTH_SHORT).show()
                    }
                    return
                }

                response.body?.string()?.let { jsonResponse ->
                    try {
                        val gson = Gson()
                        val postType = object : TypeToken<List<Post>>() {}.type
                        val posts: List<Post> = gson.fromJson(jsonResponse, postType)

                        requireActivity().runOnUiThread {
                            postList.clear()
                            postList.addAll(posts)
                            postAdapter.notifyDataSetChanged()

                            if (posts.isEmpty()) {
                                noFollowingPostsTextView.visibility = View.VISIBLE // Show message
                                recyclerView.visibility = View.GONE // Hide RecyclerView
                            } else {
                                noFollowingPostsTextView.visibility = View.GONE // Hide message
                                recyclerView.visibility = View.VISIBLE // Show RecyclerView
                            }
                        }
                    } catch (e: Exception) {
                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), "Error parsing data: ${e.message}", Toast.LENGTH_SHORT).show()
                            Log.e("HomeFragment", "Error parsing data: ${e.message}", e)
                        }
                    }
                } ?: run {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Response body is null", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    // Load data for FORYOU tab
    private fun loadForYouData() {
        Toast.makeText(requireContext(), "Loading FORYOU data...", Toast.LENGTH_SHORT).show()
        //fetchForYouPosts()
        fetchPosts(true)
    }

    // Load data for FOLLOW tab
    private fun loadFollowingData() {
        Toast.makeText(requireContext(), "Loading FOLLOW data...", Toast.LENGTH_SHORT).show()
        fetchFollowingPosts()
    }

    private fun fetchForYouPosts() {
        // Implement API call or local data fetching for FORYOU posts
    }

    private fun fetchFollowingPosts() {
        // Show a loading indicator
        noFollowingPostsTextView.visibility = View.GONE // Hide the message initially

        val sharedPreferences = context?.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences?.getString("TOKEN", null)

        if (token == null) {
            Toast.makeText(requireContext(), "Token not found. Please login again.", Toast.LENGTH_SHORT).show()
            return
        }

        val url = getString(R.string.root_url) + "/api/following/posts"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Failed to fetch followed posts: ${response.message}", Toast.LENGTH_SHORT).show()
                    }
                    return
                }

                response.body?.string()?.let { jsonResponse ->
                    try {
                        val gson = Gson()
                        val jsonObject = gson.fromJson(jsonResponse, JsonObject::class.java)
                        val postsJsonArray = jsonObject.getAsJsonArray("posts")

                        val postType = object : TypeToken<List<Post>>() {}.type
                        val posts: List<Post> = gson.fromJson(postsJsonArray, postType)

                        requireActivity().runOnUiThread {
                            postList.clear()

                            if (posts.isEmpty()) {
                                noFollowingPostsTextView.visibility = View.VISIBLE
                                recyclerView.visibility = View.GONE
                            } else {
                                noFollowingPostsTextView.visibility = View.GONE
                                recyclerView.visibility = View.VISIBLE
                                postAdapter.notifyDataSetChanged()  // เรียกเมื่อต้องการอัปเดตข้อมูล
                            }

                        }
                    } catch (e: Exception) {
                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), "Error parsing data: ${e.message}", Toast.LENGTH_SHORT).show()
                            Log.e("HomeFragment", "Error parsing data: ${e.message}", e)
                        }
                    }
                } ?: run {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Response body is null", Toast.LENGTH_SHORT).show()
                    }
                }
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
