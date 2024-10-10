package com.example.reviewhub

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        enableEdgeToEdge()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // หา NavHostFragment จาก layout และตั้งค่า NavController
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // กำหนดค่าให้กับตัวแปร bottomNavigationView โดยใช้ตัวแปร lateinit ที่ประกาศไว้ด้านบน
        bottomNavigationView = findViewById(R.id.bottom_navigation)

        // เชื่อมต่อ BottomNavigationView กับ NavController
        bottomNavigationView.setupWithNavController(navController)

        fetchAndShowBadge()

        // ฟังการเปลี่ยนแปลงเส้นทางการนำทางเพื่อแสดงหรือซ่อน BottomNavigationView
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.homeFragment, R.id.searchFragment, R.id.profileFragment, R.id.notificationsFragment -> {
                    // แสดง BottomNavigationView ในหน้า Home, Search, Profile, Notifications
                    bottomNavigationView.visibility = View.VISIBLE
                }
                else -> {
                    // ซ่อน BottomNavigationView ใน Fragment อื่นๆ เช่น DetailFragment
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
                // ใช้ NavController เพื่อเปลี่ยนเส้นทางไปยัง Fragment อื่น
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
                    val notificationList: List<Notification> = Gson().fromJson(
                        jsonResponse,
                        object : TypeToken<List<Notification>>() {}.type
                    )

                    val distinctNotifications = notificationList.distinctBy { it.id }
                    val unreadCount = distinctNotifications.count { it.read_status == 0 }

                    runOnUiThread {
                        updateBadge(unreadCount)
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
