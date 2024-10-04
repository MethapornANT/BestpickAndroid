package com.example.reviewhub

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.widget.AppCompatEditText
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import android.animation.ObjectAnimator
import android.view.animation.DecelerateInterpolator


class SearchFragment : Fragment(), OnItemClickListener { // Implement OnItemClickListener

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
        searchEditText = view.findViewById(R.id.search_edit_text)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())


        // กำหนด Adapter พร้อมส่ง `this` เป็น Listener
        searchAdapter = SearchAdapter(searchResults, this)
        recyclerView.adapter = searchAdapter


        searchEditText.translationX = 1000f // เลื่อนไปขวานอกจอ
        ObjectAnimator.ofFloat(searchEditText, "translationX", 0f).apply {
            duration = 500 // กำหนดเวลา 500 มิลลิวินาที
            interpolator = DecelerateInterpolator() // ให้ความเร็วลดลงเมื่อถึงจุดสุดท้าย
            start()
        }

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

        val request = Request.Builder()
            .url(url)
            .build()

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



    override fun onItemClick(postId: Int) {
        // สร้าง Bundle สำหรับส่งข้อมูล `POST_ID` ไปยัง `PostDetailFragment`
        val bundle = Bundle().apply {
            putInt("POST_ID", postId)
        }

        // ใช้ NavController เพื่อนำทางจาก SearchFragment ไปยัง PostDetailFragment พร้อมส่ง `postId`
        findNavController().navigate(R.id.action_searchFragment_to_postDetailFragment, bundle)
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
                -1 // กำหนดค่า default ถ้าไม่มี user_id
            }

            val username = if (userObject.has("username") && !userObject.get("username").isJsonNull) {
                userObject.get("username").asString
            } else {
                "Unknown User"
            }

            // เพิ่มข้อมูลผู้ใช้
            searchResults.add(SearchResult(userId = userId, username = username))

            // ตรวจสอบว่า `posts` มีอยู่ใน `userObject` หรือไม่
            if (userObject.has("posts") && !userObject.get("posts").isJsonNull) {
                val postsArray = userObject.getAsJsonArray("posts")
                postsArray.forEach { postElement ->
                    val postObject = postElement.asJsonObject
                    val postId = if (postObject.has("post_id") && !postObject.get("post_id").isJsonNull) {
                        postObject.get("post_id").asInt
                    } else {
                        -1 // กำหนดค่า default ถ้าไม่มี post_id
                    }

                    val content = if (postObject.has("content_preview") && !postObject.get("content_preview").isJsonNull) {
                        postObject.get("content_preview").asString
                    } else {
                        "No Content"
                    }

                    // เพิ่มข้อมูลโพสต์ในรูปแบบ SearchResult
                    searchResults.add(SearchResult(userId = userId, username = username, postId = postId, content = content))
                }
            }
        }
        return searchResults
    }

}

