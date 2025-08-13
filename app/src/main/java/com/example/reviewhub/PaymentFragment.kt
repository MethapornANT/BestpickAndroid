package com.bestpick.reviewhub

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bestpick.reviewhub.models.UserAd
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class PaymentFragment : Fragment() {

    private var userAd: UserAd? = null
    private var promptPayPayload: String? = null
    private var selectedSlipUri: Uri? = null
    private val client = OkHttpClient()

    private lateinit var packageInfoTextView: TextView
    private lateinit var transferAmountValueTextView: TextView
    private lateinit var qrCodeImageView: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var uploadSlipButton: Button
    private lateinit var slipPreviewImageView: ImageView
    private lateinit var confirmButton: Button

    private val pickSlipLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedSlipUri = uri
                slipPreviewImageView.setImageURI(uri)
                slipPreviewImageView.visibility = View.VISIBLE
                confirmButton.isEnabled = true
                confirmButton.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue)
                confirmButton.setTextColor(Color.WHITE)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            val adJson = it.getString("ad_json")
            if (adJson != null) userAd = Gson().fromJson(adJson, UserAd::class.java)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_payment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViews(view)
        setupClickListeners()

        if (userAd != null) {
            bindData()
            generateQrCode()
        } else {
            Toast.makeText(context, "Error: Ad details not found.", Toast.LENGTH_SHORT).show()
            progressBar.visibility = View.GONE
        }
    }

    private fun initializeViews(view: View) {
        packageInfoTextView = view.findViewById(R.id.packageInfoTextView)
        transferAmountValueTextView = view.findViewById(R.id.transfer_amount_value)
        qrCodeImageView = view.findViewById(R.id.qrCodeImageView)
        progressBar = view.findViewById(R.id.progressBar)
        uploadSlipButton = view.findViewById(R.id.uploadSlipButton)
        slipPreviewImageView = view.findViewById(R.id.slipPreviewImageView)
        confirmButton = view.findViewById(R.id.confirmButton)
        view.findViewById<ImageView>(R.id.backButton).setOnClickListener { findNavController().popBackStack() }
    }

    private fun setupClickListeners() {
        uploadSlipButton.setOnClickListener { openGalleryForSlip() }
        confirmButton.setOnClickListener {
            if (selectedSlipUri != null && !promptPayPayload.isNullOrBlank()) {
                uploadSlip()
            } else {
                Toast.makeText(context, "Please select a slip image first.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindData() {
        val packageName = userAd?.package_name ?: "Unknown Package"
        val packagePrice = userAd?.package_price ?: 0.0
        val packageDuration = userAd?.package_duration ?: 0
        packageInfoTextView.text = "$packageName\n$packageDuration Day - $packagePrice Baht"
        transferAmountValueTextView.text = "%.2f Baht".format(packagePrice)
    }

    private fun generateQrCode() {
        progressBar.visibility = View.VISIBLE
        qrCodeImageView.visibility = View.INVISIBLE

        val qrUrl = "${getString(R.string.root_url2)}/api/generate-qrcode/${userAd!!.order_id}"
        val request = Request.Builder().url(qrUrl).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    Toast.makeText(context, "Failed to generate QR code: ${e.message}", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = View.GONE
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (response.isSuccessful && responseBody != null) {
                    val json = JSONObject(responseBody)
                    val base64Image = json.getString("qrcode_base64")
                    promptPayPayload = json.getString("promptpay_payload")
                    val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
                    val decodedImage = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    activity?.runOnUiThread {
                        qrCodeImageView.setImageBitmap(decodedImage)
                        progressBar.visibility = View.GONE
                        qrCodeImageView.visibility = View.VISIBLE
                    }
                } else {
                    activity?.runOnUiThread {
                        val errorMessage = try { JSONObject(responseBody ?: "{}").getString("message") } catch (_: Exception) { "Unknown error" }
                        Toast.makeText(context, "Error generating QR: $errorMessage", Toast.LENGTH_SHORT).show()
                        progressBar.visibility = View.GONE
                    }
                }
            }
        })
    }

    private fun openGalleryForSlip() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickSlipLauncher.launch(intent)
    }

    private fun uploadSlip() {
        confirmButton.isEnabled = false
        confirmButton.text = "Uploading..."

        val uri = selectedSlipUri
        if (uri == null) {
            Toast.makeText(context, "No slip selected", Toast.LENGTH_SHORT).show()
            confirmButton.isEnabled = true
            confirmButton.text = "Confirm"
            return
        }

        val slipFile = uriToFile(uri, requireContext())
        if (slipFile == null || !slipFile.exists()) {
            Toast.makeText(context, "Cannot read slip image", Toast.LENGTH_SHORT).show()
            confirmButton.isEnabled = true
            confirmButton.text = "Confirm"
            return
        }

        val token = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE).getString("TOKEN", null)

        // ต้องใช้ key "slip_image" ให้ตรงกับ backend
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("slip_image", slipFile.name, slipFile.asRequestBody("image/jpeg".toMediaTypeOrNull()))
            .addFormDataPart("payload", promptPayPayload ?: "")
            .build()

        val uploadUrl = "${getString(R.string.root_url2)}/api/verify-slip/${userAd!!.order_id}"
        val reqBuilder = Request.Builder().url(uploadUrl).post(requestBody)
        if (!token.isNullOrBlank()) reqBuilder.addHeader("Authorization", "Bearer $token")
        val request = reqBuilder.build()

        client.newCall(request).enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                    confirmButton.isEnabled = true
                    confirmButton.text = "Confirm"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                activity?.runOnUiThread {
                    if (response.isSuccessful) {
                        findNavController().navigate(R.id.action_paymentFragment_to_paymentSuccessFragment)
                    } else {
                        val json = try { JSONObject(responseBody ?: "{}") } catch (_: Exception) { JSONObject() }
                        val msg = json.optString("message", "Verification failed.")
                        Toast.makeText(context, "Error: $msg", Toast.LENGTH_LONG).show()
                        confirmButton.isEnabled = true
                        confirmButton.text = "Confirm"
                    }
                }
            }
        })
    }

    private fun uriToFile(uri: Uri, context: Context): File? {
        return try {
            val ext = context.contentResolver.getType(uri)?.substringAfterLast('/') ?: "jpg"
            val file = File(context.cacheDir, "slip_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            file
        } catch (_: IOException) {
            null
        }
    }
}
