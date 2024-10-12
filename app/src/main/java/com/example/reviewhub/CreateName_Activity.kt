package com.example.reviewhub

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.*

class CreateName_Activity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private var selectedImageUri: Uri? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_name)

        sharedPreferences = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)

        val changeUsernameButton = findViewById<Button>(R.id.change)
        val usernameEditText = findViewById<EditText>(R.id.txtusername)
        val pictureButton = findViewById<Button>(R.id.editimage)
        val birthdayEditText = findViewById<EditText>(R.id.txtbirthday)
        val txterror = findViewById<TextView>(R.id.txterror)

        pictureButton.setOnClickListener {
            pickImageFromGallery()
        }

        birthdayEditText.setOnClickListener {
            showDatePickerDialog(birthdayEditText)
        }

        changeUsernameButton.setOnClickListener {
            val newUsername = usernameEditText.text.toString()
            val birthday = birthdayEditText.text.toString()

            if (newUsername.isEmpty() || selectedImageUri == null || birthday.isEmpty()) {
                txterror.text = "Please enter username, select a picture, and birthday"
            } else {
                // Pass selectedImageUri to handle image upload
                setProfile(newUsername, selectedImageUri, birthday, txterror)
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

    private fun setProfile(newUsername: String, imageUri: Uri?, birthday: String, txterror: TextView) {
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
                    fileBytes.toRequestBody(MultipartBody.FORM)
                }

                // Build multipart request body
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("newUsername", newUsername)
                    .addFormDataPart("birthday", birthday)
                    .addFormDataPart("picture", fileName ?: "image.jpg", imageRequestBody!!)
                    .build()

                val url = getString(R.string.root_url) + getString(R.string.setprofile)
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .post(requestBody)
                    .build()
                Log.d("CreateName_Activity", "Sending newUsername: $newUsername, birthday: $birthday, picture: $fileName")

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
                        editor.apply()

                        // Navigate to MainActivity
                        val intent = Intent(this@CreateName_Activity, MainActivity::class.java)
                        startActivity(intent)
                    } else {
                        // If there's an error (500) from the server, display the error message
                        val errorMessage = responseBody?.let { JSONObject(it).optString("message", "Error updating profile") }
                        txterror.text = "Failed: $errorMessage"
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

    private fun getFileNameFromUri(uri: Uri): String {
        var fileName = "image.jpg"
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                fileName = it.getString(nameIndex)
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
            this,  // Changed from requireContext() to this
            { _, selectedYear, selectedMonth, selectedDay ->
                val formattedDate = "$selectedDay/${selectedMonth + 1}/$selectedYear"
                editText.setText(formattedDate)
            },
            year, month, day
        )

        datePickerDialog.show()
    }
}
