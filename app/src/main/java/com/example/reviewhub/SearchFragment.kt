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
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException // เพิ่ม import นี้
import com.google.gson.JsonNull // เพิ่ม import นี้ เพื่อตรวจสอบ JsonNull โดยตรง
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
                    // ซ่อน progress bar เมื่อไม่มีการค้นหา
                    progressBar.visibility = View.GONE
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        return view
    }

    private fun performSearch(query: String) {
        progressBar.visibility = View.VISIBLE
        // ควร encode query parameter เพื่อจัดการภาษาไทยหรืออักขระพิเศษ
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        // ใช้ getString(R.string.root_url) ซึ่งถูกต้องแล้ว
        val url = getString(R.string.root_url) + "/api/search?query=$encodedQuery"
        Log.d("SearchFragment", "Performing search with URL: $url") // เพิ่ม Log เพื่อตรวจสอบ URL

        val request = Request.Builder().url(url).build()
        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("SearchFragment", "Search API call failed: ${e.message}")
                activity?.runOnUiThread {
                    progressBar.visibility = View.GONE
                    searchResults.clear() // Clear results on network error
                    searchAdapter.notifyDataSetChanged() // Update UI to show empty state
                    // Optionally, show a toast to the user
                    // Toast.makeText(requireContext(), "การค้นหาล้มเหลว: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                Log.d("SearchFragment", "Search API Response: $body") // Log response body

                activity?.runOnUiThread {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful && body != null) {
                        val results = parseResults(body)
                        searchResults.clear()
                        searchResults.addAll(results)
                        searchAdapter.notifyDataSetChanged()
                        if (results.isEmpty()) {
                            Log.d("SearchFragment", "No results found for query: $query")
                            // อาจจะแสดงข้อความ "ไม่พบผลลัพธ์" ใน UI
                        }
                    } else {
                        Log.e("SearchFragment", "Search API response not successful or body is null. Code: ${response.code}. Message: ${response.message}. Body: $body")
                        searchResults.clear() // Clear previous results on error
                        searchAdapter.notifyDataSetChanged() // Update UI to show empty state
                        // Optionally, show a toast to the user for API error
                        // Toast.makeText(requireContext(), "ไม่พบผลลัพธ์ หรือเกิดข้อผิดพลาดในการค้นหา", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun parseResults(json: String): List<SearchResult> {
        val searchResults = mutableListOf<SearchResult>()
        try {
            val jsonObject = Gson().fromJson(json, JsonObject::class.java)

            // ตรวจสอบว่ามี "results" key และเป็น JsonArray หรือไม่
            val resultsElement: JsonElement? = jsonObject.get("results")
            if (resultsElement == null || resultsElement.isJsonNull || !resultsElement.isJsonArray) {
                Log.w("ParseResults", "JSON does not contain a valid 'results' array or it is null. Raw JSON: $json")
                return emptyList()
            }

            val resultsArray: JsonArray = resultsElement.asJsonArray

            resultsArray.forEach { element ->
                // ตรวจสอบว่าแต่ละ element เป็น JsonObject
                if (element == null || element.isJsonNull || !element.isJsonObject) {
                    Log.w("ParseResults", "Skipping invalid element in results array: $element")
                    return@forEach // ข้าม element นี้ไป
                }
                val userObject = element.asJsonObject

                // ดึงข้อมูลผู้ใช้ - เพิ่มการตรวจสอบ JsonNull และให้ค่า default ที่เหมาะสม
                val userId = userObject.get("user_id")?.let {
                    if (it.isJsonNull) -1 else it.asInt
                } ?: -1 // ถ้า user_id เป็น null หรือ JsonNull ให้เป็น -1

                val username = userObject.get("username")?.let {
                    if (it.isJsonNull) "" else it.asString
                }.orEmpty() // ถ้า username เป็น null หรือ JsonNull ให้เป็น String ว่าง

                val profileImageUrl = userObject.get("profile_image")?.let {
                    if (it.isJsonNull) "/uploads/animal.png" else it.asString
                }.orEmpty() // ถ้า profile_image เป็น null หรือ JsonNull ให้เป็นรูป default

                // ตรวจสอบว่ามี posts หรือไม่ และไม่ใช่ JsonNull และเป็น JsonArray
                val postsElement: JsonElement? = userObject.get("posts")
                if (postsElement == null || postsElement.isJsonNull || !postsElement.isJsonArray || postsElement.asJsonArray.isEmpty) {
                    // เพิ่มผู้ใช้ที่ไม่มีโพสต์ หรือโพสต์เป็น null/ว่างเปล่า
                    searchResults.add(SearchResult(userId, username, profileImageUrl = profileImageUrl))
                    Log.d("ParseResults", "Added user without posts: $username (ID: $userId)")
                } else {
                    // ดึง posts ซึ่งเป็น JsonArray
                    val postsArray = postsElement.asJsonArray
                    postsArray.forEach { postElement ->
                        // ตรวจสอบว่าแต่ละ postElement เป็น JsonObject
                        if (postElement == null || postElement.isJsonNull || !postElement.isJsonObject) {
                            Log.w("ParseResults", "Skipping invalid post element for user: $username. Element: $postElement")
                            return@forEach // ข้าม post element นี้ไป
                        }
                        val postObject = postElement.asJsonObject

                        try {
                            // ดึงข้อมูลโพสต์ - เพิ่มการตรวจสอบ JsonNull และให้ค่า default ที่เหมาะสม
                            val postId = postObject.get("post_id")?.let {
                                if (it.isJsonNull) -1 else it.asInt
                            } ?: -1

                            val title = postObject.get("title")?.let {
                                if (it.isJsonNull) "" else it.asString
                            }.orEmpty()

                            val content = postObject.get("content_preview")?.let {
                                if (it.isJsonNull) "" else it.asString
                            }.orEmpty()

                            // เลือกรูปภาพแรกจาก photo_url
                            val photoArrayElement: JsonElement? = postObject.get("photo_url")
                            val firstPhotoUrl = if (photoArrayElement != null && !photoArrayElement.isJsonNull && photoArrayElement.isJsonArray) {
                                val photoArray = photoArrayElement.asJsonArray
                                if (photoArray.size() > 0) {
                                    photoArray[0].let {
                                        if (it.isJsonNull) "" else it.asString.orEmpty()
                                    }
                                } else {
                                    ""
                                }
                            } else {
                                "" // ถ้าไม่มี photo_url หรือเป็น null/ไม่ใช่ array
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
                            Log.d("ParseResults", "Added post: '$title' by $username (Post ID: $postId)")

                        } catch (e: Exception) {
                            Log.e("ParseResults", "Error parsing individual post data. User: $username, Post JSON: $postObject. Error: ${e.message}", e)
                            // ในกรณีที่ post มีปัญหา แต่ user ยังโอเค เรายังคงเพิ่ม user นั้นเข้าไป
                            searchResults.add(SearchResult(userId, username, profileImageUrl = profileImageUrl))
                        }
                    }
                }
            }
        } catch (e: JsonSyntaxException) {
            Log.e("ParseResults", "JSON Syntax Error: ${e.message}. Raw JSON (truncated): ${json.take(500)}", e) // ตัด JSON ที่ยาวเกินไป
            return emptyList() // คืนค่าว่างเปล่าถ้า JSON ไม่ถูกต้อง
        } catch (e: Exception) {
            Log.e("ParseResults", "General Error parsing search results: ${e.message}. Raw JSON (truncated): ${json.take(500)}", e) // ตัด JSON ที่ยาวเกินไป
            return emptyList() // คืนค่าว่างเปล่าสำหรับข้อผิดพลาดอื่น ๆ
        }
        return searchResults
    }

    override fun onItemClick(postId: Int?, userId: Int) {
        val bundle = Bundle()

        val sharedPreferences = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val userIdString = sharedPreferences.getString("USER_ID", null)
        val currentUserId = userIdString?.toIntOrNull() ?: -1
        Log.d("SearchFragment", "Current User ID: $currentUserId")
        Log.d("SearchFragment", "User ID from clicked item: $userId") // เปลี่ยน log เพื่อความชัดเจน

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
            } else if (userId != -1) { // ตรวจสอบว่า userId ไม่ใช่ค่าเริ่มต้น
                // หากเป็นผู้ใช้คนอื่น ให้ไปที่ `UserProfileFragment`
                bundle.putInt("USER_ID", userId) // ส่ง ID ของผู้ใช้ที่คลิก
                findNavController().navigate(R.id.action_searchFragment_to_userProfileFragment, bundle)
            } else {
                Log.w("SearchFragment", "Attempted to navigate to a user profile with invalid ID: $userId")
                // อาจจะแสดง Toast หรือข้อความแจ้งเตือนผู้ใช้ว่าไม่สามารถไปยังโปรไฟล์นี้ได้
            }
        }
    }
}