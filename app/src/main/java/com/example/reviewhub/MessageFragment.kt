package com.bestpick.reviewhub // เปลี่ยน Package name กลับเป็นของคุณ

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController // ยังคงใช้ findNavController หากคุณยังมีการนำทางไปยัง AnotherUserFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import com.bestpick.reviewhub.ChatActivity // ตรวจสอบการ import นี้
import com.bestpick.reviewhub.AnotherUserFragment // ตรวจสอบการ import นี้
import com.bestpick.reviewhub.R // ตรวจสอบการ import นี้
import com.bestpick.reviewhub.databinding.FragmentMessageBinding // ใช้ View Binding

class MessageFragment : Fragment() {

    private var _binding: FragmentMessageBinding? = null
    private val binding get() = _binding!! // ใช้ View Binding
    private var userID: Int = -1
    private val client = OkHttpClient()
    private var matchedUsers = listOf<MatchedUser>()
    private val handler = Handler()
    private val refreshInterval = 2000L // Refresh every 2 seconds

    private lateinit var adapter: MatchedUserAdapter

    private val refreshRunnable = object : Runnable {
        override fun run() {
            fetchMatchedUsers { fetchedUsers ->
                if (fetchedUsers.isNotEmpty()) {
                    matchedUsers = fetchedUsers
                    adapter.updateUsers(fetchedUsers) // Update the adapter with the latest data
                }
            }
            handler.postDelayed(this, refreshInterval) // Schedule the next refresh
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMessageBinding.inflate(inflater, container, false) // Initialize View Binding
        val root: View = binding.root

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO) // ยังคงไว้ตามโค้ดของคุณ

        // Retrieve userID passed from MainActivity (หรือ SharedPreferences ตามที่เคยคุยกัน)
        // คุณเคยใช้ SharedPreferences ในโค้ดก่อนหน้า ถ้ายังใช้อยู่ก็ใช้แบบเดิม
        // แต่ในโค้ดล่าสุดของคุณใช้ arguments?.getInt("userID")
        // เลือกใช้วิธีที่เหมาะสมกับโปรเจกต์ของคุณ
        val sharedPreferences = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val userIdString = sharedPreferences.getString("USER_ID", null)
        userID = userIdString?.toIntOrNull() ?: -1

        if (userID != -1) {
            fetchMatchedUsers { fetchedUsers ->
                if (fetchedUsers.isNotEmpty()) {
                    matchedUsers = fetchedUsers
                    setupRecyclerView() // Display matched users using RecyclerView
                } else {
                    Toast.makeText(requireContext(), "ไม่พบผู้ใช้ที่จับคู่กัน", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(requireContext(), "UserID ไม่ถูกพบ", Toast.LENGTH_SHORT).show()
        }

        // Setup restore all chats button - ลบส่วนนี้ออก
        // binding.buttonRestoreAllChats.setOnClickListener {
        //     restoreAllChats(userID)
        // }

        return root
    }

    private fun setupRecyclerView() {
        adapter = MatchedUserAdapter(
            matchedUsers,
            userID,
            onChatClick = { user ->
                val intent = Intent(requireContext(), ChatActivity::class.java).apply {
                    putExtra("matchID", user.matchID)
                    putExtra("senderID", userID)
                    putExtra("nickname", user.nickname)
                }
                startActivity(intent)
            },
            onProfileClick = { user ->
                // ในโค้ดล่าสุดของคุณใช้ Intent สำหรับ AnotherUserFragment
                val intent = Intent(requireContext(), AnotherUserFragment::class.java).apply {
                    putExtra("userID", user.userID)
                }
                startActivity(intent)
                // หากต้องการใช้ Navigation Component เหมือนโค้ดก่อนหน้า ให้ใช้บรรทัดนี้แทน:
                // val bundle = Bundle().apply {
                //     putInt("USER_ID", user.userID)
                // }
                // findNavController().navigate(R.id.action_messageFragment_to_anotherUserFragment, bundle)
            },
            onDeleteChatClick = { user ->
                deleteChat(user.matchID)
            }
        )
        binding.recyclerViewUserList.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewUserList.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable) // Start data refresh
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable) // Stop data refresh
    }

    private fun deleteChat(matchID: Int) {
        // ไม่มี progressBar ให้แสดง/ซ่อนแล้ว
        lifecycleScope.launch(Dispatchers.IO) {
            val url = getString(R.string.root_url) + "/api/delete-chat"
            val requestBody = FormBody.Builder()
                .add("userID", userID.toString())
                .add("matchID", matchID.toString())
                .build()

            val request = Request.Builder().url(url).post(requestBody).build()

            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "ลบแชทเรียบร้อย", Toast.LENGTH_SHORT).show()
                        fetchMatchedUsers { adapter.updateUsers(it) }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "ไม่สามารถลบแชทได้ ลองใหม่อีกครั้ง", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "เกิดข้อผิดพลาด: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ลบฟังก์ชัน restoreAllChats ออกทั้งหมด
    // private fun restoreAllChats(userID: Int) {
    //     lifecycleScope.launch(Dispatchers.IO) {
    //         val url = getString(R.string.root_url) + "/api/restore-all-chats"
    //         val requestBody = FormBody.Builder()
    //             .add("userID", userID.toString())
    //             .build()

    //         val request = Request.Builder().url(url).post(requestBody).build()

    //         try {
    //             val response = client.newCall(request).execute()
    //             if (response.isSuccessful) {
    //                 withContext(Dispatchers.Main) {
    //                     Toast.makeText(requireContext(), "เรียกคืนแชททั้งหมดเรียบร้อย", Toast.LENGTH_SHORT).show()
    //                     fetchMatchedUsers { adapter.updateUsers(it) } // อัปเดตรายชื่อผู้ใช้ที่แสดง
    //                 }
    //             } else {
    //                 withContext(Dispatchers.Main) {
    //                     Toast.makeText(requireContext(), "ไม่สามารถเรียกคืนแชททั้งหมดได้ ลองใหม่อีกครั้ง", Toast.LENGTH_SHORT).show()
    //                 }
    //             }
    //         } catch (e: IOException) {
    //             withContext(Dispatchers.Main) {
    //                 Toast.makeText(requireContext(), "เกิดข้อผิดพลาด: ${e.message}", Toast.LENGTH_SHORT).show()
    //             }
    //         }
    //     }
    // }

    private fun fetchMatchedUsers(callback: (List<MatchedUser>) -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            val url = getString(R.string.root_url) + "/api/matches/$userID"
            Log.d("API Request", "Fetching matched users from URL: $url")
            val request = Request.Builder().url(url).build()

            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.d("API Response", responseBody ?: "ไม่มีการตอบกลับ")
                    val matchedUsersList = parseUsers(responseBody)

                    withContext(Dispatchers.Main) {
                        callback(matchedUsersList)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Log.e("API Error", "Response not successful: ${response.message}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("API Error", "Exception occurred: ${e.message}")
                }
            }
        }
    }

    private fun parseUsers(responseBody: String?): List<MatchedUser> {
        val users = mutableListOf<MatchedUser>()
        responseBody?.let {
            val jsonArray = JSONArray(it)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)

                val imageUrl = jsonObject.optString("imageFile", "")

                val user = MatchedUser(
                    userID = jsonObject.getInt("userID"),
                    nickname = jsonObject.getString("nickname"),
                    profilePicture = imageUrl, // ใช้ URL ที่ตรงจาก JSON
                    lastMessage = jsonObject.optString("lastMessage"),
                    matchID = jsonObject.getInt("matchID"),
                    lastInteraction = jsonObject.optString("lastInteraction"),
                    isBlocked = jsonObject.optBoolean("isBlocked")
                )
                users.add(user)
            }
        }
        return users
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// MatchedUserAdapter และ MatchedUser data class ไม่มีการเปลี่ยนแปลง
class MatchedUserAdapter(
    private var users: List<MatchedUser>,
    private val userID: Int,
    private val onChatClick: (MatchedUser) -> Unit,
    private val onProfileClick: (MatchedUser) -> Unit,
    private val onDeleteChatClick: (MatchedUser) -> Unit
) : RecyclerView.Adapter<MatchedUserAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nickname: TextView = view.findViewById(R.id.textNickname)
        val profileImage: ImageView = view.findViewById(R.id.imageProfile)
        val lastMessage: TextView = view.findViewById(R.id.lastMessage)
        val lastInteraction: TextView = view.findViewById(R.id.textLastInteraction)
        val buttonDeleteChat: Button = view.findViewById(R.id.buttonDeleteChat)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]
        holder.nickname.text = user.nickname
        holder.lastMessage.text = user.lastMessage ?: "ไม่มีข้อความล่าสุด"
        holder.lastInteraction.text = formatTime(user.lastInteraction)

        // โหลดภาพด้วย Glide โดยมี placeholder และ error image
        Glide.with(holder.profileImage.context)
            .load(user.profilePicture)
            .placeholder(R.drawable.default_profile_picture) // ภาพที่แสดงระหว่างโหลด
            .error(R.drawable.error_loading_image) // ภาพที่แสดงถ้าโหลดไม่สำเร็จ
            .into(holder.profileImage)

        holder.itemView.setOnClickListener { onChatClick(user) }
        holder.profileImage.setOnClickListener { onProfileClick(user) }

        holder.buttonDeleteChat.setOnClickListener {
            AlertDialog.Builder(holder.itemView.context)
                .setTitle("ยืนยันการลบแชท")
                .setMessage("คุณแน่ใจหรือไม่ว่าต้องการลบแชทนี้? การลบจะทำให้แชทไม่แสดงผล")
                .setPositiveButton("ลบ") { dialog, _ ->
                    onDeleteChatClick(user)
                    dialog.dismiss()
                }
                .setNegativeButton("ยกเลิก") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }


    override fun getItemCount() = users.size

    private fun formatTime(timestamp: String?): String {
        return if (timestamp != null) {
            try {
                // ใช้ format ที่รองรับ microseconds ตามโค้ดที่คุณให้มาล่าสุด
                val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS", Locale.getDefault())
                val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val date = inputFormat.parse(timestamp)
                outputFormat.format(date)
            } catch (e: Exception) {
                // หากเกิดข้อผิดพลาดในการ parse ให้แสดง timestamp เดิม หรือ String ว่าง
                Log.e("formatTime", "Error parsing timestamp: $timestamp, Error: ${e.message}")
                timestamp ?: ""
            }
        } else {
            ""
        }
    }

    fun updateUsers(newUsers: List<MatchedUser>) {
        users = newUsers
        notifyDataSetChanged()
    }
}

data class MatchedUser(
    val userID: Int,
    val nickname: String,
    val profilePicture: String,
    val lastMessage: String?,
    val matchID: Int,
    val lastInteraction: String?,
    val isBlocked: Boolean
)