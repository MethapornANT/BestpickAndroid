// MainActivity.kt

package com.bestpick.reviewhub

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.tabs.TabLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var navController: NavController
    private var lastClickedItemId = -1
    private var lastClickedTime: Long = 0

    // เพิ่มตัวแปรเพื่อเก็บตำแหน่งแท็บปัจจุบันของ HomeFragment (0 = For You, 1 = Following)
    private var currentHomeTabPosition: Int = 0

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNavigationIntent(intent)
        handleDeepLink(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        bottomNavigationView = findViewById(R.id.bottom_navigation)
        bottomNavigationView.setupWithNavController(navController)

        ViewCompat.setOnApplyWindowInsetsListener(bottomNavigationView) { view, insets ->
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, 0, 0, systemBarsInsets.bottom)
            insets
        }

        fetchAndShowBadge()

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.homeFragment, R.id.messageFragment, R.id.profileFragment, R.id.notificationsFragment, R.id.addPostFragment -> {
                    bottomNavigationView.visibility = View.VISIBLE
                }
                else -> {
                    bottomNavigationView.visibility = View.GONE
                }
            }
        }

        bottomNavigationView.setOnItemSelectedListener { item ->
            val currentTime = System.currentTimeMillis()

            // ตรวจสอบว่ากำลังอยู่บน HomeFragment หรือไม่
            val isCurrentlyOnHomeFragment = navController.currentDestination?.id == R.id.homeFragment

            // กรณี Double-tap Home (ปุ่มล่าง)
            if (item.itemId == R.id.home && lastClickedItemId == item.itemId && (currentTime - lastClickedTime) < 500) {
                if (isCurrentlyOnHomeFragment) {
                    Log.d("MainActivity", "Double-tap Home detected. Forcing refresh on current HomeFragment tab.")
                    // เมื่อ Double-tap Home ให้บังคับ refresh เฉพาะแท็บปัจจุบันใน HomeFragment
                    refreshHomeFragment(forceRefreshFromBottomNavDoubleTap = true)
                }
            } else {
                // กรณีเลือกเมนูที่ไม่ใช่ Home หรือเลือก Home ครั้งแรก
                when (item.itemId) {
                    R.id.home -> {
                        // ถ้ากำลังจะ navigate ไป HomeFragment และไม่ได้เป็นการ Double-tap
                        // ให้ NavController จัดการ ถ้า HomeFragment อยู่ใน Back Stack แล้ว
                        // จะไม่สร้าง instance ใหม่ (ซึ่งทำให้ isForYouDataLoaded/isFollowingDataLoaded ยังคงค่าเดิม)
                        // HomeFragment จะตรวจสอบว่าต้องโหลดข้อมูลใหม่หรือไม่เอง
                        navController.navigate(R.id.homeFragment)
                    }
                    R.id.messages -> navController.navigate(R.id.messageFragment)
                    R.id.profile -> navController.navigate(R.id.profileFragment)
                    R.id.add -> navController.navigate(R.id.addPostFragment)
                    R.id.notification -> navController.navigate(R.id.notificationsFragment)
                }
            }

            lastClickedItemId = item.itemId
            lastClickedTime = currentTime

            true
        }

        handleNavigationIntent(intent)
    }

    private fun handleDeepLink(intent: Intent) {
        val data = intent.data
        Log.d("DeepLink", "Received deep link data: $data")
        data?.let {
            val postId = it.lastPathSegment?.toIntOrNull()
            if (postId != null) {
                Log.d("DeepLink", "Navigating to post with ID: $postId")
                val bundle = Bundle().apply {
                    putInt("POST_ID", postId)
                }
                navController.navigate(R.id.postDetailFragment, bundle)
            } else {
                Log.e("DeepLink", "Invalid post ID in deep link")
            }
        }
    }

    private fun handleNavigationIntent(intent: Intent?) {
        intent?.let {
            if (it.hasExtra("NAVIGATE_TO_USER_PROFILE_ID")) {
                val userIDToNavigate = it.getIntExtra("NAVIGATE_TO_USER_PROFILE_ID", -1)
                if (userIDToNavigate != -1) {
                    Log.d("MainActivity", "Navigating to AnotherUserFragment for userID: $userIDToNavigate")
                    val bundle = Bundle().apply {
                        putInt("USER_ID", userIDToNavigate)
                    }
                    navController.navigate(R.id.AnotherUserFragment, bundle)
                    it.removeExtra("NAVIGATE_TO_USER_PROFILE_ID")
                }
            }
        }
    }

    private fun refreshHomeFragment(forceRefreshFromBottomNavDoubleTap: Boolean) {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
        navHostFragment?.childFragmentManager?.fragments?.forEach { fragment ->
            if (fragment is HomeFragment) {
                Log.d("MainActivity", "Attempting to refresh HomeFragment. forceRefreshFromBottomNavDoubleTap: $forceRefreshFromBottomNavDoubleTap")

                // เรียก refreshPosts ของ HomeFragment โดยตรง
                // HomeFragment จะมี Logic ตัดสินใจเองว่าจะเรียก API แบบ ?refresh=true หรือไม่
                // โดยอิงจาก forceRefreshFromBottomNavDoubleTap และสถานะ isLoaded ของตัวเอง
                fragment.refreshPosts(forceRefreshFromBottomNavDoubleTap)
            }
        }
    }

    private fun fetchAndShowBadge() {
        val sharedPreferences = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val token = sharedPreferences.getString("TOKEN", null)

        if (token.isNullOrEmpty()) {
            Log.e("MainActivity", "Token not found")
            return
        }

        val url = getString(R.string.root_url) + "/api/notifications"
        val client = OkHttpClient()

        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { jsonResponse ->
                    try {
                        val notificationList: List<Notification> = Gson().fromJson(
                            jsonResponse,
                            object : TypeToken<List<Notification>>() {}.type
                        )

                        val distinctNotifications = notificationList.distinctBy { it.id }
                        val unreadCount = distinctNotifications.count { it.read_status == 0 }

                        runOnUiThread {
                            updateBadge(unreadCount)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Log.e("JSON Parsing", "Error parsing response: ${e.message}")
                    }
                }
            }
        })
    }

    private fun updateBadge(unreadCount: Int) {
        val badge = bottomNavigationView.getOrCreateBadge(R.id.notification)
        if (unreadCount > 0) {
            badge.isVisible = true
            badge.number = unreadCount
        } else {
            badge.isVisible = false
        }
    }
}