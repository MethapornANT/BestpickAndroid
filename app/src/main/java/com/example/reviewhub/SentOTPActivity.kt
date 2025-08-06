package com.bestpick.reviewhub

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.widget.ImageButton
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
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
    private lateinit var progressBar: LottieAnimationView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_sent_otpactivity)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

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

        // Start initial countdown
        startCountdown()

        val backButton = findViewById<ImageButton>(R.id.backButton)
        // เพิ่ม OnClickListener สำหรับปุ่ม backButton
        backButton.setOnClickListener {
            finish() // ใช้ finish() เพื่อปิด Activity ปัจจุบันและกลับไปหน้าก่อนหน้า
        }

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
                    updateButtonState(sentOTPButton, otpFields)
                }
            })
        }
        otp1.requestFocus()
        updateButtonState(sentOTPButton, otpFields)

        sentOTPButton.setOnClickListener {
            val otp = otp1.text.toString() + otp2.text.toString() + otp3.text.toString() + otp4.text.toString()
            progressBar.visibility = View.VISIBLE
            performRegister(email, otp)
        }

        resendButton.setOnClickListener {
            val email = intent.getStringExtra("email") ?: return@setOnClickListener
            // เรียกใช้ resend otp
            resendOtp(email)
        }
    }

    private fun resendOtp(email: String) {
        // ยกเลิก timer เก่าก่อนเริ่มใหม่
        countdownTimer?.cancel()

        // ปิดการใช้งานปุ่ม Resend และเริ่มนับถอยหลังใหม่
        resendButton.isEnabled = false
        resendButton.visibility = View.GONE
        countdownTextView.visibility = View.VISIBLE

        progressBar.visibility = View.VISIBLE

        // Start countdown
        startCountdown()

        // Make network request to resend OTP
        CoroutineScope(Dispatchers.IO).launch {
            val url = getString(R.string.root_url) + getString(R.string.resentotpregis)
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
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful) {
                        Toast.makeText(applicationContext, "ส่ง OTP ใหม่แล้ว", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(applicationContext, "ไม่สามารถส่ง OTP ใหม่ได้", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(applicationContext, "เกิดข้อผิดพลาดในการเชื่อมต่อ", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    private fun startCountdown() {
        // Set countdown timer for 1 minute
        countdownTimer = object : CountDownTimer(60 * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000)
                countdownTextView.text = "ส่งใหม่ใน ${seconds}s"
            }

            override fun onFinish() {
                countdownTextView.text = "คุณสามารถส่งใหม่ได้แล้ว"
                countdownTextView.visibility = View.VISIBLE
                resendButton.isEnabled = true
                resendButton.visibility = View.VISIBLE
            }
        }.start()
    }


    private fun updateButtonState(button: Button, otpFields: List<EditText>) {
        val allFilled = otpFields.all { it.text.length == 1 }
        button.isEnabled = allFilled
    }

    private fun performRegister(email: String, otp: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val url = getString(R.string.root_url) + getString(R.string.registerotp)
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

                Log.d("ResponseBody", responseBody)

                withContext(Dispatchers.Main) {
                    handleCreateResponse(response, responseBody, email)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun handleCreateResponse(response: okhttp3.Response, responseBody: String, email: String) {
        progressBar.visibility = View.GONE
        try {
            if (response.isSuccessful) {
                val obj = JSONObject(responseBody)
                val message = obj.optString("message", "")

                when {
                    message.contains("OTP verified, you can set your password now") -> {
                        val intent = Intent(this, Register_Create_PasswordActivity::class.java)
                        intent.putExtra("email", email)
                        startActivity(intent)
                        finish()
                    }
                    else -> {
                        Toast.makeText(this, "OTP ไม่ถูกต้อง", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                val errorMessage = try {
                    val errorObj = JSONObject(responseBody)
                    errorObj.optString("error", "Unknown error")
                } catch (e: JSONException) {
                    "Unknown error"
                }
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
            }
        } catch (e: JSONException) {
            Toast.makeText(this, "เกิดข้อผิดพลาดในการประมวลผล", Toast.LENGTH_SHORT).show()
        }
    }
}