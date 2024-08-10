package com.example.reviewhub

import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.os.StrictMode
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.concurrent.thread

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread.sleep(2000)
        installSplashScreen()
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


    }

    fun Forgetpass(view: View) {
        // Intent to navigate to the new page
        val intent = Intent(this, Forget_Password_Activity::class.java)
        startActivity(intent)
    }
    fun onclickRegister(view: View) {
        // Intent to navigate to the new page
        val intent = Intent(this, RegisterActivity::class.java)
        startActivity(intent)
    }
}


