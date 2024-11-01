package com.bestpick.reviewhub

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.*
import java.io.IOException

class SearchFragment : Fragment(), OnItemClickListener {

    private lateinit var searchEditText: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchAdapter: SearchAdapter
    private val searchResults = mutableListOf<SearchResult>()
    private lateinit var progressBar: LottieAnimationView
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_search, container, false)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        searchEditText = view.findViewById(R.id.search_edit_text)
        recyclerView = view.findViewById(R.id.recycler_view_search_results)
        progressBar = view.findViewById(R.id.lottie_loading)


        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        searchAdapter = SearchAdapter(searchResults, this)
        recyclerView.adapter = searchAdapter


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
        val url = getString(R.string.root_url) + "/api/search?query=$query"

        val request = Request.Builder().url(url).build()
        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    progressBar.visibility = View.GONE
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

            // ตรวจสอบการเข้าถึงข้อมูลผู้ใช้
            val userId = userObject.get("user_id")?.asInt ?: -1
            val username = userObject.get("username")?.asString ?: "Unknown User"
            val profileImageUrl = userObject.get("profile_image")?.asString ?: ""

            // ตรวจสอบว่ามี posts หรือไม่
            if (!userObject.has("posts") || userObject.get("posts").isJsonNull) {
                // เพิ่มผู้ใช้ที่ไม่มีโพสต์
                searchResults.add(SearchResult(userId, username, profileImageUrl = profileImageUrl))
            } else {
                // ดึง posts ซึ่งเป็น JsonArray และเลือกเฉพาะรูปแรกจาก photo_url
                val postsArray = userObject.getAsJsonArray("posts")
                postsArray.forEach { postElement ->
                    val postObject = postElement.asJsonObject

                    // ใช้ try-catch เพื่อจัดการกับข้อผิดพลาด
                    try {
                        val postId = postObject.get("post_id")?.asInt ?: -1
                        val title = postObject.get("title")?.asString ?: "Untitled"
                        val content = postObject.get("content_preview")?.asString ?: "No Content"

                        // เลือกรูปภาพแรกจาก photo_url
                        val photoArray = postObject.getAsJsonArray("photo_url")
                        val firstPhotoUrl = if (photoArray != null && photoArray.size() > 0) {
                            photoArray[0].asString // ดึงรูปภาพแรกจากอาร์เรย์
                        } else {
                            "" // ถ้าไม่มีรูปภาพให้เป็นค่าว่าง
                        }

                        searchResults.add(
                            SearchResult(
                                userId = userId,
                                username = username,
                                postId = postId,
                                title = title,
                                content = content,
                                profileImageUrl = profileImageUrl,
                                imageUrl = firstPhotoUrl
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("ParseResults", "Error parsing post data", e)
                    }
                }
            }
        }
        return searchResults
    }




    override fun onItemClick(postId: Int?, userId: Int) {
        val bundle = Bundle()

        // ดึง currentUserId ของผู้ใช้ที่เข้าสู่ระบบในขณะนั้นจาก SharedPreferences
        val sharedPreferences = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val userIdString = sharedPreferences.getString("USER_ID", null)
        val currentUserId = userIdString?.toIntOrNull() ?: -1
        Log.d("SearchFragment", "Current User ID: $currentUserId")
        Log.d("SearchFragment", "User ID from Bundle: $userId")

        if (postId != null && postId != -1) {
            // เมื่อคลิกที่โพสต์ ให้เปลี่ยนไปยัง `PostDetailFragment`
            bundle.putInt("POST_ID", postId)
            findNavController().navigate(R.id.action_searchFragment_to_postDetailFragment, bundle)
        } else {
            // ตรวจสอบว่าผู้ใช้ที่ถูกคลิกเป็นผู้ใช้ที่เข้าสู่ระบบหรือไม่
            if (userId == currentUserId) {
                // หากเป็นโปรไฟล์ของผู้ใช้เอง
                val bottomNavigationView = activity?.findViewById<BottomNavigationView>(R.id.bottom_navigation)
                bottomNavigationView?.menu?.findItem(R.id.profile)?.isChecked = true
                findNavController().navigate(R.id.action_searchFragment_to_myProfileFragment) // ใช้ action ที่กำหนดไว้
            } else {
                // หากเป็นผู้ใช้คนอื่น ให้ไปที่ `UserProfileFragment`
                bundle.putInt("USER_ID", userId) // ส่ง ID ของผู้ใช้ที่คลิก
                findNavController().navigate(R.id.action_searchFragment_to_userProfileFragment, bundle)
            }
        }
    }


}
