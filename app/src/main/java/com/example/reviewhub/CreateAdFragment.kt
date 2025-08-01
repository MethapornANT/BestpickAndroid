package com.example.reviewhub

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts // สำหรับ Activity Result API
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bestpick.reviewhub.R
import com.bestpick.reviewhub.data.AdPackage // ต้องมี Data Class นี้ (อยู่ในแพ็กเกจ data)
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CreateAdFragment : Fragment() {

    private val client = OkHttpClient()
    private lateinit var radioGroupPackages: RadioGroup
    private lateinit var editTextSelectDate: EditText
    private lateinit var errorTextDate: TextView
    private lateinit var buttonNext: Button
    private lateinit var editTextCaption: EditText
    private lateinit var editTextURL: EditText
    private lateinit var buttonSelectPhoto: Button
    private lateinit var buttonChangePhoto: Button
    private lateinit var imageViewSelectedPhoto: ImageView
    private lateinit var editTextPrompay: EditText

    // *** เปลี่ยนจาก List ของ Calendar เป็น Calendar? สำหรับวันเริ่มต้นวันเดียว ***
    private var selectedStartDate: Calendar? = null
    private var adPackages = listOf<AdPackage>()
    private var selectedPackageId: Int = -1 // ID ของแพ็กเกจที่เลือก
    private var selectedPackageDurationDays: Int = 0 // Duration ของแพ็กเกจที่เลือก

    private var selectedImageUri: Uri? = null

    // Activity Result API สำหรับการเลือกรูปภาพจาก Gallery
    private val pickImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageUri: Uri? = result.data?.data
            imageUri?.let {
                selectedImageUri = it
                imageViewSelectedPhoto.setImageURI(it)
                imageViewSelectedPhoto.visibility = View.VISIBLE
                buttonSelectPhoto.visibility = View.GONE
                buttonChangePhoto.visibility = View.VISIBLE
            }
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.activity_create_ad, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Views
        val backButton: ImageView = view.findViewById(R.id.backButton)
        radioGroupPackages = view.findViewById(R.id.radioGroupPackages)
        editTextSelectDate = view.findViewById(R.id.editTextSelectDate)
        errorTextDate = view.findViewById(R.id.errorTextDate)
        buttonNext = view.findViewById(R.id.buttonNext)
        editTextCaption = view.findViewById(R.id.editTextCaption)
        editTextURL = view.findViewById(R.id.editTextURL)
        buttonSelectPhoto = view.findViewById(R.id.buttonSelectPhoto)
        buttonChangePhoto = view.findViewById(R.id.button4)
        imageViewSelectedPhoto = view.findViewById(R.id.imageViewSelectedPhoto)
        editTextPrompay = view.findViewById(R.id.textPay)

        // Back Button
        backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        // Photo Selection Logic
        buttonSelectPhoto.setOnClickListener {
            openGallery()
        }

        buttonChangePhoto.setOnClickListener {
            openGallery()
        }

        // Fetch Ad Packages from API
        fetchAdPackages()

        // RadioGroup Listener for Package Selection
        radioGroupPackages.setOnCheckedChangeListener { group, checkedId ->
            val checkedRadioButton = view.findViewById<RadioButton>(checkedId)
            val selectedAdPackage = adPackages.find { it.id == checkedRadioButton.tag.toString().toIntOrNull() }
            if (selectedAdPackage != null) {
                selectedPackageId = selectedAdPackage.id
                selectedPackageDurationDays = selectedAdPackage.durationDays // <<< เก็บ duration ของแพ็กเกจที่เลือก
                Toast.makeText(context, "Selected Package: ${selectedAdPackage.name} (${selectedAdPackage.price} baht)", Toast.LENGTH_SHORT).show()
                // เมื่อเลือกแพ็กเกจแล้ว อาจจะตรวจสอบเงื่อนไข "Select two or more days" ทันที
                if (selectedPackageDurationDays < 2) {
                    errorTextDate.visibility = View.VISIBLE
                    errorTextDate.text = "* Selected package duration must be 2 days or more."
                } else {
                    errorTextDate.visibility = View.GONE
                }
            } else {
                selectedPackageId = -1
                selectedPackageDurationDays = 0
            }
        }

        // Date Picker for Advertisement Date
        editTextSelectDate.setOnClickListener {
            showDatePicker()
        }

        // Next Button Click Listener
        buttonNext.setOnClickListener {
            validateInputs()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImage.launch(intent)
    }

    private fun fetchAdPackages() {
        val rootUrl = getString(R.string.root_url)
        val url = "$rootUrl/api/ad-packages"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("CreateAdFragment", "Failed to fetch ad packages: ${e.message}")
                activity?.runOnUiThread {
                    Toast.makeText(context, "Error fetching ad packages", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseData = response.body?.string()
                    responseData?.let {
                        try {
                            val jsonArray = JSONArray(it)
                            val packages = mutableListOf<AdPackage>()
                            for (i in 0 until jsonArray.length()) {
                                val jsonObject = jsonArray.getJSONObject(i)
                                packages.add(
                                    AdPackage(
                                        id = jsonObject.getInt("id"),
                                        name = jsonObject.getString("name"),
                                        description = jsonObject.optString("description", ""),
                                        durationDays = jsonObject.getInt("duration_days"),
                                        price = jsonObject.getDouble("price")
                                    )
                                )
                            }
                            adPackages = packages
                            activity?.runOnUiThread {
                                displayAdPackages(adPackages)
                            }
                        } catch (e: JSONException) {
                            Log.e("CreateAdFragment", "Error parsing ad packages JSON: ${e.message}")
                            activity?.runOnUiThread {
                                Toast.makeText(context, "Error parsing ad package data", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    Log.e("CreateAdFragment", "Server error fetching ad packages: ${response.message}")
                    activity?.runOnUiThread {
                        Toast.makeText(context, "Failed to fetch ad packages", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun displayAdPackages(packages: List<AdPackage>) {
        val radioButtons = listOf(
            view?.findViewById<RadioButton>(R.id.radioPackage1),
            view?.findViewById<RadioButton>(R.id.radioPackage2),
            view?.findViewById<RadioButton>(R.id.radioPackage3)
        )

        packages.forEachIndexed { index, adPackage ->
            if (index < radioButtons.size) {
                radioButtons[index]?.apply {
                    text = "${adPackage.name}\n${adPackage.durationDays} Day\n${adPackage.description}"
                    tag = adPackage.id // เก็บ package ID ไว้ใน tag
                }
            }
        }

        // เลือก RadioButton แรกเป็นค่าเริ่มต้นถ้ามี
        if (packages.isNotEmpty() && radioButtons[0] != null) {
            radioGroupPackages.check(radioButtons[0]!!.id)
            // ตั้งค่า selectedPackageId และ selectedPackageDurationDays สำหรับค่าเริ่มต้น
            selectedPackageId = packages[0].id
            selectedPackageDurationDays = packages[0].durationDays
        }
    }


    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDayOfMonth ->
                val newSelectedDate = Calendar.getInstance()
                newSelectedDate.set(selectedYear, selectedMonth, selectedDayOfMonth, 0, 0, 0)

                // *** ตรวจสอบว่าวันที่เลือกไม่ย้อนหลังกว่า 2 วันถัดจากวันนี้ ***
                val minSelectableDate = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_MONTH, 2)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }

                if (newSelectedDate.before(minSelectableDate)) {
                    Toast.makeText(context, "Please select a date at least 2 days from today.", Toast.LENGTH_SHORT).show()
                    selectedStartDate = null // เคลียร์ค่าหากเลือกวันไม่ถูกต้อง
                } else {
                    selectedStartDate = newSelectedDate // *** เก็บวันเริ่มต้นเพียงวันเดียว ***
                }
                updateSelectedDateEditText() // *** เรียกใช้เมธอดที่ปรับปรุงใหม่ ***
            },
            year,
            month,
            day
        )

        // *** ตั้งค่า minDate ให้เป็น 2 วันถัดจากวันที่ปัจจุบัน ***
        val minAllowedDate = Calendar.getInstance()
        minAllowedDate.add(Calendar.DAY_OF_MONTH, 2)
        datePickerDialog.datePicker.minDate = minAllowedDate.timeInMillis

        datePickerDialog.show()
    }

    // *** เมธอดใหม่/ปรับปรุงสำหรับอัปเดต EditText ด้วยวันเริ่มต้นวันเดียว ***
    private fun updateSelectedDateEditText() {
        if (selectedStartDate != null) {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            editTextSelectDate.setText(dateFormat.format(selectedStartDate!!.time))
            errorTextDate.visibility = View.GONE // ซ่อน Error เมื่อเลือกวันได้แล้ว
        } else {
            editTextSelectDate.setText("")
        }
    }

    private fun validateInputs() {
        val caption = editTextCaption.text.toString().trim()
        val url = editTextURL.text.toString().trim()
        val prompayNumber = editTextPrompay.text.toString().trim()

        if (selectedPackageId == -1) {
            Toast.makeText(context, "Please select an ad package", Toast.LENGTH_SHORT).show()
            return
        }

        // *** ตรวจสอบเงื่อนไข "Select two or more days" จาก selectedPackageDurationDays ***
        if (selectedPackageDurationDays < 2) {
            errorTextDate.visibility = View.VISIBLE
            errorTextDate.text = "* Selected package duration must be 2 days or more."
            Toast.makeText(context, "Please select an ad package with duration of 2 days or more.", Toast.LENGTH_SHORT).show()
            return
        } else {
            errorTextDate.visibility = View.GONE
            // คืนข้อความ errorTextDate กลับไปเป็นข้อความเดิม "Select two or more days"
            // หากคุณต้องการให้มันมีข้อความนี้ตลอดเวลาเมื่อ duration ตรงเงื่อนไข
            // หรือซ่อนไปเลย
            // errorTextDate.text = "* Select two or more days" // ถ้าต้องการให้กลับเป็นข้อความเดิม
        }

        // ตรวจสอบว่ามีการเลือกวันเริ่มต้นแล้ว
        if (selectedStartDate == null) {
            errorTextDate.visibility = View.VISIBLE
            errorTextDate.text = "* Please select an advertisement start date."
            Toast.makeText(context, "Please select an advertisement start date.", Toast.LENGTH_SHORT).show()
            return
        } else {
            // ซ่อน errorTextDate หากเลือกวันถูกต้อง
            errorTextDate.visibility = View.GONE
        }


        if (caption.isEmpty()) {
            editTextCaption.error = "Caption cannot be empty"
            Toast.makeText(context, "Please enter a caption", Toast.LENGTH_SHORT).show()
            return
        }

        if (url.isEmpty()) {
            editTextURL.error = "URL cannot be empty"
            Toast.makeText(context, "Please enter a URL", Toast.LENGTH_SHORT).show()
            return
        }

        if (prompayNumber.isEmpty()) {
            editTextPrompay.error = "Prompay number cannot be empty"
            Toast.makeText(context, "Please enter your Prompay number", Toast.LENGTH_SHORT).show()
            return
        }

        val imageFile: File? = selectedImageUri?.let { uriToFile(it, requireContext()) }

        createOrder(caption, url, selectedPackageId, selectedStartDate!!, imageFile, prompayNumber)

        // Toast.makeText(context, "All inputs are valid! Proceeding to next step.", Toast.LENGTH_SHORT).show()
    }

    private fun uriToFile(uri: Uri, context: Context): File? {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri)
        val fileExtension = mimeType?.substringAfterLast('/') ?: "jpg"
        val fileName = "${System.currentTimeMillis()}.$fileExtension"
        val file = File(context.cacheDir, fileName)
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            return file
        } catch (e: IOException) {
            Log.e("CreateAdFragment", "Error converting URI to file: ${e.message}")
            return null
        }
    }


    private fun createOrder(caption: String, url: String, packageId: Int, startDate: Calendar, imageFile: File?, prompayNumber: String) {
        val sharedPreferences = requireActivity().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val userId = sharedPreferences.getString("USER_ID", null)?.toIntOrNull()
        val token = sharedPreferences.getString("TOKEN", null)

        if (userId == null || token == null) {
            Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val rootUrl = getString(R.string.root_url)
        val orderEndpoint = "/api/orders"
        val createOrderUrl = "$rootUrl$orderEndpoint"

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val adStartDateString = dateFormat.format(startDate.time)

        val requestBodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)

        requestBodyBuilder.addFormDataPart("user_id", userId.toString())
        requestBodyBuilder.addFormDataPart("package_id", packageId.toString())
        requestBodyBuilder.addFormDataPart("title", caption)
        requestBodyBuilder.addFormDataPart("content", "Ad Content")
        requestBodyBuilder.addFormDataPart("link", url)
        requestBodyBuilder.addFormDataPart("prompay_number", prompayNumber)
        requestBodyBuilder.addFormDataPart("ad_start_date", adStartDateString)

        imageFile?.let {
            val mediaType = "image/*".toMediaTypeOrNull()
            if (mediaType != null) {
                requestBodyBuilder.addFormDataPart(
                    "image",
                    it.name,
                    it.asRequestBody(mediaType)
                )
            }
        }

        val requestBody = requestBodyBuilder.build()

        val request = Request.Builder()
            .url(createOrderUrl)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("CreateAdFragment", "Failed to create order: ${e.message}")
                activity?.runOnUiThread {
                    Toast.makeText(context, "Error creating order", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseData = response.body?.string()
                    responseData?.let {
                        try {
                            val jsonResponse = JSONObject(it)
                            val orderId = jsonResponse.optInt("order_id", -1)
                            activity?.runOnUiThread {
                                Toast.makeText(context, "Order created successfully! Order ID: $orderId", Toast.LENGTH_LONG).show()
                                // นำทางไปยังหน้า Payment หรือ Order Detail
                                // findNavController().navigate(R.id.action_createAdFragment_to_orderDetailFragment, Bundle().apply { putInt("orderId", orderId) })
                            }
                        } catch (e: JSONException) {
                            Log.e("CreateAdFragment", "Error parsing order creation response: ${e.message}")
                            activity?.runOnUiThread {
                                Toast.makeText(context, "Error processing order response", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    val errorBody = response.body?.string()
                    Log.e("CreateAdFragment", "Failed to create order: ${response.code} - $errorBody")
                    activity?.runOnUiThread {
                        val errorMessage = try {
                            JSONObject(errorBody).optString("message", "Failed to create order.")
                        } catch (e: JSONException) {
                            "Failed to create order."
                        }
                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }
}