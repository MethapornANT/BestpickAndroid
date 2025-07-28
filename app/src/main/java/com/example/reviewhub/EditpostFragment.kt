package com.bestpick.reviewhub

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
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull

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
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

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

        // Initial fetch of post details when fragment view is created
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
                // Show delete button if media is added
                deleteButton.visibility = if (selectedMedia.isNotEmpty()) View.VISIBLE else View.GONE
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
                // Hide delete button if no media left
                deleteButton.visibility = if (selectedMedia.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        return view
    }

    private fun fetchCategories(callback: (List<Category>) -> Unit) {
        val sharedPreferences = context?.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences?.getString("TOKEN", null)

        if (token == null) {
            activity?.runOnUiThread { // Added runOnUiThread here as well for safety
                Toast.makeText(requireContext(), "Token not available", Toast.LENGTH_SHORT).show()
            }
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
                // Read responseBodyString outside runOnUiThread
                val responseData = response.body?.string()

                activity?.runOnUiThread {
                    if (response.isSuccessful) {
                        val categories = parseCategories(responseData)
                        callback(categories)
                    } else {
                        Toast.makeText(requireContext(), "Failed to fetch categories: ${response.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun parseCategories(json: String?): List<Category> {
        val categories = mutableListOf<Category>()
        if (json != null) {
            try {
                val jsonArray = JSONArray(json)
                for (i in 0 until jsonArray.length()) {
                    val category = jsonArray.getJSONObject(i)
                    val id = category.optInt("CategoryID") // Using optInt for safety
                    val name = category.optString("CategoryName") // Using optString for safety
                    categories.add(Category(id, name))
                }
            } catch (e: Exception) {
                Log.e("EditPostFragment", "Error parsing categories JSON: ${e.message}", e)
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Error parsing category data.", Toast.LENGTH_SHORT).show()
                }
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
            activity?.runOnUiThread { // Added runOnUiThread here as well for safety
                Toast.makeText(requireContext(), "Token not available", Toast.LENGTH_SHORT).show()
            }
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
                    Toast.makeText(requireContext(), "Failed to fetch post details: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string() // Read response data on background thread

                activity?.runOnUiThread {
                    if (response.isSuccessful) {
                        responseData?.let {
                            try {
                                val postJson = JSONObject(it)
                                TitleEditText.setText(postJson.optString("Title", "")) // Use optString
                                contentEditText.setText(postJson.optString("content", "")) // Use optString
                                ProductNameEditText.setText(postJson.optString("ProductName", "")) // Use optString

                                val categoryId = postJson.optInt("CategoryID", -1) // Use optInt and provide default

                                fetchCategories { categories ->
                                    setupCategorySpinner(categories, categoryId)
                                }

                                val baseUrl = getString(R.string.root_url) + "/api" // Changed to baseUrl for clarity
                                selectedMedia.clear() // Clear existing media before adding new ones

                                val photoUrlsArray = postJson.optJSONArray("photo_url")
                                photoUrlsArray?.let { array ->
                                    for (i in 0 until array.length()) {
                                        val item = array.opt(i) // Use opt to get object safely
                                        if (item is JSONArray) {
                                            // Handle case: [[url1], [url2]]
                                            for (j in 0 until item.length()) {
                                                val photoUrl = item.optString(j, "")
                                                if (photoUrl.isNotEmpty()) {
                                                    val fullUrl = Uri.parse(baseUrl + photoUrl)
                                                    Log.d("PhotoUrls", "Photo URL (JSONArray in JSONArray): $fullUrl")
                                                    selectedMedia.add(fullUrl)
                                                }
                                            }
                                        } else if (item is String) {
                                            // Handle case: [url1, url2]
                                            val photoUrl = item
                                            if (photoUrl.isNotEmpty()) {
                                                val fullUrl = Uri.parse(baseUrl + photoUrl)
                                                Log.d("PhotoUrls", "Photo URL (String directly): $fullUrl")
                                                selectedMedia.add(fullUrl)
                                            }
                                        }
                                    }
                                }

                                val videoUrlsArray = postJson.optJSONArray("video_url")
                                videoUrlsArray?.let { array ->
                                    for (i in 0 until array.length()) {
                                        val item = array.opt(i) // Use opt to get object safely
                                        if (item is JSONArray) {
                                            // Handle case: [[url1], [url2]]
                                            for (j in 0 until item.length()) {
                                                val videoUrl = item.optString(j, "")
                                                if (videoUrl.isNotEmpty()) {
                                                    val fullUrl = Uri.parse(baseUrl + videoUrl)
                                                    Log.d("VideoUrls", "Video URL (JSONArray in JSONArray): $fullUrl")
                                                    selectedMedia.add(fullUrl)
                                                }
                                            }
                                        } else if (item is String) {
                                            // Handle case: [url1, url2]
                                            val videoUrl = item
                                            if (videoUrl.isNotEmpty()) {
                                                val fullUrl = Uri.parse(baseUrl + videoUrl)
                                                Log.d("VideoUrls", "Video URL (String directly): $fullUrl")
                                                selectedMedia.add(fullUrl)
                                            }
                                        }
                                    }
                                }

                                // อัปเดต ViewPager และ Dot Indicator
                                viewPager.adapter?.notifyDataSetChanged()
                                setupDotIndicator()
                                deleteButton.visibility = if (selectedMedia.isNotEmpty()) View.VISIBLE else View.GONE

                            } catch (e: Exception) {
                                Log.e("fetchPostDetails", "Error parsing post details JSON: ${e.message}", e)
                                Toast.makeText(requireContext(), "Error parsing post details.", Toast.LENGTH_SHORT).show()
                            }
                        } ?: run {
                            Toast.makeText(requireContext(), "Response data is empty.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(requireContext(), "Failed to fetch post details: ${response.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }


    private fun uploadPost() {
        val Title = TitleEditText.text.toString().trim()
        val content = contentEditText.text.toString().trim()
        val productName = ProductNameEditText.text.toString().trim()
        val categoryID = selectedCategoryId?.toString() ?: "NULL"

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

        val requestBodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("content", content)
            .addFormDataPart("Title", Title)
            .addFormDataPart("ProductName", productName)
            .addFormDataPart("CategoryID", categoryID)
            .addFormDataPart("user_id", userId)

        val existingPhotoPaths = mutableListOf<String>()
        val existingVideoPaths = mutableListOf<String>()

        selectedMedia.forEach { uri ->
            if (uri.toString().startsWith("content://")) { // New media from gallery
                val file = getFileFromUri(uri)
                if (file != null) {
                    val mimeType = requireContext().contentResolver.getType(uri)
                    val mediaType = mimeType?.toMediaTypeOrNull()
                    if (mimeType?.startsWith("video") == true) {
                        requestBodyBuilder.addFormDataPart("video", file.name, RequestBody.create(mediaType, file))
                    } else {
                        requestBodyBuilder.addFormDataPart("photo", file.name, RequestBody.create(mediaType, file))
                    }
                }
            } else { // Existing media from API
                val fullPath = uri.path
                if (fullPath != null) {
                    // Extract relative path like /uploads/filename.jpg or /uploads/filename.mp4
                    val relativePath = if (fullPath.startsWith("/api/uploads/")) {
                        fullPath.substringAfter("/api")
                    } else if (fullPath.startsWith("/uploads/")) {
                        fullPath
                    } else {
                        null // Should not happen if paths are consistent
                    }

                    relativePath?.let {
                        // Determine if it's a video based on extension
                        if (it.endsWith(".mp4", true) || it.endsWith(".mov", true) || it.endsWith(".avi", true)) {
                            existingVideoPaths.add(it)
                        } else {
                            existingPhotoPaths.add(it)
                        }
                    }
                }
            }
        }

        // Add existing media paths as JSON Arrays
        if (existingPhotoPaths.isNotEmpty()) {
            requestBodyBuilder.addFormDataPart("existing_photos", JSONArray(existingPhotoPaths).toString())
            Log.d("UPLOAD", "Existing Photos JSON: ${JSONArray(existingPhotoPaths).toString()}")
        }
        if (existingVideoPaths.isNotEmpty()) {
            requestBodyBuilder.addFormDataPart("existing_videos", JSONArray(existingVideoPaths).toString())
            Log.d("UPLOAD", "Existing Videos JSON: ${JSONArray(existingVideoPaths).toString()}")
        }

        val url = getString(R.string.root_url2) + "/ai/posts/$postId"
        val request = Request.Builder()
            .url(url)
            .put(requestBodyBuilder.build())
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
                val responseBodyString = response.body?.string() // <--- Read response body on background thread!

                activity?.runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(requireContext(), "แก้ไขโพสต์สำเร็จ", Toast.LENGTH_SHORT).show()
                        parentFragmentManager.popBackStack()
                        val bottomNavigationView = activity?.findViewById<BottomNavigationView>(R.id.bottom_navigation)
                        bottomNavigationView?.visibility = View.VISIBLE
                        bottomNavigationView?.menu?.findItem(R.id.home)?.isChecked = true
                    } else {
                        try {
                            if (!responseBodyString.isNullOrEmpty()) {
                                val errorJson = JSONObject(responseBodyString)
                                val status = errorJson.optString("status")
                                val message = errorJson.optString("message", "ไม่สามารถแก้ไขโพสต์ได้: เกิดข้อผิดพลาด")
                                val suggestion = errorJson.optString("suggestion", "")

                                var displayMessage = message
                                if (suggestion.isNotEmpty()) {
                                    displayMessage += "\n$suggestion"
                                }
                                Toast.makeText(requireContext(), displayMessage, Toast.LENGTH_LONG).show()

                                if (status != "warning") {
                                    // If not a warning from NSFW detection, might consider popping back
                                    // parentFragmentManager.popBackStack()
                                }
                            } else {
                                Toast.makeText(requireContext(), "ไม่สามารถแก้ไขโพสต์ได้: ${response.message}. Response body is empty.", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), "ไม่สามารถแก้ไขโพสต์ได้: ${response.message}. Error parsing response.", Toast.LENGTH_LONG).show()
                            e.printStackTrace()
                        }
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
            // Determine file extension based on MIME type, default to .jpg
            val fileExtension = when {
                fileType?.startsWith("video") == true -> ".mp4"
                fileType?.startsWith("image") == true -> ".jpg"
                else -> ".tmp" // Fallback for unknown types
            }
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
        // Ensure dot indicator is updated for the initial state if media exists
        if (selectedMedia.isNotEmpty()) {
            updateDotIndicator(viewPager.currentItem)
        }
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