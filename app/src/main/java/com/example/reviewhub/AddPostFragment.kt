package com.bestpick.reviewhub

import android.app.Activity
import android.content.ContentResolver
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONArray
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

class AddPostFragment : Fragment() {

    private val selectedMedia: MutableList<Uri> = mutableListOf()
    private lateinit var viewPager: ViewPager2
    private var selectedCategoryId: Int? = null
    private lateinit var contentEditText: EditText //content
    private lateinit var TitleEditText: EditText //Detail
    private lateinit var categorySpinner: Spinner
    private lateinit var backButton: ImageView
    private lateinit var selectMediaButton: Button
    private lateinit var dotIndicatorLayout: LinearLayout
    private lateinit var deleteButton: ImageView
    private lateinit var ProductNameEditText: EditText
    private val client = OkHttpClient()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_add_post, container, false)

        viewPager = view.findViewById(R.id.viewPager)
        contentEditText = view.findViewById(R.id.contentEditText) //content
        TitleEditText = view.findViewById(R.id.TitleEditText) //Title
        categorySpinner = view.findViewById(R.id.categorySpinner)
        selectMediaButton = view.findViewById(R.id.selectPhotoButton)
        val submitButton = view.findViewById<Button>(R.id.submitButton)
        backButton = view.findViewById(R.id.ic_baseline_arrow_back_24)
        dotIndicatorLayout = view.findViewById(R.id.dot_indicator_layout)
        deleteButton = view.findViewById(R.id.deleteButton)
        ProductNameEditText = view.findViewById(R.id.ProductNameEditText)


        selectMediaButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "*/*"
            intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            startActivityForResult(intent, 1)
        }
        fetchCategories()
        viewPager.adapter = ImagePagerAdapter(selectedMedia)

        submitButton.setOnClickListener { uploadPost() }

        backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
            val navController = findNavController()

            // Use the correct action ID or destination ID from the navigation graph
            navController.navigate(R.id.action_addPostFragment_to_home)

            val bottomNavigationView = activity?.findViewById<BottomNavigationView>(R.id.bottom_navigation)
            bottomNavigationView?.menu?.findItem(R.id.home)?.isChecked = true
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
                deleteButton.visibility = if (selectedMedia.isEmpty()) View.GONE else View.VISIBLE
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
        // อ่านค่าจากฟิลด์ต่างๆ
        val content = contentEditText.text.toString().trim()
        val Title = TitleEditText.text.toString().trim()
        val category = categorySpinner.selectedItem.toString().trim()
        val ProductNumber = ProductNameEditText.text.toString().trim()

        // ตรวจสอบว่าฟิลด์ต่างๆ ไม่ใช่ค่าว่าง
        if (content.isEmpty()) {
            Toast.makeText(requireContext(), "กรุณากรอกชื่อโพสต์.", Toast.LENGTH_SHORT).show()
            return
        }

        if (Title.isEmpty()) {
            Toast.makeText(requireContext(), "กรุณากรอกรายละเอียด.", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedCategoryId == null) {
            Toast.makeText(requireContext(), "กรุณาเลือกหมวดหมู่.", Toast.LENGTH_SHORT).show()
            return
        }

        if (ProductNumber.isEmpty()) {
            Toast.makeText(requireContext(), "กรุณากรอกชื่อสินค้า.", Toast.LENGTH_SHORT).show()
            return
        }

        val sharedPreferences = context?.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences?.getString("TOKEN", null)
        val id = sharedPreferences?.getString("USER_ID", null)

        if (token == null || id == null) {
            Toast.makeText(requireContext(), "UserID", Toast.LENGTH_SHORT).show()
            return
        }

        val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("user_id", id)
            .addFormDataPart("content", content)
            .addFormDataPart("Title", Title)
            .addFormDataPart("category", selectedCategoryId.toString())
            .addFormDataPart("ProductName", ProductNumber)

        // แยกประเภทไฟล์สำหรับ video และ photo
        var videoFileName: String? = null // แยกตัวแปรสำหรับเก็บชื่อไฟล์วิดีโอ
        val photoFileNames = mutableListOf<String>() // สร้าง List สำหรับเก็บชื่อไฟล์รูปภาพ

        selectedMedia.forEach { uri ->
            val file = getFileFromUri(uri)
            if (file != null) {
                val mimeType = requireContext().contentResolver.getType(uri)
                val mediaType = mimeType?.toMediaTypeOrNull()
                if (mimeType?.startsWith("video") == true) {
                    videoFileName = file.name // กำหนดชื่อไฟล์วิดีโอให้กับตัวแปร videoFileName
                    requestBody.addFormDataPart("video", file.name, RequestBody.create(mediaType, file))
                } else {
                    photoFileNames.add(file.name) // เพิ่มชื่อไฟล์รูปภาพใน List
                    requestBody.addFormDataPart("photo", file.name, RequestBody.create(mediaType, file))
                }
            }
        }

        // เพิ่มฟิลด์ video_url ในกรณีที่มีวิดีโอ
        videoFileName?.let {
            requestBody.addFormDataPart("video_url", it)
        }

        // เพิ่มฟิลด์ photo_url ในกรณีที่มีรูปภาพ
        if (photoFileNames.isNotEmpty()) {
            requestBody.addFormDataPart("photo_url", photoFileNames.joinToString(",")) // แปลง List เป็น String
        }

        val url = getString(R.string.root_url) + "/api/posts/create"
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
                        parentFragmentManager.popBackStack()
                        val navController = findNavController()
                        // Use the correct action ID or destination ID from the navigation graph
                        navController.navigate(R.id.action_addPostFragment_to_home)
                        val bottomNavigationView = activity?.findViewById<BottomNavigationView>(R.id.bottom_navigation)
                        bottomNavigationView?.menu?.findItem(R.id.home)?.isChecked = true
                    } else {
                        val errorBody = response.body?.string() ?: "เกิดข้อผิดพลาดที่ไม่ทราบ"
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
            val fileType = requireContext().contentResolver.getType(uri)
            val fileExtension = if (fileType?.startsWith("video") == true) ".mp4" else ".jpg"
            val file = File(requireContext().cacheDir, "temp_file_${System.currentTimeMillis()}$fileExtension")

            file.outputStream().use { outputStream ->
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

    // Function to fetch categories from the backend
    private fun fetchCategories() {
        val sharedPreferences = context?.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences?.getString("TOKEN", null)

        if (token == null) {

            return
        }

        val url = getString(R.string.root_url) + "/api/type" // Endpoint to fetch categories
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {

                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseData = response.body?.string()
                    val categories = parseCategories(responseData)

                    activity?.runOnUiThread {
                        // Now call setupSpinner with the fetched categories
                        setupSpinner(categories)
                    }
                } else {
                    activity?.runOnUiThread {

                    }
                }
            }
        })
    }
    data class Category(val id: Int, val name: String)

    // Function to parse categories from JSON response
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


    private fun setupSpinner(categories: List<Category>) {
        // Create a mutable list to hold the category names
        val categoryNames = mutableListOf("Select type") // Add the default option first

        // Add the actual category names to the list
        categoryNames.addAll(categories.map { it.name })

        // Set up the adapter using the category names
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categoryNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = adapter

        // Handle selection event to get the selected category ID
        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 0) {
                    // "Select type" is selected, so no valid category is chosen
                    selectedCategoryId = null
                } else {
                    // Adjust position because "Select type" is at position 0
                    val selectedCategory = categories[position - 1]
                    selectedCategoryId = selectedCategory.id
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Handle the case where no category is selected if needed

            }
        }
    }



}
