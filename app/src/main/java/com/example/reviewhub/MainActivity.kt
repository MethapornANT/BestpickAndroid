package com.example.reviewhub

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    // ตัวแปรสำหรับเก็บ ID ของเมนูที่ถูกคลิกครั้งล่าสุด
    private var lastClickedItemId = -1
    private var lastClickedTime: Long = 0

    // ประกาศ Fragment แต่ละตัวเพื่อเก็บไว้
    private lateinit var homeFragment: HomeFragment
    private lateinit var searchFragment: SearchFragment
    private lateinit var profileFragment: ProfileFragment

    // เก็บ Fragment ปัจจุบันที่กำลังแสดง
    private var activeFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        enableEdgeToEdge()
        // Handle window insets to avoid overlapping with system bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        homeFragment = HomeFragment()
        searchFragment = SearchFragment()
        profileFragment = ProfileFragment()


        supportFragmentManager.beginTransaction().apply {
            add(R.id.container, homeFragment, "home").hide(homeFragment)
            add(R.id.container, searchFragment, "search").hide(searchFragment)
            add(R.id.container, profileFragment, "profile").hide(profileFragment)
        }.commit()

        // กำหนดค่าเริ่มต้นให้ HomeFragment เป็น Fragment เริ่มต้น
        supportFragmentManager.beginTransaction()
            .show(homeFragment)
            .commit()
        activeFragment = homeFragment

        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        bottomNavigationView.setOnItemSelectedListener { item ->
            val currentFragment: Fragment? = when (item.itemId) {
                R.id.home -> {
                    // ตรวจสอบการคลิกสองครั้งที่เมนู Home
                    val currentTime = System.currentTimeMillis()
                    if (lastClickedItemId == item.itemId && (currentTime - lastClickedTime) < 500) {
                        // คลิกสองครั้งในเวลา 500 มิลลิวินาที รีเฟรชข้อมูลใน HomeFragment
                        homeFragment.refreshPosts()
                    }
                    lastClickedItemId = item.itemId
                    lastClickedTime = currentTime
                    homeFragment
                }
                R.id.search -> searchFragment
                R.id.profile -> profileFragment
                else -> homeFragment
            }

            // แสดง Fragment ใหม่และซ่อน Fragment ปัจจุบัน
            if (currentFragment != null && currentFragment != activeFragment) {
                supportFragmentManager.beginTransaction().apply {
                    activeFragment?.let { hide(it) } // ซ่อน Fragment ปัจจุบัน
                    show(currentFragment) // แสดง Fragment ใหม่
                }.commit()
                activeFragment = currentFragment
            }
            true
        }
    }
}
