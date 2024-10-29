package com.example.reviewhub

import android.content.ContentResolver
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import kotlin.math.log

class EditPostFragment : Fragment() {

    private val selectedMedia: MutableList<Uri> = mutableListOf()
    private lateinit var viewPager: ViewPager2
    private var selectedCategoryId: Int? = null
    private lateinit var contentEditText: EditText
    private lateinit var TitleEditText: EditText //Detail
    private lateinit var categorySpinner: Spinner
    private lateinit var backButton: ImageView
    private lateinit var selectMediaButton: Button
    private lateinit var dotIndicatorLayout: LinearLayout
    private lateinit var deleteButton: ImageView
    private lateinit var ProductNameEditText: EditText
    private val client = OkHttpClient()
    private var postId: String? = null
    private lateinit var selectMediaLauncher: ActivityResultLauncher<Intent>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_editpost, container, false)

        viewPager = view.findViewById(R.id.viewPager)
        contentEditText = view.findViewById(R.id.contentEditText)
        TitleEditText = view.findViewById(R.id.TitleEditText) //Title
        categorySpinner = view.findViewById(R.id.categorySpinner)
        selectMediaButton = view.findViewById(R.id.selectPhotoButton)
        val submitButton = view.findViewById<Button>(R.id.submitButton)
        backButton = view.findViewById(R.id.ic_baseline_arrow_back_24)
        dotIndicatorLayout = view.findViewById(R.id.dot_indicator_layout)
        deleteButton = view.findViewById(R.id.deleteButton)
        ProductNameEditText = view.findViewById(R.id.ProductNameEditText)

        postId = if (arguments?.get("POST_ID") is Int) {
            arguments?.getInt("POST_ID").toString()
        } else {
            arguments?.getString("POST_ID")
        }

        println("Post ID: $postId")

        postId?.let { fetchPostDetails(it) }

        // Register activity result launcher for selecting media
        selectMediaLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            data?.let {
                if (it.clipData != null) {
                    val count = it.clipData?.itemCount ?: 0
                    for (i in 0 until count) {
                        val uri = it.clipData!!.getItemAt(i).uri
                        selectedMedia.add(uri)
                    }
                } else {
                    val uri = it.data
                    uri?.let { selectedMedia.add(it) }
                }
                viewPager.adapter?.notifyDataSetChanged()
                setupDotIndicator()
            }
        }

        selectMediaButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "*/*"
            intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            selectMediaLauncher.launch(intent)
        }

        viewPager.adapter = ImagePagerAdapter(selectedMedia)

        submitButton.setOnClickListener { uploadPost() }

        backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
            val bottomNavigationView = activity?.findViewById<BottomNavigationView>(R.id.bottom_navigation)
            bottomNavigationView?.visibility = View.VISIBLE // ทำให้แน่ใจว่าแสดงผล
            bottomNavigationView?.menu?.findItem(R.id.home)?.isChecked = true // อัปเดตการเลือกไปที่หน้า home
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateDotIndicator(position)
                deleteButton.visibility = if (selectedMedia.isNotEmpty()) View.VISIBLE else View.GONE
            }
        })

        deleteButton.setOnClickListener {
            val currentPosition = viewPager.currentItem
            if (selectedMedia.isNotEmpty() && currentPosition < selectedMedia.size) {
                selectedMedia.removeAt(currentPosition)
                viewPager.adapter?.notifyDataSetChanged()
                setupDotIndicator()
                deleteButton.visibility = if (selectedMedia.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        return view
    }

    private fun fetchCategories(callback: (List<Category>) -> Unit) {
        val sharedPreferences = context?.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences?.getString("TOKEN", null)

        if (token == null) {
            Toast.makeText(requireContext(), "Token not available", Toast.LENGTH_SHORT).show()
            return
        }

        val url = getString(R.string.root_url) + "/api/type"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Failed to fetch categories: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseData = response.body?.string()
                    val categories = parseCategories(responseData)

                    activity?.runOnUiThread {
                        callback(categories)
                    }
                } else {
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), "Failed to fetch categories", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun parseCategories(json: String?): List<Category> {
        val categories = mutableListOf<Category>()
        if (json != null) {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val category = jsonArray.getJSONObject(i)
                val id = category.getInt("CategoryID")
                val name = category.getString("CategoryName")
                categories.add(Category(id, name))
            }
        }
        return categories
    }

    private fun setupCategorySpinner(categories: List<Category>, initialSelectedCategoryId: Int?) {
        val categoryNames = categories.map { it.name }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categoryNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = adapter

        initialSelectedCategoryId?.let { selectedId ->
            val categoryIndex = categories.indexOfFirst { it.id == selectedId }
            if (categoryIndex != -1) {
                categorySpinner.setSelection(categoryIndex)
            }
        }

        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (position >= 0 && position < categories.size) {
                    selectedCategoryId = categories[position].id
                } else {
                    selectedCategoryId = null
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Handle the case where no category is selected (optional)
            }
        }
    }

    private fun fetchPostDetails(postId: String) {
        val sharedPreferences = context?.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences?.getString("TOKEN", null)

        if (token == null) {
            Toast.makeText(requireContext(), "Token not available", Toast.LENGTH_SHORT).show()
            return
        }

        val url = getString(R.string.root_url) + "/api/posts/$postId"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                activity?.runOnUiThread {

                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseData = response.body?.string()
                    responseData?.let {
                        val postJson = JSONObject(it)
                        activity?.runOnUiThread {
                            TitleEditText.setText(postJson.getString("Title"))
                            contentEditText.setText(postJson.getString("content"))
                            ProductNameEditText.setText(postJson.getString("ProductName"))

                            val categoryId = if (!postJson.isNull("CategoryID")) {
                                postJson.getInt("CategoryID")
                            } else {
                                -1
                            }

                            fetchCategories { categories ->
                                setupCategorySpinner(categories, categoryId)
                            }

                            val url = getString(R.string.root_url)
                            val photoUrlsArray = postJson.getJSONArray("photo_url")
                            val videoUrlsArray = postJson.getJSONArray("video_url")

                            for (i in 0 until photoUrlsArray.length()) {
                                val innerArray = photoUrlsArray.getJSONArray(i)
                                for (j in 0 until innerArray.length()) {
                                    val photoUrl = innerArray.getString(j)
                                    val photoPath = Uri.parse(photoUrl).path ?: ""
                                    val fullUrl = if (photoPath.startsWith("/api")) {
                                        url + photoPath // ใช้ URL ตรง ๆ เพราะมี /api อยู่แล้ว
                                    } else {
                                        url + "/api" + photoPath // เพิ่ม /api ถ้ายังไม่มี
                                    }
                                    selectedMedia.add(Uri.parse(fullUrl))
                                }
                            }

                            for (i in 0 until videoUrlsArray.length()) {
                                val innerArray = videoUrlsArray.getJSONArray(i)
                                for (j in 0 until innerArray.length()) {
                                    val videoUrl = innerArray.getString(j)
                                    val videoPath = Uri.parse(videoUrl).path ?: ""
                                    val fullUrl = if (videoPath.startsWith("/api")) {
                                        url + videoPath // ใช้ URL ตรง ๆ เพราะมี /api อยู่แล้ว
                                    } else {
                                        url + "/api" + videoPath // เพิ่ม /api ถ้ายังไม่มี
                                    }
                                    selectedMedia.add(Uri.parse(fullUrl))
                                }
                            }

                            viewPager.adapter?.notifyDataSetChanged()
                            setupDotIndicator()



                        }
                    }
                } else {
                    activity?.runOnUiThread {
                    }
                }
            }
        })
    }


    private fun uploadPost() {
        val Title = TitleEditText.text.toString().trim()
        val content = contentEditText.text.toString().trim()
        val productName = ProductNameEditText.text.toString().trim() // เก็บค่า ProductName ที่ได้รับจาก EditText
        val categoryID = selectedCategoryId?.toString() ?: "NULL"  // ส่ง CategoryID เป็น NULL ถ้าไม่ถูกเลือก

        if (content.isEmpty() || Title.isEmpty() || productName.isEmpty()) {
            Toast.makeText(requireContext(), "กรุณากรอกข้อมูลให้ครบถ้วน", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("UPLOAD", "Post ID: $postId, Content: $content, Title: $Title, ProductName: $productName, CategoryID: $categoryID")

        val sharedPreferences = context?.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences?.getString("TOKEN", null)
        val userId = sharedPreferences?.getString("USER_ID", null)

        if (token == null || postId == null || userId == null) {
            Toast.makeText(requireContext(), "User ID ไม่ถูกต้อง", Toast.LENGTH_SHORT).show()
            return
        }

        // สร้าง request body สำหรับอัปเดตโพสต์
        val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("content", content)
            .addFormDataPart("Title", Title)
            .addFormDataPart("ProductName", productName)  // ส่ง ProductName ไปพร้อมกับ request body
            .addFormDataPart("CategoryID", categoryID)  // ส่ง CategoryID
            .addFormDataPart("user_id", userId)

        // เพิ่มรูปภาพ/วิดีโอที่เลือก
        selectedMedia.forEach { uri ->
            if (uri.toString().startsWith("content://")) {
                val file = getFileFromUri(uri)
                if (file != null) {
                    val mimeType = requireContext().contentResolver.getType(uri)
                    val mediaType = mimeType?.toMediaTypeOrNull()
                    if (mimeType?.startsWith("video") == true) {
                        requestBody.addFormDataPart("video", file.name, RequestBody.create(mediaType, file))
                    } else {
                        requestBody.addFormDataPart("photo", file.name, RequestBody.create(mediaType, file))
                    }
                }
            } else {
                val relativePath = Uri.parse(uri.toString()).path
                relativePath?.let {
                    requestBody.addFormDataPart("existing_photos[]", it)
                    Log.d("UPLOAD", "Adding existing photo: $it")
                    Log.d("UPLOAD2", "Adding existing photo: $relativePath")
                }
            }
        }

        val url = getString(R.string.root_url) + "/api/posts/$postId"
        val request = Request.Builder()
            .url(url)
            .put(requestBody.build())
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "ไม่สามารถแก้ไขโพสต์ได้: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                activity?.runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(requireContext(), "แก้ไขโพสต์สำเร็จ", Toast.LENGTH_SHORT).show()
                        parentFragmentManager.popBackStack()
                        val bottomNavigationView = activity?.findViewById<BottomNavigationView>(R.id.bottom_navigation)
                        bottomNavigationView?.visibility = View.VISIBLE // ทำให้แน่ใจว่าแสดงผล
                        bottomNavigationView?.menu?.findItem(R.id.home)?.isChecked = true
                    } else {
                        val errorBody = response.body?.string() ?: "เกิดข้อผิดพลาดที่ไม่ทราบ"
                        Toast.makeText(requireContext(), "ไม่สามารถแก้ไขโพสต์ได้: ${response.message}, Body: $errorBody", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })

    }

    private fun getFileFromUri(uri: Uri): File? {
        val contentResolver: ContentResolver = requireContext().contentResolver
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val fileType = requireContext().contentResolver.getType(uri)
            val fileExtension = if (fileType?.startsWith("video") == true) ".mp4" else ".jpg"
            val file = File(requireContext().cacheDir, "temp_file_${System.currentTimeMillis()}$fileExtension")

            FileOutputStream(file).use { outputStream ->
                inputStream?.copyTo(outputStream)
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

    data class Category(val id: Int, val name: String)
}
