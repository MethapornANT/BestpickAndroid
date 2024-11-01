package com.bestpick.reviewhub

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.airbnb.lottie.LottieAnimationView
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
    private lateinit var progressBar: LottieAnimationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_change_password)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        progressBar = findViewById(R.id.lottie_loading)
        val passwordEditText = findViewById<EditText>(R.id.password)
        val confirmPasswordEditText = findViewById<EditText>(R.id.conpassword)
        val button = findViewById<Button>(R.id.confirm)
        val togglePassword1 = findViewById<ImageView>(R.id.togglePasswordConfirm1_re)
        val togglePassword2 = findViewById<ImageView>(R.id.togglePasswordConfirm2_re)
        // Toggle visibility for password field
        togglePassword1.setOnClickListener {
            if (passwordEditText.inputType == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                // Hide password
                passwordEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                togglePassword1.setImageResource(R.drawable.eye_hide)
            } else {
                // Show password
                passwordEditText.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                togglePassword1.setImageResource(R.drawable.eye_open)
            }
            // Move the cursor to the end of the text
            progressBar.visibility = View.VISIBLE
            passwordEditText.setSelection(passwordEditText.text.length)
        }

        // Toggle visibility for confirm password field
        togglePassword2.setOnClickListener {
            if (confirmPasswordEditText.inputType == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                // Hide confirm password
                confirmPasswordEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                togglePassword2.setImageResource(R.drawable.eye_hide)
            } else {
                // Show confirm password
                confirmPasswordEditText.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                togglePassword2.setImageResource(R.drawable.eye_open)
            }
            // Move the cursor to the end of the text
            confirmPasswordEditText.setSelection(confirmPasswordEditText.text.length)
        }
        val email = intent.getStringExtra("email") ?: run {
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Email Not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        button.setOnClickListener {
            val password = passwordEditText.text.toString()
            val confirmPassword = confirmPasswordEditText.text.toString()

            if (password != confirmPassword) {
                progressBar.visibility = View.GONE
                confirmPasswordEditText.error = "Passwords do not match"
            } else if (password.isEmpty()) {
                progressBar.visibility = View.GONE
                passwordEditText.error = "Password cannot be empty"
                confirmPasswordEditText.error = "Confirm password cannot be empty"
            } else {
                performFrogetpass(email, password)
            }
        }
    }
    private fun performFrogetpass(email: String, password: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val url = getString(R.string.root_url) + getString(R.string.resetpassword)
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
                    progressBar.visibility = View.GONE
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
                        progressBar.visibility = View.GONE
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
                progressBar.visibility = View.GONE
            }
        } catch (e: JSONException) {
            progressBar.visibility = View.GONE

        }
    }
}