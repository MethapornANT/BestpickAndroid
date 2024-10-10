package com.example.reviewhub

import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class NotificationsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var notificationsAdapter: NotificationsAdapter
    private val notificationList = mutableListOf<Notification>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_notifications, container, false)
        recyclerView = view.findViewById(R.id.recycler_view_posts)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        notificationsAdapter = NotificationsAdapter(notificationList) { notification ->
            // เมื่อผู้ใช้คลิกที่ Notification ให้ทำการอัปเดตสถานะ
            updatestatus(notification.id)
        }
        recyclerView.adapter = notificationsAdapter

        fetchNotifications()
        return view
    }

    private fun fetchNotifications() {
        context?.let { ctx ->
            val sharedPreferences = ctx.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
            val token = sharedPreferences?.getString("TOKEN", null)

            if (token.isNullOrEmpty()) {
                Log.e("Notifications", "Token not found")
                return
            }

            val url = getString(R.string.root_url) + "/api/notifications"
            val client = OkHttpClient()

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", "Bearer $token")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                }

                override fun onResponse(call: Call, response: Response) {
                    response.body?.string()?.let { jsonResponse ->
                        val notificationList: List<Notification> = Gson().fromJson(
                            jsonResponse,
                            object : TypeToken<List<Notification>>() {}.type
                        )

                        // กรอง Notification ที่ซ้ำกันออกโดยใช้ `id`
                        val distinctNotifications = notificationList.distinctBy { it.id }
                        Log.d("fetchNotifications", "Number of distinct notifications: ${distinctNotifications.size}")

                        showNotifications(distinctNotifications)  // แสดง Notification ที่ไม่ซ้ำกัน
                    }
                }
            })
        }
    }

    private fun updatestatus(notificationId: Int) {
        val sharedPreferences = context?.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences?.getString("TOKEN", null)

        if (token.isNullOrEmpty()) {
            Log.e("CheckStatus", "Token not found")
            return
        }

        val client = OkHttpClient()
        val url = getString(R.string.root_url) + "/api/notifications/$notificationId/read"

        val request = Request.Builder()
            .url(url)
            .put(RequestBody.create(null, ""))  // Empty body สำหรับ PUT Request
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("CheckStatus", "Failed to update read status", e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d("CheckStatus", "Notification marked as read")

                    // อัปเดตสถานะ read_status ใน notificationList
                    val index = notificationList.indexOfFirst { it.id == notificationId }
                    if (index != -1) {
                        notificationList[index].read_status = 1  // เปลี่ยน read_status เป็น 1 (อ่านแล้ว)

                        // อัปเดต UI ใน Main Thread
                        activity?.runOnUiThread {
                            notificationsAdapter.notifyItemChanged(index)  // อัปเดตเฉพาะรายการที่เปลี่ยนแปลง
                        }
                    }
                } else {
                    Log.e("CheckStatus", "Error: ${response.message}")
                }
            }
        })
    }


    private fun checkStatus(notificationId: Int) {
        val sharedPreferences = context?.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences?.getString("TOKEN", null)

        if (token.isNullOrEmpty()) {
            Log.e("CheckStatus", "Token not found")
            return
        }

        val client = OkHttpClient()
        val url = getString(R.string.root_url) + "/api/notifications/$notificationId/status"

        val request = Request.Builder()
            .url(url)
            .get()  // เรียก GET Request เพื่อเช็คสถานะ
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("CheckStatus", "Failed to check read status", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { jsonResponse ->
                    Log.d("CheckStatus", "Response: $jsonResponse")
                    if (response.isSuccessful) {
                        try {
                            val jsonObject = JSONObject(jsonResponse)
                            if (jsonObject.has("read_status")) {
                                val isRead = when (jsonObject.get("read_status")) {
                                    is Int -> jsonObject.getInt("read_status") == 1  // แปลงค่า Int เป็น Boolean
                                    is Boolean -> jsonObject.getBoolean("read_status")
                                    else -> false
                                }
                                Log.d("CheckStatus", "Read Status: $isRead")
                            } else {
                                Log.e("CheckStatus", "Key 'read_status' not found in response")
                            }
                        } catch (e: JSONException) {
                            Log.e("CheckStatus", "JSON Parsing Error: ${e.message}")
                        }
                    } else {
                        Log.e("CheckStatus", "Error: ${response.message}")
                        when (response.code) {
                            404 -> Log.e("CheckStatus", "Notification not found or not the owner.")
                            403 -> Log.e("CheckStatus", "Access denied. Unauthorized access.")
                            else -> Log.e("CheckStatus", "Unexpected error: ${response.code}")
                        }
                    }
                }
            }
        })


    }



    private fun showNotifications(notificationList: List<Notification>) {
        activity?.runOnUiThread {
            this.notificationList.clear()  // ล้างข้อมูลเก่าออกก่อนเพิ่มใหม่
            this.notificationList.addAll(notificationList)
            notificationsAdapter.notifyDataSetChanged()  // แจ้งเตือน Adapter เพื่อรีเฟรช UI
            Log.d("showNotifications", "Number of notifications to display: ${this.notificationList.size}")
        }
    }
}
