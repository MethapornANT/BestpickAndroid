package com.example.reviewhub

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.facebook.login.Login

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // เข้าถึง SharedPreferences เพื่อตรวจสอบว่าผู้ใช้เคยเปิดแอปหรือไม่
        val sharedPreferences: SharedPreferences = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val isFirstTime = sharedPreferences.getBoolean("isFirstTime", true)

        if (isFirstTime) {
            // หากเป็นการเปิดแอปครั้งแรก
            setContentView(R.layout.activity_splash)

            // หน่วงเวลาเล็กน้อยก่อนเปลี่ยนไปยัง MainActivity
            Handler(Looper.getMainLooper()).postDelayed({
                // เปลี่ยนไปยัง MainActivity
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
                finish()


                sharedPreferences.edit().putBoolean("isFirstTime", false).apply()
            }, 3000)

        } else {
            // หากไม่ใช่การเปิดครั้งแรก ให้ข้าม Splash Screen ไปยัง MainActivity ทันที
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
