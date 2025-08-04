package com.bestpick.reviewhub

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class YourAdsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var yourAdsAdapter: YourAdsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_your_ads, container, false)

        // --- เพิ่มส่วนนี้เข้ามา ---
        val backButton: ImageView = view.findViewById(R.id.backButton)
        backButton.setOnClickListener {
            findNavController().popBackStack() // สั่งให้ย้อนกลับไปหน้าก่อนหน้า
        }
        // --- จบส่วนที่เพิ่ม ---

        recyclerView = view.findViewById(R.id.your_ads_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(context)

        // TODO: ดึงข้อมูลโฆษณาจริงจาก API ในขั้นตอนต่อไป
        val dummyAdsList = listOf(
            YourAd(1, "https://i.ytimg.com/vi/Hmz4R2e1y-Y/maxresdefault.jpg", "Ad: Waiting for Approval", "https://example.com", "pending_approval", null, null, null, null),
            YourAd(2, "https://i.ytimg.com/vi/Hmz4R2e1y-Y/maxresdefault.jpg", "Ad: Waiting for Payment", "https://example.com", "pending_payment", null, null, null, null),
            YourAd(3, "https://i.ytimg.com/vi/Hmz4R2e1y-Y/maxresdefault.jpg", "Ad: Active & Running", "https://example.com", "active", "Popular packages", 15, "28/7/25", "12/8/25")
        )

        yourAdsAdapter = YourAdsAdapter(dummyAdsList)
        recyclerView.adapter = yourAdsAdapter

        return view
    }
}