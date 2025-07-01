package com.bestpick.reviewhub

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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
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

class MessageFragment : Fragment() {

    private lateinit var recyclerViewUserList: RecyclerView
    private lateinit var buttonRestoreAllChats: Button
    private lateinit var progressBar: LottieAnimationView

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
                    adapter.updateUsers(fetchedUsers)
                }
            }
            handler.postDelayed(this, refreshInterval)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_message, container, false)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // Initialize views
        recyclerViewUserList = view.findViewById(R.id.recyclerViewUserList)
        buttonRestoreAllChats = view.findViewById(R.id.buttonRestoreAllChats)

        // Get userID from SharedPreferences
        val sharedPreferences = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val userIdString = sharedPreferences.getString("USER_ID", null)
        userID = userIdString?.toIntOrNull() ?: -1

        if (userID != -1) {
            fetchMatchedUsers { fetchedUsers ->
                if (fetchedUsers.isNotEmpty()) {
                    matchedUsers = fetchedUsers
                    setupRecyclerView()
                } else {
                    Toast.makeText(requireContext(), "No matched users found", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(requireContext(), "UserID not found", Toast.LENGTH_SHORT).show()
        }

        // Setup restore all chats button
        buttonRestoreAllChats.setOnClickListener {
            restoreAllChats(userID)
        }

        return view
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
                // Navigate to AnotherUserFragment
                val bundle = Bundle().apply {
                    putInt("USER_ID", user.userID)
                }
                findNavController().navigate(R.id.action_messageFragment_to_anotherUserFragment, bundle)
            },
            onDeleteChatClick = { user ->
                deleteChat(user.matchID)
            }
        )
        recyclerViewUserList.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewUserList.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    private fun deleteChat(matchID: Int) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val url = getString(R.string.root_url) + "/api/delete-chat"
            val requestBody = FormBody.Builder()
                .add("userID", userID.toString())
                .add("matchID", matchID.toString())
                .build()

            val request = Request.Builder().url(url).post(requestBody).build()

            try {
                val response = client.newCall(request).execute()
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful) {
                        Toast.makeText(requireContext(), "Chat deleted successfully", Toast.LENGTH_SHORT).show()
                        fetchMatchedUsers { adapter.updateUsers(it) }
                    } else {
                        Toast.makeText(requireContext(), "Failed to delete chat", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun restoreAllChats(userID: Int) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val url = getString(R.string.root_url) + "/api/restore-all-chats"
            val requestBody = FormBody.Builder()
                .add("userID", userID.toString())
                .build()

            val request = Request.Builder().url(url).post(requestBody).build()

            try {
                val response = client.newCall(request).execute()
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful) {
                        Toast.makeText(requireContext(), "All chats restored successfully", Toast.LENGTH_SHORT).show()
                        fetchMatchedUsers { adapter.updateUsers(it) }
                    } else {
                        Toast.makeText(requireContext(), "Failed to restore chats", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchMatchedUsers(callback: (List<MatchedUser>) -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            val url = getString(R.string.root_url) + "/api/matches/$userID"
            Log.d("API Request", "Fetching matched users from URL: $url")
            val request = Request.Builder().url(url).build()

            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.d("API Response", responseBody ?: "No response")
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
                    profilePicture = imageUrl,
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
}

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
        holder.lastMessage.text = user.lastMessage ?: "No messages yet"
        holder.lastInteraction.text = formatTime(user.lastInteraction)

        Glide.with(holder.profileImage.context)
            .load(user.profilePicture)
            .placeholder(R.drawable.user)
            .error(R.drawable.user)
            .into(holder.profileImage)

        holder.itemView.setOnClickListener { onChatClick(user) }
        holder.profileImage.setOnClickListener { onProfileClick(user) }

        holder.buttonDeleteChat.setOnClickListener {
            AlertDialog.Builder(holder.itemView.context)
                .setTitle("Delete Chat")
                .setMessage("Are you sure you want to delete this chat? This will hide the chat from your list.")
                .setPositiveButton("Delete") { dialog, _ ->
                    onDeleteChatClick(user)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }

    override fun getItemCount() = users.size

    private fun formatTime(timestamp: String?): String {
        return if (timestamp != null && timestamp != "null") {
            try {
                // Handle both HH:mm format and full timestamp
                if (timestamp.contains(":") && timestamp.length == 5) {
                    // Already in HH:mm format
                    timestamp
                } else {
                    // Convert from full timestamp
                    val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val date = inputFormat.parse(timestamp)
                    outputFormat.format(date!!)
                }
            } catch (e: Exception) {
                ""
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