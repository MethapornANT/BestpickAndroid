package com.example.shopee

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.Intent
import android.os.StrictMode
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        val txtusername = findViewById<EditText>(R.id.txtusername)
        val txtpassword = findViewById<EditText>(R.id.txtpassword)
        val btnlogin = findViewById<Button>(R.id.btnlogin)
        val btnregister = findViewById<Button>(R.id.btnregister)
        btnregister.setOnClickListener{
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
        btnlogin.setOnClickListener setOnclickListener@{
            val username = txtusername.text.toString()
            val password = txtpassword.text.toString()

            if (username.isEmpty()) {
                txtusername.error = "Please enter your username"
                return@setOnclickListener
            }
            if (password.isEmpty()) {
                txtpassword.error = "Please enter your password"
                return@setOnclickListener
            }

            val url = "http://10.10.9.112:3000/login"

            val okHttpClient = OkHttpClient()
            val formBody: RequestBody = FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .build()
            val request: Request = Request.Builder()
                .url(url)
                .post(formBody)
                .build()

            try {
                val response = okHttpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val obj = JSONObject(responseBody!!)

                    if (obj.has("token")) {
                        val token = obj.getString("token")

                        // Redirect to main page
                        val intent = Intent(this, MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        val message = obj.getString("message")
                        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(applicationContext, "Cannot connect to the server", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(applicationContext, "An error occurred: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
