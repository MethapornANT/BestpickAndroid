package com.example.reviewhub

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

class CreateName_Activity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    @SuppressLint("WrongViewCast", "MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_name)

        sharedPreferences = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)

        val changeUsernameButton = findViewById<Button>(R.id.change)
        val usernameEditText = findViewById<EditText>(R.id.txtusername)
        val txterror = findViewById<TextView>(R.id.txterror) // Reference to the error field

        changeUsernameButton.setOnClickListener {
            val newUsername = usernameEditText.text.toString()

            if (newUsername.isEmpty()) {
                txterror.text = "Please enter a username"
            } else {
                setUsername(newUsername, txterror)
            }
        }
    }

    private fun setUsername(newUsername: String, txterror: TextView) { // Change to TextView
        // Get the token from SharedPreferences
        val token = sharedPreferences.getString("TOKEN", null)

        if (token == null) {
            txterror.text = "Authentication token not found"
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()

            // Create request body
            val requestBody = FormBody.Builder()
                .add("newUsername", newUsername)
                .build()

            // Create the request
            val url = getString(R.string.root_url) + getString(R.string.setusername)
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token") // Add the token in the header
                .post(requestBody)
                .build()

            try {
                val response = client.newCall(request).execute()

                // Get the full response body as a string
                val responseBody = response.body?.string()
                val jsonObject = JSONObject(responseBody)
                val message = jsonObject.optString("message", "No message")

                // Check if the response is successful
                if (response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@CreateName_Activity, "Username set successfully", Toast.LENGTH_SHORT).show()
                        val sharedPreferences = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
                        val editor = sharedPreferences.edit()
                        editor.putString("username", newUsername)
                        editor.apply()
                        val intent = Intent(this@CreateName_Activity, MainActivity::class.java)
                        startActivity(intent)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        // Display the full error response
                        txterror.text = "$message  "
                    }
                }

                response.close()
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    // Display error in the txterror TextView
                    txterror.text = "Error: ${e.message}"
                    Log.e("CreateName_Activity", "Error: ${e.message}", e)
                }
            }
        }
    }
}
