package com.example.reviewhub

import android.app.Activity
import android.app.DatePickerDialog
import android.app.ProgressDialog
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class EditprofileFragment : Fragment() {
    private lateinit var usernameEditText: EditText
    private lateinit var bioEditText: EditText
    private lateinit var genderSpinner: Spinner
    private lateinit var profileImageView: ImageView
    private lateinit var email: EditText
    private lateinit var birthday: EditText
    private var imageUri: Uri? = null
    private val client = OkHttpClient()
    var filename = ""

    companion object {
        const val PICK_IMAGE_REQUEST = 1
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_editprofile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sharedPreferences = requireActivity().getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences.getString("TOKEN", null)
        val userId = sharedPreferences.getString("USER_ID", null)

        usernameEditText = view.findViewById(R.id.username_edit)
        bioEditText = view.findViewById(R.id.bio_edit)
        genderSpinner = view.findViewById(R.id.gender_spinner)
        profileImageView = view.findViewById(R.id.Imgview)
        email = view.findViewById(R.id.email_edit)
        birthday = view.findViewById(R.id.editTextBirthday)
        val editImg = view.findViewById<TextView>(R.id.editImg)

        // Load gender array into Spinner
        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.gender_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            genderSpinner.adapter = adapter
        }

        view.findViewById<TextView>(R.id.back).setOnClickListener {
            requireActivity().onBackPressed()
        }

        editImg.setOnClickListener {
            pickImageFromGallery()
        }

        birthday.setOnClickListener {
            showDatePickerDialog(birthday)
        }

        view.findViewById<TextView>(R.id.save_button).setOnClickListener {
            if (userId != null) {
                updateUserProfile(userId, token)
            }
        }

        if (userId != null) {
            fetchUserProfile(view, userId, token)
        }
    }

    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.data != null) {
            imageUri = data.data
            Log.d("ProfileFragment", "Selected image URI: $imageUri")
            profileImageView.setImageURI(imageUri)
        }
    }

    private fun updateUserProfile(userId: String, token: String?) {
        val username = usernameEditText.text.toString()
        val bio = bioEditText.text.toString()
        val gender = genderSpinner.selectedItem.toString()
        val birthdayStr = birthday.text.toString()

        if (username.isEmpty() || bio.isEmpty() || gender.isEmpty() || birthdayStr.isEmpty()) {
            if (isAdded) {
                Toast.makeText(requireContext(), "All fields must be filled", Toast.LENGTH_SHORT).show()
            }
            return
        }
        // Convert birthday from "d MMM yyyy" back to "yyyy-MM-dd" for the server
        val formattedBirthday = formatDateForServer(birthdayStr)

        val requestBodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("username", username)
            .addFormDataPart("bio", bio)
            .addFormDataPart("gender", gender)
            .addFormDataPart("birthday", formattedBirthday)

        if (imageUri != null) {
            val inputStream = requireContext().contentResolver.openInputStream(imageUri!!)
            val tempFile = File.createTempFile("profile_img", ".jpg", requireContext().cacheDir)
            inputStream?.use { input -> tempFile.outputStream().use { output -> input.copyTo(output) } }
            val mediaType = "image/jpeg".toMediaTypeOrNull()
            filename = tempFile.name
            requestBodyBuilder.addFormDataPart("profileImage", tempFile.name, RequestBody.create(mediaType, tempFile))
        }

        val requestBody = requestBodyBuilder.build()
        val url = getString(R.string.root_url) + getString(R.string.userprofileupdate) + userId + "/profile"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .put(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                if (isAdded) {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Failed to update profile: ${e.message}", Toast.LENGTH_SHORT).show()
                        Log.e("ProfileFragment", "API update error: ${e.message}")
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                Log.d("ProfileFragment", "Response Data: $responseData")

                if (response.isSuccessful) {
                    if (isAdded && activity != null) {
                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show()
                            requireActivity().onBackPressed() // ย้อนกลับเมื่ออัปเดตสำเร็จ
                        }
                    }
                } else {
                    if (isAdded && activity != null) {
                        requireActivity().runOnUiThread {
                            try {
                                // แปลงข้อมูล response เป็น JSONObject เพื่อดึง error message
                                val jsonResponse = JSONObject(responseData ?: "")
                                val errorMessage = jsonResponse.optString("error", "Unknown error")

                                // แสดงข้อความ error
                                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                // กรณีที่ไม่สามารถแปลง JSON ได้
                                Toast.makeText(requireContext(), "Error: ${response.message}", Toast.LENGTH_SHORT).show()
                            }
                            Log.e("ProfileFragment", "API error: ${response.message}, Response body: $responseData")
                        }
                    }
                }
            }

        })
    }
    // Helper function to format birthday for server (yyyy-MM-dd)
    private fun formatDateForServer(dateStr: String): String {
        return try {
            // Input format is "dd/MM/yyyy" from the UI
            val inputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val outputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) // Format the server expects
            // No need to set time zone here since we're only dealing with date
            val date = inputFormat.parse(dateStr)
            date?.let { outputFormat.format(it) } ?: dateStr
        } catch (e: Exception) {
            dateStr // In case of an error, return the original string
        }
    }





    private fun fetchUserProfile(view: View, userId: String, token: String?) {
        val rootUrl = getString(R.string.root_url)
        val userProfileEndpoint = getString(R.string.userprofile)
        val url = "$rootUrl$userProfileEndpoint$userId/profile"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ProfileFragment", "Failed to fetch user profile: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseData = response.body?.string()
                    Log.d("ProfileFragment", "Response Data: $responseData")
                    responseData?.let {
                        try {
                            val jsonObject = JSONObject(it)
                            val username = jsonObject.getString("username")
                            val profileImageUrl = jsonObject.getString("profileImageUrl")
                            val emailuser = jsonObject.getString("email")
                            val bio = jsonObject.getString("bio")
                            val birthday = formatTime(jsonObject.optString("birthday", ""))
                            val gender = jsonObject.getString("gender")
                            val imgProfileUrl = rootUrl + profileImageUrl

                            if (isAdded) {
                                activity?.runOnUiThread {
                                    usernameEditText.setText(username)
                                    email.setText(emailuser)
                                    bioEditText.setText(bio)
                                    this@EditprofileFragment.birthday.setText(birthday)
                                    genderSpinner.setSelection(
                                        resources.getStringArray(R.array.gender_array).indexOf(gender)
                                    )

                                    Glide.with(this@EditprofileFragment)
                                        .load(imgProfileUrl)
                                        .centerCrop()
                                        .placeholder(R.drawable.ic_launcher_background)
                                        .into(profileImageView)
                                }
                            } else {
                                Log.e("EditprofileFragment", "Fragment is not attached to context")
                            }
                        } catch (e: Exception) {
                            Log.e("ProfileFragment", "Error parsing JSON: ${e.message}")
                        }
                    }
                } else {
                    Log.e("ProfileFragment", "Server error: ${response.message}")
                }
            }
        })
    }

    private fun showDatePickerDialog(editText: EditText) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDay ->
                val formattedDate = "$selectedDay/${selectedMonth + 1}/$selectedYear"
                editText.setText(formattedDate)
            },
            year, month, day
        )

        datePickerDialog.show()
    }

    private fun formatTime(timeString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC") // ตั้งค่า inputFormat เป็น UTC
            }
            val outputFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("Asia/Bangkok") // ตั้งค่า outputFormat เป็น Asia/Bangkok
            }
            val date = inputFormat.parse(timeString ?: "")
            date?.let { outputFormat.format(it) } ?: "N/A"

        } catch (e: Exception) {
            timeString
        }
    }
}
