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
import android.widget.ImageView
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
import com.google.gson.JsonSyntaxException
import okhttp3.*
import java.io.IOException

class SearchFragment : Fragment(), OnItemClickListener {

    private lateinit var searchEditText: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchAdapter: SearchAdapter
    private val searchResults = mutableListOf<SearchResult>()
    private lateinit var progressBar: LottieAnimationView

    // NEW: store BottomNavigationView reference so navigateHome can update it
    private var bottomNavigationView: BottomNavigationView? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_search, container, false)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // --- back button now uses navigateHome() like NotificationsFragment ---
        val backButton: ImageView = view.findViewById(R.id.backButton)
        backButton.setOnClickListener {
            navigateHome()
        }
        // --- end back button change ---

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
                    progressBar.visibility = View.GONE
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Acquire bottom nav reference early (safe to try here)
        bottomNavigationView = activity?.findViewById(R.id.bottom_navigation)

        return view
    }

    // safe acquire in lifecycle in case activity was recreated
    override fun onStart() {
        super.onStart()
        bottomNavigationView = activity?.findViewById(R.id.bottom_navigation)
    }

    // navigateHome: mirror behavior from NotificationsFragment (update bottom nav, go to Home)
    private fun navigateHome() {
        activity?.runOnUiThread {
            val navController = findNavController()
            val bnv = activity?.findViewById<BottomNavigationView>(R.id.bottom_navigation)

            // Try common ids: prefer R.id.home if present, else R.id.homeFragment
            when {
                bnv?.menu?.findItem(R.id.home) != null -> bnv.selectedItemId = R.id.home
                bnv?.menu?.findItem(R.id.homeFragment) != null -> bnv.selectedItemId = R.id.homeFragment
                else -> Log.w("SearchFragment", "No matching home id in bottom nav menu")
            }

            // Prefer popping back to existing HomeFragment if present in back stack
            val popped = try {
                navController.popBackStack(R.id.homeFragment, false)
            } catch (t: Throwable) {
                false
            }

            if (!popped) {
                // fallback: navigate to HomeFragment (may create a new instance)
                try {
                    navController.navigate(R.id.homeFragment)
                } catch (t: Throwable) {
                    Log.w("SearchFragment", "Failed to navigate to homeFragment: ${t.message}")
                }
            }
        }
    }

    private fun performSearch(query: String) {
        progressBar.visibility = View.VISIBLE
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = getString(R.string.root_url) + "/api/search?query=$encodedQuery"
        Log.d("SearchFragment", "Performing search with URL: $url")

        val request = Request.Builder().url(url).build()
        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("SearchFragment", "Search API call failed: ${e.message}")
                activity?.runOnUiThread {
                    progressBar.visibility = View.GONE
                    searchResults.clear()
                    searchAdapter.notifyDataSetChanged()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                Log.d("SearchFragment", "Search API Response: $body")

                activity?.runOnUiThread {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful && body != null) {
                        val results = parseResults(body)
                        searchResults.clear()
                        searchResults.addAll(results)
                        searchAdapter.notifyDataSetChanged()
                        if (results.isEmpty()) {
                            Log.d("SearchFragment", "No results found for query: $query")
                        }
                    } else {
                        Log.e("SearchFragment", "Search API response not successful or body is null. Code: ${response.code}. Message: ${response.message}. Body: $body")
                        searchResults.clear()
                        searchAdapter.notifyDataSetChanged()
                    }
                }
            }
        })
    }

    private fun parseResults(json: String): List<SearchResult> {
        val searchResults = mutableListOf<SearchResult>()
        try {
            val jsonObject = Gson().fromJson(json, JsonObject::class.java)

            val resultsElement: JsonElement? = jsonObject.get("results")
            if (resultsElement == null || resultsElement.isJsonNull || !resultsElement.isJsonArray) {
                Log.w("ParseResults", "JSON does not contain a valid 'results' array or it is null. Raw JSON: $json")
                return emptyList()
            }

            val resultsArray: JsonArray = resultsElement.asJsonArray

            resultsArray.forEach { element ->
                if (element == null || element.isJsonNull || !element.isJsonObject) {
                    Log.w("ParseResults", "Skipping invalid element in results array: $element")
                    return@forEach
                }
                val userObject = element.asJsonObject

                val userId = userObject.get("user_id")?.let {
                    if (it.isJsonNull) -1 else it.asInt
                } ?: -1

                val username = userObject.get("username")?.let {
                    if (it.isJsonNull) "" else it.asString
                }.orEmpty()

                val profileImageUrl = userObject.get("profile_image")?.let {
                    if (it.isJsonNull) "/uploads/animal.png" else it.asString
                }.orEmpty()

                val postsElement: JsonElement? = userObject.get("posts")
                if (postsElement == null || postsElement.isJsonNull || !postsElement.isJsonArray || postsElement.asJsonArray.isEmpty) {
                    searchResults.add(SearchResult(userId, username, profileImageUrl = profileImageUrl))
                    Log.d("ParseResults", "Added user without posts: $username (ID: $userId)")
                } else {
                    val postsArray = postsElement.asJsonArray
                    postsArray.forEach { postElement ->
                        if (postElement == null || postElement.isJsonNull || !postElement.isJsonObject) {
                            Log.w("ParseResults", "Skipping invalid post element for user: $username. Element: $postElement")
                            return@forEach
                        }
                        val postObject = postElement.asJsonObject

                        try {
                            val postId = postObject.get("post_id")?.let {
                                if (it.isJsonNull) -1 else it.asInt
                            } ?: -1

                            val title = postObject.get("title")?.let {
                                if (it.isJsonNull) "" else it.asString
                            }.orEmpty()

                            val content = postObject.get("content_preview")?.let {
                                if (it.isJsonNull) "" else it.asString
                            }.orEmpty()

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
                                ""
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
                            searchResults.add(SearchResult(userId, username, profileImageUrl = profileImageUrl))
                        }
                    }
                }
            }
        } catch (e: JsonSyntaxException) {
            Log.e("ParseResults", "JSON Syntax Error: ${e.message}. Raw JSON (truncated): ${json.take(500)}", e)
            return emptyList()
        } catch (e: Exception) {
            Log.e("ParseResults", "General Error parsing search results: ${e.message}. Raw JSON (truncated): ${json.take(500)}", e)
            return emptyList()
        }
        return searchResults
    }

    override fun onItemClick(postId: Int?, userId: Int) {
        val bundle = Bundle()

        val sharedPreferences = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val userIdString = sharedPreferences.getString("USER_ID", null)
        val currentUserId = userIdString?.toIntOrNull() ?: -1
        Log.d("SearchFragment", "Current User ID: $currentUserId")
        Log.d("SearchFragment", "User ID from clicked item: $userId")

        if (postId != null && postId != -1) {
            bundle.putInt("POST_ID", postId)
            findNavController().navigate(R.id.action_searchFragment_to_postDetailFragment, bundle)
        } else {
            if (userId == currentUserId) {
                // Update bottom nav to profile and navigate to my profile
                try {
                    val bnv = activity?.findViewById<BottomNavigationView>(R.id.bottom_navigation)
                    if (bnv?.menu?.findItem(R.id.profile) != null) {
                        bnv.selectedItemId = R.id.profile
                    }
                } catch (t: Throwable) {
                    Log.w("SearchFragment", "Failed to update bottom nav: ${t.message}")
                }
                findNavController().navigate(R.id.action_searchFragment_to_myProfileFragment)
            } else if (userId != -1) {
                bundle.putInt("USER_ID", userId)
                findNavController().navigate(R.id.action_searchFragment_to_userProfileFragment, bundle)
            } else {
                Log.w("SearchFragment", "Attempted to navigate to a user profile with invalid ID: $userId")
            }
        }
    }
}
