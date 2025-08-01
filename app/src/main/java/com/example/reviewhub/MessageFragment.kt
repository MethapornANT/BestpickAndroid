package com.bestpick.reviewhub

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator
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
    private lateinit var progressBar: ProgressBar
    private var userID: Int = -1
    private val client = OkHttpClient()
    private var matchedUsers = mutableListOf<MatchedUser>()
    private val handler = Handler()
    private val refreshInterval = 2000L
    private lateinit var adapter: MatchedUserAdapter

    private val refreshRunnable = object : Runnable {
        override fun run() {
            fetchMatchedUsers { fetchedUsers ->
                adapter.updateUsers(fetchedUsers)
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

        recyclerViewUserList = view.findViewById(R.id.recyclerViewUserList)
        buttonRestoreAllChats = view.findViewById(R.id.buttonRestoreAllChats)
        progressBar = view.findViewById(R.id.progress_bar)

        val sharedPreferences = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val userIdString = sharedPreferences.getString("USER_ID", null)
        userID = userIdString?.toIntOrNull() ?: -1

        setupRecyclerView()

        if (userID != -1) {
            fetchMatchedUsers { fetchedUsers ->
                adapter.updateUsers(fetchedUsers)
            }
        } else {
            Toast.makeText(requireContext(), "UserID not found", Toast.LENGTH_SHORT).show()
        }

        buttonRestoreAllChats.setOnClickListener {
            restoreAllChats(userID)
        }

        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val userToDelete = adapter.getUserAt(position)
                    showDeleteConfirmationDialog(userToDelete, position)
                }
            }

            override fun onChildDraw(c: android.graphics.Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                RecyclerViewSwipeDecorator.Builder(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                    .addSwipeLeftBackgroundColor(Color.RED)
                    .addSwipeLeftActionIcon(R.drawable.ic_delete)
                    .addSwipeLeftLabel("Delete")
                    .setSwipeLeftLabelColor(Color.WHITE)
                    .create()
                    .decorate()
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(recyclerViewUserList)

        return view
    }

    private fun showDeleteConfirmationDialog(user: MatchedUser, position: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Chat")
            .setMessage("Are you sure you want to delete the chat with ${user.nickname}?")
            .setPositiveButton("Delete") { dialog, _ ->
                deleteChat(user.matchID)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                adapter.notifyItemChanged(position)
                dialog.dismiss()
            }
            .setOnCancelListener {
                adapter.notifyItemChanged(position)
            }
            .show()
    }

    private fun setupRecyclerView() {
        adapter = MatchedUserAdapter(
            matchedUsers,
            onChatClick = { user ->
                val intent = Intent(requireContext(), ChatActivity::class.java).apply {
                    putExtra("matchID", user.matchID)
                    putExtra("senderID", userID)
                    putExtra("nickname", user.nickname)
                }
                startActivity(intent)
            },
            onProfileClick = { user ->
                val bundle = Bundle().apply { putInt("USER_ID", user.userID) }
                findNavController().navigate(R.id.action_messageFragment_to_anotherUserFragment, bundle)
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
                        fetchMatchedUsers { updatedUsers -> adapter.updateUsers(updatedUsers) }
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
                        fetchMatchedUsers { updatedUsers -> adapter.updateUsers(updatedUsers) }
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
            if (userID == -1) return@launch
            val url = getString(R.string.root_url) + "/api/matches/$userID"
            val request = Request.Builder().url(url).build()
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val matchedUsersList = parseUsers(responseBody)
                    withContext(Dispatchers.Main) {
                        callback(matchedUsersList)
                    }
                }
            } catch (e: Exception) {
                Log.e("MessageFragment", "Failed to fetch users: ${e.message}")
            }
        }
    }

    private fun parseUsers(responseBody: String?): List<MatchedUser> {
        val users = mutableListOf<MatchedUser>()
        responseBody?.let {
            try {
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
            } catch (e: Exception) {
                Log.e("MessageFragment", "Error parsing users JSON: ${e.message}")
            }
        }
        return users
    }
}

class MatchedUserAdapter(
    private var users: MutableList<MatchedUser>,
    private val onChatClick: (MatchedUser) -> Unit,
    private val onProfileClick: (MatchedUser) -> Unit
) : RecyclerView.Adapter<MatchedUserAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nickname: TextView = view.findViewById(R.id.textNickname)
        val profileImage: ImageView = view.findViewById(R.id.imageProfile)
        val lastMessage: TextView = view.findViewById(R.id.lastMessage)
        val lastInteraction: TextView = view.findViewById(R.id.textLastInteraction)
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
    }

    override fun getItemCount() = users.size

    fun getUserAt(position: Int): MatchedUser { return users[position] }

    fun updateUsers(newUsers: List<MatchedUser>) {
        users.clear()
        users.addAll(newUsers)
        notifyDataSetChanged()
    }

    private fun formatTime(timestamp: String?): String {
        return if (timestamp != null && timestamp != "null") {
            try {
                if (timestamp.contains(":") && timestamp.length == 5) {
                    timestamp
                } else {
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