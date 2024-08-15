package com.example.reviewhub

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONException
import org.json.JSONObject

class Change_PasswordActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_change_password)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val passwordEditText = findViewById<EditText>(R.id.password)
        val confirmPasswordEditText = findViewById<EditText>(R.id.conpassword)
        val button = findViewById<Button>(R.id.confirm)
        val email = intent.getStringExtra("email") ?: run {
            Toast.makeText(this, "No email found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        button.setOnClickListener {
            val password = passwordEditText.text.toString()
            val confirmPassword = confirmPasswordEditText.text.toString()

            if (password != confirmPassword) {
                confirmPasswordEditText.error = "Passwords do not match"
            } else if (password.isEmpty()) {
                passwordEditText.error = "Password cannot be empty"
                confirmPasswordEditText.error = "Confirm password cannot be empty"
            } else {
                performFrogetpass(email, password)
            }
        }
    }
    private fun performFrogetpass(email: String, password: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val url = "http://192.168.194.49:3000/reset-password"
            val okHttpClient = OkHttpClient()
            val formBody: RequestBody = FormBody.Builder()
                .add("email", email)
                .add("newPassword", password)
                .build()
            val request: Request = Request.Builder()
                .url(url)
                .post(formBody)
                .build()

            try {
                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                Log.d("ResponseBody", responseBody) // Debugging line

                withContext(Dispatchers.Main) {
                    handleForgetResponse(response, responseBody)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun handleForgetResponse(response: okhttp3.Response, responseBody: String) {
        try {
            if (response.isSuccessful) {
                val obj = JSONObject(responseBody)
                val message = obj.optString("message", "")

                when {
                    message.contains("Password has been updated successfully") -> {
                        val intent = Intent(this, LoginActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                    else -> {
                        Toast.makeText(applicationContext, "Unknown response: $message", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                // Parse the responseBody for error message
                val errorMessage = try {
                    val errorObj = JSONObject(responseBody)
                    errorObj.optString("error", "Unknown error")
                } catch (e: JSONException) {
                    "Unknown error"
                }

                Toast.makeText(applicationContext, "Response: $errorMessage", Toast.LENGTH_LONG).show()
            }
        } catch (e: JSONException) {
            Toast.makeText(applicationContext, "Error parsing response: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}