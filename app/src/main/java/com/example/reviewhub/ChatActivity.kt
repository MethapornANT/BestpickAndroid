package com.bestpick.reviewhub

import android.content.Intent
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
    // ถ้ามี ProgressBar ใน activity_chat.xml และคุณต้องการใช้งาน ให้ประกาศและ initialize ตรงนี้
    // private lateinit var progressBar: ProgressBar // ไม่ได้อยู่ใน log ปัญหาล่าสุด แต่ถ้ามีใน layout ควรจะ initialize

    private val client = OkHttpClient()
    private var matchID: Int = -1
    private var senderID: Int = -1
    private var receiverNickname: String = ""
    private var isBlocked: Boolean = false

    private val handler = Handler(Looper.getMainLooper())
    private val refreshInterval = 2000L // รีเฟรชทุก 2 วินาที
    private val refreshRunnable = object : Runnable {
        override fun run() {
            Log.d("ChatActivity", "Fetching chat messages...")
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

        // ถ้ามี ProgressBar ใน activity_chat.xml ให้ initialize ตรงนี้
        // try {
        //     progressBar = findViewById(R.id.progressBarChat) // สมมติว่ามี ID ชื่อ progressBarChat
        // } catch (e: Exception) {
        //     Log.e("ChatActivity", "ProgressBar not found in layout, or ID is incorrect: ${e.message}")
        // }


        // รับ matchID, senderID, และ nickname ของคู่สนทนา
        matchID = intent.getIntExtra("matchID", -1)
        senderID = intent.getIntExtra("senderID", -1)
        receiverNickname = intent.getStringExtra("nickname") ?: ""

        Log.d("ChatActivity", "Received matchID: $matchID, senderID: $senderID, nickname: $receiverNickname")

        if (matchID == -1 || senderID == -1) {
            val errorMessage = "Chat data not found. matchID: $matchID, senderID: $senderID"
            Log.e("ChatActivity", errorMessage)
            Toast.makeText(this, "ไม่พบข้อมูลการสนทนา", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // ตั้งค่า Toolbar ให้แสดงชื่อเล่นของคู่สนทนา
        setSupportActionBar(toolbar)
        supportActionBar?.title = receiverNickname
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        toolbar.setNavigationOnClickListener {
            Log.d("ChatActivity", "Back button clicked. Finishing ChatActivity.")
            finish()
        }

        // ตั้งค่า RecyclerView
        val chatAdapter = ChatAdapter(senderID) { clickedUserID ->
            // นี่คือสิ่งที่จะเกิดขึ้นเมื่อรูปโปรไฟล์ถูกคลิกใน ChatAdapter
            Log.d("ChatActivity", "onProfileClick received for userID: $clickedUserID. Navigating to AnotherUserFragment.")

            // วิธีที่ 1: กลับไปที่ Activity หลัก (เช่น MainActivity) แล้วให้ Activity หลักจัดการ navigate ด้วย Navigation Component
            // วิธีนี้จะคล้ายกับการทำงานของ MessageFragment
            val intent = Intent(this, MainActivity::class.java).apply { // เปลี่ยน MainActivity::class.java เป็น Activity หลักของคุณที่มี NavHostFragment
                putExtra("NAVIGATE_TO_USER_PROFILE_ID", clickedUserID)
                // เพิ่มแฟล็กเพื่อเคลียร์ stack ของ activity ถ้าจำเป็น
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish() // ปิด ChatActivity เพื่อกลับไปที่ Activity หลัก

            // วิธีที่ 2: ถ้า AnotherUserFragment ถูกโฮสต์อยู่ใน ChatActivity เอง (ซึ่งโค้ดปัจจุบันไม่ได้เป็นแบบนั้น)
            // val bundle = Bundle().apply { putInt("USER_ID", clickedUserID) }
            // supportFragmentManager.beginTransaction()
            //     .replace(R.id.fragment_container, AnotherUserFragment::class.java, bundle) // fragment_container คือ ID ของ FrameLayout/FragmentContainerView ใน activity_chat.xml
            //     .addToBackStack(null)
            //     .commit()
        }
        recyclerViewChat.layoutManager = LinearLayoutManager(this)
        recyclerViewChat.adapter = chatAdapter

        // ตรวจสอบสถานะการบล็อกเมื่อเข้าสู่หน้านี้
        checkBlockStatus() // เพิ่มฟังก์ชันนี้เพื่อดึงสถานะการบล็อกเริ่มต้น

        fetchChatMessages()

        // เมื่อผู้ใช้ส่งข้อความ
        sendButton.setOnClickListener {
            val message = messageInput.text.toString().trim()
            if (message.isNotEmpty()) {
                Log.d("ChatActivity", "Send button clicked. Sending message: \"$message\"")
                sendMessage(message)
                messageInput.text.clear()
            } else {
                Log.d("ChatActivity", "Attempted to send empty message.")
                Toast.makeText(this, "กรุณาพิมพ์ข้อความ", Toast.LENGTH_SHORT).show()
            }
        }

        // กำหนดฟังก์ชันให้กับปุ่ม Block และ Unblock
        buttonBlockChat.setOnClickListener {
            Log.d("ChatActivity", "Block chat button clicked.")
            blockChat()
        }
        buttonUnblockChat.setOnClickListener {
            Log.d("ChatActivity", "Unblock chat button clicked.")
            unblockChat()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("ChatActivity", "Activity resumed. Starting chat refresh.")
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        Log.d("ChatActivity", "Activity paused. Stopping chat refresh.")
        handler.removeCallbacks(refreshRunnable)
    }

    // เพิ่มฟังก์ชันเพื่อตรวจสอบสถานะการบล็อกเมื่อ Activity ถูกสร้าง
    private fun checkBlockStatus() {
        lifecycleScope.launch(Dispatchers.IO) {
            val url = getString(R.string.root_url) + "/api/block-status" // สมมติว่ามี API สำหรับตรวจสอบสถานะบล็อก
            val requestBody = FormBody.Builder()
                .add("userID", senderID.toString())
                .add("matchID", matchID.toString())
                .build()

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                Log.d("ChatActivity", "Block status API Response: ${response.code} - $responseBody")

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && responseBody != null) {
                        val jsonObject = JSONObject(responseBody)
                        val blockedStatus = jsonObject.optBoolean("isBlocked", false) // อ่านค่า isBlocked จาก response
                        isBlocked = blockedStatus
                        updateUIBasedOnBlockStatus()
                    } else {
                        Log.e("ChatActivity", "Failed to get block status: ${response.code} - $responseBody")
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatActivity", "Error checking block status: ${e.message}", e)
            }
        }
    }

    private fun updateUIBasedOnBlockStatus() {
        if (isBlocked) {
            buttonBlockChat.visibility = View.GONE
            buttonUnblockChat.visibility = View.VISIBLE
            messageInput.isEnabled = false
            sendButton.isEnabled = false
            messageInput.hint = "คุณได้บล็อกการสนทนานี้"
            Log.d("ChatActivity", "UI updated: Chat is blocked.")
        } else {
            buttonBlockChat.visibility = View.VISIBLE
            buttonUnblockChat.visibility = View.GONE
            messageInput.isEnabled = true
            sendButton.isEnabled = true
            messageInput.hint = "พิมพ์ข้อความ"
            Log.d("ChatActivity", "UI updated: Chat is unblocked.")
        }
    }


    private fun blockChat() {
        Log.d("ChatActivity", "Attempting to block chat - matchID: $matchID, senderID: $senderID")

        lifecycleScope.launch(Dispatchers.IO) {
            val url = getString(R.string.root_url) + "/api/block-chat"
            val requestBody = FormBody.Builder()
                .add("userID", senderID.toString())
                .add("matchID", matchID.toString())
                .add("isBlocked", "1") // ส่ง 1 เพื่อบล็อก
                .build()

            Log.d("ChatActivity", "Calling Block API: $url with userID: $senderID, matchID: $matchID")

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                Log.d("ChatActivity", "Block API Response: ${response.code} - $responseBody")

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        isBlocked = true
                        Toast.makeText(this@ChatActivity, "บล็อกแชทเรียบร้อย", Toast.LENGTH_SHORT).show()
                        updateUIBasedOnBlockStatus() // อัพเดท UI
                        Log.i("ChatActivity", "Chat blocked successfully for matchID: $matchID")
                    } else {
                        val errorMessage = "Failed to block chat: ${response.code} - $responseBody"
                        Log.e("ChatActivity", errorMessage)
                        Toast.makeText(this@ChatActivity, "ไม่สามารถบล็อคแชทได้: $responseBody", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatActivity", "Error blocking chat: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ChatActivity, "เกิดข้อผิดพลาดในการบล็อคแชท: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun unblockChat() {
        Log.d("ChatActivity", "Attempting to unblock chat - matchID: $matchID, senderID: $senderID")

        lifecycleScope.launch(Dispatchers.IO) {
            val url = getString(R.string.root_url) + "/api/unblock-chat" // หรือใช้ API เดียวกับ block แล้วส่ง isBlocked เป็น 0
            val requestBody = FormBody.Builder()
                .add("userID", senderID.toString())
                .add("matchID", matchID.toString())
                .build()

            Log.d("ChatActivity", "Calling Unblock API: $url with userID: $senderID, matchID: $matchID")

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                Log.d("ChatActivity", "Unblock API Response: ${response.code} - $responseBody")

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        isBlocked = false
                        Toast.makeText(this@ChatActivity, "ปลดบล็อคแชทเรียบร้อย", Toast.LENGTH_SHORT).show()
                        updateUIBasedOnBlockStatus() // อัพเดท UI
                        Log.i("ChatActivity", "Chat unblocked successfully for matchID: $matchID")
                    } else {
                        val errorMessage = "Failed to unblock chat: ${response.code} - $responseBody"
                        Log.e("ChatActivity", errorMessage)
                        Toast.makeText(this@ChatActivity, "ไม่สามารถปลดบล็อคแชทได้: $responseBody", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatActivity", "Error unblocking chat: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ChatActivity, "เกิดข้อผิดพลาดในการปลดบล็อคแชท: ${e.message}", Toast.LENGTH_SHORT).show()
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
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val messages = parseChatMessages(responseBody)

                    withContext(Dispatchers.Main) {
                        if (messages.isEmpty()) {
                            emptyChatMessage.text = "เริ่มแชทกันเลย !!!"
                            emptyChatMessage.visibility = View.VISIBLE
                            recyclerViewChat.visibility = View.GONE
                            Log.d("ChatActivity", "No chat messages found for matchID: $matchID. Displaying empty message.")
                        } else {
                            emptyChatMessage.visibility = View.GONE
                            recyclerViewChat.visibility = View.VISIBLE
                            (recyclerViewChat.adapter as ChatAdapter).setMessages(messages)
                            // เลื่อนไปข้อความล่าสุดเสมอเมื่อโหลดข้อความใหม่
                            recyclerViewChat.scrollToPosition(messages.size - 1)
                            Log.d("ChatActivity", "Fetched ${messages.size} chat messages for matchID: $matchID. Updating RecyclerView.")
                        }
                    }
                } else {
                    val errorMessage = "Failed to fetch chat messages: ${response.code} - ${response.message}"
                    Log.e("ChatActivity", errorMessage)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ChatActivity, "ไม่สามารถดึงข้อความแชทได้: ${response.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatActivity", "Error fetching messages: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ChatActivity, "เกิดข้อผิดพลาดในการดึงข้อความ: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sendMessage(message: String) {
        if (isBlocked) {
            Log.w("ChatActivity", "Attempted to send message while chat is blocked. Message: \"$message\"")
            Toast.makeText(this, "ไม่สามารถส่งข้อความได้ในแชทที่ถูกบล็อก", Toast.LENGTH_SHORT).show()
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
                    val errorResponseBody = response.body?.string()
                    withContext(Dispatchers.Main) {
                        if (response.code == 403) {
                            Log.w("ChatActivity", "Sender blocked from sending message. Response: $errorResponseBody")
                            Toast.makeText(this@ChatActivity, "คุณถูกบล็อกจากการส่งข้อความในแชทนี้", Toast.LENGTH_SHORT).show()
                            isBlocked = true // อัพเดทสถานะบล็อกใน UI
                            updateUIBasedOnBlockStatus() // อัพเดท UI
                        } else {
                            val errorMessage = "Failed to send message: ${response.code} - $errorResponseBody"
                            Log.e("ChatActivity", errorMessage)
                            Toast.makeText(this@ChatActivity, "ไม่สามารถส่งข้อความได้: $errorResponseBody", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Log.i("ChatActivity", "Message sent successfully. Refreshing chat.")
                    fetchChatMessages() // Refresh messages after sending
                }
            } catch (e: Exception) {
                Log.e("ChatActivity", "Error sending message: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ChatActivity, "เกิดข้อผิดพลาดในการส่งข้อความ: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun parseChatMessages(responseBody: String?): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        responseBody?.let {
            try {
                val jsonObject = JSONObject(it)
                val messagesArray = jsonObject.getJSONArray("messages")

                for (i in 0 until messagesArray.length()) {
                    val messageObject = messagesArray.getJSONObject(i)
                    val chatMessage = ChatMessage(
                        messageObject.getInt("senderID"),
                        messageObject.getString("nickname"),
                        messageObject.getString("imageFile"), // อาจเป็น "null" string หรือ URL
                        messageObject.getString("message"),
                        messageObject.getString("timestamp")
                    )
                    messages.add(chatMessage)
                }
                Log.d("ChatActivity", "Successfully parsed ${messages.size} chat messages.")
            } catch (e: Exception) {
                Log.e("ChatActivity", "Error parsing chat messages from JSON: ${e.message}. Response body: $responseBody", e)
            }
        } ?: Log.w("ChatActivity", "Response body is null when parsing chat messages.")
        return messages
    }
}

// Data class for storing chat message data
data class ChatMessage(
    val senderID: Int,
    val nickname: String,
    val profilePicture: String, // String อาจเป็น URL หรือ "null"
    val message: String,
    val timestamp: String
)