@file:Suppress("DEPRECATION")

package com.example.reviewhub

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.facebook.AccessToken
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.FacebookSdk
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FacebookAuthProvider
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
import android.widget.ProgressBar
import android.widget.TextView



class LoginActivity : AppCompatActivity() {
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>
    private lateinit var callbackManager: CallbackManager
    private lateinit var progressBar: ProgressBar
    private lateinit var forgetPassTextView: TextView
    private lateinit var blockingView: View


    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
       //installSplashScreen()
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        progressBar = findViewById(R.id.progress_bar)
        forgetPassTextView = findViewById(R.id.forgetpass)
        blockingView = findViewById(R.id.viewpage)


//        // Simulate a loading process
//        Handler(Looper.getMainLooper()).postDelayed({
//            // Hide the progress bar after loading is complete
//            progressBar.visibility = View.GONE
//        }, 1000)
        forgetPassTextView.setOnClickListener {
            showLoadingAndNavigate()
        }



        // Initialize Facebook SDK
        FacebookSdk.setClientToken("1021807229429436")
        FacebookSdk.sdkInitialize(applicationContext)
        callbackManager = CallbackManager.Factory.create()

        // Check if user is already signed in
        val firebaseAuth = FirebaseAuth.getInstance()
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            navigateToMainActivity()
        }

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

    private fun setupViews() {
        val loginButton = findViewById<Button>(R.id.loginButton)
        val emailEditText = findViewById<EditText>(R.id.Email)
        val passwordEditText = findViewById<EditText>(R.id.password)

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()
            when {
                email.isEmpty() -> emailEditText.error = "Email is required"
                password.isEmpty() -> passwordEditText.error = "Password is required"
                else -> performLogin(email, password)
            }
        }

    }

    private fun navigateToMainActivity() {
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
                        if (token.isNotEmpty()) {
                            val intent = Intent(this, MainActivity::class.java)
                            intent.putExtra("TOKEN", token)
                            intent.putExtra("USER", user.toString())
                            startActivity(intent)
                            finish()
                        } else {
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
                Toast.makeText(applicationContext, "Response: $errorMessage", Toast.LENGTH_LONG).show()
            }
        } catch (e: JSONException) {
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
                val firebaseAuth = FirebaseAuth.getInstance()
                val credential = GoogleAuthProvider.getCredential(token, null)
                val authResult = firebaseAuth.signInWithCredential(credential).await()

                val user = authResult.user
                val userId = user?.uid ?: ""
                val email = user?.email ?: ""
                val name = user?.displayName ?: ""
                val picture = user?.photoUrl?.toString() ?: ""
                Log.d("GoogleSignIn", "User ID: $userId, Email: $email, Name: $name, Picture: $picture")
                val url =getString(R.string.root_url) +getString(R.string.googlesignin)
                val requestBody: RequestBody = FormBody.Builder()
                    .add("googleId", userId)
                    .add("email", email)
                    .add("name", name)
                    .add("picture", picture)
                    .build()
                val request = Request.Builder().url(url).post(requestBody).build()

                val response = OkHttpClient().newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        try {
                            val jsonObject = JSONObject(responseBody)
                            val jwtToken = jsonObject.optString("token", "")

                            if (jwtToken.isNotEmpty()) {
                                val intent = Intent(this@LoginActivity, MainActivity::class.java)
                                intent.putExtra("TOKEN", jwtToken)
                                startActivity(intent)
                                finish()
                            } else {
                                Toast.makeText(this@LoginActivity, "Authentication failed", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: JSONException) {
                            Log.e("GoogleSignIn", "JSON Parsing error: ${e.message}")
                            Toast.makeText(this@LoginActivity, "Error parsing server response: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        val errorMessage = try {
                            val errorObj = JSONObject(responseBody)
                            errorObj.optString("error", "Unknown error")
                        } catch (e: JSONException) {
                            "Unknown error"
                        }
                        Toast.makeText(this@LoginActivity, "Server error: $errorMessage", Toast.LENGTH_LONG).show()
                    }
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

    private fun showLoadingAndNavigate() {
        // Show the ProgressBar and the blocking view
        progressBar.visibility = View.VISIBLE
        forgetPassTextView.isEnabled = false // Disable the TextView to prevent multiple clicks

        Handler(Looper.getMainLooper()).postDelayed({
            // Hide the ProgressBar and the blocking view
            progressBar.visibility = View.GONE
            blockingView.visibility = View.VISIBLE
            forgetPassTextView.isEnabled = true

            // Navigate to the next page (e.g., Forget_Password_Activity)
            val intent = Intent(this, Forget_Password_Activity::class.java)
            startActivity(intent)
        }, 1000) // Delay of 2 seconds to simulate loading time
    }

}

