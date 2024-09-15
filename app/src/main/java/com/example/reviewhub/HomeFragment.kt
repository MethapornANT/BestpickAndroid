package com.example.reviewhub

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HomeFragment : Fragment() {

    private lateinit var postAdapter: PostAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Find the RecyclerView in the fragment's layout
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view_posts)

        // Set up the RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Sample data to display
        val postList = listOf("Post 1", "Post 2", "Post 3")

        // Create and set the adapter
        postAdapter = PostAdapter(postList)
        recyclerView.adapter = postAdapter

        return view
    }
}
