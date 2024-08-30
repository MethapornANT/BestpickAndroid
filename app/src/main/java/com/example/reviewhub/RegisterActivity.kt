package com.example.reviewhub

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
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

class RegisterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val emailEditText = findViewById<EditText>(R.id.registerusername)
        val create = findViewById<Button>(R.id.btnregister)
        val cooldownTime = 5000L
        create.setOnClickListener {
            val email = emailEditText.text.toString()
            if (email.isEmpty()) {
                emailEditText.error = "Email is required"
            }else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() ||
                !email.matches(Regex(".*@(?:gmail|yahoo|outlook|hotmail|icloud)\\.com$")))
            {
                emailEditText.error = "Please use a valid email from common domains like Gmail, Yahoo, Outlook, etc."
            }
            else{
                performRegister(email)
                create.isEnabled = false
                Handler(Looper.getMainLooper()).postDelayed({
                    create.isEnabled = true
                }, cooldownTime)
            }
        }


    }
    private fun performRegister(email: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val url = getString(R.string.root_url) + getString(R.string.register)
            val okHttpClient = OkHttpClient()
            val formBody: RequestBody = FormBody.Builder()
                .add("email", email)
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
                    handleCreateResponse(response, responseBody, email)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun handleCreateResponse(response: okhttp3.Response, responseBody: String, email: String) {
        try {
            if (response.isSuccessful) {
                val obj = JSONObject(responseBody)
                val message = obj.optString("message", "")

                when {
                    message.contains("OTP sent to email") -> {
                        Log.d("CreateResponse", "OTP sent to $email")
                        val intent = Intent(this, SentOTPActivity::class.java)
                        intent.putExtra("email", email)
                        startActivity(intent)
                        finish()
                    }
                    else -> {
                        Toast.makeText(applicationContext, "Response: $message", Toast.LENGTH_LONG).show()
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



    fun onclickHaveaccount(view: View) {
        // Intent to navigate to the new page
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
    }
}