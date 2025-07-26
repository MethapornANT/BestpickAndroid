package com.bestpick.reviewhub

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class CreateName_Activity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private var selectedImageUri: Uri? = null

    // อ้างอิง UI elements
    private lateinit var usernameEditText: EditText
    private lateinit var birthdayEditText: EditText
    private lateinit var spinnerGender: Spinner // เพิ่ม Spinner
    private lateinit var txterror: TextView

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_name)
        enableEdgeToEdge()
        sharedPreferences = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // Initializing UI elements
        val changeUsernameButton = findViewById<Button>(R.id.change)
        usernameEditText = findViewById(R.id.txtusername)
        val pictureButton = findViewById<Button>(R.id.editimage)
        birthdayEditText = findViewById(R.id.txtbirthday)
        txterror = findViewById(R.id.txterror)
        spinnerGender = findViewById(R.id.spinner_gender) // อ้างอิง Spinner

        // Setup Spinner
        val genders = arrayOf("Select Gender", "Male", "Female", "Other") // ตัวเลือกสำหรับ Spinner
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, genders)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerGender.adapter = adapter

        pictureButton.setOnClickListener {
            pickImageFromGallery()
        }

        birthdayEditText.setOnClickListener {
            showDatePickerDialog(birthdayEditText)
        }

        changeUsernameButton.setOnClickListener {
            val newUsername = usernameEditText.text.toString()
            val birthday = birthdayEditText.text.toString()
            val selectedGender = spinnerGender.selectedItem.toString() // ดึงค่าที่เลือกจาก Spinner

            // ตรวจสอบความถูกต้องของข้อมูลทั้งหมด (รวมถึง gender)
            if (newUsername.isEmpty() || selectedImageUri == null || birthday.isEmpty() || selectedGender == "Select Gender") {
                txterror.text = "Please enter username, select a picture, birthday, and gender."
                return@setOnClickListener // หยุดการทำงานถ้าข้อมูลไม่ครบ
            }

            // Check if the user is older than 13
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val birthDate: Date? = try {
                dateFormat.parse(birthday)
            } catch (e: Exception) {
                txterror.text = "Invalid birthday format. Please use DD/MM/YYYY."
                return@setOnClickListener
            }

            if (birthDate == null) {
                txterror.text = "Invalid birthday date."
                return@setOnClickListener
            }

            val currentDate = Calendar.getInstance().time

            var age = currentDate.year - birthDate.year

            // Adjust if birthdate hasn't occurred this year
            if (currentDate.month < birthDate.month ||
                (currentDate.month == birthDate.month && currentDate.date < birthDate.date)) {
                age--
            }

            if (age < 13) {
                txterror.text = "You must be at least 13 years old to change the username."
            } else {
                // Pass selectedImageUri and selectedGender to handle image upload and profile update
                setProfile(newUsername, selectedImageUri, birthday, selectedGender, txterror) // ส่ง selectedGender ไปด้วย
            }
        }
    }

    // Use ActivityResultLauncher to pick an image
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                selectedImageUri = uri
                val viewImage = findViewById<ImageView>(R.id.viewimage)
                viewImage.setImageURI(uri)
            }
        }

    private fun pickImageFromGallery() {
        pickImageLauncher.launch("image/*")
    }

    // เพิ่ม parameter สำหรับ gender
    private fun setProfile(newUsername: String, imageUri: Uri?, birthday: String, gender: String, txterror: TextView) {
        val token = sharedPreferences.getString("TOKEN", null)
        if (token == null) {
            txterror.text = "Authentication token not found"
            return
        }

        // Coroutine in IO thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()

                // Get the image InputStream from the Uri
                val inputStream = imageUri?.let { contentResolver.openInputStream(it) }
                val fileName = imageUri?.let { getFileNameFromUri(it) }

                // Create a RequestBody for the image file
                val imageRequestBody = inputStream?.let { inputStream ->
                    val fileBytes = inputStream.readBytes()
                    // ใช้ MediaType.parse("image/*") หรือระบุ type ที่แน่นอนถ้าทราบ
                    fileBytes.toRequestBody((contentResolver.getType(imageUri) ?: "image/*").toMediaTypeOrNull())
                }

                // Build multipart request body
                val requestBodyBuilder = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("newUsername", newUsername)
                    .addFormDataPart("birthday", birthday)
                    .addFormDataPart("gender", gender) // <<-- เพิ่ม gender เข้าไป

                // เพิ่มรูปภาพถ้ามี
                if (imageRequestBody != null && fileName != null) {
                    requestBodyBuilder.addFormDataPart("picture", fileName, imageRequestBody)
                } else {
                    // หากไม่มีรูปภาพ ให้ log หรือจัดการตามต้องการ
                    Log.w("CreateName_Activity", "Image URI or RequestBody is null. Skipping picture upload.")
                }

                val requestBody = requestBodyBuilder.build()

                val url = getString(R.string.root_url) + getString(R.string.setprofile)
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .post(requestBody)
                    .build()
                Log.d("CreateName_Activity", "Sending newUsername: $newUsername, birthday: $birthday, gender: $gender, picture: ${fileName ?: "none"}") // Log gender ด้วย

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        // If profile update is successful
                        Toast.makeText(this@CreateName_Activity, "Profile updated successfully", Toast.LENGTH_SHORT).show()

                        // Save profile details in SharedPreferences
                        val editor = sharedPreferences.edit()
                        editor.putString("username", newUsername)
                        editor.putString("birthday", birthday)
                        editor.putString("gender", gender) // <<-- บันทึก gender ลง SharedPreferences
                        editor.apply()

                        // Navigate to MainActivity
                        val intent = Intent(this@CreateName_Activity, MainActivity::class.java)
                        startActivity(intent)
                        finish() // ปิด Activity นี้หลังจากไปหน้าอื่นแล้ว
                    } else {
                        // If there's an error (500) from the server, display the error message
                        val errorMessage = responseBody?.let {
                            try {
                                JSONObject(it).optString("message", "Error updating profile")
                            } catch (e: Exception) {
                                "Server response error: $it" // กรณี response ไม่ใช่ JSON
                            }
                        }
                        txterror.text = "Failed: $errorMessage (Code: ${response.code})" // แสดงโค้ด HTTP ด้วย
                        Log.e("CreateName_Activity", "Server Error: ${response.code}, Message: $errorMessage, Body: $responseBody")
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    txterror.text = "Network Error: ${e.message}"
                    Log.e("CreateName_Activity", "Network Error: ${e.message}", e)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    txterror.text = "An unexpected error occurred: ${e.message}"
                    Log.e("CreateName_Activity", "Unexpected Error: ${e.message}", e)
                }
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var fileName = "image.jpg"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
        return fileName
    }


    private fun showDatePickerDialog(editText: EditText) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                val formattedDate = "$selectedDay/${selectedMonth + 1}/$selectedYear" // DD/MM/YYYY
                editText.setText(formattedDate)
            },
            year, month, day
        )
        // ตั้งค่าให้เลือกวันที่ในอดีตเท่านั้น และไม่เกินปัจจุบัน
        datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
        datePickerDialog.show()
    }
}