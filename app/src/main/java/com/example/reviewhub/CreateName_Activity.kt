package com.example.reviewhub

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

class CreateName_Activity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    @SuppressLint("WrongViewCast", "MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_name)

        sharedPreferences = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)

        val changeUsernameButton = findViewById<Button>(R.id.change)
        val usernameEditText = findViewById<EditText>(R.id.txtusername)
//        val pictureEditText = findViewById<EditText>(R.id.txtPicture)
//        val birthdayEditText = findViewById<EditText>(R.id.txtBirthday)
        val txterror = findViewById<TextView>(R.id.txterror) // Reference to the error field

        changeUsernameButton.setOnClickListener {
            val newUsername = usernameEditText.text.toString()
//            val picture = pictureEditText.text.toString()
//            val birthday = birthdayEditText.text.toString()

//            if (newUsername.isEmpty() || picture.isEmpty() || birthday.isEmpty()) {
//                txterror.text = "Please enter username, picture, and birthday"
//            } else {
//                setProfile(newUsername, picture, birthday, txterror) // ส่งค่าครบทุกฟิลด์ไปที่ setProfile
//            }
        }
    }

    private fun setProfile(newUsername: String, picture: String, birthday: String, txterror: TextView) {
        // Get the token from SharedPreferences
        val token = sharedPreferences.getString("TOKEN", null)

        if (token == null) {
            txterror.text = "Authentication token not found"
            return
        }

        // ใช้ Coroutine ใน IO Thread
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()

            // สร้าง request body
            val requestBody = FormBody.Builder()
                .add("newUsername", newUsername)
                .add("picture", picture) // เพิ่มฟิลด์รูปภาพ
                .add("birthday", birthday) // เพิ่มฟิลด์วันเกิด
                .build()

            // สร้าง URL สำหรับคำขอ
            val url = getString(R.string.root_url) + getString(R.string.setprofile) // `setprofile` ควรตรงกับ API route ที่คุณกำหนด
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token") // ใส่ token ใน Header
                .post(requestBody)
                .build()

            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                val message = responseBody?.let { JSONObject(it).optString("message", "No message") } ?: "No message"
                response.close()

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@CreateName_Activity, "Profile set successfully for the first time", Toast.LENGTH_SHORT).show()

                        // บันทึกข้อมูล username, picture, และ birthday ลงใน SharedPreferences
                        val editor = sharedPreferences.edit()
                        editor.putString("username", newUsername)
                        editor.putString("picture", picture)
                        editor.putString("birthday", birthday)
                        editor.apply()

                        // ไปยัง MainActivity เมื่อบันทึกสำเร็จ
                        val intent = Intent(this@CreateName_Activity, MainActivity::class.java)
                        startActivity(intent)
                    } else {
                        txterror.text = "Failed: $message"
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    txterror.text = "Error: ${e.message}"
                    Log.e("CreateName_Activity", "Error: ${e.message}", e)
                }
            }
        }
    }
}

