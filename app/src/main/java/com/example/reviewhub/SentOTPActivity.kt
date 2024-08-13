package com.example.reviewhub

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
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

class SentOTPActivity : AppCompatActivity() {

    private lateinit var countdownTextView: TextView
    private lateinit var emailTextView: TextView
    private lateinit var otp1: EditText
    private lateinit var otp2: EditText
    private lateinit var otp3: EditText
    private lateinit var otp4: EditText
    private lateinit var sentOTPButton: Button
    private lateinit var resendButton: TextView
    private var countdownTimer: CountDownTimer? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_sent_otpactivity)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        emailTextView = findViewById(R.id.email)
        countdownTextView = findViewById(R.id.countdown)
        otp1 = findViewById(R.id.otp1)
        otp2 = findViewById(R.id.otp2)
        otp3 = findViewById(R.id.otp3)
        otp4 = findViewById(R.id.otp4)
        sentOTPButton = findViewById(R.id.btnsentotp)
        resendButton = findViewById(R.id.resent)

        val email = intent.getStringExtra("email") ?: return
        emailTextView.text = email

        startCountdown()

        val otpFields = listOf(otp1, otp2, otp3, otp4)
        otpFields.forEachIndexed { index, editText ->
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (s?.length == 1 && index < otpFields.size - 1) {
                        otpFields[index + 1].requestFocus()
                    } else if (s?.length == 0 && index > 0) {
                        otpFields[index - 1].requestFocus()
                    }
                    // Update button state based on all fields
                    updateButtonState(sentOTPButton, otpFields)
                }
            })
        }
        otp1.requestFocus()
        updateButtonState(sentOTPButton, otpFields)

        sentOTPButton.setOnClickListener {
            val otp = otp1.text.toString() + otp2.text.toString() + otp3.text.toString() + otp4.text.toString()
            performRegister(email, otp)
        }

        resendButton.setOnClickListener {
            val email = intent.getStringExtra("email") ?: return@setOnClickListener
            onclickResend(email)
        }

    }


    fun onclickResend(email: String) {
        // Disable the resend button to prevent multiple clicks
        val resendButton = findViewById<TextView>(R.id.resent)
        resendButton.isEnabled = false

        // Start countdown timer
        startCountdown()

        // Make network request to resend OTP
        CoroutineScope(Dispatchers.IO).launch {
            val url = "http://192.168.1.117:3000/resend-otp" // Update with your endpoint
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

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(applicationContext, "New OTP sent", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(applicationContext, "Failed to resend OTP", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }



    private fun startCountdown() {
        val countdownText = findViewById<TextView>(R.id.countdown)
        val resendButton = findViewById<TextView>(R.id.resent)
        resendButton.isEnabled = false

        // Set countdown timer for 1 minute
        countdownTimer = object : CountDownTimer(60 * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000) % 60
                countdownText.text = "Can resend again in ${seconds}s"
            }

            override fun onFinish() {
                countdownText.text = "You can resend now"
                resendButton.isEnabled = true // Re-enable resend button when countdown finishes
            }
        }.start()
    }


    private fun updateButtonState(button: Button, otpFields: List<EditText>) {
        val allFilled = otpFields.all { it.text.length == 1 }
        button.isEnabled = allFilled
    }

    private fun performRegister(email: String, otp: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val url = "http://192.168.1.117:3000/register/verify-otp"
            val okHttpClient = OkHttpClient()
            val formBody: RequestBody = FormBody.Builder()
                .add("email", email)
                .add("otp", otp)
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
                    message.contains("OTP verified, you can set your password now") -> {
                        Log.d("CreateResponse", "OTP sent to $email")

                        val intent = Intent(this, Register_Create_PasswordActivity::class.java) // Change to the appropriate activity
                        intent.putExtra("email", email)
                        startActivity(intent)
                        finish()
                    }
                    else -> {
                        Toast.makeText(applicationContext, "Response: $message", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
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
