package com.example.reviewhub

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.*
import java.io.IOException

class SearchFragment : Fragment(), OnItemClickListener {

    private lateinit var searchEditText: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var searchAdapter: SearchAdapter
    private val searchResults = mutableListOf<SearchResult>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_search, container, false)

        searchEditText = view.findViewById(R.id.search_edit_text)
        recyclerView = view.findViewById(R.id.recycler_view_search_results)
        progressBar = view.findViewById(R.id.progress_bar)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        searchAdapter = SearchAdapter(searchResults, this)
        recyclerView.adapter = searchAdapter

        // กำหนด Listener สำหรับการค้นหา
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                if (query.isNotEmpty()) {
                    performSearch(query)
                } else {
                    searchResults.clear()
                    searchAdapter.notifyDataSetChanged()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        return view
    }

    private fun performSearch(query: String) {
        progressBar.visibility = View.VISIBLE
        val url = getString(R.string.root_url) + getString(R.string.Search) + "?query=$query"

        val request = Request.Builder().url(url).build()
        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                val results = parseResults(body)

                activity?.runOnUiThread {
                    searchResults.clear()
                    searchResults.addAll(results)
                    searchAdapter.notifyDataSetChanged()
                    progressBar.visibility = View.GONE
                }
            }
        })
    }

    private fun parseResults(json: String): List<SearchResult> {
        val jsonObject = Gson().fromJson(json, JsonObject::class.java)
        val resultsArray = jsonObject.getAsJsonArray("results") ?: return emptyList()
        val searchResults = mutableListOf<SearchResult>()

        resultsArray.forEach { element ->
            val userObject = element.asJsonObject

            val userId = if (userObject.has("user_id") && !userObject.get("user_id").isJsonNull) {
                userObject.get("user_id").asInt
            } else {
                -1 // กำหนดค่าเริ่มต้นสำหรับ userId ถ้าไม่มีข้อมูล
            }

            val username = if (userObject.has("username") && !userObject.get("username").isJsonNull) {
                userObject.get("username").asString
            } else {
                "Unknown User"
            }

            val profileImageUrl = if (userObject.has("profile_image") && !userObject.get("profile_image").isJsonNull) {
                userObject.get("profile_image").asString
            } else {
                "" // กำหนดค่าเริ่มต้นเป็นว่างถ้าไม่มีรูปโปรไฟล์
            }

            // ตรวจสอบว่ามี posts และไม่เป็น null
            if (userObject.has("posts") && !userObject.get("posts").isJsonNull) {
                val postsArray = userObject.getAsJsonArray("posts")
                postsArray.forEach { postElement ->
                    val postObject = postElement.asJsonObject

                    // ตรวจสอบและดึงค่าของ postId
                    val postId = if (postObject.has("post_id") && !postObject.get("post_id").isJsonNull) {
                        postObject.get("post_id").asInt
                    } else {
                        -1 // กำหนดค่าเริ่มต้นสำหรับ postId ถ้าไม่มีข้อมูล
                    }

                    // ตรวจสอบและดึงค่าของ title
                    val title = if (postObject.has("title") && !postObject.get("title").isJsonNull) {
                        postObject.get("title").asString
                    } else {
                        "Untitled"
                    }

                    // ตรวจสอบและดึงค่าของ content_preview
                    val content = if (postObject.has("content_preview") && !postObject.get("content_preview").isJsonNull) {
                        postObject.get("content_preview").asString
                    } else {
                        "No Content"
                    }

                    // ตรวจสอบและดึงค่าของ photo_url
                    val imageUrl = if (postObject.has("photo_url") && !postObject.get("photo_url").isJsonNull) {
                        postObject.get("photo_url").asString
                    } else {
                        ""
                    }

                    // เพิ่มข้อมูลเข้าไปในรายการ SearchResult
                    searchResults.add(
                        SearchResult(
                            userId = userId,
                            username = username,
                            postId = postId,
                            title = title,
                            content = content,
                            profileImageUrl = profileImageUrl,
                            imageUrl = imageUrl
                        )
                    )
                }
            }
        }
        return searchResults
    }

    override fun onItemClick(postId: Int) {
        // เมื่อคลิกที่โพสต์ ให้เปลี่ยนไปยัง `PostDetailFragment`
        val bundle = Bundle()
        bundle.putInt("POST_ID", postId)
        findNavController().navigate(R.id.action_searchFragment_to_postDetailFragment, bundle)
    }
}
