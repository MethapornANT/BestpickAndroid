package com.bestpick.reviewhub

import android.app.AlertDialog
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import okhttp3.*
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class MoreMenuFragment : Fragment() {

    private val client = OkHttpClient()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_more_menu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val backButton: ImageView = view.findViewById(R.id.backButton)
        backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        val editAccountLayout: LinearLayout = view.findViewById(R.id.edit_account)
        val createAdLayout: LinearLayout = view.findViewById(R.id.createAdLayout)
        val yourAdsLayout: LinearLayout = view.findViewById(R.id.yourAdsLayout)
        val deleteAccountLayout: LinearLayout = view.findViewById(R.id.deleteAccountLayout)
        val logoutLayout: LinearLayout = view.findViewById(R.id.logoutLayout)

        editAccountLayout.setOnClickListener {
            findNavController().navigate(R.id.action_moreMenuFragment_to_editprofileFragment)
        }

        createAdLayout.setOnClickListener {
            Toast.makeText(context, "Create an ad clicked! Navigating...", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.action_moreMenuFragment_to_createAdFragment)
        }

        yourAdsLayout.setOnClickListener {
            Toast.makeText(context, "Your ads clicked!", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.action_moreMenuFragment_to_yourAdsFragment)
        }

        deleteAccountLayout.setOnClickListener {
            showDeleteAccountDialog()
        }

        logoutLayout.setOnClickListener {
            showLogoutConfirmationDialog()
        }

        val searchEditText: AppCompatEditText = view.findViewById(R.id.search_edit_text)
        val allMenuItems = listOf(
            editAccountLayout,
            createAdLayout,
            yourAdsLayout,
            deleteAccountLayout,
            logoutLayout
        )

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().lowercase().trim()
                filterMenuItems(query, allMenuItems)
            }
        })
    }

    private fun filterMenuItems(query: String, menuItems: List<LinearLayout>) {
        menuItems.forEach { menuItem ->
            // --- ส่วนที่แก้ไข ---
            // ViewGroup (เช่น LinearLayout) สามารถใช้ extension `children` ของ Kotlin ได้โดยตรง
            // เพื่อหา View ที่เป็น TextView ที่อยู่ข้างใน
            val textView = menuItem.children.find { it is TextView } as? TextView

            if (textView != null) {
                val menuText = textView.text.toString().lowercase()
                if (menuText.contains(query)) {
                    menuItem.visibility = View.VISIBLE
                } else {
                    menuItem.visibility = View.GONE
                }
            }
        }
    }

    private fun showLogoutConfirmationDialog() {
        val dialogBuilder = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setTitle("Logout")
            .setMessage("Are you sure you want to log out?")
            .setPositiveButton("Confirm") { dialog, _ ->
                performLogout()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }

        val alertDialog = dialogBuilder.create()
        alertDialog.show()
    }

    private fun showDeleteAccountDialog() {
        val dialogBuilder = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setTitle("Delete Account")
            .setMessage("Are you sure you want to delete your account? This action cannot be undone.")
            .setPositiveButton("Confirm") { dialog, _ ->
                deleteAccount()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }

        val alertDialog = dialogBuilder.create()
        alertDialog.show()
    }

    private fun performLogout() {
        val firebaseAuth = FirebaseAuth.getInstance()
        firebaseAuth.signOut()
        clearLocalData()

        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    private fun clearLocalData() {
        if (isAdded) {
            val sharedPreferences = requireContext().getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
            sharedPreferences.edit().clear().apply()
        }
    }

    private fun deleteAccount() {
        val sharedPreferences = requireActivity().getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences.getString("TOKEN", null)
        val userId = sharedPreferences.getString("USER_ID", null)

        if (token != null && userId != null) {
            val rootUrl = getString(R.string.root_url)
            val deleteAccountEndpoint = "/api/users/$userId"
            val url = "$rootUrl$deleteAccountEndpoint"

            val request = Request.Builder()
                .url(url)
                .delete()
                .addHeader("Authorization", "Bearer $token")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("MoreMenuFragment", "Failed to delete account: ${e.message}")
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), "Error deleting account", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        activity?.runOnUiThread {
                            Toast.makeText(requireContext(), "Account deleted successfully", Toast.LENGTH_SHORT).show()
                            performLogout()
                        }
                    } else {
                        val errorBody = response.body?.string()
                        Log.e("MoreMenuFragment", "Failed to delete account: ${response.message} - $errorBody")
                        activity?.runOnUiThread {
                            val errorMessage = try {
                                JSONObject(errorBody).optString("message", "Failed to delete account.")
                            } catch (e: JSONException) {
                                "Failed to delete account."
                            }
                            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
        }
    }
}