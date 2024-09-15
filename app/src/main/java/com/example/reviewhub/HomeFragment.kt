package com.example.reviewhub


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import java.io.IOException

class HomeFragment : Fragment() {

    private lateinit var postAdapter: PostAdapter
    private val postList = mutableListOf<Post>()
    private val client = OkHttpClient() // Use a single OkHttpClient instance

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view_posts)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Initialize the adapter with an empty list
        postAdapter = PostAdapter(postList)
        recyclerView.adapter = postAdapter

        // Fetch data from the API
        fetchPosts()

        return view
    }

    private fun fetchPosts() {
        val url = getString(R.string.root_url) + getString(R.string.Allpost)

        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    activity?.runOnUiThread {
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
                        }
                    } catch (e: Exception) {
                        activity?.runOnUiThread {
                            Toast.makeText(requireContext(), "Error parsing data: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } ?: run {
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), "Response body is null", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}
