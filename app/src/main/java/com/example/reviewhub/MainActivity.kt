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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ปิดโหมดกลางคืน
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // หา NavHostFragment จาก layout และตั้งค่า NavController
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // กำหนดค่าให้กับตัวแปร bottomNavigationView โดยใช้ตัวแปร lateinit ที่ประกาศไว้ด้านบน
        bottomNavigationView = findViewById(R.id.bottom_navigation)

        // เชื่อมต่อ BottomNavigationView กับ NavController
        bottomNavigationView.setupWithNavController(navController)

        // ใช้ WindowInsets เพื่อตรวจจับแถบนำทางและปรับตำแหน่งของ BottomNavigationView
        ViewCompat.setOnApplyWindowInsetsListener(bottomNavigationView) { view, insets ->
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, 0, 0, systemBarsInsets.bottom)
            insets
        }

        fetchAndShowBadge()

        // ฟังการเปลี่ยนแปลงเส้นทางการนำทางเพื่อแสดงหรือซ่อน BottomNavigationView
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.homeFragment, R.id.searchFragment, R.id.profileFragment, R.id.notificationsFragment -> {
                    bottomNavigationView.visibility = View.VISIBLE
                }
                else -> {
                    bottomNavigationView.visibility = View.GONE
                }
            }
        }

        // ตั้งค่า listener ให้กับ BottomNavigationView เพื่อเช็คการคลิกซ้ำสองครั้งที่เมนู Home
        bottomNavigationView.setOnItemSelectedListener { item ->
            val currentTime = System.currentTimeMillis()

            if (item.itemId == R.id.home && lastClickedItemId == item.itemId && (currentTime - lastClickedTime) < 500) {
                // ตรวจสอบว่าคลิกเมนู Home ซ้ำภายใน 500 มิลลิวินาที ให้ทำการ refresh HomeFragment
                refreshHomeFragment()
            } else {
                when (item.itemId) {
                    R.id.home -> navController.navigate(R.id.homeFragment)
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

    private fun refreshHomeFragment() {
        val fragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
        fragment?.childFragmentManager?.fragments?.forEach {
            if (it is HomeFragment) {
                it.refreshPosts()
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
