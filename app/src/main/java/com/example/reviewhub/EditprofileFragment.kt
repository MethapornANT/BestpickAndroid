package com.bestpick.reviewhub

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.ParseException // Import ParseException
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
    private lateinit var birthdayEditText: EditText
    private var imageUri: Uri? = null
    private val client = OkHttpClient()

    companion object {
        const val PICK_IMAGE_REQUEST = 1
        // Define a consistent date format for sending to the server AND displaying in UI
        private const val DATE_FORMAT = "yyyy-MM-dd" // Use one consistent format for both UI display and server
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
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
        birthdayEditText = view.findViewById(R.id.editTextBirthday)
        val editImg = view.findViewById<TextView>(R.id.editImg)
        val saveButton = view.findViewById<TextView>(R.id.save_button)
        val backButton = view.findViewById<TextView>(R.id.back)

        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.gender_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            genderSpinner.adapter = adapter
        }

        backButton.setOnClickListener {
            requireActivity().onBackPressed()
        }

        editImg.setOnClickListener {
            pickImageFromGallery()
        }

        // Show DatePickerDialog when birthday EditText is clicked
        birthdayEditText.setOnClickListener {
            showDatePickerDialog(birthdayEditText)
        }

        saveButton.setOnClickListener {
            if (userId != null) {
                updateUserProfile(userId, token)
            } else {
                Toast.makeText(requireContext(), "User ID not found. Cannot update profile.", Toast.LENGTH_SHORT).show()
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
            Log.d("EditprofileFragment", "Selected image URI: $imageUri")
            profileImageView.setImageURI(imageUri)
        }
    }

    private fun updateUserProfile(userId: String, token: String?) {
        val username = usernameEditText.text.toString().trim()
        val bio = bioEditText.text.toString().trim()
        val gender = genderSpinner.selectedItem.toString().trim()
        val birthdayStr = birthdayEditText.text.toString().trim()

        if (username.isEmpty() || bio.isEmpty() || gender.isEmpty() || birthdayStr.isEmpty()) {
            if (isAdded) {
                Toast.makeText(requireContext(), "กรุณากรอกข้อมูลให้ครบทุกช่องงับ", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // We now directly use birthdayStr as it should already be in YYYY-MM-DD from DatePicker or fetch
        // No need for formatDateForServer function anymore if UI_DATE_FORMAT is YYYY-MM-DD
        // However, we should validate it here to ensure it's in the correct format before sending.
        if (!isValidDateFormat(birthdayStr, DATE_FORMAT)) {
            if (isAdded) {
                Toast.makeText(requireContext(), "รูปแบบวันเกิดไม่ถูกต้อง กรุณาเลือกใหม่เป็น YYYY-MM-DD", Toast.LENGTH_SHORT).show()
            }
            return
        }


        val requestBodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("username", username)
            .addFormDataPart("bio", bio)
            .addFormDataPart("gender", gender)
            .addFormDataPart("birthday", birthdayStr) // Send as YYYY-MM-DD directly

        if (imageUri != null) {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(imageUri!!)
                val tempFile = File.createTempFile("profile_img_", ".jpg", requireContext().cacheDir)
                inputStream?.use { input -> tempFile.outputStream().use { output -> input.copyTo(output) } }

                val mediaType = "image/jpeg".toMediaTypeOrNull()
                requestBodyBuilder.addFormDataPart("profileImage", tempFile.name, RequestBody.create(mediaType, tempFile))
                Log.d("EditprofileFragment", "Adding profileImage: ${tempFile.name}")
            } catch (e: Exception) {
                Log.e("EditprofileFragment", "Error creating temp file for image: ${e.message}", e)
                if (isAdded) {
                    Toast.makeText(requireContext(), "เกิดข้อผิดพลาดในการเตรียมรูปภาพ", Toast.LENGTH_SHORT).show()
                }
                return
            }
        }

        val requestBody = requestBodyBuilder.build()
        val url = getString(R.string.root_url2) + getString(R.string.userprofileupdate) + userId + "/profile"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .put(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                if (isAdded && activity != null) {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "ไม่สามารถอัปเดตโปรไฟล์ได้: ${e.message}", Toast.LENGTH_LONG).show()
                        Log.e("EditprofileFragment", "API update error: ${e.message}")
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                Log.d("EditprofileFragment", "Response Data: $responseData")

                if (isAdded && activity != null) {
                    requireActivity().runOnUiThread {
                        if (response.isSuccessful) {
                            Toast.makeText(requireContext(), "อัปเดตโปรไฟล์สำเร็จงับ", Toast.LENGTH_SHORT).show()
                            requireActivity().onBackPressed()
                        } else {
                            try {
                                val jsonResponse = JSONObject(responseData ?: "")
                                val status = jsonResponse.optString("status", "")
                                val message = jsonResponse.optString("message", "เกิดข้อผิดพลาดที่ไม่ทราบงับ")
                                val suggestion = jsonResponse.optString("suggestion", "")
                                val errorDetail = jsonResponse.optString("error", "")

                                var displayMessage = message

                                // Override message for image warning
                                if (status == "warning") { // If status is warning, it's likely about the image
                                    displayMessage = "กรุณาเปลี่ยนภาพ"
                                } else if (errorDetail.isNotEmpty() && displayMessage == "เกิดข้อผิดพลาดที่ไม่ทราบงับ") {
                                    displayMessage = errorDetail // Prioritize specific error message if available
                                } else if (suggestion.isNotEmpty() && displayMessage == "เกิดข้อผิดพลาดที่ไม่ทราบงับ") {
                                    // If there's no specific error, but a suggestion, use that
                                    displayMessage = suggestion
                                }

                                Toast.makeText(requireContext(), displayMessage, Toast.LENGTH_LONG).show()

                                if (status != "warning") {
                                    // If not a warning, maybe pop back
                                }

                            } catch (e: Exception) {
                                Log.e("EditprofileFragment", "Error parsing error response: ${e.message}", e)
                                Toast.makeText(requireContext(), "เกิดข้อผิดพลาด: ${response.message} (ไม่สามารถอ่านรายละเอียดได้)", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } else {
                    Log.w("EditprofileFragment", "Fragment not attached after API response, cannot update UI.")
                }
            }
        })
    }

    // New helper function to validate date format (optional but good practice)
    private fun isValidDateFormat(dateString: String, format: String): Boolean {
        return try {
            val sdf = SimpleDateFormat(format, Locale.US) // Use Locale.US for strict parsing
            sdf.isLenient = false // Disable lenient parsing to ensure exact format match
            sdf.parse(dateString)
            true
        } catch (e: ParseException) {
            false
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
                Log.e("EditprofileFragment", "Failed to fetch user profile: ${e.message}")
                if (isAdded) {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "ไม่สามารถดึงข้อมูลโปรไฟล์ได้", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                Log.d("EditprofileFragment", "Fetch Profile Response Data: $responseData")

                if (isAdded) {
                    activity?.runOnUiThread {
                        if (response.isSuccessful) {
                            responseData?.let {
                                try {
                                    val jsonObject = JSONObject(it)
                                    val username = jsonObject.optString("username", "")
                                    val profileImageUrl = jsonObject.optString("profileImageUrl", "")
                                    val emailuser = jsonObject.optString("email", "")
                                    val bio = jsonObject.optString("bio", "ยังไม่มีข้อมูล")
                                    val birthdayJson = jsonObject.optString("birthday", "")
                                    val gender = jsonObject.optString("gender", "")
                                    val imgProfileUrl = if (profileImageUrl.isNotEmpty()) "$rootUrl/api$profileImageUrl" else ""

                                    usernameEditText.setText(username)
                                    email.setText(emailuser)
                                    bioEditText.setText(bio)

                                    // Format birthday string from server for display in YYYY-MM-DD
                                    val formattedBirthdayForUI = formatTimeForUIDisplay(birthdayJson) // Call new helper
                                    birthdayEditText.setText(formattedBirthdayForUI)


                                    val genderArray = resources.getStringArray(R.array.gender_array)
                                    val genderIndex = genderArray.indexOf(gender)
                                    if (genderIndex != -1) {
                                        genderSpinner.setSelection(genderIndex)
                                    } else {
                                        genderSpinner.setSelection(0)
                                    }

                                    if (imgProfileUrl.isNotEmpty()) {
                                        Glide.with(this@EditprofileFragment)
                                            .load(imgProfileUrl)
                                            .centerCrop()
                                            .placeholder(R.drawable.default_profile_picture) // Example placeholder
                                            .error(R.drawable.error_loading_image) // Example error image
                                            .into(profileImageView)
                                    } else {
                                        profileImageView.setImageResource(R.drawable.default_profile_picture)
                                    }

                                } catch (e: Exception) {
                                    Log.e("EditprofileFragment", "Error parsing JSON for fetchUserProfile: ${e.message}", e)
                                    Toast.makeText(requireContext(), "เกิดข้อผิดพลาดในการแสดงข้อมูลโปรไฟล์", Toast.LENGTH_SHORT).show()
                                }
                            } ?: run {
                                Log.w("EditprofileFragment", "Fetch user profile response data is null.")
                            }
                        } else {
                            Log.e("EditprofileFragment", "Server error on fetchUserProfile: ${response.code} - ${response.message}")
                            Toast.makeText(requireContext(), "ไม่สามารถดึงข้อมูลโปรไฟล์ได้: ${response.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Log.w("EditprofileFragment", "Fragment not attached during fetchUserProfile, cannot update UI.")
                }
            }
        })
    }

    private fun showDatePickerDialog(editText: EditText) {
        val calendar = Calendar.getInstance()
        // Try to parse existing text to set initial date, if not valid, use current date
        try {
            val existingDate = SimpleDateFormat(DATE_FORMAT, Locale.US).parse(editText.text.toString())
            existingDate?.let {
                calendar.time = it
            }
        } catch (e: ParseException) {
            // Do nothing, calendar already holds current date
        }

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDay ->
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(selectedYear, selectedMonth, selectedDay)

                if (isOlderThan13(selectedCalendar)) {
                    val formattedDate = SimpleDateFormat(DATE_FORMAT, Locale.US).format(selectedCalendar.time) // Ensure YYYY-MM-DD
                    editText.setText(formattedDate)
                } else {
                    Toast.makeText(requireContext(), "ต้องมีอายุอย่างน้อย 13 ปีขึ้นไปงับ", Toast.LENGTH_SHORT).show()
                    editText.setText("") // Clear the text if invalid
                }
            },
            year, month, day
        )
        datePickerDialog.datePicker.maxDate = Calendar.getInstance().timeInMillis
        datePickerDialog.show()
    }

    private fun isOlderThan13(birthDate: Calendar): Boolean {
        val today = Calendar.getInstance()
        var age = today.get(Calendar.YEAR) - birthDate.get(Calendar.YEAR)

        if (today.get(Calendar.DAY_OF_YEAR) < birthDate.get(Calendar.DAY_OF_YEAR)) {
            age--
        }
        return age >= 13
    }

    // Helper function to format timestamp string from server for UI display in YYYY-MM-DD
    // This replaces the old formatTime function
    private fun formatTimeForUIDisplay(timeString: String): String {
        if (timeString.isEmpty()) return "N/A"

        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val outputFormat = SimpleDateFormat(DATE_FORMAT, Locale.US) // Ensure YYYY-MM-DD for UI
            val date = inputFormat.parse(timeString)
            date?.let { outputFormat.format(it) } ?: "N/A"
        } catch (e: Exception) {
            Log.e("EditprofileFragment", "Error formatting time string '$timeString' for UI: ${e.message}")
            timeString // Return original string if parsing fails
        }
    }
}