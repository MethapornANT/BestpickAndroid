package com.bestpick.reviewhub

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

class PaymentSuccessFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_payment_success, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val doneButton: Button = view.findViewById(R.id.doneButton)
        doneButton.setOnClickListener {
            // กลับไปที่หน้ารายการโฆษณา
            findNavController().navigate(R.id.action_paymentSuccessFragment_to_yourAdsFragment)
        }
    }
}