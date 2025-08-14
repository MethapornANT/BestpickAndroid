package com.bestpick.reviewhub

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bestpick.reviewhub.data.AdPackage
import com.bestpick.reviewhub.models.UserAd
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class RenewAdFragment : Fragment() {

    private var userAd: UserAd? = null
    private var adPackages = listOf<AdPackage>()
    private var selectedPackage: AdPackage? = null
    private val client = OkHttpClient()
    private val gson = Gson()
    private var isCreatingOrder = false

    // Views
    private lateinit var adImageView: ImageView
    private lateinit var adTitleTextView: TextView
    private lateinit var radioGroupPackages: RadioGroup
    private lateinit var nextButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            val adJson = it.getString("ad_json")
            if (adJson != null) {
                userAd = gson.fromJson(adJson, UserAd::class.java)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_renew_ad, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViews(view)
        setupClickListeners()
        bindData()
        fetchAdPackages()
    }

    private fun initializeViews(view: View) {
        adImageView = view.findViewById(R.id.adImageView)
        adTitleTextView = view.findViewById(R.id.adTitleTextView)
        radioGroupPackages = view.findViewById(R.id.radioGroupPackages)
        nextButton = view.findViewById(R.id.nextButton)
        view.findViewById<ImageView>(R.id.backButton).setOnClickListener { findNavController().popBackStack() }
    }

    private fun setupClickListeners() {
        nextButton.setOnClickListener {
            if (selectedPackage == null) {
                Toast.makeText(context, "Please select a package to continue.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            createRenewOrder()
        }

        radioGroupPackages.setOnCheckedChangeListener { _, checkedId ->
            val checkedRadioButton = view?.findViewById<RadioButton>(checkedId)
            val selectedTag = checkedRadioButton?.tag as? Int
            selectedPackage = adPackages.find { it.id == selectedTag }
        }
    }

    private fun bindData() {
        if (userAd == null) return
        val rootUrl = getString(R.string.root_url)
        adTitleTextView.text = userAd!!.title

        Glide.with(this)
            .load("$rootUrl${userAd!!.image}")
            .placeholder(R.color.grey)
            .into(adImageView)
    }

    private fun fetchAdPackages() {
        val rootUrl = getString(R.string.root_url)
        val url = "$rootUrl/api/ad-packages"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread { Toast.makeText(context, "Error fetching packages: ${e.message}", Toast.LENGTH_SHORT).show() }
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
                        activity?.runOnUiThread { Toast.makeText(context, "Error parsing package data", Toast.LENGTH_SHORT).show() }
                    }
                }
            }
        })
    }

    private fun displayAdPackages(packages: List<AdPackage>) {
        radioGroupPackages.removeAllViews()
        if (packages.isEmpty()) return

        packages.forEach { adPackage ->
            val radioButton = (LayoutInflater.from(context).inflate(R.layout.item_radio_button_package, radioGroupPackages, false) as RadioButton).apply {
                text = "${adPackage.name}\n${adPackage.durationDays} Days - ${adPackage.price} Baht"
                tag = adPackage.id
                id = View.generateViewId()
            }
            radioGroupPackages.addView(radioButton)
        }

        if (adPackages.isNotEmpty()) {
            (radioGroupPackages.getChildAt(0) as? RadioButton)?.isChecked = true
            selectedPackage = adPackages[0]
        }
    }

    // **[ นี่คือส่วนที่แก้ไข ]**
    private fun createRenewOrder() {
        if (isCreatingOrder) return
        isCreatingOrder = true
        nextButton.isEnabled = false
        nextButton.text = "Processing..."

        val sharedPreferences = requireActivity().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val userId = sharedPreferences.getString("USER_ID", null)
        val token = sharedPreferences.getString("TOKEN", null)
        val prompayNumber = sharedPreferences.getString("PROMPAY_NUMBER", "0000000000")

        // ตรวจสอบว่า userId และ token ไม่ใช่ค่าว่าง ก่อนนำไปใช้
        if (userId.isNullOrBlank() || token.isNullOrBlank()) {
            Toast.makeText(context, "Authentication error. Please log in again.", Toast.LENGTH_SHORT).show()
            isCreatingOrder = false
            nextButton.isEnabled = true
            nextButton.text = "Next"
            return
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("user_id", userId) // userId ตอนนี้ปลอดภัยแล้ว
            .addFormDataPart("package_id", selectedPackage!!.id.toString())
            .addFormDataPart("prompay_number", prompayNumber!!) // prompayNumber มีค่า default อยู่แล้ว
            .addFormDataPart("renew_ads_id", userAd!!.id.toString())
            .build()

        val url = "${getString(R.string.root_url)}/api/orders"
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    Toast.makeText(context, "Failed to create renewal order: ${e.message}", Toast.LENGTH_LONG).show()
                    isCreatingOrder = false
                    nextButton.isEnabled = true
                    nextButton.text = "Next"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                activity?.runOnUiThread {
                    try {
                        if (response.isSuccessful && responseBody != null) {
                            val json = JSONObject(responseBody)
                            val newOrderId = json.getInt("order_id")

                            val adForPayment = userAd!!.copy(
                                order_id = newOrderId,
                                package_name = selectedPackage!!.name,
                                package_price = selectedPackage!!.price.toDouble(),
                                package_duration = selectedPackage!!.durationDays
                            )

                            val adJson = gson.toJson(adForPayment)
                            val bundle = Bundle().apply {
                                putString("ad_json", adJson)
                            }
                            findNavController().navigate(R.id.action_renewAdFragment_to_paymentFragment, bundle)

                        } else {
                            val errorMsg = JSONObject(responseBody).optString("error", "Could not create renewal order.")
                            Toast.makeText(context, "Error: $errorMsg", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: JSONException) {
                        Toast.makeText(context, "Error processing server response.", Toast.LENGTH_LONG).show()
                    } finally {
                        isCreatingOrder = false
                        nextButton.isEnabled = true
                        nextButton.text = "Next"
                    }
                }
            }
        })
    }
}