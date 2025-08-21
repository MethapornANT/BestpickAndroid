package com.bestpick.reviewhub

import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class HomeFragment : Fragment() {

    private lateinit var postAdapter: PostAdapter
    private val postList = mutableListOf<Any>()
    private val client = OkHttpClient()
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var noFollowingPostsTextView: TextView
    private lateinit var tabLayout: TabLayout

    private var isForYouLoading = false
    private var isFollowingLoading = false

    private val forYouData = mutableListOf<Any>()
    private val followingData = mutableListOf<Any>()

    private val AD_INTERVAL = 15

    // ===================== [ADD] REALTIME SEEN TRACKER (อัปเกรด) =====================
    // กลไกใหม่: "สะสมเวลาเห็นรวม" (ไม่ต้องติดกัน) + sampling ถี่ขึ้น + hook ตอน attach/detach
    private val VIS_FRACTION = 0.50f       // เห็น >= 50% ของ view
    private val MIN_ACCUM_MS = 160L        // สะสม >= 160ms นับว่า "เห็น"
    private val SAMPLE_MS = 60L            // sample ทุก ~1 เฟรม 60Hz
    private val FLUSH_MS = 300L            // flush บัฟเฟอร์ทุก 300ms หรือตอน idle

    private val sampleHandler by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
    private val flushHandler by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }

    private var sampling = false
    private var flushScheduled = false

    // สะสมเวลาที่เห็นต่อ postId (ms)
    private val visibleAccumMs = mutableMapOf<Int, Long>()
    // กันส่งซ้ำใน session + บัฟเฟอร์รอส่ง
    private val seenSent = mutableSetOf<Int>()
    private val seenBuffer = linkedSetOf<Int>()

    private var lastSampleAtMs: Long = 0L

    private val sampleRunnable = object : Runnable {
        override fun run() {
            try {
                sampleVisibilityOnce()
            } finally {
                if (sampling) sampleHandler.postDelayed(this, SAMPLE_MS)
            }
        }
    }

    private val flushRunnable = Runnable {
        flushScheduled = false
        flushSeenBuffer()
    }
    // ============================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d("APP_LIFECYCLE", "HomeFragment: onCreateView")
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        recyclerView = view.findViewById(R.id.recycler_view_posts)
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout)
        noFollowingPostsTextView = view.findViewById(R.id.no_following_posts)
        tabLayout = view.findViewById(R.id.tab_layout)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        postAdapter = PostAdapter(postList)
        recyclerView.adapter = postAdapter

        // [ADD] hook ตอน child attach/detach เพื่อ sampling ช่วงที่เลื่อนเร็วมาก ๆ
        recyclerView.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                // เพิ่งเข้าหน้าจอ → sample ทันที
                sampleVisibilityOnce()
            }
            override fun onChildViewDetachedFromWindow(view: View) {
                // หลุดหน้าจอ → sample อีกที (เก็บช่วงเวลาสุดท้าย)
                sampleVisibilityOnce()
            }
        })

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("APP_LIFECYCLE", "HomeFragment: onViewCreated - START")

        val searchEditText = view.findViewById<ImageView>(R.id.searchEditText)
        searchEditText.setOnClickListener {
            val navController = findNavController()
            navController.navigate(R.id.searchFragment)
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        if (forYouData.isEmpty() || forYouData.size != postList.size || !forYouData.containsAll(postList)) {
                            postList.clear()
                            postList.addAll(forYouData)
                            postAdapter.notifyDataSetChanged()
                            Log.d("HomeFragment", "Displaying cached For You data. Size: ${forYouData.size}")
                            resetSeenTrackingForNewData()
                        }
                        if (forYouData.isEmpty()) {
                            fetchForYouPosts(false)
                        }
                        noFollowingPostsTextView.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                    }
                    1 -> {
                        if (followingData.isEmpty() || followingData.size != postList.size || !followingData.containsAll(postList)) {
                            postList.clear()
                            postList.addAll(followingData)
                            postAdapter.notifyDataSetChanged()
                            Log.d("HomeFragment", "Displaying cached Following data. Size: ${followingData.size}")
                            resetSeenTrackingForNewData()
                        }
                        if (followingData.isEmpty()) {
                            fetchFollowingPosts(false)
                        }
                        if (followingData.isEmpty()) {
                            noFollowingPostsTextView.visibility = View.VISIBLE
                            recyclerView.visibility = View.GONE
                        } else {
                            noFollowingPostsTextView.visibility = View.GONE
                            recyclerView.visibility = View.VISIBLE
                        }
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}

            override fun onTabReselected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> refreshPosts(forceRefreshForYou = true)
                    1 -> refreshPosts(forceRefreshFollowing = true)
                }
            }
        })

        if (forYouData.isEmpty() && !isForYouLoading) {
            fetchForYouPosts(false)
        }

        val messengerIcon = view.findViewById<ImageView>(R.id.messengerImageView)
        messengerIcon.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_messageFragment)
        }

        swipeRefreshLayout.setOnRefreshListener {
            val selectedTab = tabLayout.selectedTabPosition
            if (selectedTab == 0) {
                refreshPosts(forceRefreshForYou = true)
            } else {
                refreshPosts(forceRefreshFollowing = true)
            }
        }

        // Add OnScrollListener to RecyclerView to track ad impressions
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()

                for (i in firstVisibleItemPosition..lastVisibleItemPosition) {
                    val item = postList.getOrNull(i)
                    if (item is PostAdapter.Ad && !item.isCounted) {
                        item.isCounted = true
                        trackAdImpression(item.id)
                    }
                }

                // [ADD] sample ระหว่างสกรอลล์
                sampleVisibilityOnce()
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    sampleVisibilityOnce()
                    scheduleFlushSoon()
                }
            }
        })

        Log.d("APP_LIFECYCLE", "HomeFragment: onViewCreated - END")
    }

    // ===================== Sampling lifecycle =====================
    override fun onResume() {
        super.onResume()
        startSampling()
    }

    override fun onPause() {
        stopSampling()
        flushSeenBuffer() // flush ก่อนพัก
        super.onPause()
    }

    private fun startSampling() {
        if (sampling) return
        sampling = true
        lastSampleAtMs = 0L
        sampleHandler.post(sampleRunnable)
    }

    private fun stopSampling() {
        sampling = false
        sampleHandler.removeCallbacks(sampleRunnable)
        flushHandler.removeCallbacks(flushRunnable)
        flushScheduled = false
    }
    // ===============================================================

    private fun insertAds(posts: List<Post>, ads: List<PostAdapter.Ad>, interval: Int = 5): List<Any> {
        val mixedList = mutableListOf<Any>()
        var adIndex = 0
        for ((index, post) in posts.withIndex()) {
            mixedList.add(post)
            if ((index + 1) % interval == 0 && adIndex < ads.size) {
                mixedList.add(ads[adIndex])
                adIndex++
            }
        }
        return mixedList
    }

    fun refreshPosts(forceRefreshForYou: Boolean = false, forceRefreshFollowing: Boolean = false) {
        recyclerView.smoothScrollToPosition(0)
        val selectedTab = tabLayout.selectedTabPosition
        if (selectedTab == 0) {
            fetchForYouPosts(forceRefreshForYou)
        } else {
            fetchFollowingPosts(forceRefreshFollowing)
        }
    }

    private fun fetchForYouPosts(forceRefresh: Boolean = false) {
        if (isForYouLoading) return
        isForYouLoading = true
        swipeRefreshLayout.isRefreshing = true
        noFollowingPostsTextView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE

        val sharedPreferences = context?.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences?.getString("TOKEN", null) ?: return

        val baseUrl = getString(R.string.root_url2) + "/ai" + "/recommend"
        val url = if (forceRefresh) "$baseUrl?refresh=true" else baseUrl

        val requestBody = FormBody.Builder().build()
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (isAdded) {
                    requireActivity().runOnUiThread {
                        swipeRefreshLayout.isRefreshing = false
                        isForYouLoading = false
                        Toast.makeText(requireContext(), "Failed to load posts: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    if (isAdded) {
                        requireActivity().runOnUiThread {
                            swipeRefreshLayout.isRefreshing = false
                            isForYouLoading = false
                        }
                    }
                    return
                }

                val jsonResponse = response.body?.string()
                jsonResponse?.let {
                    try {
                        val gson = Gson()
                        val postType = object : TypeToken<List<Post>>() {}.type
                        val posts: List<Post> = gson.fromJson(it, postType)

                        // Start the process of fetching and inserting ads
                        fetchAndInsertAdsSequentially(posts) { mixedList ->
                            if (isAdded) {
                                requireActivity().runOnUiThread {
                                    forYouData.clear()
                                    forYouData.addAll(mixedList)

                                    if (tabLayout.selectedTabPosition == 0) {
                                        postList.clear()
                                        postList.addAll(forYouData)
                                        postAdapter.notifyDataSetChanged()
                                        resetSeenTrackingForNewData()
                                        sampleVisibilityOnce()
                                    }

                                    if (forYouData.isEmpty()) {
                                        noFollowingPostsTextView.visibility = View.VISIBLE
                                        recyclerView.visibility = View.GONE
                                    } else {
                                        noFollowingPostsTextView.visibility = View.GONE
                                        recyclerView.visibility = View.VISIBLE
                                    }
                                    swipeRefreshLayout.isRefreshing = false
                                    isForYouLoading = false
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (isAdded) {
                            requireActivity().runOnUiThread {
                                swipeRefreshLayout.isRefreshing = false
                                isForYouLoading = false
                            }
                        }
                    }
                }
            }
        })
    }

    private fun fetchFollowingPosts(forceRefresh: Boolean = false) {
        if (isFollowingLoading) return
        isFollowingLoading = true
        swipeRefreshLayout.isRefreshing = true
        noFollowingPostsTextView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE

        val sharedPreferences = context?.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences?.getString("TOKEN", null) ?: return

        val url = getString(R.string.root_url) + "/api/following/posts"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (isAdded) {
                    requireActivity().runOnUiThread {
                        swipeRefreshLayout.isRefreshing = false
                        isFollowingLoading = false
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    if (isAdded) {
                        requireActivity().runOnUiThread {
                            swipeRefreshLayout.isRefreshing = false
                            isFollowingLoading = false
                        }
                    }
                    return
                }

                val responseBody = response.body?.string()
                responseBody?.let {
                    try {
                        val gson = Gson()
                        val jsonObject = gson.fromJson(it, JsonObject::class.java)
                        val postsJsonArray = jsonObject.getAsJsonArray("posts")
                        val postType = object : TypeToken<List<Post>>() {}.type
                        val posts: List<Post> = gson.fromJson(postsJsonArray, postType)

                        if (isAdded) {
                            requireActivity().runOnUiThread {
                                followingData.clear()
                                followingData.addAll(posts)

                                if (tabLayout.selectedTabPosition == 1) {
                                    postList.clear()
                                    postList.addAll(followingData)
                                    postAdapter.notifyDataSetChanged()
                                    resetSeenTrackingForNewData()
                                    sampleVisibilityOnce()
                                }

                                if (followingData.isEmpty()) {
                                    recyclerView.visibility = View.GONE
                                    noFollowingPostsTextView.visibility = View.VISIBLE
                                } else {
                                    recyclerView.visibility = View.VISIBLE
                                    noFollowingPostsTextView.visibility = View.GONE
                                }
                                swipeRefreshLayout.isRefreshing = false
                                isFollowingLoading = false
                            }
                        }
                    } catch (e: Exception) {
                        if (isAdded) {
                            requireActivity().runOnUiThread {
                                swipeRefreshLayout.isRefreshing = false
                                isFollowingLoading = false
                            }
                        }
                    }
                }
            }
        })
    }

    // New function to fetch and insert ads sequentially
    private fun fetchAndInsertAdsSequentially(posts: List<Post>, callback: (List<Any>) -> Unit) {
        val mixedList = mutableListOf<Any>()
        val adIndices = mutableListOf<Int>()
        var postCounter = 0

        for (post in posts) {
            mixedList.add(post)
            postCounter++
            if (postCounter == AD_INTERVAL) {
                adIndices.add(mixedList.size)
                postCounter = 0
            }
        }

        val adsToFetch = adIndices.size
        if (adsToFetch == 0) {
            callback(mixedList)
            return
        }

        var adIndex = 0
        fun fetchNextAd() {
            if (adIndex < adsToFetch) {
                fetchRandomAd { ad ->
                    if (ad != null) {
                        mixedList.add(adIndices[adIndex], ad)
                    }
                    adIndex++
                    fetchNextAd()
                }
            } else {
                callback(mixedList)
            }
        }
        fetchNextAd()
    }

    private fun fetchRandomAd(callback: (PostAdapter.Ad?) -> Unit) {
        val url = getString(R.string.root_url) + "/api/ads/random"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (isAdded) callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful || !isAdded) {
                    callback(null)
                    return
                }

                response.body?.string()?.let { jsonResponse ->
                    try {
                        val gson = Gson()
                        val adType = object : TypeToken<List<PostAdapter.Ad>>() {}.type
                        val ads: List<PostAdapter.Ad> = gson.fromJson(jsonResponse, adType)
                        val singleAd = ads.firstOrNull()
                        callback(singleAd)
                    } catch (e: Exception) {
                        callback(null)
                    }
                } ?: callback(null)
            }
        })
    }

    private fun trackAdImpression(adId: String) {
        val url = getString(R.string.root_url) + "/api/ads/track"
        val requestBody = FormBody.Builder()
            .add("id", adId)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("AdTracker", "Failed to track ad impression: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("AdTracker", "Failed to track ad impression. Response code: ${response.code}")
                } else {
                    Log.d("AdTracker", "Ad impression tracked successfully for ad ID: $adId")
                }
            }
        })
    }

    private fun performLogout() {
        FirebaseAuth.getInstance().signOut()
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

    override fun onDestroyView() {
        super.onDestroyView()
        tabLayout.clearOnTabSelectedListeners()
        stopSampling()
        flushSeenBuffer()
    }

    // ===================== Seen tracker helpers =====================

    private fun resetSeenTrackingForNewData() {
        visibleAccumMs.clear()
        seenBuffer.clear()
        // ไม่ลบ seenSent เพื่อกันส่งซ้ำภายในการใช้งานรอบเดียวกัน
        lastSampleAtMs = 0L
    }

    private fun sampleVisibilityOnce() {
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val first = lm.findFirstVisibleItemPosition()
        val last = lm.findLastVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) return

        val now = System.currentTimeMillis()
        val dt = if (lastSampleAtMs == 0L) 0L else (now - lastSampleAtMs)
        lastSampleAtMs = now

        if (dt > 0L) {
            // เก็บรายชื่อ post ที่เข้าเกณฑ์ในรอบนี้
            val qualified = mutableListOf<Int>()

            for (i in first..last) {
                val vh = recyclerView.findViewHolderForAdapterPosition(i) ?: continue
                val item = postList.getOrNull(i) ?: continue
                val postId = (item as? Post)?.id ?: continue

                val child = vh.itemView
                val rect = Rect()
                val visible = child.getLocalVisibleRect(rect)
                if (!visible) continue

                val visibleH = rect.height().coerceAtLeast(0)
                val frac = if (child.height > 0) visibleH.toFloat() / child.height.toFloat() else 0f
                if (frac >= VIS_FRACTION) qualified.add(postId)
            }

            // สะสมเวลาให้ post ที่เข้าเกณฑ์
            for (pid in qualified) {
                val acc = (visibleAccumMs[pid] ?: 0L) + dt
                visibleAccumMs[pid] = acc
                if (acc >= MIN_ACCUM_MS && !seenSent.contains(pid)) {
                    seenSent.add(pid)
                    seenBuffer.add(pid)
                }
            }

            if (seenBuffer.isNotEmpty()) scheduleFlushSoon()
        }
    }

    private fun scheduleFlushSoon() {
        if (flushScheduled) return
        flushScheduled = true
        flushHandler.postDelayed(flushRunnable, FLUSH_MS)
    }

    private fun flushSeenBuffer() {
        if (seenBuffer.isEmpty()) return
        val batch = seenBuffer.toList()
        seenBuffer.clear()
        sendSeenBatch(batch)
    }

    private fun sendSeenBatch(ids: List<Int>) {
        val sharedPreferences = context?.getSharedPreferences("MyAppPrefs", MODE_PRIVATE) ?: return
        val token = sharedPreferences.getString("TOKEN", null) ?: return

        // ใช้ base เดียวกับ /ai/recommend เพื่อเลี่ยง 404
        val url = getString(R.string.root_url2) + "/ai/seen"

        val gson = Gson()
        val json = gson.toJson(mapOf("seen_ids" to ids))
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toRequestBody(mediaType)

        val req = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // requeue เพื่อไม่ให้หาย
                seenBuffer.addAll(ids)
                scheduleFlushSoon()
                Log.e("SeenTrack", "sendSeenBatch failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        seenBuffer.addAll(ids)
                        scheduleFlushSoon()
                        Log.e("SeenTrack", "sendSeenBatch non-200: ${it.code}")
                    } else {
                        Log.d("SeenTrack", "Sent seen_ids: ${ids.size}")
                    }
                }
            }
        })
    }
    // =====================================================================
}
