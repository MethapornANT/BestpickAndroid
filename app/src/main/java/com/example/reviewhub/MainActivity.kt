package com.example.reviewhub

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()  // Enables full-screen mode
        setContentView(R.layout.activity_main)  // Sets the layout resource for this activity

        // Handle window insets to avoid overlapping with system bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Set up the logout button click listener
        val logoutButton = findViewById<Button>(R.id.logout)
        logoutButton.setOnClickListener {
            performLogout()
        }
    }

    private fun performLogout() {
        // Sign out from Firebase Authentication
        val firebaseAuth = FirebaseAuth.getInstance()
        firebaseAuth.signOut()

        // Clear shared preferences or any other local data
        clearLocalData()

        // Redirect to the login screen
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()  // Optionally close the current activity
    }

    private fun clearLocalData() {
        
        val sharedPreferences: SharedPreferences = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        sharedPreferences.edit().clear().apply()


        val tokenPrefs: SharedPreferences = getSharedPreferences("TokenPrefs", MODE_PRIVATE)
        tokenPrefs.edit().remove("TOKEN").apply()


    }
}
