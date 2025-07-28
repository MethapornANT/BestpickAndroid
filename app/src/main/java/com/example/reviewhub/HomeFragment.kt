// HomeFragment.kt

package com.bestpick.reviewhub

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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import okhttp3.*
import java.io.IOException

// สมมติว่ามี Post และ PostAdapter.Ad class อยู่แล้ว
// import com.bestpick.reviewhub.model.Post // ถ้า Post เป็น data class แยกออกมา
// import com.bestpick.reviewhub.adapter.PostAdapter // ถ้า PostAdapter อยู่ใน package adapter

class HomeFragment : Fragment() {

    private lateinit var postAdapter: PostAdapter
    private val postList = mutableListOf<Any>() // ใช้เก็บทั้ง Post และ Ad
    private val client = OkHttpClient()
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var noFollowingPostsTextView: TextView
    private lateinit var tabLayout: TabLayout // เพิ่ม tabLayout เพื่อให้เข้าถึงง่ายขึ้น

    // ตัวแปรสถานะการโหลดข้อมูลแต่ละแท็บ
    private var isForYouLoading = false
    private var isFollowingLoading = false

    // ตัวแปรเก็บข้อมูลแยกสำหรับแต่ละแท็บ เพื่อจัดการเมื่อสลับไปมา
    private val forYouData = mutableListOf<Any>()
    private val followingData = mutableListOf<Any>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Initialize view elements
        recyclerView = view.findViewById(R.id.recycler_view_posts)
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout)
        noFollowingPostsTextView = view.findViewById(R.id.no_following_posts)
        tabLayout = view.findViewById(R.id.tab_layout) // Initialize tabLayout here

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        postAdapter = PostAdapter(postList) // Adapter ใช้ postList เดียวกัน
        recyclerView.adapter = postAdapter

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val searchEditText = view.findViewById<ImageView>(R.id.searchEditText)
        searchEditText.setOnClickListener {
            val navController = findNavController()
            val bottomNavigationView = activity?.findViewById<BottomNavigationView>(R.id.bottom_navigation)
            navController.navigate(R.id.searchFragment)
            bottomNavigationView?.menu?.findItem(R.id.messages)?.isChecked = true // ควรลบหรือปรับถ้าไม่ต้องการ
        }

        // Setup TabLayout with a listener for tab selection
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> { // For You tab selected
                        if (forYouData.isEmpty() || forYouData.size != postList.size || !forYouData.containsAll(postList)) { // ตรวจสอบว่าข้อมูลใน forYouData ไม่ตรงกับที่กำลังแสดงหรือไม่
                            postList.clear()
                            postList.addAll(forYouData) // โหลดข้อมูล For You ที่มีอยู่แล้ว
                            postAdapter.notifyDataSetChanged()
                            Log.d("HomeFragment", "Displaying cached For You data. Size: ${forYouData.size}")
                        }
                        if (forYouData.isEmpty()) { // ถ้ายังไม่มีข้อมูลใน For You
                            fetchForYouPosts(false) // โหลดครั้งแรก ไม่ต้องบังคับรีเฟรช
                        }
                        // ซ่อน/แสดง ข้อความ no_following_posts ตามความเหมาะสม
                        noFollowingPostsTextView.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                    }
                    1 -> { // Following tab selected
                        if (followingData.isEmpty() || followingData.size != postList.size || !followingData.containsAll(postList)) { // ตรวจสอบว่าข้อมูลใน followingData ไม่ตรงกับที่กำลังแสดงหรือไม่
                            postList.clear()
                            postList.addAll(followingData) // โหลดข้อมูล Following ที่มีอยู่แล้ว
                            postAdapter.notifyDataSetChanged()
                            Log.d("HomeFragment", "Displaying cached Following data. Size: ${followingData.size}")
                        }
                        if (followingData.isEmpty()) { // ถ้ายังไม่มีข้อมูลใน Following
                            fetchFollowingPosts(false) // โหลดครั้งแรก ไม่ต้องบังคับรีเฟรช
                        }
                        // ซ่อน/แสดง ข้อความ no_following_posts ตามความเหมาะสม
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

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                // เก็บข้อมูลปัจจุบันก่อนเปลี่ยนแท็บ
                // ไม่ต้องทำอะไรตรงนี้ เพราะข้อมูลจะถูก update ใน onTabSelected ตอนที่กลับมา
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                // กรณี: กดแท็บซ้ำ (Double-tap tab)
                when (tab?.position) {
                    0 -> { // แท็บ For You กดซ้ำ ให้รีเฟรช
                        Log.d("HomeFragment", "For You tab reselected. Forcing refresh.")
                        refreshPosts(forceRefreshForYou = true)
                    }
                    1 -> { // แท็บ Following กดซ้ำ ให้รีเฟรช
                        Log.d("HomeFragment", "Following tab reselected. Forcing refresh.")
                        refreshPosts(forceRefreshFollowing = true)
                    }
                }
            }
        })

        // Default: Load data for the initial tab (For You is the default tab 0)
        // โหลดข้อมูล For You ครั้งแรก เมื่อ Fragment ถูกสร้างขึ้น
        if (forYouData.isEmpty() && !isForYouLoading) { // ตรวจสอบว่ายังไม่มีข้อมูลและไม่ได้กำลังโหลด
            Log.d("HomeFragment", "Initial load of For You posts.")
            fetchForYouPosts(false) // โหลดครั้งแรก ไม่ต้องบังคับรีเฟรช Server cache
        }


        // Set up menu listener
        val menuImageView = view.findViewById<ImageView>(R.id.menuImageView)
        menuImageView.setOnClickListener {
            val popupMenu = PopupMenu(ContextThemeWrapper(requireContext(), R.style.CustomPopupMenuHomepage), menuImageView)
            popupMenu.menuInflater.inflate(R.menu.navbar_home, popupMenu.menu)
            popupMenu.menu.findItem(R.id.deleteAccount).isVisible = false // ตั้งค่าเป็น false ตามคำแนะนำเดิม
            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.logout -> {
                        performLogout()
                        true
                    }
                    else -> false
                }
            }
            popupMenu.show()
        }

        // Pull-to-refresh functionality
        swipeRefreshLayout.setOnRefreshListener {
            val selectedTab = tabLayout.selectedTabPosition
            if (selectedTab == 0) {
                // กรณี: Pull-to-refresh บนแท็บ For You
                Log.d("HomeFragment", "Pull-to-refresh on For You. Forcing refresh.")
                refreshPosts(forceRefreshForYou = true)
            } else {
                // กรณี: Pull-to-refresh บนแท็บ Following
                Log.d("HomeFragment", "Pull-to-refresh on Following. Forcing refresh.")
                refreshPosts(forceRefreshFollowing = true)
            }
        }
    }

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

    // ฟังก์ชันนี้จะถูกเรียกเมื่อต้องการรีเฟรชโพสต์ (จาก MainActivity หรือ Pull-to-refresh หรือ Double-tap tab)
    // เพิ่ม parameter เพื่อระบุว่าต้องการบังคับรีเฟรชแท็บไหน
    fun refreshPosts(forceRefreshForYou: Boolean = false, forceRefreshFollowing: Boolean = false) {
        // เลื่อน RecyclerView ไปบนสุดเมื่อมีการรีเฟรช
        recyclerView.smoothScrollToPosition(0)

        val selectedTab = tabLayout.selectedTabPosition
        if (selectedTab == 0) {
            // กรณีรีเฟรชแท็บ For You
            fetchForYouPosts(forceRefreshForYou) // ส่งค่า forceRefresh เข้าไป
        } else {
            // กรณีรีเฟรชแท็บ Following
            fetchFollowingPosts(forceRefreshFollowing) // ส่งค่า forceRefresh เข้าไป
        }
    }

    // เปลี่ยน fetchForYouPosts ให้รับ parameter forceRefresh
    private fun fetchForYouPosts(forceRefresh: Boolean = false) {
        if (isForYouLoading) {
            Log.d("HomeFragment", "For You data already loading, skipping.")
            return
        }
        isForYouLoading = true
        swipeRefreshLayout.isRefreshing = true // แสดง loading spinner
        noFollowingPostsTextView.visibility = View.GONE // ซ่อนเมื่อเริ่มโหลด
        recyclerView.visibility = View.VISIBLE // แสดง RecyclerView เผื่อว่าถูกซ่อนอยู่

        val sharedPreferences = context?.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences?.getString("TOKEN", null) ?: run {
            Log.e("HomeFragment", "Token not found for For You posts")
            isForYouLoading = false
            swipeRefreshLayout.isRefreshing = false
            return
        }

        // สร้าง URL พร้อมเพิ่ม Query Parameter "refresh=true" ถ้า forceRefresh เป็น true
        val baseUrl = getString(R.string.root_url2) + "/ai" + "/recommend"
        val url = if (forceRefresh) {
            Log.d("HomeFragment", "Fetching For You posts with forced refresh from Backend.")
            "$baseUrl?refresh=true"
        } else {
            Log.d("HomeFragment", "Fetching For You posts without forced refresh (using server cache if available).")
            baseUrl
        }

        val requestBody = FormBody.Builder().build()
        val request = Request.Builder()
            .url(url)
            .post(requestBody) // ใช้ POST method
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (isAdded) {
                    requireActivity().runOnUiThread {
                        swipeRefreshLayout.isRefreshing = false
                        isForYouLoading = false
                        Toast.makeText(requireContext(), "ไม่สามารถโหลดโพสต์แนะนำได้: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    if (isAdded) {
                        requireActivity().runOnUiThread {
                            swipeRefreshLayout.isRefreshing = false
                            isForYouLoading = false
                            Toast.makeText(requireContext(), "เกิดข้อผิดพลาดในการโหลดโพสต์แนะนำ: ${response.code}", Toast.LENGTH_SHORT).show()
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

                        // Fetch ads concurrently if possible or chain them
                        fetchRandomAds { ads ->
                            if (isAdded) {
                                requireActivity().runOnUiThread {
                                    val randomSize = (10..15).random() // ขนาดโฆษณาที่ต้องการ
                                    val randomAds = ads.shuffled().take(randomSize) // สุ่มโฆษณา
                                    val mixedList = insertAds(posts, randomAds, randomSize / 2) // ผสมโพสต์กับโฆษณา

                                    forYouData.clear() // เคลียร์ข้อมูลเก่าของ For You
                                    forYouData.addAll(mixedList) // เพิ่มข้อมูลใหม่

                                    // อัปเดต RecyclerView เฉพาะเมื่อแท็บ For You กำลัง Active
                                    if (tabLayout.selectedTabPosition == 0) {
                                        postList.clear()
                                        postList.addAll(forYouData)
                                        postAdapter.notifyDataSetChanged()
                                        Log.d("HomeFragment", "For You posts updated in RecyclerView. Total: ${postList.size}")
                                    } else {
                                        Log.d("HomeFragment", "For You posts loaded but not displayed (tab not active). Total: ${forYouData.size}")
                                    }

                                    if (forYouData.isEmpty()) {
                                        noFollowingPostsTextView.visibility = View.VISIBLE
                                        recyclerView.visibility = View.GONE
                                    } else {
                                        noFollowingPostsTextView.visibility = View.GONE
                                        recyclerView.visibility = View.VISIBLE
                                    }
                                    swipeRefreshLayout.isRefreshing = false
                                    isForYouLoading = false // เสร็จสิ้นการโหลด
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("HomeFragment", "Error parsing For You posts: ${e.message}", e)
                        if (isAdded) {
                            requireActivity().runOnUiThread {
                                swipeRefreshLayout.isRefreshing = false
                                isForYouLoading = false
                                Toast.makeText(requireContext(), "ข้อมูลโพสต์แนะนำไม่ถูกต้อง", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } ?: run {
                    if (isAdded) {
                        requireActivity().runOnUiThread {
                            swipeRefreshLayout.isRefreshing = false
                            isForYouLoading = false
                        }
                    }
                }
            }
        })
    }

    // เปลี่ยน fetchFollowingPosts ให้รับ parameter forceRefresh (เผื่อในอนาคต API นี้รองรับ)
    private fun fetchFollowingPosts(forceRefresh: Boolean = false) {
        if (isFollowingLoading) {
            Log.d("HomeFragment", "Following data already loading, skipping.")
            return
        }
        isFollowingLoading = true
        swipeRefreshLayout.isRefreshing = true // แสดง loading spinner
        noFollowingPostsTextView.visibility = View.GONE // ซ่อนเมื่อเริ่มโหลด
        recyclerView.visibility = View.VISIBLE // แสดง RecyclerView เผื่อว่าถูกซ่อนอยู่

        val sharedPreferences = context?.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences?.getString("TOKEN", null) ?: run {
            Log.e("HomeFragment", "Token not found for Following posts")
            isFollowingLoading = false
            swipeRefreshLayout.isRefreshing = false
            return
        }

        // URL สำหรับ Following ไม่ได้มี cache logic ที่ Server (ตามที่คุยกัน) จึงไม่ต้องเพิ่ม parameter refresh
        // และ API นี้ใช้ GET request
        val url = getString(R.string.root_url) + "/api/following/posts"
        Log.d("HomeFragment", "Fetching Following posts. URL: $url")


        val request = Request.Builder()
            .url(url)
            .get() // ใช้ GET method
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (isAdded) {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "ไม่สามารถโหลดโพสต์จากผู้ติดตามได้: ${e.message}", Toast.LENGTH_SHORT).show()
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
                            Toast.makeText(requireContext(), "เกิดข้อผิดพลาดในการโหลดโพสต์จากผู้ติดตาม: ${response.code}", Toast.LENGTH_SHORT).show()
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
                                followingData.clear() // เคลียร์ข้อมูลเก่าของ Following
                                followingData.addAll(posts) // เพิ่มข้อมูลใหม่

                                // อัปเดต RecyclerView เฉพาะเมื่อแท็บ Following กำลัง Active
                                if (tabLayout.selectedTabPosition == 1) {
                                    postList.clear()
                                    postList.addAll(followingData)
                                    postAdapter.notifyDataSetChanged()
                                    Log.d("HomeFragment", "Following posts updated in RecyclerView. Total: ${postList.size}")
                                } else {
                                    Log.d("HomeFragment", "Following posts loaded but not displayed (tab not active). Total: ${followingData.size}")
                                }


                                if (followingData.isEmpty()) {
                                    recyclerView.visibility = View.GONE
                                    noFollowingPostsTextView.visibility = View.VISIBLE
                                } else {
                                    recyclerView.visibility = View.VISIBLE
                                    noFollowingPostsTextView.visibility = View.GONE
                                }
                                swipeRefreshLayout.isRefreshing = false
                                isFollowingLoading = false // เสร็จสิ้นการโหลด
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("HomeFragment", "Error parsing Following posts: ${e.message}", e)
                        if (isAdded) {
                            requireActivity().runOnUiThread {
                                swipeRefreshLayout.isRefreshing = false
                                isFollowingLoading = false
                                Toast.makeText(requireContext(), "ข้อมูลโพสต์จากผู้ติดตามไม่ถูกต้อง", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } ?: run {
                    if (isAdded) {
                        requireActivity().runOnUiThread {
                            swipeRefreshLayout.isRefreshing = false
                            isFollowingLoading = false
                        }
                    }
                }
            }
        })
    }

    private fun fetchRandomAds(callback: (List<PostAdapter.Ad>) -> Unit) {
        val url = getString(R.string.root_url) + "/api/ads/random"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (isAdded) callback(emptyList())
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful || !isAdded) {
                    callback(emptyList())
                    return
                }

                response.body?.string()?.let { jsonResponse ->
                    try {
                        val gson = Gson()
                        val adType = object : TypeToken<List<PostAdapter.Ad>>() {}.type
                        val ads: List<PostAdapter.Ad> = gson.fromJson(jsonResponse, adType)
                        callback(ads)
                    } catch (e: Exception) {
                        Log.e("HomeFragment", "Error parsing ads: ${e.message}", e)
                        callback(emptyList())
                    }
                } ?: callback(emptyList())
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

    override fun onDestroyView() {
        super.onDestroyView()
        // ควรยกเลิกการลงทะเบียน listener หรือ binding ที่นี่
        // เช่น binding = null ถ้าใช้ view binding
        // และลบ listener ออกจาก tabLayout ถ้ามีการเพิ่ม addOnTabSelectedListener
        tabLayout.clearOnTabSelectedListeners() // ลบ listener ที่เพิ่มไว้
    }
}