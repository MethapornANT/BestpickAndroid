package com.example.reviewhub

import android.app.Activity
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
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.File
import java.io.IOException

class EditprofileFragment : Fragment() {

    private lateinit var usernameEditText: EditText
    private lateinit var bioEditText: EditText
    private lateinit var genderSpinner: Spinner
    private lateinit var profileImageView: ImageView
    private lateinit var email: EditText
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
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_editprofile, container, false)
    }

    // Override onViewCreated to set up listeners and other view-related logic
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize sharedPreferences inside onViewCreated (after the fragment is attached to the activity)
        val sharedPreferences = requireActivity().getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences.getString("TOKEN", null)
        val userId = sharedPreferences.getString("USER_ID", null)

        // Initialize EditText, Spinner, and ImageView
        usernameEditText = view.findViewById(R.id.username_edit)
        bioEditText = view.findViewById(R.id.bio_edit)
        genderSpinner = view.findViewById(R.id.gender_spinner)
        profileImageView = view.findViewById(R.id.Imgview)
        email = view.findViewById(R.id.email_edit)
        val editImg = view.findViewById<TextView>(R.id.editImg)

        // Load gender array into Spinner
        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.gender_array, // Use the gender array from strings.xml
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            genderSpinner.adapter = adapter
        }

        // Set up back button functionality
        view.findViewById<TextView>(R.id.back).setOnClickListener {
            requireActivity().onBackPressed()
        }

        editImg.setOnClickListener {
            pickImageFromGallery()
        }



        // Save button functionality
        view.findViewById<TextView>(R.id.save_button).setOnClickListener {
            if (userId != null) {
                updateUserProfile(userId, token)
                requireActivity().onBackPressed()
            }
        }

        // Fetch user profile using token and userId
        if (userId != null) {
            fetchUserProfile(view, userId, token)
        }
    }

    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    // Handle the image selection result
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            imageUri = data?.data
            Log.d("ProfileFragment", "Selected image URI: $imageUri")
            profileImageView.setImageURI(imageUri) // Show the selected image in the ImageView
        }
    }

    // Update ฟังก์ชัน updateUserProfile
    private fun updateUserProfile(userId: String, token: String?) {
        val username = usernameEditText.text.toString()
        val bio = bioEditText.text.toString()
        val gender = genderSpinner.selectedItem.toString()

        if (username.isEmpty() || bio.isEmpty() || gender.isEmpty()) {
            if (isAdded) {
                Toast.makeText(requireContext(), "All fields must be filled", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val requestBodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("username", username)
            .addFormDataPart("bio", bio)
            .addFormDataPart("gender", gender)

        if (imageUri != null) {
            val inputStream = requireContext().contentResolver.openInputStream(imageUri!!)
            val tempFile = File.createTempFile("profile_img", ".jpg", requireContext().cacheDir)
            inputStream?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
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
                        Toast.makeText(requireContext(), "Failed to update profile", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body
                    val jsonResponse = responseBody?.string()

                    if (!jsonResponse.isNullOrEmpty()) {
                        try {
                            val jsonObject = JSONObject(jsonResponse)
                            val profileImg = jsonObject.getString("profileImage")

                            Log.d("ProfileFragment", "Profile Image: $profileImg")

                            if (imageUri != null) {
                                if (isAdded) {
                                    val sharedPreferences = requireActivity().getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
                                    val editor = sharedPreferences.edit()
                                    editor.putString("PICTURE", profileImg)
                                    Log.d("ProfileFragment", "Profile Image saved to SharedPreferences: $profileImg")
                                    editor.apply()
                                }
                            }

                            if (isAdded) {
                                requireActivity().runOnUiThread {
                                    Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show()
                                }
                                requireActivity().onBackPressed()
                            }
                        } catch (e: Exception) {
                            Log.e("ProfileFragment", "Error parsing JSON response: ${e.message}")
                        }
                    } else {
                        if (isAdded) {
                            requireActivity().runOnUiThread {
                                Toast.makeText(requireContext(), "Error: Empty response from server", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    val errorBody = response.body?.string()
                    if (!errorBody.isNullOrEmpty()) {
                        try {
                            val jsonObject = JSONObject(errorBody)
                            val errorMessage = jsonObject.getString("error")

                            if (isAdded) {
                                requireActivity().runOnUiThread {
                                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            if (isAdded) {
                                requireActivity().runOnUiThread {
                                    Toast.makeText(requireContext(), "Error updating profile", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } else {
                        if (isAdded) {
                            requireActivity().runOnUiThread {
                                Toast.makeText(requireContext(), "Error: Empty error response from server", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        })
    }

    // Update ฟังก์ชัน fetchUserProfile
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
                    responseData?.let {
                        try {
                            val jsonObject = JSONObject(it)
                            val username = jsonObject.getString("username")
                            val profileImageUrl = jsonObject.getString("profileImageUrl")
                            val emailuser = jsonObject.getString("email")
                            val bio = jsonObject.getString("bio")
                            val gender = jsonObject.getString("gender")
                            val imgProfileUrl = rootUrl + profileImageUrl

                            if (isAdded) {
                                activity?.runOnUiThread {
                                    usernameEditText.setText(username)
                                    email.setText(emailuser)
                                    bioEditText.setText(bio)
                                    genderSpinner.setSelection(
                                        resources.getStringArray(R.array.gender_array).indexOf(gender)
                                    )

                                    Glide.with(this@EditprofileFragment)
                                        .load(imgProfileUrl)
                                        .centerCrop()
                                        .placeholder(R.drawable.ic_launcher_background)
                                        .into(profileImageView)
                                }
                            }else{
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


}
