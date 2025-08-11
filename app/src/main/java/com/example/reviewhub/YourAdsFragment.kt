package com.bestpick.reviewhub

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bestpick.reviewhub.models.UserAd
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class YourAdsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyStateTextView: TextView
    private lateinit var adapter: YourAdsAdapter
    private val client = OkHttpClient()
    private val gson = Gson()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_your_ads, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.adsRecyclerView)
        progressBar = view.findViewById(R.id.progressBar)
        emptyStateTextView = view.findViewById(R.id.emptyStateTextView)

        view.findViewById<ImageView>(R.id.backButton).setOnClickListener {
            findNavController().popBackStack()
        }

        setupRecyclerView()
        fetchUserAds()
    }

    private fun setupRecyclerView() {
        adapter = YourAdsAdapter(requireContext(), emptyList()) { ad ->
            // เมื่อมีการคลิกที่โฆษณา ให้ส่งข้อมูลไปหน้ารายละเอียด
            val adJson = Gson().toJson(ad)
            val bundle = Bundle().apply {
                putString("ad_json", adJson)
            }
            findNavController().navigate(R.id.action_yourAdsFragment_to_adDetailFragment, bundle)
        }
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
    }

    private fun fetchUserAds() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyStateTextView.visibility = View.GONE

        val sharedPreferences = requireActivity().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val token = sharedPreferences.getString("TOKEN", null)

        if (token == null) {
            Toast.makeText(context, "Authentication error. Please log in again.", Toast.LENGTH_SHORT).show()
            progressBar.visibility = View.GONE
            return
        }

        // เรียก API เพื่อดึงโฆษณาทุกสถานะ ยกเว้นที่ผู้ใช้ลบไปแล้ว
        val url = "${getString(R.string.root_url)}/api/my/ads?status=all"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    progressBar.visibility = View.GONE
                    emptyStateTextView.text = "Failed to load ads: ${e.message}"
                    emptyStateTextView.visibility = View.VISIBLE
                    Log.e("YourAdsFragment", "API call failed", e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                activity?.runOnUiThread {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful && responseBody != null) {
                        try {
                            val jsonResponse = JSONObject(responseBody)
                            val dataArray = jsonResponse.getJSONArray("data")

                            val adListType = object : TypeToken<List<UserAd>>() {}.type
                            val ads: List<UserAd> = gson.fromJson(dataArray.toString(), adListType)

                            if (ads.isEmpty()) {
                                emptyStateTextView.visibility = View.VISIBLE
                            } else {
                                recyclerView.visibility = View.VISIBLE
                                adapter.updateData(ads)
                            }
                        } catch (e: Exception) {
                            emptyStateTextView.text = "Error parsing data."
                            emptyStateTextView.visibility = View.VISIBLE
                            Log.e("YourAdsFragment", "JSON parsing error", e)
                        }
                    } else {
                        emptyStateTextView.text = "Error: ${response.message}"
                        emptyStateTextView.visibility = View.VISIBLE
                        Log.e("YourAdsFragment", "API error: ${response.code} - $responseBody")
                    }
                }
            }
        })
    }
}