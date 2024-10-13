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
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.airbnb.lottie.LottieAnimationView
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import java.io.IOException

class HomeFragment : Fragment() {

    private lateinit var postAdapter: PostAdapter
    private val postList = mutableListOf<Post>()
    private val client = OkHttpClient()
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var progressBar: LottieAnimationView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Initialize view elements
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view_posts)
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout)
        progressBar = view.findViewById(R.id.lottie_loading)


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
            bottomNavigationView?.menu?.findItem(R.id.search)?.isChecked = true
            navController.navigate(R.id.searchFragment)
        }


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
            popupMenu.show()  // แสดง PopupMenu
        }





        // Fetch data from the API
        fetchPosts(showLoading = true)

        // Pull-to-refresh functionality
        swipeRefreshLayout.setOnRefreshListener {
            fetchPosts(showLoading = false) // Show swipe refresh only, not progress bar
        }
    }



    // ฟังก์ชัน refreshPosts ที่จะถูกเรียกเมื่อคลิก Home สองครั้ง
    fun refreshPosts() {
        Toast.makeText(requireContext(), "Refreshing posts...", Toast.LENGTH_SHORT).show()
        view?.findViewById<RecyclerView>(R.id.recycler_view_posts)?.smoothScrollToPosition(0)
        fetchPosts(showLoading = true)
    }

    private fun fetchPosts(showLoading: Boolean) {
        if (showLoading) {
            progressBar.visibility = View.VISIBLE // Show progress bar only for the first load
        }
        swipeRefreshLayout.isRefreshing = false // Ensure swipe refresh icon is reset

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
                if (isAdded) {
                    requireActivity().runOnUiThread {
                        progressBar.visibility = View.GONE
                        swipeRefreshLayout.isRefreshing = false
                        Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    if (isAdded) {
                        requireActivity().runOnUiThread {
                            progressBar.visibility = View.GONE
                            swipeRefreshLayout.isRefreshing = false
                            Toast.makeText(requireContext(), "Failed to fetch posts: ${response.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return
                }

                response.body?.string()?.let { jsonResponse ->
                    try {
                        val gson = Gson()
                        val postType = object : TypeToken<List<Post>>() {}.type
                        val posts: List<Post> = gson.fromJson(jsonResponse, postType)

                        if (isAdded) {
                            requireActivity().runOnUiThread {
                                postList.clear()
                                postList.addAll(posts)
                                postAdapter.notifyDataSetChanged()
                                progressBar.visibility = View.GONE
                                swipeRefreshLayout.isRefreshing = false
                            }
                        }
                    } catch (e: Exception) {
                        if (isAdded) {
                            requireActivity().runOnUiThread {
                                progressBar.visibility = View.GONE
                                swipeRefreshLayout.isRefreshing = false
                                Toast.makeText(requireContext(), "Error parsing data: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } ?: run {
                    if (isAdded) {
                        requireActivity().runOnUiThread {
                            progressBar.visibility = View.GONE
                            swipeRefreshLayout.isRefreshing = false
                            Toast.makeText(requireContext(), "Response body is null", Toast.LENGTH_SHORT).show()
                        }
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
