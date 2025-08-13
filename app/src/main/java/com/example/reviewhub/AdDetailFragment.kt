package com.bestpick.reviewhub

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bestpick.reviewhub.models.UserAd
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okio.source
import org.json.JSONObject
import java.io.File
import java.io.IOException

class AdDetailFragment : Fragment() {

    private var userAd: UserAd? = null
    private var adIdArg: Int? = null
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { args ->
            val adJson = args.getString("ad_json")
            adIdArg = if (args.containsKey("ad_id")) args.getInt("ad_id") else null
            if (!adJson.isNullOrBlank()) {
                try { userAd = Gson().fromJson(adJson, UserAd::class.java) } catch (_: JsonSyntaxException) {}
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_ad_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageView>(R.id.backButton)?.setOnClickListener {
            findNavController().popBackStack()
        }

        userAd?.let { bindAdData(view, it) } ?: run {
            val id = adIdArg
            if (id == null) {
                Toast.makeText(context, "No Ad ID provided", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            } else {
                fetchAdById(id) { ad ->
                    if (!isAdded) return@fetchAdById
                    if (ad == null) {
                        Toast.makeText(context, "Failed to load ad details", Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                    } else {
                        userAd = ad
                        bindAdData(view, ad)
                    }
                }
            }
        }
    }

    private fun fetchAdById(adId: Int, cb: (UserAd?) -> Unit) {
        val token = requireActivity()
            .getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
            .getString("TOKEN", null)

        if (token.isNullOrEmpty()) { cb(null); return }

        val url = "${getString(R.string.root_url)}/api/my/ads/$adId"
        val req = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("AdDetail", "fetch fail", e)
                activity?.runOnUiThread { cb(null) }
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        Log.e("AdDetail", "HTTP ${it.code}")
                        activity?.runOnUiThread { cb(null) }
                        return
                    }
                    val body = it.body?.string()
                    val ad = try { Gson().fromJson(body, UserAd::class.java) } catch (_: Exception) { null }
                    activity?.runOnUiThread { cb(ad) }
                }
            }
        })
    }

    private fun bindAdData(view: View, ad: UserAd) {
        val imageView = view.findViewById<ImageView>(R.id.adImageView)
        val titleView = view.findViewById<TextView>(R.id.adTitleTextView)
        val statusView = view.findViewById<TextView>(R.id.adStatusTextView)
        val packageInfoView = view.findViewById<TextView>(R.id.packageInfoTextView)
        val dateView = view.findViewById<TextView>(R.id.adDateTextView)
        val datesLabel = view.findViewById<TextView>(R.id.datesLabel)
        val detailMessageView = view.findViewById<TextView>(R.id.adDetailMessageTextView)
        val payButton = view.findViewById<Button>(R.id.payButton)
        val renewButton = view.findViewById<Button>(R.id.renewButton)
        val deleteButton = view.findViewById<Button>(R.id.deleteButton)

        val rootUrl = getString(R.string.root_url)

        titleView.text = ad.title
        statusView.text = ad.status.replaceFirstChar { it.uppercase() }

        val packageName = ad.package_name ?: "N/A"
        val packagePrice = ad.package_price ?: 0.0
        packageInfoView.text = "$packageName - %.2f Baht".format(packagePrice)

        payButton.visibility = View.GONE
        renewButton.visibility = View.GONE
        deleteButton.visibility = View.GONE
        dateView.visibility = View.GONE
        datesLabel.visibility = View.GONE

        val statusColor: Int
        when (ad.status.lowercase()) {
            "pending" -> {
                statusColor = ContextCompat.getColor(requireContext(), R.color.yellow)
                detailMessageView.text = "Your ad is currently waiting for review by an administrator."
                deleteButton.visibility = View.VISIBLE
            }
            "approved" -> {
                statusColor = ContextCompat.getColor(requireContext(), R.color.blue)
                detailMessageView.text = "Your ad has been approved. Please complete the payment to make it active."
                payButton.visibility = View.VISIBLE
                deleteButton.visibility = View.VISIBLE
            }
            "active" -> {
                statusColor = ContextCompat.getColor(requireContext(), R.color.green)
                detailMessageView.text = "Your ad is currently active."
                datesLabel.visibility = View.VISIBLE
                dateView.visibility = View.VISIBLE
                dateView.text = "${ad.show_at} to ${ad.expiration_date}"
                renewButton.visibility = View.VISIBLE
            }
            "paid" -> {
                statusColor = ContextCompat.getColor(requireContext(), R.color.purple_200)
                detailMessageView.text = "Payment received. Please wait for an admin to activate your ad."
            }
            "rejected" -> {
                statusColor = ContextCompat.getColor(requireContext(), R.color.red)
                detailMessageView.text = "This ad was rejected. Reason: ${ad.admin_notes ?: "Not specified"}"
                deleteButton.visibility = View.VISIBLE
            }
            "expired" -> {
                statusColor = ContextCompat.getColor(requireContext(), R.color.grey)
                detailMessageView.text = "This ad has expired."
                datesLabel.visibility = View.VISIBLE
                dateView.visibility = View.VISIBLE
                dateView.text = "Expired on: ${ad.expiration_date}"
                renewButton.visibility = View.VISIBLE
            }
            else -> {
                statusColor = ContextCompat.getColor(requireContext(), R.color.grey)
                detailMessageView.text = "Status: ${ad.status}"
            }
        }
        statusView.background?.setTint(statusColor)

        payButton.setOnClickListener {
            val bundle = Bundle().apply { putString("ad_json", Gson().toJson(ad)) }
            findNavController().navigate(R.id.action_adDetailFragment_to_paymentFragment, bundle)
        }
        renewButton.setOnClickListener {
            Toast.makeText(context, "Renew flow coming soon", Toast.LENGTH_SHORT).show()
        }
        deleteButton.setOnClickListener { showDeleteConfirmationDialog(ad) }

        Glide.with(this)
            .load(if ((ad.image ?: "").startsWith("http")) ad.image else "$rootUrl${ad.image}")
            .placeholder(R.color.grey)
            .error(R.drawable.right)
            .into(imageView)
    }

    private fun showDeleteConfirmationDialog(ad: UserAd) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Ad")
            .setMessage("Are you sure you want to delete '${ad.title}'?")
            .setPositiveButton("Delete") { dialog, _ ->
                deleteAd(ad.id)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteAd(adId: Int) {
        val token = requireActivity()
            .getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
            .getString("TOKEN", null) ?: return

        val url = "${getString(R.string.root_url)}/api/my/ads/$adId/delete"
        val req = Request.Builder()
            .url(url)
            .put("".toRequestBody(null))
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    Toast.makeText(context, "Failed to delete ad: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                activity?.runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(context, "Ad deleted successfully.", Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                    } else {
                        val msg = try { JSONObject(body ?: "").optString("error") } catch (_: Exception) { null }
                        Toast.makeText(context, "Error: ${msg ?: "Could not delete this ad."}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    // -------- Payment slip helpers (concise EN result) --------

    // อัปโหลดจาก File แบบเดิม แต่เช็คให้ชัด
    private fun verifySlipFromFile(
        orderId: Int,
        imageFile: java.io.File,
        qrPayload: String,
        amount: Double?,
        onDone: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!imageFile.exists()) {
            onError("Image file not found"); return
        }
        val token = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
            .getString("TOKEN", null) ?: return onError("Unauthorized")

        val url = getString(R.string.root_url) + "/api/verify-slip/$orderId"
        val media = "image/jpeg".toMediaTypeOrNull()
        val fileBody = okhttp3.RequestBody.create(media, imageFile)

        val form = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("payload", qrPayload)
            .addFormDataPart("slip", imageFile.name, fileBody)
            .apply { amount?.let { addFormDataPart("amount", it.toString()) } }
            .build()

        val req = okhttp3.Request.Builder().url(url).post(form)
            .addHeader("Authorization", "Bearer $token").build()

        client.newCall(req).enqueue(object: okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) = onError(e.message ?: "Network error")
            override fun onResponse(call: okhttp3.Call, resp: okhttp3.Response) {
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) onError(body) else onDone(body)
            }
        })
    }
}
