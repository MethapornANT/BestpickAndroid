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
            profileImageView.setImageURI(imageUri) // Show the selected image in the ImageView
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
                    responseData?.let {
                        try {
                            val jsonObject = JSONObject(it)
                            val username = jsonObject.getString("username")
                            val profileImageUrl = jsonObject.getString("profileImageUrl")
                            val bio = jsonObject.getString("bio")
                            val gender = jsonObject.getString("gender")

                            val imgProfileUrl = rootUrl + profileImageUrl

                            // Update UI elements on the main thread
                            activity?.runOnUiThread {
                                usernameEditText.setText(username)
                                bioEditText.setText(bio)
                                genderSpinner.setSelection(
                                    resources.getStringArray(R.array.gender_array).indexOf(gender)
                                )

                                // Load the profile image using Glide
                                Glide.with(this@EditprofileFragment)
                                    .load(imgProfileUrl)
                                    .centerCrop()
                                    .placeholder(R.drawable.ic_launcher_background)
                                    .into(profileImageView)
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

    private fun updateUserProfile(userId: String, token: String?) {
        val username = usernameEditText.text.toString()
        val bio = bioEditText.text.toString()
        val gender = genderSpinner.selectedItem.toString()

        // Validate inputs
        if (username.isEmpty() || bio.isEmpty() || gender.isEmpty()) {
            Toast.makeText(requireContext(), "All fields must be filled", Toast.LENGTH_SHORT).show()
            return
        }

        // Create MultipartBody Builder for the profile update
        val requestBodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("username", username)
            .addFormDataPart("bio", bio)
            .addFormDataPart("gender", gender)

        // Add image if selected
        if (imageUri != null) {
            val inputStream = requireContext().contentResolver.openInputStream(imageUri!!)
            val tempFile = File.createTempFile("profile_img", ".jpg", requireContext().cacheDir)
            inputStream?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output) // Copy the InputStream to the temp file
                }
            }

            val mediaType = "image/jpeg".toMediaTypeOrNull() // Update the MIME type if needed
            filename = tempFile.name
            requestBodyBuilder.addFormDataPart(
                "profileImage",
                tempFile.name,
                RequestBody.create(mediaType, tempFile)
            )
        }

        val requestBody = requestBodyBuilder.build()

        // Example: replace with the actual API endpoint
        val url = getString(R.string.root_url) + getString(R.string.userprofileupdate) + userId + "/profile"

        // Build the request
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .put(requestBody)
            .build()

        // Make the asynchronous network request using OkHttp
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Failed to update profile", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val jsonResponse = response.body?.string()

                    try {
                        // Parse the jsonResponse string into a JSONObject
                        val jsonObject = JSONObject(jsonResponse)
                        val profileImg = jsonObject.getString("profileImage")

                        // Log the profile image URL or path
                        Log.d("ProfileFragment", "Profile Image: $profileImg")

                        // Optionally: Store the profile image in SharedPreferences or update the UI
                        val sharedPreferences = requireActivity().getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
                        val editor = sharedPreferences.edit()
                        editor.putString("PICTURE", profileImg)
                        editor.apply()
                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show()
                        }

                    } catch (e: Exception) {
                        // Log any errors in parsing
                        Log.e("ProfileFragment", "Error parsing JSON response: ${e.message}")
                    }
                } else {
                    // Extract error message from the response
                    val errorBody = response.body?.string()
                    try {
                        val jsonObject = JSONObject(errorBody)
                        val errorMessage = jsonObject.getString("error")

                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), "Error updating profile", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })
    }



}
