package com.bestpick.reviewhub

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomnavigation.BottomNavigationView

class AdPendingFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // ซ่อน Bottom Nav เมื่อเข้ามาหน้านี้
        activity?.findViewById<BottomNavigationView>(R.id.bottom_navigation)?.visibility = View.GONE
        return inflater.inflate(R.layout.fragment_ad_pending, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val confirmButton: Button = view.findViewById(R.id.confirmButton)
        confirmButton.setOnClickListener {
            // กลับไปที่หน้า ProfileFragment (ตามที่ NavGraph กำหนด)
            findNavController().navigate(R.id.action_adPendingFragment_to_profileFragment)
        }
    }
}