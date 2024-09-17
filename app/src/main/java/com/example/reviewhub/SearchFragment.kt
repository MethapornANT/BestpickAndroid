package com.example.reviewhub

import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth

class SearchFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Find and set up the logout button
        val logoutButton = view.findViewById<Button>(R.id.logout)
        logoutButton.setOnClickListener {
            performLogout()
        }
    }

    private fun performLogout() {
        // Sign out from Firebase Authentication
        val firebaseAuth = FirebaseAuth.getInstance()
        firebaseAuth.signOut()
        // Clear shared preferences or any other local data
        clearLocalData()
        // Redirect to the login screen
        val intent = Intent(requireContext(), LoginActivity::class.java)
        startActivity(intent)
        // Close the current activity
        requireActivity().finish()
    }

    private fun clearLocalData() {
        // Clear shared preferences
        val sharedPreferences: SharedPreferences =
            requireContext().getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        sharedPreferences.edit().clear().apply()

        // Clear the token if stored separately
        val tokenPrefs: SharedPreferences = requireContext().getSharedPreferences("TokenPrefs", MODE_PRIVATE)
        tokenPrefs.edit().remove("TOKEN").apply()
    }
}
