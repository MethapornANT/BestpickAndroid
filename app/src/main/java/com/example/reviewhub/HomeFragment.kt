package com.example.reviewhub

import android.annotation.SuppressLint
import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import java.io.IOException

class HomeFragment : Fragment() {

    private lateinit var postAdapter: PostAdapter
    private val postList = mutableListOf<Post>()
    private val client = OkHttpClient() // Use a single OkHttpClient instance
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar

    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view_posts)
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout)
        progressBar = view.findViewById(R.id.progress_bar)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        val sharedPreferences = requireActivity().getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences.getString("TOKEN", null)
        val userId = sharedPreferences.getString("USER_ID", null)
        val picture = sharedPreferences.getString("PICTURE", null)
        val profileImg = view.findViewById<ImageView>(R.id.profile_image)

        if (picture != null) {
            val url = getString(R.string.root_url) + picture
            context?.let {
                Glide.with(it)
                    .load(url)
                    .circleCrop() // Apply circle crop if needed
                    .placeholder(R.drawable.ic_launcher_background) // Placeholder image while loading
                    .error(R.drawable.ic_error) // Error image if the loading fails
                    .into(profileImg)
            }
        }

        // Initialize the adapter with an empty list
        postAdapter = PostAdapter(postList)
        recyclerView.adapter = postAdapter

        // Fetch data from the API
        fetchPosts(showLoading = true)

        // Pull-to-refresh functionality
        swipeRefreshLayout.setOnRefreshListener {
            fetchPosts(showLoading = false) // Show swipe refresh only, not progress bar
        }

        return view
    }

    private fun fetchPosts(showLoading: Boolean) {
        if (showLoading) {
            progressBar.visibility = View.VISIBLE // Show progress bar only for the first load
        }
        swipeRefreshLayout.isRefreshing = false // Ensure swipe refresh icon is reset

        val url = getString(R.string.root_url) + getString(R.string.Allpost)

        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    progressBar.visibility = View.GONE // Hide progress bar
                    swipeRefreshLayout.isRefreshing = false // Ensure refreshing is stopped
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    activity?.runOnUiThread {
                        progressBar.visibility = View.GONE // Hide progress bar
                        swipeRefreshLayout.isRefreshing = false // Ensure refreshing is stopped
                        Toast.makeText(requireContext(), "Failed to fetch posts: ${response.message}", Toast.LENGTH_SHORT).show()
                    }
                    return
                }

                response.body?.string()?.let { jsonResponse ->
                    try {
                        val gson = Gson()
                        val postType = object : TypeToken<List<Post>>() {}.type
                        val posts: List<Post> = gson.fromJson(jsonResponse, postType)

                        activity?.runOnUiThread {
                            // Update the postList and notify the adapter
                            postList.clear()
                            postList.addAll(posts)
                            postAdapter.notifyDataSetChanged()
                            progressBar.visibility = View.GONE // Hide progress bar
                            swipeRefreshLayout.isRefreshing = false // Stop refresh animation
                        }
                    } catch (e: Exception) {
                        activity?.runOnUiThread {
                            progressBar.visibility = View.GONE // Hide progress bar
                            swipeRefreshLayout.isRefreshing = false // Stop refresh animation
                            Toast.makeText(requireContext(), "Error parsing data: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } ?: run {
                    activity?.runOnUiThread {
                        progressBar.visibility = View.GONE // Hide progress bar
                        swipeRefreshLayout.isRefreshing = false // Stop refresh animation
                        Toast.makeText(requireContext(), "Response body is null", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}
