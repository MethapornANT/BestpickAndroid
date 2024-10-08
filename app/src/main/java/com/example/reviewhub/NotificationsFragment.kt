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
        notificationsAdapter = NotificationsAdapter(notificationList)
        recyclerView.adapter = notificationsAdapter

        fetchNotifications()
        return view
    }

    private fun fetchNotifications() {
        context?.let { ctx ->
            val sharedPreferences = ctx.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
            val token = sharedPreferences?.getString("TOKEN", null)
            val url = getString(R.string.root_url) + "/api/notifications"
            val client = OkHttpClient()

            val request = token?.let {
                Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Authorization", "Bearer $it")
                    .build()
            }

            request?.let { req ->
                client.newCall(req).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        e.printStackTrace()
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.body?.string()?.let { jsonResponse ->
                            val notificationList: List<Notification> = Gson().fromJson(
                                jsonResponse,
                                object : TypeToken<List<Notification>>() {}.type
                            )
                            showNotifications(notificationList.distinctBy { it.created_at }) // กรองรายการซ้ำ
                            Log.d("Notifications", notificationList.toString())

                        }
                    }
                })
            }
        }
    }

    private fun showNotifications(notificationList: List<Notification>) {
        activity?.runOnUiThread {
            this.notificationList.clear()
            this.notificationList.addAll(notificationList)
            notificationsAdapter.notifyDataSetChanged()
        }
    }
}
