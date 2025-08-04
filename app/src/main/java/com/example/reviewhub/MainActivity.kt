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

    private var currentHomeTabPosition: Int = 0

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNavigationIntent(intent)
        handleDeepLink(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("APP_LIFECYCLE", "MainActivity: onCreate - START") // <-- เพิ่ม Log
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
                R.id.homeFragment, R.id.searchFragment, R.id.profileFragment, R.id.notificationsFragment, R.id.addPostFragment -> {
                    bottomNavigationView.visibility = View.VISIBLE
                }
                else -> {
                    bottomNavigationView.visibility = View.GONE
                }
            }
        }

        bottomNavigationView.setOnItemSelectedListener { item ->
            val currentTime = System.currentTimeMillis()

            val isCurrentlyOnHomeFragment = navController.currentDestination?.id == R.id.homeFragment

            if (item.itemId == R.id.home && lastClickedItemId == item.itemId && (currentTime - lastClickedTime) < 500) {
                if (isCurrentlyOnHomeFragment) {
                    Log.d("MainActivity", "Double-tap Home detected. Forcing refresh on current HomeFragment tab.")
                    refreshHomeFragment(forceRefreshFromBottomNavDoubleTap = true)
                }
            } else {
                when (item.itemId) {
                    R.id.home -> navController.navigate(R.id.homeFragment)
                    R.id.search -> navController.navigate(R.id.searchFragment)
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
        Log.d("APP_LIFECYCLE", "MainActivity: onCreate - END") // <-- เพิ่ม Log
    }

    override fun onStart() {
        super.onStart()
        Log.d("APP_LIFECYCLE", "MainActivity: onStart") // <-- เพิ่ม Log
    }

    override fun onResume() {
        super.onResume()
        Log.d("APP_LIFECYCLE", "MainActivity: onResume") // <-- เพิ่ม Log
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