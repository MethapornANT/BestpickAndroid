package com.bestpick.reviewhub

import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class NotificationsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var notificationsAdapter: NotificationsAdapter
    private val notificationList = mutableListOf<Notification>()
    private var bottomNavigationView: BottomNavigationView? = null
    private val http by lazy { OkHttpClient() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_notifications, container, false)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // Toolbar + back
        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.navigationIcon = ContextCompat.getDrawable(
            requireContext(),
            androidx.appcompat.R.drawable.abc_ic_ab_back_material
        )
        toolbar.setNavigationOnClickListener { navigateHome() }

        recyclerView = view.findViewById(R.id.recycler_view_posts)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        notificationsAdapter = NotificationsAdapter(notificationList) { n ->
            updatestatus(n.id)
            Log.d("NotificationsFragment", "click id=${n.id} post=${n.post_id} ads=${n.ads_id}")

            when {
                (n.post_id ?: -1) > 0 -> {
                    findNavController().navigate(
                        R.id.action_to_postdetailFragment,
                        Bundle().apply { putInt("POST_ID", n.post_id!!) }
                    )
                }
                (n.ads_id ?: -1) > 0 -> {
                    fetchAdAndNavigate(n.ads_id!!)
                }
            }
        }
        recyclerView.adapter = notificationsAdapter

        bottomNavigationView = activity?.findViewById(R.id.bottom_navigation)
        fetchNotifications()
        return view
    }

    private fun navigateHome() {
        activity?.runOnUiThread {
            val bnv = activity?.findViewById<BottomNavigationView>(R.id.bottom_navigation)
            val homeId = bnv?.menu?.findItem(R.id.homeFragment)?.itemId ?: R.id.home
            bnv?.selectedItemId = homeId
            findNavController().navigate(R.id.homeFragment)
        }
    }


    private fun fetchNotifications() {
        val ctx = context ?: return
        val token = ctx.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
            .getString("TOKEN", null) ?: return

        val url = getString(R.string.root_url) + "/api/notifications"
        val req = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()

        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("Notifications", "fetch fail", e)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                response.close()
                if (body.isNullOrEmpty()) return

                val list: List<Notification> = Gson().fromJson(
                    body, object : TypeToken<List<Notification>>() {}.type
                )
                val distinct = list.distinctBy { it.id }
                showNotifications(distinct)
            }
        })
    }

    private fun fetchAdAndNavigate(adId: Int) {
        val ctx = context ?: return
        val token = ctx.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
            .getString("TOKEN", null) ?: return

        val url = getString(R.string.root_url) + "/api/my/ads/$adId"
        val req = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()

        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("AdFetch", "fail", e)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrEmpty()) {
                    Log.e("AdFetch", "HTTP error or empty. body=$body")
                    return
                }
                val bundle = Bundle().apply { putString("ad_json", body) }
                activity?.runOnUiThread {
                    findNavController().navigate(R.id.action_to_adDetailFragment, bundle)
                }
            }
        })
    }

    private fun updatestatus(notificationId: Int) {
        val token = context?.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
            ?.getString("TOKEN", null) ?: return

        val url = getString(R.string.root_url) + "/api/notifications/$notificationId/read"
        val req = Request.Builder()
            .url(url)
            .put("".toRequestBody(null))
            .addHeader("Authorization", "Bearer $token")
            .build()

        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("CheckStatus", "update fail", e)
            }

            override fun onResponse(call: Call, response: Response) {
                val ok = response.isSuccessful
                response.close()
                if (!ok) return

                val index = notificationList.indexOfFirst { it.id == notificationId }
                if (index != -1) {
                    notificationList[index].read_status = 1
                    activity?.runOnUiThread {
                        notificationsAdapter.notifyItemChanged(index)
                        updateBadge()
                    }
                }
            }
        })
    }

    private fun showNotifications(list: List<Notification>) {
        activity?.runOnUiThread {
            notificationList.clear()
            notificationList.addAll(list)
            notificationsAdapter.notifyDataSetChanged()
            updateBadge()
        }
    }

    private fun updateBadge() {
        val unread = notificationList.count { it.read_status == 0 }
        val badge = bottomNavigationView?.getOrCreateBadge(R.id.notification)
        if (unread > 0) {
            badge?.isVisible = true
            badge?.number = unread
        } else {
            bottomNavigationView?.getBadge(R.id.notification)?.let {
                it.isVisible = false
                it.clearNumber()
            }
        }
    }
}
