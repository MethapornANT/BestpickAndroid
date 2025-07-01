package com.bestpick.reviewhub

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class ChatActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var recyclerViewChat: RecyclerView
    private lateinit var emptyChatMessage: TextView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var buttonBlockChat: Button
    private lateinit var buttonUnblockChat: Button
    private lateinit var progressBar: LottieAnimationView

    private val client = OkHttpClient()
    private var matchID: Int = -1
    private var senderID: Int = -1
    private var receiverNickname: String = ""
    private var isBlocked: Boolean = false

    private val handler = Handler(Looper.getMainLooper())
    private val refreshInterval = 2000L // รีเฟรชทุก 2 วินาที
    private val refreshRunnable = object : Runnable {
        override fun run() {
            fetchChatMessages()
            handler.postDelayed(this, refreshInterval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // Initialize views
        toolbar = findViewById(R.id.toolbar)
        recyclerViewChat = findViewById(R.id.recyclerViewChat)
        emptyChatMessage = findViewById(R.id.emptyChatMessage)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        buttonBlockChat = toolbar.findViewById(R.id.buttonBlockChat)
        buttonUnblockChat = toolbar.findViewById(R.id.buttonUnblockChat)

        // รับ matchID, senderID, และ nickname ของคู่สนทนา
        matchID = intent.getIntExtra("matchID", -1)
        senderID = intent.getIntExtra("senderID", -1)
        receiverNickname = intent.getStringExtra("nickname") ?: ""

        Log.d("ChatActivity", "Received matchID: $matchID, senderID: $senderID, nickname: $receiverNickname")

        if (matchID == -1 || senderID == -1) {
            Toast.makeText(this, "ไม่พบข้อมูลการสนทนา", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // ตั้งค่า Toolbar ให้แสดงชื่อเล่นของคู่สนทนา
        setSupportActionBar(toolbar)
        supportActionBar?.title = receiverNickname
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        toolbar.setNavigationOnClickListener {
            finish()
        }

        // ตั้งค่า RecyclerView
        val chatAdapter = ChatAdapter(senderID)
        recyclerViewChat.layoutManager = LinearLayoutManager(this)
        recyclerViewChat.adapter = chatAdapter

        fetchChatMessages()

        // เมื่อผู้ใช้ส่งข้อความ
        sendButton.setOnClickListener {
            val message = messageInput.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessage(message)
                messageInput.text.clear()
            }
        }

        // กำหนดฟังก์ชันให้กับปุ่ม Block และ Unblock
        buttonBlockChat.setOnClickListener {
            blockChat()
        }
        buttonUnblockChat.setOnClickListener {
            unblockChat()
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    private fun blockChat() {
        if (isBlocked) {
            Toast.makeText(this, "คุณไม่สามารถบล็อคได้ เนื่องจากถูกบล็อกจากอีกฝ่าย", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val url = getString(R.string.root_url) + "/api/block-chat"
            val requestBody = FormBody.Builder()
                .add("userID", senderID.toString())
                .add("matchID", matchID.toString())
                .add("isBlocked", "1")
                .build()

            val request = Request.Builder().url(url).post(requestBody).build()

            try {
                val response = client.newCall(request).execute()
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful) {
                        isBlocked = true
                        Toast.makeText(this@ChatActivity, "บล็อกแชทเรียบร้อย", Toast.LENGTH_SHORT).show()
                        buttonBlockChat.isEnabled = false
                    } else {
                        Toast.makeText(this@ChatActivity, "ไม่สามารถบล็อคแชทได้ ลองใหม่อีกครั้ง", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@ChatActivity, "เกิดข้อผิดพลาด: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun unblockChat() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val url = getString(R.string.root_url) + "/api/unblock-chat"
            val requestBody = FormBody.Builder()
                .add("userID", senderID.toString())
                .add("matchID", matchID.toString())
                .build()

            val request = Request.Builder().url(url).post(requestBody).build()

            try {
                val response = client.newCall(request).execute()
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful) {
                        isBlocked = false
                        Toast.makeText(this@ChatActivity, "ปลดบล็อคแชทเรียบร้อย", Toast.LENGTH_SHORT).show()
                        buttonBlockChat.isEnabled = true
                    } else {
                        Toast.makeText(this@ChatActivity, "ไม่สามารถปลดบล็อคแชทได้ ลองใหม่อีกครั้ง", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@ChatActivity, "เกิดข้อผิดพลาด: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchChatMessages() {
        lifecycleScope.launch(Dispatchers.IO) {
            val url = getString(R.string.root_url) + "/api/chats/$matchID"
            val request = Request.Builder().url(url).build()

            try {
                val response = client.newCall(request).execute()
                Log.d("ChatActivity", "Response code: ${response.code}") // เพิ่มบรรทัดนี้
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.d("ChatActivity", "Response body: $responseBody") // เพิ่มบรรทัดนี้
                    if (responseBody.isNullOrEmpty()) { // ตรวจสอบตรงๆ
                        Log.e("ChatActivity", "Response body is null or empty despite successful response.")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ChatActivity, "ข้อมูลแชทว่างเปล่า", Toast.LENGTH_SHORT).show()
                            emptyChatMessage.visibility = View.VISIBLE // แสดงข้อความนี้
                            recyclerViewChat.visibility = View.GONE
                        }
                        return@launch // ออกจาก coroutine
                    }
                    val messages = parseChatMessages(responseBody)
                    // ... (โค้ดเดิม)
                } else {
                    withContext(Dispatchers.Main) {
                        val errorBody = response.body?.string() // เพิ่มบรรทัดนี้
                        Log.e("ChatActivity", "API call failed with code ${response.code}: $errorBody") // เพิ่มบรรทัดนี้
                        Toast.makeText(this@ChatActivity, "ไม่สามารถดึงข้อมูลการสนทนาได้: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("ChatActivity", "Error fetching messages (network/IO error): ${e.message}", e) // เปลี่ยนเป็น e ด้วย
                    Toast.makeText(this@ChatActivity, "เกิดข้อผิดพลาดในการเชื่อมต่อ: ${e.message}", Toast.LENGTH_SHORT).show()
                    emptyChatMessage.visibility = View.VISIBLE // อาจจะแสดงข้อความนี้เมื่อเกิดข้อผิดพลาดการเชื่อมต่อ
                    recyclerViewChat.visibility = View.GONE
                }
            }

            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val messages = parseChatMessages(responseBody) // <-- ปัญหาอาจจะอยู่ที่นี่
                    withContext(Dispatchers.Main) {
                        if (messages.isEmpty()) {
                            emptyChatMessage.visibility = View.VISIBLE
                            recyclerViewChat.visibility = View.GONE
                        } else {
                            emptyChatMessage.visibility = View.GONE
                            recyclerViewChat.visibility = View.VISIBLE
                            (recyclerViewChat.adapter as ChatAdapter).setMessages(messages)
                            recyclerViewChat.scrollToPosition(messages.size - 1)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ChatActivity, "ไม่สามารถดึงข้อมูลการสนทนาได้", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("ChatActivity", "Error fetching messages: ${e.message}")
                }
            }
        }
    }

    private fun sendMessage(message: String) {
        if (isBlocked) {
            Toast.makeText(this, "คุณไม่สามารถส่งข้อความได้ เนื่องจากคุณถูกบล็อก", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val url = getString(R.string.root_url) + "/api/chats/$matchID"
            val requestBody = FormBody.Builder()
                .add("senderID", senderID.toString())
                .add("message", message)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            try {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        if (response.code == 403) {
                            Toast.makeText(this@ChatActivity, "คุณถูกบล็อกจากการส่งข้อความในแชทนี้", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@ChatActivity, "ไม่สามารถส่งข้อความได้", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    fetchChatMessages()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ChatActivity, "เกิดข้อผิดพลาด: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun parseChatMessages(responseBody: String?): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        responseBody?.let {
            try {
                Log.d("ChatActivity", "Attempting to parse JSON: $it") // เพิ่มบรรทัดนี้
                val jsonObject = JSONObject(it)
                val messagesArray = jsonObject.getJSONArray("messages")

                for (i in 0 until messagesArray.length()) {
                    val messageObject = messagesArray.getJSONObject(i)
                    val chatMessage = ChatMessage(
                        messageObject.getInt("senderID"),
                        messageObject.getString("nickname"),
                        messageObject.getString("imageFile"),
                        messageObject.getString("message"),
                        messageObject.getString("timestamp")
                    )
                    messages.add(chatMessage)
                }
                Log.d("ChatActivity", "Successfully parsed ${messages.size} messages.") // เพิ่มบรรทัดนี้
            } catch (e: Exception) {
                Log.e("ChatActivity", "Error parsing chat messages: ${e.message}", e) // เปลี่ยนเป็น e ด้วย
            }
        } ?: run {
            Log.e("ChatActivity", "Response body is null in parseChatMessages.") // เพิ่มบรรทัดนี้
        }
        return messages
    }
}

// Data class for storing chat message data
data class ChatMessage(
    val senderID: Int,
    val nickname: String,
    val profilePicture: String,
    val message: String,
    val timestamp: String
)