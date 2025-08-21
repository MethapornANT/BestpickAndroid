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
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var navController: NavController

    // เก็บ timestamp ของการ "reselect" ล่าสุดต่อ menu item (ใช้จับ double-reselect)
    private val lastReselectedTs = mutableMapOf<Int, Long>()
    private val RESELECTION_DOUBLE_TAP_MS = 500L // ปรับถ้าอยากให้เร็ว/ช้ากว่านี้

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

        // ------------------ selection handling (ปกติ) ------------------
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.home -> {
                    // ถ้าไม่อยู่บน Home ให้ navigate ไป
                    if (navController.currentDestination?.id != R.id.homeFragment) {
                        navController.navigate(R.id.homeFragment)
                    } else {
                        // ถ้าอยู่แล้ว ให้เลื่อนขึ้นบนสุด (แต่ไม่รีเฟรช)
                        val nhf = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                        nhf?.childFragmentManager?.fragments?.forEach { f ->
                            if (f is HomeFragment) {
                                f.view?.findViewById<RecyclerView>(R.id.recycler_view_posts)?.smoothScrollToPosition(0)
                            }
                        }
                    }
                }
                R.id.search -> {
                    if (navController.currentDestination?.id != R.id.searchFragment) {
                        navController.navigate(R.id.searchFragment)
                    }
                }
                R.id.profile -> {
                    if (navController.currentDestination?.id != R.id.profileFragment) {
                        navController.navigate(R.id.profileFragment)
                    }
                }
                R.id.add -> {
                    if (navController.currentDestination?.id != R.id.addPostFragment) {
                        navController.navigate(R.id.addPostFragment)
                    }
                }
                R.id.notification -> {
                    if (navController.currentDestination?.id != R.id.notificationsFragment) {
                        navController.navigate(R.id.notificationsFragment)
                    }
                }
                else -> {
                    try {
                        navController.navigate(item.itemId)
                    } catch (e: Exception) {
                        Log.w("MainActivity", "Navigation fallback failed: ${e.message}")
                    }
                }
            }
            true
        }

        // ------------------ reselection handling (สำคัญ) ------------------
        // กดที่ไอเท็มที่ถูกเลือกอยู่แล้วจะมาเข้า listener นี้
        // เราจะใช้ logic นี้:
        // - ถ้า reselect ครั้งแรก (หรือช้าเกิน threshold) -> scroll-to-top (ไม่รีเฟรช)
        // - ถ้า reselect สองครั้งภายใน RESELECTION_DOUBLE_TAP_MS -> บังคับรีเฟรช
        bottomNavigationView.setOnItemReselectedListener { item ->
            val now = System.currentTimeMillis()
            val last = lastReselectedTs[item.itemId] ?: 0L
            val isDouble = (now - last) <= RESELECTION_DOUBLE_TAP_MS

            if (item.itemId == R.id.home) {
                val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                navHost?.childFragmentManager?.fragments?.forEach { fragment ->
                    if (fragment is HomeFragment) {
                        if (isDouble) {
                            // Double-reselect -> รีเฟรชแท็บปัจจุบัน
                            val selectedTabPos = fragment.view?.findViewById<TabLayout>(R.id.tab_layout)?.selectedTabPosition ?: 0
                            when (selectedTabPos) {
                                0 -> fragment.refreshPosts(forceRefreshForYou = true)
                                1 -> fragment.refreshPosts(forceRefreshFollowing = true)
                                else -> fragment.refreshPosts(forceRefreshForYou = true)
                            }
                            // feedback เล็กน้อย: scroll-top ด้วย (ไม่จำเป็นแต่ UX ดี)
                            fragment.view?.findViewById<RecyclerView>(R.id.recycler_view_posts)?.smoothScrollToPosition(0)
                        } else {
                            // Single reselect -> scroll to top only (no refresh)
                            fragment.view?.findViewById<RecyclerView>(R.id.recycler_view_posts)?.smoothScrollToPosition(0)
                        }
                    }
                }
            } else {
                // กรณี reselection สำหรับ tab อื่น: ให้ scroll-to-top เป็น default
                val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                navHost?.childFragmentManager?.fragments?.forEach { fragment ->
                    fragment.view?.findViewById<RecyclerView>(R.id.recycler_view_posts)?.smoothScrollToPosition(0)
                }
            }

            // update timestamp
            lastReselectedTs[item.itemId] = now
        }

        // -------------------------------------------------------------------

        handleNavigationIntent(intent)
        handleDeepLink(intent)
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
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
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
