@file:Suppress("DEPRECATION")

package com.example.reviewhub

import android.content.Intent
import android.os.Bundle
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
import com.facebook.*
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.*
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import org.json.JSONException
import kotlinx.coroutines.withContext as withContext1

class LoginActivity : AppCompatActivity() {
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>
    private lateinit var callbackManager: CallbackManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        // Initialize Facebook SDK
        FacebookSdk.setClientToken("1021807229429436")
        FacebookSdk.sdkInitialize(applicationContext)
        FacebookSdk.fullyInitialize()
        callbackManager = CallbackManager.Factory.create()
        // Check if user is already signed in
        val firebaseAuth = FirebaseAuth.getInstance()
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            // User is already signed in
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }



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

        val facebookLoginLayout = findViewById<LinearLayout>(R.id.loginfacebook)
        facebookLoginLayout.setOnClickListener {
            onFacebookLoginClick(it)
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
        try {
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
                        Log.d("LoginResponse", "Token: $token, User: $user")
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
                    else -> {
                        Toast.makeText(applicationContext, "Unknown response: $message", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                // Parse the responseBody for error message
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



    private fun onFacebookLoginClick(view: View) {
        val loginManager = LoginManager.getInstance()
        loginManager.registerCallback(callbackManager, object : FacebookCallback<LoginResult> {
            override fun onSuccess(result: LoginResult) {
                handleFacebookAccessToken(result.accessToken)
            }

            override fun onCancel() {
                Toast.makeText(this@LoginActivity, "Facebook login canceled", Toast.LENGTH_LONG).show()
            }

            override fun onError(error: FacebookException) {
                Log.e("FacebookLogin", "Login failed", error)
                Toast.makeText(this@LoginActivity, "Facebook login failed: ${error.message}", Toast.LENGTH_LONG).show()
            }
        })
        loginManager.logInWithReadPermissions(this, listOf("email", "public_profile"))
    }

    private fun handleFacebookAccessToken(token: AccessToken) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get Firebase Auth Credential using the token
                val credential = FacebookAuthProvider.getCredential(token.token)
                val firebaseAuth = FirebaseAuth.getInstance()
                val authResult = firebaseAuth.signInWithCredential(credential).await()

                // Extract user information
                val user = authResult.user
                val userId = user?.uid ?: ""
                val email = user?.email ?: ""
                val name = user?.displayName ?: ""
                val picture = user?.photoUrl?.toString() ?: ""

                // Send user data to your server
                val url = "http://192.168.1.109:3000/facebook-signin"
                val client = OkHttpClient()
                val requestBody: RequestBody = FormBody.Builder()
                    .add("facebookId", userId)
                    .add("email", email)
                    .add("name", name)
                    .add("picture", picture)
                    .build()
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                // Execute the request and handle the response
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                // Handle server response
                withContext1(Dispatchers.Main) {
                    handleFacebookSignInResponse(response, responseBody)
                }
            } catch (e: Exception) {
                withContext1(Dispatchers.Main) {
                    Log.e("FacebookSignIn", "Error: ${e.message}")
                    Toast.makeText(this@LoginActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun handleFacebookSignInResponse(response: okhttp3.Response, responseBody: String) {
        try {
            if (response.isSuccessful) {
                val jsonObject = JSONObject(responseBody)
                val message = jsonObject.optString("message", "")
                val jwtToken = jsonObject.optString("token", "")

                Log.d("FacebookSignIn", "JWT Token: $jwtToken")

                if (message.contains("successfully")) {
                    val intent = Intent(this@LoginActivity, MainActivity::class.java)
                    intent.putExtra("TOKEN", jwtToken)
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this@LoginActivity, message, Toast.LENGTH_LONG).show()
                }
            } else {
                val errorBody = responseBody // Use responseBody directly
                try {
                    val errorJson = JSONObject(errorBody)
                    val errorMessage = errorJson.optString("error", "Unknown error")
                    Toast.makeText(this@LoginActivity, "Server error: $errorMessage", Toast.LENGTH_LONG).show()
                } catch (e: JSONException) {
                    // Fallback if errorBody is not valid JSON
                    Toast.makeText(this@LoginActivity, "Server error: $errorBody", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: JSONException) {
            Toast.makeText(this@LoginActivity, "Error parsing response: ${e.message}", Toast.LENGTH_LONG).show()
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
                    val firebaseAuth = FirebaseAuth.getInstance()
                    val credential = GoogleAuthProvider.getCredential(it, null)
                    val authResult = firebaseAuth.signInWithCredential(credential).await()

                    val user = authResult.user
                    val userId = user?.uid ?: ""
                    val email = user?.email ?: ""
                    val name = user?.displayName ?: ""
                    val picture = user?.photoUrl?.toString() ?: ""

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

                    withContext1(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            try {
                                val jsonObject = JSONObject(responseBody)
                                val message = jsonObject.optString("message", "")
                                val jwtToken = jsonObject.optString("token", "")

                                Log.d("GoogleSignIn", "JWT Token: $jwtToken")
                                Log.d("GoogleSignIn", "User Info: User ID: $userId, Email: $email, Name: $name, Picture: $picture")

                                if (message.contains("successfully")) {
                                    val intent = Intent(this@LoginActivity, MainActivity::class.java)
                                    intent.putExtra("TOKEN", jwtToken)
                                    startActivity(intent)
                                    finish()
                                } else {
                                    Toast.makeText(this@LoginActivity, message, Toast.LENGTH_LONG).show()
                                }
                            } catch (e: JSONException) {
                                Log.e("GoogleSignIn", "JSON Parsing error: ${e.message}")
                                Toast.makeText(this@LoginActivity, "Error parsing server response: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            // Extract and handle error message from response
                            val errorBody = try {
                                val errorObj = JSONObject(responseBody)
                                errorObj.optString("error", "Unknown error")
                            } catch (e: JSONException) {
                                "Unknown error"
                            }
                            Toast.makeText(this@LoginActivity, "Server error: $errorBody", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        callbackManager.onActivityResult(requestCode, resultCode, data)
    }
}
