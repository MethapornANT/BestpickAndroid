package com.bestpick.reviewhub

import android.app.AlertDialog // สำหรับ AlertDialog ถ้าจะใช้ใน Fragment นี้
import android.content.Context.MODE_PRIVATE // สำหรับ SharedPreferences
import android.content.Intent // สำหรับ Intent ไป LoginActivity
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log // สำหรับ Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment // <--- สำคัญมาก: ต้องเป็น Fragment
import androidx.navigation.fragment.findNavController // สำหรับ Navigation Component
import com.google.firebase.auth.FirebaseAuth // สำหรับ Firebase Auth ถ้าใช้ Logout ที่นี่
import okhttp3.Call // สำหรับ OkHttp
import okhttp3.Callback // สำหรับ OkHttp
import okhttp3.OkHttpClient // สำหรับ OkHttp
import okhttp3.Request // สำหรับ OkHttp
import okhttp3.Response // สำหรับ OkHttp
import org.json.JSONException // สำหรับ JSON
import org.json.JSONObject // สำหรับ JSON
import java.io.IOException // สำหรับ OkHttp

class MoreMenuFragment : Fragment() { // <--- สำคัญมาก: ต้องสืบทอดจาก Fragment()

    // OkHttpClient สำหรับการเรียก API ในกรณีที่ย้าย deleteAccount มาที่นี่
    private val client = OkHttpClient()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate layout สำหรับ Fragment นี้
        // ตรวจสอบชื่อไฟล์ Layout ให้ถูกต้อง: fragment_more_menu.xml
        return inflater.inflate(R.layout.fragment_more_menu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ตั้งค่า Click Listener สำหรับปุ่มย้อนกลับ (backButton)
        val backButton: ImageView = view.findViewById(R.id.backButton)
        backButton.setOnClickListener {
            findNavController().popBackStack() // ย้อนกลับไปยัง Fragment ก่อนหน้า
        }

        // ตั้งค่า Click Listeners สำหรับแต่ละรายการเมนู
        val createAdLayout: LinearLayout = view.findViewById(R.id.createAdLayout)
        createAdLayout.setOnClickListener {
            // *** แก้ไขตรงนี้: ยกเลิกคอมเมนต์บรรทัด navigate เพื่อให้ทำงานจริง ***
            Toast.makeText(context, "Create an ad clicked! Navigating...", Toast.LENGTH_SHORT).show() // เปลี่ยนข้อความ Toast
            findNavController().navigate(R.id.action_moreMenuFragment_to_createAdFragment) // <--- บรรทัดนี้จะนำทางไปหน้า Create an Ad
        }

        val yourAdsLayout: LinearLayout = view.findViewById(R.id.yourAdsLayout)
        yourAdsLayout.setOnClickListener {
            Toast.makeText(context, "Your ads clicked!", Toast.LENGTH_SHORT).show()
            // findNavController().navigate(R.id.action_moreMenuFragment_to_yourAdsFragment)
        }

        val deleteAccountLayout: LinearLayout = view.findViewById(R.id.deleteAccountLayout)
        deleteAccountLayout.setOnClickListener {
            // เรียกเมธอด showDeleteAccountDialog ที่อยู่ใน Fragment นี้
            showDeleteAccountDialog()
        }

        val logoutLayout: LinearLayout = view.findViewById(R.id.logoutLayout)
        logoutLayout.setOnClickListener {
            // เรียกเมธอด performLogout ที่อยู่ใน Fragment นี้
            performLogout()
        }

        // สำหรับ search_edit_text (ถ้าต้องการให้ทำงานได้)
        // val searchEditText: AppCompatEditText = view.findViewById(R.id.search_edit_text)
        // searchEditText.setOnEditorActionListener { textView, actionId, keyEvent ->
        //     if (actionId == EditorInfo.IME_ACTION_SEARCH) {
        //         val query = textView.text.toString()
        //         Toast.makeText(context, "Searching for: $query", Toast.LENGTH_SHORT).show()
        //         true
        //     } else {
        //         false
        //     }
        // }
    }

    // *** เมธอดที่ย้ายมาจาก ProfileFragment เพื่อให้ MoreMenuFragment จัดการเอง ***

    private fun showDeleteAccountDialog() {
        val dialogBuilder = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setTitle("Delete Account")
            .setMessage("Are you sure you want to delete your account? This action cannot be undone.")
            .setPositiveButton("Confirm") { dialog, _ ->
                deleteAccount()  // Call deleteAccount API here
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
        // ล้าง Back Stack และสร้าง Task ใหม่เพื่อไปหน้า LoginActivity
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish() // ปิด Activity ที่เป็น host ของ Fragment นี้
    }

    private fun clearLocalData() {
        if (isAdded) { // ตรวจสอบว่า Fragment ติดตั้งกับ Activity แล้ว
            val sharedPreferences: SharedPreferences =
                requireContext().getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
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
                    Log.e("MoreMenuFragment", "Failed to delete account: ${e.message}") // เปลี่ยน Tag Log
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), "Error deleting account", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        activity?.runOnUiThread {
                            Toast.makeText(requireContext(), "Account deleted successfully", Toast.LENGTH_SHORT).show()
                            performLogout()  // Log out user after account deletion
                        }
                    } else {
                        val errorBody = response.body?.string()
                        Log.e("MoreMenuFragment", "Failed to delete account: ${response.message} - $errorBody") // เปลี่ยน Tag Log
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