@file:Suppress("DEPRECATION")

package com.example.reviewhub

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.*
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext as withContext1

class LoginActivity : AppCompatActivity() {
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        // Create GoogleSignInOptions with force reauthentication
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("741201850844-l69jb0ief835gt14ogbn9041g4b318os.apps.googleusercontent.com") // Ensure this is your Firebase Web Client ID
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val loginButton = findViewById<Button>(R.id.loginButton)
        val emailEditText = findViewById<EditText>(R.id.Email)
        val passwordEditText = findViewById<EditText>(R.id.password)

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
        CoroutineScope(Dispatchers.IO).launch {
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

                withContext1(Dispatchers.Main) {
                    handleLoginResponse(response, responseBody)
                }
            } catch (e: Exception) {
                withContext1(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun handleLoginResponse(response: okhttp3.Response, responseBody: String) {
        if (response.isSuccessful) {
            val obj = JSONObject(responseBody)
            val message = obj.optString("message", "")

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
        } else {
            Toast.makeText(applicationContext, "Can't connect with server. Status code: ${response.code}", Toast.LENGTH_LONG).show()
        }
    }

    fun onGoogleLoginClick(view: View) {
        // Sign out from Google to ensure a new login session is created
        googleSignInClient.signOut().addOnCompleteListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            val idToken = account?.idToken
            Log.d("GoogleSignIn", "ID Token: $idToken")
            if (idToken != null) {
                googleSignIn(idToken)
            } else {
                Log.e("GoogleSignIn", "No ID token")
                Toast.makeText(this, "Google Sign-In failed: No ID token", Toast.LENGTH_LONG).show()
            }
        } catch (e: ApiException) {
            Log.e("GoogleSignIn", "Google Sign-In failed", e)
            Toast.makeText(this, "Google Sign-In failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun googleSignIn(token: String?) {
        token?.let {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Get an instance of FirebaseAuth
                    val firebaseAuth = FirebaseAuth.getInstance()

                    // Sign in with the Google ID token
                    val credential = GoogleAuthProvider.getCredential(it, null)
                    val authResult = firebaseAuth.signInWithCredential(credential).await()

                    // Sign-in successful
                    val user = authResult.user
                    val userId = user?.uid ?: ""
                    val email = user?.email ?: ""
                    val name = user?.displayName ?: ""
                    val picture = user?.photoUrl?.toString() ?: ""

                    // Send user info to your API
                    val url = "http://192.168.1.109:3000/google-signin"
                    val client = OkHttpClient()
                    val requestBody: RequestBody = FormBody.Builder()
                        .add("googleId", userId)
                        .add("email", email)
                        .add("name", name)
                        .add("picture", picture)
                        .build()
                    val request = Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .build()

                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string() ?: ""

                    // Switch to the main thread to handle response
                    withContext1(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            Log.d("GoogleSignIn", "Server Response: $responseBody")
                            val jsonObject = JSONObject(responseBody)
                            val message = jsonObject.optString("message", "")
                            val jwtToken = jsonObject.optString("token", "")

                            Log.d("GoogleSignIn", "JWT Token: $jwtToken")
                            Log.d("GoogleSignIn", "User Info: User ID: $userId, Email: $email, Name: $name, Picture: $picture")

                            if (message.contains("successful")) {
                                val intent = Intent(this@LoginActivity, MainActivity::class.java)
                                intent.putExtra("TOKEN", jwtToken)
                                startActivity(intent)
                                finish()
                            } else {
                                Toast.makeText(this@LoginActivity, message, Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(this@LoginActivity, "Server error: ${response.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    // Switch to the main thread to show the error
                    withContext1(Dispatchers.Main) {
                        Log.e("GoogleSignIn", "Error: ${e.message}")
                        Toast.makeText(this@LoginActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    fun onclickRegister(view: View) {
        val intent = Intent(this, RegisterActivity::class.java)
        startActivity(intent)
    }

    fun onclickForgetpass(view: View) {
        val intent = Intent(this, Forget_Password_Activity::class.java)
        startActivity(intent)
    }
}
