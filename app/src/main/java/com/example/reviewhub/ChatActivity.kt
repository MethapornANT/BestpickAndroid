package com.bestpick.reviewhub

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
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
    private lateinit var buttonBlockOptions: ImageButton

    // ✅ **จุดแก้ไข: ปิดการใช้งาน Cache ของ OkHttpClient**
    private val client = OkHttpClient.Builder()
        .cache(null)
        .build()

    private var matchID: Int = -1
    private var senderID: Int = -1
    private var receiverNickname: String = ""
    private var isBlocked: Boolean = false

    private val handler = Handler(Looper.getMainLooper())
    private val refreshInterval = 2000L
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

        toolbar = findViewById(R.id.toolbar)
        recyclerViewChat = findViewById(R.id.recyclerViewChat)
        emptyChatMessage = findViewById(R.id.emptyChatMessage)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        buttonBlockOptions = toolbar.findViewById(R.id.buttonBlockOptions)

        matchID = intent.getIntExtra("matchID", -1)
        senderID = intent.getIntExtra("senderID", -1)
        receiverNickname = intent.getStringExtra("nickname") ?: ""

        Log.d("ChatActivity", "Received matchID: $matchID, senderID: $senderID, nickname: $receiverNickname")

        if (matchID == -1 || senderID == -1) {
            Log.e("ChatActivity", "Chat data not found. matchID: $matchID, senderID: $senderID")
            Toast.makeText(this, "ไม่พบข้อมูลการสนทนา", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setSupportActionBar(toolbar)
        supportActionBar?.title = receiverNickname
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val chatAdapter = ChatAdapter(senderID) { clickedUserID ->
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("NAVIGATE_TO_USER_PROFILE_ID", clickedUserID)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        }
        recyclerViewChat.layoutManager = LinearLayoutManager(this)
        recyclerViewChat.adapter = chatAdapter

        checkBlockStatus()

        sendButton.setOnClickListener {
            val message = messageInput.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessage(message)
                messageInput.text.clear()
            } else {
                Toast.makeText(this, "กรุณาพิมพ์ข้อความ", Toast.LENGTH_SHORT).show()
            }
        }

        buttonBlockOptions.setOnClickListener { showBlockSheet(isBlocked) }
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    private fun checkBlockStatus() {
        lifecycleScope.launch(Dispatchers.IO) {
            val url = getString(R.string.root_url) + "/api/block-status"
            val requestBody = FormBody.Builder()
                .add("userID", senderID.toString())
                .add("matchID", matchID.toString())
                .build()
            val request = Request.Builder().url(url).post(requestBody).build()

            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                Log.d("ChatActivity", "Block status API Response: ${response.code} - $responseBody")

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && responseBody != null) {
                        val jsonObject = JSONObject(responseBody)
                        isBlocked = jsonObject.optBoolean("isBlocked", false)
                    } else {
                        Log.e("ChatActivity", "Failed to get block status: ${response.code} - $responseBody")
                    }
                    updateUIBasedOnBlockStatus()
                    fetchChatMessages()
                }
            } catch (e: Exception) {
                Log.e("ChatActivity", "Error checking block status: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    updateUIBasedOnBlockStatus()
                    fetchChatMessages()
                }
            }
        }
    }

    private fun updateUIBasedOnBlockStatus() {
        if (isBlocked) {
            messageInput.isEnabled = false
            sendButton.isEnabled = false
            messageInput.hint = "คุณได้บล็อกการสนทนานี้"
            Log.d("ChatActivity", "UI updated: Chat is blocked.")
        } else {
            messageInput.isEnabled = true
            sendButton.isEnabled = true
            messageInput.hint = "พิมพ์ข้อความ"
            Log.d("ChatActivity", "UI updated: Chat is unblocked.")
        }
    }

    private fun showBlockSheet(blocked: Boolean) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_block_actions, null)
        dialog.setContentView(view)

        val title = view.findViewById<TextView>(R.id.bs_title)
        val subtitle = view.findViewById<TextView>(R.id.bs_subtitle)
        val icon = view.findViewById<ImageView>(R.id.bs_icon)
        val btnBlock = view.findViewById<MaterialButton>(R.id.btnBlock)
        val btnUnblock = view.findViewById<MaterialButton>(R.id.btnUnblock)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnCancel)

        if (blocked) {
            title.text = "การสนทนานี้ถูกบล็อกอยู่"
            subtitle.text = "คุณต้องการปลดบล็อกหรือไม่?"
            icon.setImageResource(R.drawable.ic_warning)
            btnBlock.visibility = View.GONE
            btnUnblock.visibility = View.VISIBLE
        } else {
            title.text = "ตัวเลือกการบล็อก"
            subtitle.text = "คุณต้องการบล็อกการสนทนานี้หรือไม่?"
            icon.setImageResource(R.drawable.ic_warning)
            btnBlock.visibility = View.VISIBLE
            btnUnblock.visibility = View.GONE
        }

        btnBlock.setOnClickListener {
            dialog.dismiss()
            confirmBlockSheet()
        }
        btnUnblock.setOnClickListener {
            dialog.dismiss()
            unblockChat()
        }
        btnCancel.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun confirmBlockSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_block_actions, null)
        dialog.setContentView(view)

        val title = view.findViewById<TextView>(R.id.bs_title)
        val subtitle = view.findViewById<TextView>(R.id.bs_subtitle)
        val icon = view.findViewById<ImageView>(R.id.bs_icon)
        val btnBlock = view.findViewById<MaterialButton>(R.id.btnBlock)
        val btnUnblock = view.findViewById<MaterialButton>(R.id.btnUnblock)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnCancel)

        title.text = "บล็อกการสนทนา?"
        subtitle.text = "คุณจะไม่สามารถส่งข้อความได้จนกว่าจะปลดบล็อก"
        icon.setImageResource(R.drawable.ic_block)

        btnBlock.visibility = View.VISIBLE
        btnBlock.text = "ยืนยันการบล็อก"
        btnUnblock.visibility = View.GONE

        btnBlock.setOnClickListener {
            dialog.dismiss()
            blockChat()
        }
        btnCancel.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun blockChat() {
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
                val responseBody = response.body?.string()
                Log.d("ChatActivity", "Block API Response: ${response.code} - $responseBody")

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        isBlocked = true
                        Toast.makeText(this@ChatActivity, "บล็อกแชทเรียบร้อย", Toast.LENGTH_SHORT).show()
                        updateUIBasedOnBlockStatus()
                    } else {
                        Toast.makeText(this@ChatActivity, "ไม่สามารถบล็อคแชทได้: $responseBody", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatActivity", "Error blocking chat: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ChatActivity, "เกิดข้อผิดพลาดในการบล็อคแชท", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun unblockChat() {
        lifecycleScope.launch(Dispatchers.IO) {
            val url = getString(R.string.root_url) + "/api/unblock-chat"
            val requestBody = FormBody.Builder()
                .add("userID", senderID.toString())
                .add("matchID", matchID.toString())
                .build()
            val request = Request.Builder().url(url).post(requestBody).build()

            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                Log.d("ChatActivity", "Unblock API Response: ${response.code} - $responseBody")

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        isBlocked = false
                        Toast.makeText(this@ChatActivity, "ปลดบล็อคแชทเรียบร้อย", Toast.LENGTH_SHORT).show()
                        updateUIBasedOnBlockStatus()
                    } else {
                        Toast.makeText(this@ChatActivity, "ไม่สามารถปลดบล็อคแชทได้: $responseBody", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatActivity", "Error unblocking chat: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ChatActivity, "เกิดข้อผิดพลาดในการปลดบล็อคแชท", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sendMessage(message: String) {
        if (isBlocked) {
            Toast.makeText(this, "ไม่สามารถส่งข้อความได้ในแชทที่ถูกบล็อก", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val url = getString(R.string.root_url) + "/api/chats/$matchID"
            val requestBody = FormBody.Builder()
                .add("senderID", senderID.toString())
                .add("message", message)
                .build()
            val request = Request.Builder().url(url).post(requestBody).build()

            try {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    withContext(Dispatchers.Main) {
                        if (response.code == 403) {
                            Toast.makeText(this@ChatActivity, "คุณถูกบล็อก ไม่สามารถส่งข้อความได้", Toast.LENGTH_SHORT).show()
                            isBlocked = true
                            updateUIBasedOnBlockStatus()
                        } else {
                            Toast.makeText(this@ChatActivity, "ส่งข้อความไม่สำเร็จ: $errorBody", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Log.i("ChatActivity", "Message sent successfully")
                    fetchChatMessages()
                }
            } catch (e: Exception) {
                Log.e("ChatActivity", "Error sending message: ${e.message}", e)
                withContext(Dispatchers.Main) {
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
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val messages = parseChatMessages(responseBody)

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
                    Log.e("ChatActivity", "Failed to fetch chat messages: ${response.code} - ${response.message}")
                }
            } catch (e: Exception) {
                Log.e("ChatActivity", "Error fetching messages: ${e.message}", e)
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
                    val obj = messagesArray.getJSONObject(i)
                    messages.add(
                        ChatMessage(
                            senderID = obj.getInt("senderID"),
                            nickname = obj.getString("nickname"),
                            profilePicture = obj.getString("imageFile"),
                            message = obj.getString("message"),
                            timestamp = obj.getString("timestamp")
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("ChatActivity", "Error parsing chat messages: ${e.message}. Body: $responseBody", e)
            }
        }
        return messages
    }
}

data class ChatMessage(
    val senderID: Int,
    val nickname: String,
    val profilePicture: String,
    val message: String,
    val timestamp: String
)