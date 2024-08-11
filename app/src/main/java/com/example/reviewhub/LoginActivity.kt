package com.example.reviewhub

import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.os.StrictMode
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.startActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
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

        val loginButton = findViewById<Button>(R.id.loginButton)
        val emailEditText = findViewById<EditText>(R.id.Email)
        val passwordEditText = findViewById<EditText>(R.id.password)
        val forgetPasswordTextView = findViewById<TextView>(R.id.forgetpass)

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()
            if (email.isEmpty()) {
                emailEditText.error = "Email is required"
            } else if (password.isEmpty()) {
                passwordEditText.error = "Password is required"
            } else {
                performLogin(email, password)
            }
        }

    }

    private fun performLogin(email: String, password: String) {
        thread {
            val url = "http://192.168.1.109:3000/login"
            val okHttpClient = OkHttpClient()
            val formBody: RequestBody = FormBody.Builder()
                .add("email", email)
                .add("password", password)
                .build()
            val request: Request = Request.Builder()
                .url(url)
                .post(formBody)
                .build()

            try {
                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                Log.d("ResponseBody", responseBody) // Debugging line

                if (response.isSuccessful) {
                    val obj = JSONObject(responseBody)
                    val message = obj.optString("message", "")

                    runOnUiThread {
                        when {
                            message.contains("Too many failed login attempts") -> {
                                Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                            }
                            message.contains("No user found") -> {
                                Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                            }
                            message.contains("Password is incorrect") -> {
                                Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                            }
                            message.contains("Authentication successful") -> {
                                val token = obj.optString("token", "")
                                val user = obj.optJSONObject("user")
                                if (token.isNotEmpty() && user != null) {
                                    val intent = Intent(this, MainActivity::class.java)
                                    intent.putExtra("TOKEN", token)
                                    intent.putExtra("USER", user.toString())
                                    startActivity(intent)
                                    finish()
                                } else {
                                    Toast.makeText(applicationContext, "Error: Missing token or user data", Toast.LENGTH_LONG).show()
                                }
                            }
                            else -> {
                                Toast.makeText(applicationContext, "Unknown response: $message", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(applicationContext, "Can't connect with server. Status code: ${response.code}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(applicationContext, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun onRegisterClick(view: View) {
        val intent = Intent(this, RegisterActivity::class.java)
        startActivity(intent)
    }

    private fun googleSignIn(token: String?) {
        token?.let {
            val url = "http://192.168.1.109:4000/google-signin"
            val client = OkHttpClient()
            val requestBody: RequestBody = FormBody.Builder()
                .add("token", it)
                .build()
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            thread {
                try {
                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        val jsonObject = JSONObject(responseBody)
                        val message = jsonObject.optString("message", "")
                        val jwtToken = jsonObject.optString("token", "")

                        runOnUiThread {
                            if (message.contains("Authentication successful")) {
                                val intent = Intent(this, MainActivity::class.java)
                                intent.putExtra("TOKEN", jwtToken)
                                startActivity(intent)
                                finish()
                            } else {
                                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this, "Server error: ${response.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }


    fun onclickRegister(view: View) {
        val intent = Intent(this, RegisterActivity::class.java)
        startActivity(intent)
    }
    fun onclickForgetpass(view: View){
        val intent = Intent(this, Forget_Password_Activity::class.java)
        startActivity(intent)
    }
}


