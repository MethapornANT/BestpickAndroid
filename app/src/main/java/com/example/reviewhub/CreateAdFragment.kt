package com.bestpick.reviewhub

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
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bestpick.reviewhub.data.AdPackage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class CreateAdFragment : Fragment() {

    // --- Properties ---
    private val client = OkHttpClient()
    private val gson = Gson()
    private var adPackages = listOf<AdPackage>()
    private var selectedPackageId: Int = -1
    private var selectedPackageDurationDays: Int = 0
    private var selectedStartDate: Calendar? = null
    private var selectedImageUri: Uri? = null
    private var isCreatingOrder = false

    // --- Views ---
    private lateinit var radioGroupPackages: RadioGroup
    private lateinit var editTextSelectDate: EditText
    private lateinit var errorTextDate: TextView
    private lateinit var buttonNext: Button
    private lateinit var editTextTitle: EditText          // CHANGED
    private lateinit var editTextContent: EditText        // NEW
    private lateinit var editTextURL: EditText
    private lateinit var buttonSelectPhoto: Button
    private lateinit var buttonChangePhoto: Button
    private lateinit var imageViewSelectedPhoto: ImageView
    private lateinit var editTextPrompay: EditText

    // --- Activity Result Launcher ---
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    selectedImageUri = uri
                    imageViewSelectedPhoto.setImageURI(uri)
                    imageViewSelectedPhoto.visibility = View.VISIBLE
                    buttonSelectPhoto.visibility = View.GONE
                    buttonChangePhoto.visibility = View.VISIBLE
                }
            }
        }

    // --- Fragment Lifecycle ---
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.activity_create_ad, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViews(view)
        setupClickListeners()
        fetchAdPackages()
    }

    // --- Initialization ---
    private fun initializeViews(view: View) {
        radioGroupPackages = view.findViewById(R.id.radioGroupPackages)
        editTextSelectDate = view.findViewById(R.id.editTextSelectDate)
        errorTextDate = view.findViewById(R.id.errorTextDate)
        buttonNext = view.findViewById(R.id.buttonNext)
        editTextTitle = view.findViewById(R.id.editTextTitle)          // CHANGED
        editTextContent = view.findViewById(R.id.editTextContent)      // NEW
        editTextURL = view.findViewById(R.id.editTextURL)
        buttonSelectPhoto = view.findViewById(R.id.buttonSelectPhoto)
        buttonChangePhoto = view.findViewById(R.id.changePhotoButton)
        imageViewSelectedPhoto = view.findViewById(R.id.imageViewSelectedPhoto)
        editTextPrompay = view.findViewById(R.id.promptPayEditText)
    }

    private fun setupClickListeners() {
        view?.findViewById<ImageView>(R.id.backButton)?.setOnClickListener { findNavController().popBackStack() }
        buttonSelectPhoto.setOnClickListener { openGallery() }
        buttonChangePhoto.setOnClickListener { openGallery() }
        editTextSelectDate.setOnClickListener { showDatePicker() }
        buttonNext.setOnClickListener {
            if (validateAllInputs()) {
                createOrder()
            }
        }
        radioGroupPackages.setOnCheckedChangeListener { _, checkedId ->
            val checkedRadioButton = view?.findViewById<RadioButton>(checkedId)
            val selectedTag = checkedRadioButton?.tag as? Int
            val selectedAdPackage = adPackages.find { it.id == selectedTag }

            selectedAdPackage?.let {
                selectedPackageId = it.id
                selectedPackageDurationDays = it.durationDays
                validatePackageDuration()
            }
        }
    }

    // --- Network Calls ---
    private fun fetchAdPackages() {
        val rootUrl = getString(R.string.root_url)
        val url = "$rootUrl/api/ad-packages"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("CreateAdFragment", "Failed to fetch ad packages", e)
                activity?.runOnUiThread {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (response.isSuccessful && responseBody != null) {
                    try {
                        val packageListType = object : TypeToken<List<AdPackage>>() {}.type
                        val packages: List<AdPackage> = gson.fromJson(responseBody, packageListType)
                        adPackages = packages
                        activity?.runOnUiThread { displayAdPackages(packages) }
                    } catch (e: Exception) {
                        Log.e("CreateAdFragment", "Error parsing ad packages JSON", e)
                        activity?.runOnUiThread {
                            Toast.makeText(context, "Error parsing server data", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Log.e("CreateAdFragment", "Server error: ${response.code} ${response.message}")
                    activity?.runOnUiThread {
                        Toast.makeText(context, "Failed to fetch packages", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun createOrder() {
        if (isCreatingOrder) return

        isCreatingOrder = true
        buttonNext.isEnabled = false
        buttonNext.text = "Creating..."

        val sharedPreferences = requireActivity().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val userId = sharedPreferences.getString("USER_ID", null)
        val token = sharedPreferences.getString("TOKEN", null)

        if (userId.isNullOrBlank() || token.isNullOrBlank()) {
            Toast.makeText(context, "User not logged in.", Toast.LENGTH_LONG).show()
            isCreatingOrder = false
            buttonNext.isEnabled = true
            buttonNext.text = "Next"
            return
        }

        val imageFile = selectedImageUri?.let { uriToFile(it, requireContext()) }
        if (imageFile == null) {
            Toast.makeText(context, "Could not process the selected image.", Toast.LENGTH_SHORT).show()
            isCreatingOrder = false
            buttonNext.isEnabled = true
            buttonNext.text = "Next"
            return
        }

        val title = editTextTitle.text.toString().trim()          // CHANGED
        val content = editTextContent.text.toString().trim()      // NEW
        val url = editTextURL.text.toString().trim()
        val prompayNumber = editTextPrompay.text.toString().trim()
        val adStartDateString = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(selectedStartDate!!.time)

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("user_id", userId)
            .addFormDataPart("package_id", selectedPackageId.toString())
            .addFormDataPart("title", title)                      // CHANGED
            .addFormDataPart("content", content)                  // CHANGED
            .addFormDataPart("link", url)
            .addFormDataPart("prompay_number", prompayNumber)
            .addFormDataPart("ad_start_date", adStartDateString)
            .addFormDataPart("image", imageFile.name, imageFile.asRequestBody("image/*".toMediaTypeOrNull()))
            .build()

        val rootUrl = getString(R.string.root_url)
        val createOrderUrl = "$rootUrl/api/orders"
        val request = Request.Builder()
            .url(createOrderUrl)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("CreateAdFragment", "Failed to create order", e)
                activity?.runOnUiThread {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    isCreatingOrder = false
                    buttonNext.isEnabled = true
                    buttonNext.text = "Next"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                activity?.runOnUiThread {
                    try {
                        if (response.isSuccessful && responseBody != null) {
                            val jsonResponse = JSONObject(responseBody)
                            val orderId = jsonResponse.optInt("order_id", -1)
                            if (orderId != -1) {
                                findNavController().navigate(R.id.action_createAdFragment_to_adPendingFragment)
                            } else {
                                Toast.makeText(context, "Failed to get Order ID from server.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            val errorMessage = try {
                                JSONObject(responseBody).getString("error")
                            } catch (e: Exception) {
                                "Failed to create order (${response.code})"
                            }
                            Log.e("CreateAdFragment", "Failed to create order: ${response.code} - $responseBody")
                            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                        }
                    } finally {
                        isCreatingOrder = false
                        buttonNext.isEnabled = true
                        buttonNext.text = "Next"
                    }
                }
            }
        })
    }

    // --- UI and View Logic ---
    private fun displayAdPackages(packages: List<AdPackage>) {
        radioGroupPackages.removeAllViews()
        if (packages.isEmpty()) {
            val noPackageTextView = TextView(context).apply { text = "Could not load ad packages." }
            radioGroupPackages.addView(noPackageTextView)
            return
        }

        val marginBottomInPixels = (16 * resources.displayMetrics.density).toInt()

        packages.forEach { adPackage ->
            val radioButton =
                (LayoutInflater.from(context).inflate(R.layout.item_radio_button_package, radioGroupPackages, false) as RadioButton).apply {
                    text = "${adPackage.name}\n${adPackage.durationDays} Days - ${adPackage.price} Baht"
                    tag = adPackage.id
                    id = View.generateViewId()

                    val params = RadioGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    params.bottomMargin = marginBottomInPixels
                    layoutParams = params
                }
            radioGroupPackages.addView(radioButton)
        }

        if (adPackages.isNotEmpty()) {
            (radioGroupPackages.getChildAt(0) as? RadioButton)?.isChecked = true
            selectedPackageId = adPackages[0].id
            selectedPackageDurationDays = adPackages[0].durationDays
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun showDatePicker() {
        val calendar = selectedStartDate ?: Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val newSelectedDate = Calendar.getInstance().apply { set(year, month, dayOfMonth, 0, 0, 0) }
                selectedStartDate = newSelectedDate
                updateSelectedDateEditText()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        val minAllowedDate = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 2) }
        datePickerDialog.datePicker.minDate = minAllowedDate.timeInMillis
        datePickerDialog.show()
    }

    private fun updateSelectedDateEditText() {
        selectedStartDate?.let {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            editTextSelectDate.setText(dateFormat.format(it.time))
            if (errorTextDate.text.contains("date")) {
                errorTextDate.visibility = View.GONE
            }
        } ?: editTextSelectDate.setText("")
    }

    // --- Validation ---
    private fun validateAllInputs(): Boolean {
        if (selectedImageUri == null) {
            Toast.makeText(context, "Please select a photo", Toast.LENGTH_SHORT).show(); return false
        }
        if (editTextTitle.text.isBlank()) {                 // CHANGED
            editTextTitle.error = "Title cannot be empty"; return false
        }
        if (editTextContent.text.isBlank()) {               // NEW
            editTextContent.error = "Content cannot be empty"; return false
        }
        if (editTextURL.text.isBlank()) {
            editTextURL.error = "URL cannot be empty"; return false
        }
        val prompay = editTextPrompay.text?.toString()?.filter(Char::isDigit).orEmpty()
        if (!(prompay.length == 10 || prompay.length == 13)) {
            editTextPrompay.error = "Please enter a valid Prompay number (10 or 13 digits)"
            return false
        } else {
            editTextPrompay.error = null
        }
        if (selectedPackageId == -1) {
            Toast.makeText(context, "Please select an ad package", Toast.LENGTH_SHORT).show(); return false
        }
        if (!validatePackageDuration()) {
            return false
        }
        if (selectedStartDate == null) {
            errorTextDate.text = "* Please select an advertisement start date."; errorTextDate.visibility = View.VISIBLE; return false
        }
        return true
    }

    private fun validatePackageDuration(): Boolean {
        return if (selectedPackageDurationDays > 0 && selectedPackageDurationDays < 2) {
            errorTextDate.text = "* Selected package duration must be 2 days or more."
            errorTextDate.visibility = View.VISIBLE
            false
        } else {
            if (errorTextDate.text.contains("duration")) { errorTextDate.visibility = View.GONE }
            true
        }
    }

    // --- Utility ---
    private fun uriToFile(uri: Uri, context: Context): File? {
        return try {
            val contentResolver = context.contentResolver
            val fileExtension = contentResolver.getType(uri)?.substringAfterLast('/') ?: "jpg"
            val file = File(context.cacheDir, "upload_${System.currentTimeMillis()}.$fileExtension")
            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(file).use { outputStream -> inputStream.copyTo(outputStream) }
            }
            file
        } catch (e: IOException) {
            Log.e("CreateAdFragment", "Error converting URI to file", e)
            null
        }
    }
}
