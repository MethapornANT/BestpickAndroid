package com.example.reviewhub

import android.app.Activity
import android.content.ContentResolver
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.*

class AddPostFragment : Fragment() {

    private val selectedMedia: MutableList<Uri> = mutableListOf()
    private lateinit var viewPager: ViewPager2
    private lateinit var contentEditText: EditText
    private lateinit var categorySpinner: Spinner
    private lateinit var backButton: ImageView
    private lateinit var selectMediaButton: Button
    private lateinit var dotIndicatorLayout: LinearLayout
    private val client = OkHttpClient()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_add_post, container, false)

        viewPager = view.findViewById(R.id.viewPager)
        contentEditText = view.findViewById(R.id.contentEditText)
        categorySpinner = view.findViewById(R.id.categorySpinner)
        selectMediaButton = view.findViewById(R.id.selectPhotoButton)
        val submitButton = view.findViewById<Button>(R.id.submitButton)
        backButton = view.findViewById(R.id.ic_baseline_arrow_back_24)
        dotIndicatorLayout = view.findViewById(R.id.dot_indicator_layout)

        setupSpinner()

        selectMediaButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "*/*"
            intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            startActivityForResult(intent, 1)
        }

        viewPager.adapter = ImagePagerAdapter(selectedMedia)

        submitButton.setOnClickListener {
            uploadPost()
        }

        backButton.setOnClickListener {
            val intent = Intent(requireContext(), MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            activity?.finish()
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateDotIndicator(position)
            }
        })

        return view
    }

    private fun setupSpinner() {
        val categories = arrayOf("หมวดหมู่ 1", "หมวดหมู่ 2", "หมวดหมู่ 3")
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
                        val mediaUri = it.clipData!!.getItemAt(i).uri
                        selectedMedia.add(mediaUri)
                    }
                } else if (it.data != null) {
                    selectedMedia.add(it.data!!)
                }
                viewPager.adapter?.notifyDataSetChanged()
                setupDotIndicator()
            }
        }
    }

    private fun setupDotIndicator() {
        dotIndicatorLayout.removeAllViews()
        val dots = Array(selectedMedia.size) { ImageView(requireContext()) }
        for (i in dots.indices) {
            dots[i] = ImageView(requireContext())
            dots[i].setImageResource(R.drawable.outline_circle_24)
            dots[i].layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(8, 0, 8, 0)
            }
            dotIndicatorLayout.addView(dots[i])
        }
        updateDotIndicator(0)
    }

    private fun updateDotIndicator(position: Int) {
        val dotCount = dotIndicatorLayout.childCount
        for (i in 0 until dotCount) {
            val imageView = dotIndicatorLayout.getChildAt(i) as ImageView
            if (i == position) {
                imageView.setImageResource(R.drawable.baseline_circle_24)
            } else {
                imageView.setImageResource(R.drawable.outline_circle_24)
            }
        }
    }

    private fun uploadPost() {
        if (selectedMedia.isEmpty()) {
            Toast.makeText(requireContext(), "กรุณาเลือกมีเดียก่อนส่ง.", Toast.LENGTH_SHORT).show()
            return
        }

        val sharedPreferences = context?.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences?.getString("TOKEN", null)
        val id = sharedPreferences?.getString("USER_ID", null)

        if (token == null || id == null) {
            Toast.makeText(requireContext(), "Token หรือ User ID ไม่ถูกต้อง", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedCategory = categorySpinner.selectedItem.toString()
        val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("user_id", id)
            .addFormDataPart("content", contentEditText.text.toString())
            .addFormDataPart("category", selectedCategory)

        selectedMedia.forEach { uri ->
            val file = getFileFromUri(uri)
            if (file != null) {
                val mimeType = requireContext().contentResolver.getType(uri)
                val fieldName = if (mimeType?.startsWith("video") == true) "video" else "photo"
                val mediaType = if (fieldName == "video") "video/mp4".toMediaTypeOrNull() else "image/jpg".toMediaTypeOrNull()
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
        return try {
            val inputStream: InputStream? = requireContext().contentResolver.openInputStream(uri)
            val fileType = requireContext().contentResolver.getType(uri)
            val fileExtension = if (fileType?.startsWith("video") == true) ".mp4" else ".jpg"
            val file = File(requireContext().cacheDir, "temp_file_${System.currentTimeMillis()}$fileExtension")

            if (fileExtension == ".jpg") {
                val bitmap = BitmapFactory.decodeStream(inputStream)
                file.outputStream().use { outputStream ->
                    bitmap?.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                }
            } else {
                file.outputStream().use { outputStream ->
                    inputStream?.copyTo(outputStream)
                }
            }
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
