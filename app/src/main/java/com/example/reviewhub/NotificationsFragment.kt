package com.bestpick.reviewhub

import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import java.io.IOException

class NotificationsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var notificationsAdapter: NotificationsAdapter
    private val notificationList = mutableListOf<Notification>()
    private var bottomNavigationView: BottomNavigationView? = null

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
            Log.d("NotificationsFragment", "Notification clicked: ${notification.id}")
        }
        recyclerView.adapter = notificationsAdapter

        // ดึง BottomNavigationView จาก Activity แม่
        bottomNavigationView = activity?.findViewById(R.id.bottom_navigation)

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
        Log.d("CheckStatus", "notificationId: $notificationId")
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
                response.use {
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        Log.d("CheckStatus", "Notification marked as read. Response: $responseBody")

                        // อัปเดตสถานะ read_status ใน notificationList
                        val index = notificationList.indexOfFirst { it.id == notificationId }
                        if (index != -1) {
                            notificationList[index].read_status = 1  // เปลี่ยน read_status เป็น 1 (อ่านแล้ว)

                            // อัปเดต UI ใน Main Thread
                            activity?.runOnUiThread {
                                notificationsAdapter.notifyItemChanged(index)  // อัปเดตเฉพาะรายการที่เปลี่ยนแปลง
                            }

                            // อัปเดต Badge
                            updateBadge()
                        } else {
                        }
                    } else {
                        Log.e("CheckStatus", "Error: ${response.message} - ${response.body?.string()}")
                    }
                }
            }
        })
    }

    private fun showNotifications(notificationList: List<Notification>) {
        activity?.runOnUiThread {
            this.notificationList.clear()
            this.notificationList.addAll(notificationList)
            notificationsAdapter.notifyDataSetChanged()
            Log.d("showNotifications", "Number of notifications to display: ${this.notificationList.size}")

            // อัปเดต Badge หลังจากแสดงการแจ้งเตือน
            updateBadge()
        }
    }

    private fun updateBadge() {
        val unreadCount = notificationList.count { it.read_status == 0 }
        if (unreadCount > 0) {
            val badge = bottomNavigationView?.getOrCreateBadge(R.id.notification)
            badge?.isVisible = true
            badge?.number = unreadCount
        } else {
            val badge = bottomNavigationView?.getBadge(R.id.notification)
            badge?.isVisible = false
        }
    }
}
