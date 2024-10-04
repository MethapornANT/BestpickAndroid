package com.example.reviewhub

import android.app.Activity
import android.content.ContentResolver
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.Fragment
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

class AddPostFragment : Fragment() {

    private val selectedImages: MutableList<Uri> = mutableListOf()
    private lateinit var imageView: ImageView
    private lateinit var contentEditText: EditText
    private lateinit var categorySpinner: Spinner // Spinner สำหรับเลือกหมวดหมู่
    private val client = OkHttpClient()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_add_post, container, false)

        imageView = view.findViewById(R.id.imageView)
        contentEditText = view.findViewById(R.id.contentEditText)
        categorySpinner = view.findViewById(R.id.categorySpinner) // สร้าง Spinner
        val submitButton = view.findViewById<Button>(R.id.submitButton)

        setupSpinner() // เรียกฟังก์ชันสำหรับตั้งค่า Spinner

        imageView.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "*/*"
            intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            startActivityForResult(intent, 1)
        }

        submitButton.setOnClickListener {
            uploadPost()
        }

        return view
    }

    private fun setupSpinner() {
        val categories = arrayOf("หมวดหมู่ 1", "หมวดหมู่ 2", "หมวดหมู่ 3") // รายการหมวดหมู่
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = adapter
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            data?.let {
                if (it.clipData != null) {
                    val count = it.clipData!!.itemCount
                    for (i in 0 until count) {
                        val imageUri = it.clipData!!.getItemAt(i).uri
                        selectedImages.add(imageUri)
                    }
                } else if (it.data != null) {
                    selectedImages.add(it.data!!)
                }
                updateImageView()
            }
        }
    }

    private fun updateImageView() {
        if (selectedImages.isNotEmpty()) {
            imageView.setImageURI(selectedImages[0])
        }
    }

    private fun uploadPost() {
        if (selectedImages.isEmpty()) {
            Toast.makeText(requireContext(), "กรุณาเลือกภาพหรือวิดีโอ ก่อนส่ง.", Toast.LENGTH_SHORT).show()
            return
        }

        val sharedPreferences = context?.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences?.getString("TOKEN", null)
        val id = sharedPreferences?.getString("USER_ID", null)

        if (token == null || id == null) {
            Toast.makeText(requireContext(), "Token หรือ User ID ไม่ถูกต้อง", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedCategory = categorySpinner.selectedItem.toString() // รับค่าหมวดหมู่ที่เลือก
        val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("user_id", id)
            .addFormDataPart("content", contentEditText.text.toString())
            .addFormDataPart("category", selectedCategory) // เพิ่มหมวดหมู่ในข้อมูลโพสต์

        selectedImages.forEach { uri ->
            val file = getFileFromUri(uri)
            if (file != null) {
                val mediaType = when {
                    uri.toString().endsWith(".mp4") || uri.toString().endsWith(".mkv") -> "video/*".toMediaTypeOrNull()
                    else -> "image/*".toMediaTypeOrNull()
                }

                // เก็บวิดีโอในฐานข้อมูลแยกจากรูปภาพ
                val fieldName = if (mediaType.toString().startsWith("video")) "video" else "photo"
                requestBody.addFormDataPart(fieldName, file.name, RequestBody.create(mediaType, file))
            }
        }

        val url = getString(R.string.root_url) + "/posts/create"
        val request = Request.Builder()
            .url(url)
            .post(requestBody.build())
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "ไม่สามารถสร้างโพสต์ได้: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                activity?.runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(requireContext(), "สร้างโพสต์สำเร็จ", Toast.LENGTH_SHORT).show()
                        val intent = Intent(requireContext(), MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                        activity?.finish()
                    } else {
                        val errorBody = response.body?.string() ?: "เกิดข้อผิดพลาดที่ไม่ทราบ"
                        Log.e("AddPostFragment", "Error creating post: ${response.code}, Body: $errorBody")
                        Toast.makeText(requireContext(), "ไม่สามารถสร้างโพสต์ได้: ${response.message}, Body: $errorBody", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun getFileFromUri(uri: Uri): File? {
        val contentResolver: ContentResolver = requireContext().contentResolver
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val file = File(requireContext().cacheDir, "temp_file_${System.currentTimeMillis()}")
            file.outputStream().use { outputStream ->
                inputStream?.copyTo(outputStream)
            }
            inputStream?.close() // Close the InputStream
            file
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            null
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}
