@file:Suppress("DEPRECATION")

package com.bestpick.reviewhub
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.facebook.CallbackManager
import com.facebook.FacebookSdk
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONException
import org.json.JSONObject
import android.widget.TextView
import com.airbnb.lottie.LottieAnimationView
import okio.IOException


class LoginActivity : AppCompatActivity() {
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>
    private lateinit var callbackManager: CallbackManager
    private lateinit var progressBar: LottieAnimationView


    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)
        progressBar = findViewById(R.id.lottie_loading)
        // Initialize Facebook SDK
        FacebookSdk.setClientToken("1021807229429436")
        FacebookSdk.sdkInitialize(applicationContext)
        callbackManager = CallbackManager.Factory.create()

        // Check if user is already signed in via SharedPreferences
        val sharedPreferences = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences.getString("TOKEN", null)
        val username = sharedPreferences.getString("USERNAME", null)

        val policyTextView: TextView = findViewById(R.id.policy)

        policyTextView.setOnClickListener {
            val dialog = Dialog(this@LoginActivity)
            dialog.setContentView(R.layout.item_policy) // ใช้ layout item_policy.xml
            dialog.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            val backButton: TextView = dialog.findViewById(R.id.back_button)
            backButton.setOnClickListener {
                dialog.dismiss()
            }
            dialog.show()
        }


        if (!token.isNullOrEmpty() && !username.isNullOrEmpty()) {
            Log.d("LoginActivity", "Token and Username found, navigating to MainActivity")
            navigateToMainActivity()
        } else {
            // Configure Google Sign-In
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
            googleSignInClient = GoogleSignIn.getClient(this, gso)

            googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                handleSignInResult(task)
            }

            // Apply window insets
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }
            // Set up views and listeners
            setupViews()


        }
    }

    private fun setupViews() {
        val loginButton = findViewById<Button>(R.id.loginButton)
        val emailEditText = findViewById<EditText>(R.id.Email)
        val passwordEditText = findViewById<EditText>(R.id.password)
        val togglePassword = findViewById<ImageView>(R.id.togglePasswordConfirm)
        progressBar = findViewById(R.id.lottie_loading) // กำหนดค่า progressBar
        progressBar.visibility = View.GONE // ตั้งค่าเริ่มต้นให้เป็น GONE

        togglePassword.setOnClickListener {
            if (passwordEditText.inputType == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                // Hide password
                passwordEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                togglePassword.setImageResource(R.drawable.eye_hide)
            } else {
                // Show password
                passwordEditText.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                togglePassword.setImageResource(R.drawable.eye_open)
            }
            // Move the cursor to the end of the text
            passwordEditText.setSelection(passwordEditText.text.length)
        }

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()
            when {
                email.isEmpty() -> emailEditText.error = "Email is required"
                password.isEmpty() -> passwordEditText.error = "Password is required"
                else -> {
                    performLogin(email, password)
                    progressBar.visibility = View.VISIBLE
                }

            }
        }

    }

    private fun navigateToMainActivity() {
        progressBar.visibility = View.GONE
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun performLogin(email: String, password: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val url = getString(R.string.root_url) + getString(R.string.Login)
            val requestBody: RequestBody = FormBody.Builder()
                .add("email", email)
                .add("password", password)
                .build()
            val request = Request.Builder().url(url).post(requestBody).build()

            try {
                val response = OkHttpClient().newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                Log.d("ResponseBody", responseBody)

                withContext(Dispatchers.Main) {
                    handleLoginResponse(response, responseBody)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(applicationContext, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    private fun handleLoginResponse(response: okhttp3.Response, responseBody: String) {
        try {
            if (response.isSuccessful) {
                val obj = JSONObject(responseBody)
                val message = obj.optString("message", "")

                when {
                    message.contains("Authentication successful") -> {
                        val token = obj.optString("token", "")
                        val user = obj.optJSONObject("user")
                        val id = user?.optString("id", "")
                        val picture = user?.optString("picture", "")
                        val username = user?.optString("username", "")
                        if (token.isNotEmpty()) {
                            // Store token in SharedPreferences
                            val sharedPreferences = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
                            val editor = sharedPreferences.edit()
                            editor.putString("PICTURE", picture)
                            editor.putString("TOKEN", token)
                            editor.putString("USER_ID", id)
                            editor.putString("USERNAME", username)
                            editor.apply()
                            // Navigate to MainActivity
                            if (username.isNullOrEmpty()) {
                                val intent = Intent(this, CreateName_Activity::class.java)
                                startActivity(intent)
                                finish() // Prevents further execution of the current activity
                            } else {
                                navigateToMainActivity()
                            }

                        } else {
                            progressBar.visibility = View.GONE
                            Toast.makeText(applicationContext, "Error: Missing token or user data", Toast.LENGTH_LONG).show()
                        }
                    }
                    else -> Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                }
            } else {
                val errorMessage = try {
                    val errorObj = JSONObject(responseBody)
                    errorObj.optString("message", "Unknown error")
                } catch (e: JSONException) {
                    "Unknown error"
                }
                progressBar.visibility = View.GONE
                Toast.makeText(applicationContext, "Response: $errorMessage", Toast.LENGTH_LONG).show()
            }
        } catch (e: JSONException) {
            progressBar.visibility = View.GONE
            Toast.makeText(applicationContext, "Error parsing response: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }


    fun onGoogleLoginClick(view: View) {
        googleSignInClient.signOut().addOnCompleteListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            val idToken = account?.idToken
            val email = account?.email

            if (email != null) {
                // Check if the email is already associated with another provider
                checkIfEmailIsLinked(email) { isLinked ->
                    if (isLinked) {
                        Log.e("GoogleSignIn", "Email already linked with another provider")
                        Toast.makeText(this, "Email already linked with another provider", Toast.LENGTH_LONG).show()
                    } else {
                        if (idToken != null) {
                            Log.d("GoogleSignIn", "ID Token: $idToken")
                            googleSignIn(idToken)
                        } else {
                            Log.e("GoogleSignIn", "No ID token found")
                            Toast.makeText(this, "Google Sign-In failed: No ID token", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                Log.e("GoogleSignIn", "No email found")
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Google Sign-In failed: No email", Toast.LENGTH_LONG).show()
            }
        } catch (e: ApiException) {
            val statusCode = e.statusCode
            val errorMessage = when (statusCode) {
                GoogleSignInStatusCodes.SIGN_IN_FAILED -> "Sign-In failed"
                GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> "Sign-In cancelled"
                GoogleSignInStatusCodes.SIGN_IN_CURRENTLY_IN_PROGRESS -> "Sign-In already in progress"
                else -> "Unknown error occurred"
            }
            progressBar.visibility = View.GONE
            Log.e("GoogleSignIn", "Google Sign-In failed: ${e.message} (Code: $statusCode)")
            Toast.makeText(this, "Google Sign-In failed: $errorMessage", Toast.LENGTH_LONG).show()
        }
    }


    private fun checkIfEmailIsLinked(email: String, callback: (Boolean) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        auth.fetchSignInMethodsForEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val signInMethods = task.result?.signInMethods
                    val isLinked = signInMethods != null && signInMethods.isNotEmpty()
                    callback(isLinked)
                } else {
                    Log.e("CheckEmailLinked", "Error checking sign-in methods", task.exception)
                    Toast.makeText(this, "Error checking sign-in methods", Toast.LENGTH_LONG).show()
                    callback(false) // Consider as not linked if there's an error
                }
            }
    }




    private fun googleSignIn(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Sign in with Firebase using Google token
                val firebaseAuth = FirebaseAuth.getInstance()
                val credential = GoogleAuthProvider.getCredential(token, null)
                val authResult = firebaseAuth.signInWithCredential(credential).await()

                val user = authResult.user
                val userId = user?.uid ?: ""
                val email = user?.email ?: ""

                Log.d("GoogleSignIn", "User ID: $userId, Email: $email")

                // Build request to your backend
                val url = getString(R.string.root_url) + getString(R.string.googlesignin)
                val requestBody: RequestBody = FormBody.Builder()
                    .add("googleId", userId)
                    .add("email", email)
                    .build()
                val request = Request.Builder().url(url).post(requestBody).build()

                // Execute request and handle the response
                val response = OkHttpClient().newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        try {
                            val jsonObject = JSONObject(responseBody)
                            val jwtToken = jsonObject.optString("token", "")
                            val users = jsonObject.optJSONObject("user")
                            val id = users?.optString("id", "")
                            val picture = users?.optString("picture", "")
                            val username = users?.optString("username", "")
                            Log.d("GoogleSignIn", "username: $username")

                            if (jwtToken.isNotEmpty()) {
                                // Store token and user info in SharedPreferences
                                val sharedPreferences = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
                                with(sharedPreferences.edit()) {
                                    putString("PICTURE", picture)
                                    putString("TOKEN", jwtToken)
                                    putString("USER_ID", id)
                                    putString("USERNAME", username)
                                    apply()
                                }

                                if (username == null || username.isEmpty()) {
                                    Log.d("GoogleSignIn", "Navigating to CreateName_Activity because username is null or empty")
                                    Log.d("GoogleSignIn", "username value before check: $username")
                                    val intent = Intent(this@LoginActivity, CreateName_Activity::class.java)
                                    startActivity(intent)
                                    finish()
                                } else {
                                    Log.d("GoogleSignIn", "Navigating to MainActivity because username is not null or empty")
                                    Log.d("GoogleSignIn", "username value before check: $username")
                                    navigateToMainActivity()
                                }

                            } else {
                                Toast.makeText(this@LoginActivity, "Authentication failed", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: JSONException) {
                            Log.e("GoogleSignIn", "JSON Parsing error: ${e.message}")
                            Toast.makeText(this@LoginActivity, "Error parsing server response: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        // Handle server errors gracefully
                        val errorMessage = try {
                            val errorObj = JSONObject(responseBody)
                            errorObj.optString("error", "Unknown error")
                        } catch (e: JSONException) {
                            "Unknown error"
                        }
                        Toast.makeText(this@LoginActivity, "Server error: $errorMessage", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Log.e("GoogleSignIn", "Network Error: ${e.message}")
                    Toast.makeText(this@LoginActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("GoogleSignIn", "Error: ${e.message}")
                    Toast.makeText(this@LoginActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun onclickRegister(view: View) {
        startActivity(Intent(this, RegisterActivity::class.java))
    }

    fun onclickForgetpass(view: View) {
        startActivity(Intent(this, Forget_Password_Activity::class.java))
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        callbackManager.onActivityResult(requestCode, resultCode, data)
    }
}
