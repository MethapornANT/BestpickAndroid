package com.example.reviewhub

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    // เก็บ NavController เพื่อควบคุมการนำทาง
    private lateinit var navController: NavController

    // เก็บ ID ของเมนูที่ถูกคลิกครั้งล่าสุด
    private var lastClickedItemId = -1
    private var lastClickedTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // จัดการการแสดงผลเต็มจอด้วย WindowInsetsCompat
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // หา NavHostFragment จาก layout และตั้งค่า NavController
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // เชื่อมต่อ BottomNavigationView กับ NavController
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigationView.setupWithNavController(navController)

        // ฟังการเปลี่ยนแปลงเส้นทางการนำทางเพื่อแสดงหรือซ่อน BottomNavigationView
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.homeFragment, R.id.searchFragment, R.id.profileFragment, R.id.addPostFragment -> {
                    // แสดง BottomNavigationView ในหน้า Home, Search, Profile และ Add Post
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
                    R.id.add -> navController.navigate(R.id.addPostFragment) // เพิ่มการนำทางไปยัง Add Post
                }
            }

            lastClickedItemId = item.itemId
            lastClickedTime = currentTime

            true
        }
    }

    private fun refreshHomeFragment() {
        // ดึง HomeFragment จาก NavHostFragment มาเพื่อทำการ refresh
        val fragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
        fragment?.childFragmentManager?.fragments?.forEach {
            if (it is HomeFragment) {
                it.refreshPosts()
            }
        }
    }
}
