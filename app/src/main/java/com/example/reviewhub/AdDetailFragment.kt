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
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class AdDetailFragment : Fragment() {

    private var userAd: UserAd? = null
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            val adJson = it.getString("ad_json")
            if (adJson != null) {
                userAd = Gson().fromJson(adJson, UserAd::class.java)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ad_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageView>(R.id.backButton).setOnClickListener {
            findNavController().popBackStack()
        }

        if (userAd == null) {
            Toast.makeText(context, "Failed to load ad details", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        bindAdData(view, userAd!!)
    }

    private fun bindAdData(view: View, ad: UserAd) {
        val imageView = view.findViewById<ImageView>(R.id.adImageView)
        val titleView = view.findViewById<TextView>(R.id.adTitleTextView)
        val statusView = view.findViewById<TextView>(R.id.adStatusTextView)
        val detailMessageView = view.findViewById<TextView>(R.id.adDetailMessageTextView)
        val payButton = view.findViewById<Button>(R.id.payButton)
        val renewButton = view.findViewById<Button>(R.id.renewButton)
        val deleteButton = view.findViewById<Button>(R.id.deleteButton)

        val rootUrl = getString(R.string.root_url)
        titleView.text = ad.title
        statusView.text = ad.status.replaceFirstChar { it.uppercase() }

        // --- Logic to control UI based on status ---
        payButton.visibility = View.GONE
        renewButton.visibility = View.GONE
        deleteButton.visibility = View.GONE

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
                detailMessageView.text = "Your ad is active! It will expire on: ${ad.expiration_date?.substring(0, 10)}"
                renewButton.visibility = View.VISIBLE
            }
            "paid" -> {
                statusColor = ContextCompat.getColor(requireContext(), R.color.purple_200)
                detailMessageView.text = "We have received your payment. Please wait for an admin to activate your ad."
            }
            "rejected", "expired" -> {
                statusColor = ContextCompat.getColor(requireContext(), R.color.red)
                detailMessageView.text = "This ad is now ${ad.status}."
            }
            else -> {
                statusColor = ContextCompat.getColor(requireContext(), R.color.grey)
                detailMessageView.text = "Status: ${ad.status}"
            }
        }
        statusView.background?.setTint(statusColor)

        // Setup button clicks
        payButton.setOnClickListener {
            // แปลง object 'ad' ทั้งก้อนให้เป็น String JSON
            val adJson = Gson().toJson(ad)
            val bundle = Bundle().apply {
                putString("ad_json", adJson) // ส่ง String JSON ไปแทน
            }
            findNavController().navigate(R.id.action_adDetailFragment_to_paymentFragment, bundle)
        }
        renewButton.setOnClickListener {
            // TODO: Navigate to renew screen
        }
        deleteButton.setOnClickListener {
            showDeleteConfirmationDialog(ad)
        }

        Glide.with(this)
            .load("$rootUrl${ad.image}")
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
        val sharedPreferences = requireActivity().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val token = sharedPreferences.getString("TOKEN", null) ?: return

        val url = "${getString(R.string.root_url)}/api/my/ads/$adId/delete"
        val requestBody = "".toRequestBody(null)
        val request = Request.Builder()
            .url(url)
            .put(requestBody)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    Toast.makeText(context, "Failed to delete ad: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                activity?.runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(context, "Ad deleted successfully.", Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                    } else {
                        val errorMessage = try { JSONObject(responseBody).getString("error") } catch (e: Exception) { "Could not delete ad." }
                        Toast.makeText(context, "Error: $errorMessage", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }
}