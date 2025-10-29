package com.bestpick.reviewhub

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import androidx.navigation.fragment.findNavController
import org.json.JSONException

class PostDetailFragment : Fragment() {
    private lateinit var dotIndicatorLayout: LinearLayout
    private lateinit var follower: TextView
    private var bottomNav: BottomNavigationView? = null
    private lateinit var recyclerViewComments: RecyclerView
    private lateinit var comments: MutableList<Comment>

    private var isBookmark: Boolean = false
    private var followingId: Int = -1
    private var isLiked: Boolean = false

    // === NEW: เก็บไว้ใช้ตอน share ===
    private var postTitle: String? = null
    private var postUserName: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_post_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        follower = view.findViewById(R.id.follower)
        recyclerViewComments = view.findViewById(R.id.recycler_view_comments)

        val postId = arguments?.getInt("POST_ID", -1) ?: -1

        recyclerViewComments.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewComments.adapter = CommentAdapter(emptyList(), postId)
        dotIndicatorLayout = view.findViewById(R.id.dot_indicator_layout)
        bottomNav = (activity as? MainActivity)?.findViewById(R.id.bottom_navigation)
        bottomNav?.visibility = View.GONE

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            bottomNav?.visibility = View.VISIBLE
            parentFragmentManager.popBackStack()
        }

        val sharedPreferences = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val token = sharedPreferences.getString("TOKEN", null)
        val userId = sharedPreferences.getString("USER_ID", null)?.toIntOrNull()

        val bookmarkButton = view.findViewById<ImageView>(R.id.bookmark_button)
        val likeButton = view.findViewById<ImageView>(R.id.like_button)
        val back = view.findViewById<ImageView>(R.id.back_button)
        val report = view.findViewById<ImageView>(R.id.report)
        val Imgview = view.findViewById<ImageView>(R.id.Imgview)
        val commentButton = view.findViewById<ImageView>(R.id.send_button)
        val commentEditText = view.findViewById<EditText>(R.id.comment_input)

        // === NEW: share button ===
        val shareButton = view.findViewById<ImageView>(R.id.share_button)
        shareButton.setOnClickListener {
            // log interaction -> DB
            if (token != null && postId != -1) {
                recordInteraction(postId, "share", null, token, requireContext())
            }
            // share to external apps
            if (postId != -1) {
                sharePost(postId)
            }
        }

        bookmarkButton.setOnClickListener {
            isBookmark = !isBookmark
            bookmarkButton.setImageResource(if (isBookmark) R.drawable.bookmarkclick else R.drawable.bookmark)
            if (token != null && userId != null) {
                bookmarkPost(postId, token, requireContext())
            }
        }

        likeButton.setOnClickListener {
            if (token != null && userId != null) {
                if (isLiked) {
                    likeUnlikePost(postId, userId, token)
                    recordInteraction(postId, "unlike", null, token, requireContext())
                } else {
                    likeUnlikePost(postId, userId, token)
                    recordInteraction(postId, "like", null, token, requireContext())
                }
            }
        }

        follower.setOnClickListener {
            if (token != null && userId != null) {
                followUser(userId.toInt(), followingId, token)
                val actionType = if (follower.text == "Following") "unfollow" else "follow"
                recordInteraction(postId, actionType, null, token, requireContext())
            }
        }

        back.setOnClickListener {
            bottomNav?.visibility = View.VISIBLE
            parentFragmentManager.popBackStack()
        }

        report.setOnClickListener {
            val isUserPost = userId == followingId
            showReportMenu(requireContext(), it, postId, isUserPost)
        }

        Imgview.setOnClickListener {
            openUserProfile(followingId)
        }

        commentButton.setOnClickListener {
            if (token != null && userId != null) {
                val commentContent = commentEditText.text.toString().trim()
                if (commentContent.isNotEmpty()) {
                    postComment(postId, userId.toInt(), commentContent, token) { commentId ->
                        sendNotification(postId, userId.toInt(), commentId, "comment", token, requireContext())
                        commentEditText.text.clear()
                        fetchPostDetails(postId, token, userId.toInt(), view)
                    }
                } else {
                    Toast.makeText(requireContext(), "Comment cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
        }

        if (postId != -1 && token != null && userId != null) {
            CoroutineScope(Dispatchers.Main).launch {
                fetchPostDetails(postId, token, userId.toInt(), view)
                checkLikeStatus(postId, userId, token, view)
            }
        }
    }

    private fun setupPageIndicators(totalPages: Int) {
        val dotSize = 30
        dotIndicatorLayout.removeAllViews()
        for (i in 0 until totalPages) {
            val dot = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                    setMargins(8, 0, 8, 0)
                }
                setImageResource(R.drawable.outline_circle_24)
                scaleX = 1.0f
                scaleY = 1.0f
            }
            dotIndicatorLayout.addView(dot)
        }
    }

    private fun checkBookmarkStatus(postId: Int, userId: Int, token: String) {
        val client = OkHttpClient()
        val url = "${requireContext().getString(R.string.root_url)}/api/bookmarks/$postId"

        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val jsonObject = JSONObject(responseBody)
                        isBookmark = jsonObject.getBoolean("isBookmarked")
                        withContext(Dispatchers.Main) {
                            val bookmarkButton = requireView().findViewById<ImageView>(R.id.bookmark_button)
                            bookmarkButton.setImageResource(if (isBookmark) R.drawable.bookmarkclick else R.drawable.bookmark)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { }
            }
        }
    }

    private fun bookmarkPost(postId: Int, token: String, context: Context) {
        val client = OkHttpClient()
        val url = "${context.getString(R.string.root_url)}/api/posts/$postId/bookmark"

        val request = Request.Builder()
            .url(url)
            .post(FormBody.Builder().build())
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                (context as? Activity)?.runOnUiThread { }
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    (context as? Activity)?.runOnUiThread { }
                }
            }
        })
    }

    private fun updatePageIndicators(selectedPosition: Int) {
        for (i in 0 until dotIndicatorLayout.childCount) {
            val dot = dotIndicatorLayout.getChildAt(i) as ImageView
            if (i == selectedPosition) {
                animateDot(dot, true)
                dot.setImageResource(R.drawable.baseline_circle_24)
            } else {
                animateDot(dot, false)
                dot.setImageResource(R.drawable.outline_circle_24)
            }
        }
    }

    private fun openUserProfile(userId: Int) {
        val sharedPreferences = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val currentUserId = sharedPreferences.getString("USER_ID", null)?.toIntOrNull()
        val token = sharedPreferences.getString("TOKEN", null)
        val navController = findNavController()

        if (currentUserId == null) {
            Log.e("UserProfile", "USER_ID is null or invalid")
            return
        }

        if (userId == currentUserId) {
            navController.navigate(R.id.action_postDetailFragment_to_myProfileFragment)
            val bottomNav = activity?.findViewById<BottomNavigationView>(R.id.bottom_navigation)
            bottomNav?.visibility = View.VISIBLE
            bottomNav?.menu?.findItem(R.id.profileFragment)?.isChecked = true
            bottomNav?.selectedItemId = R.id.profileFragment
        } else {
            val bundle = Bundle().apply { putInt("USER_ID", userId) }
            token?.let { recordInteraction(null, "view_profile", null, it, requireContext()) }
            navController.navigate(R.id.action_postDetailFragment_to_userProfileFragment, bundle)
        }
    }

    private fun animateDot(dot: ImageView, isSelected: Boolean) {
        val scale = if (isSelected) 1.4f else 1.0f
        ObjectAnimator.ofFloat(dot, "scaleX", scale).apply { duration = 300; start() }
        ObjectAnimator.ofFloat(dot, "scaleY", scale).apply { duration = 300; start() }
    }

    private fun showReportMenu(context: Context, anchorView: View, postId: Int, isUserPost: Boolean) {
        val popupMenu = PopupMenu(context, anchorView)
        popupMenu.menuInflater.inflate(R.menu.menu_report, popupMenu.menu)

        popupMenu.menu.findItem(R.id.edit_post).isVisible = isUserPost
        popupMenu.menu.findItem(R.id.delete_post).isVisible = isUserPost
        popupMenu.menu.findItem(R.id.report).isVisible = !isUserPost

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.report -> {
                    val sharedPreferences = context.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
                    val token = sharedPreferences.getString("TOKEN", null)
                    val userId = sharedPreferences.getString("USER_ID", null)?.toIntOrNull()

                    if (token != null && userId != null) {
                        val reportOptions = arrayOf("Inappropriate Content", "Copyright Violation", "Scam or Spam", "Violence or Threats", "Misinformation or False Information", "Fraud or Malicious Intent")
                        val builder = AlertDialog.Builder(context, R.style.CustomAlertDialog)
                        builder.setTitle("Report Post")
                        builder.setSingleChoiceItems(reportOptions, -1) { dialog, which ->
                            val selectedReason = reportOptions[which]
                            reportPost(postId, userId, selectedReason, token)
                            dialog.dismiss()
                        }
                        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                        builder.show()
                    }
                    true
                }
                R.id.edit_post -> {
                    val sharedPreferences = context.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
                    val token = sharedPreferences.getString("TOKEN", null)
                    token?.let {
                        val EditpostFragment = EditPostFragment()
                        val bundle = Bundle().apply {
                            putInt("POST_ID", postId)
                            putString("From", "post_detail")
                        }
                        EditpostFragment.arguments = bundle
                        (context as? FragmentActivity)?.supportFragmentManager?.beginTransaction()
                            ?.replace(R.id.nav_host_fragment, EditpostFragment)
                            ?.addToBackStack(null)
                            ?.commit()
                    }
                    true
                }
                R.id.delete_post -> {
                    val confirmDeleteBuilder = AlertDialog.Builder(context)
                    confirmDeleteBuilder.setTitle("Confirm Deletion")
                    confirmDeleteBuilder.setMessage("Are you sure you want to delete this post?")
                    confirmDeleteBuilder.setPositiveButton("Yes") { dialog, _ ->
                        deletePost(postId, context); dialog.dismiss()
                    }
                    confirmDeleteBuilder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                    confirmDeleteBuilder.show()
                    true
                }
                else -> false
            }
        }

        popupMenu.show()
    }

    private fun showDeleteMenu(context: Context, anchorView: View, commentId: Int, postId: Int) {
        val popupMenu = PopupMenu(context, anchorView)
        popupMenu.menuInflater.inflate(R.menu.menu_delete, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.delete_comment -> { deleteComment(commentId, postId, context); true }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun deleteComment(commentId: Int, postId: Int, context: Context) {
        val client = OkHttpClient()
        val sharedPreferences = context.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences.getString("TOKEN", null)
        val userId = sharedPreferences.getString("USER_ID", null)?.toIntOrNull()

        if (token != null) {
            val url = "${context.getString(R.string.root_url)}/api/posts/$postId/comment/$commentId"
            val request = Request.Builder()
                .url(url)
                .delete()
                .addHeader("Authorization", "Bearer $token")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { (context as? Activity)?.runOnUiThread { } }
                override fun onResponse(call: Call, response: Response) {
                    (context as? Activity)?.runOnUiThread {
                        if (!response.isSuccessful) {
                            Toast.makeText(context, "Failed to delete comment", Toast.LENGTH_SHORT).show()
                        } else {
                            if (userId != null) {
                                fetchPostDetails(postId, token, userId, requireView())
                            }
                        }
                    }
                }
            })
        }
    }

    private fun deletePost(postId: Int, context: Context) {
        val client = OkHttpClient()
        val sharedPreferences = context.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences.getString("TOKEN", null)
        val userId = sharedPreferences.getString("USER_ID", null)

        if (token != null && userId != null) {
            val url = "${context.getString(R.string.root_url)}/api/posts/$postId"
            val requestBody = FormBody.Builder().add("user_id", userId).build()

            val request = Request.Builder()
                .url(url)
                .delete(requestBody)
                .addHeader("Authorization", "Bearer $token")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { (context as? Activity)?.runOnUiThread { } }
                override fun onResponse(call: Call, response: Response) {
                    val jsonResponse = response.body?.string()
                    (context as? Activity)?.runOnUiThread {
                        if (!response.isSuccessful) {
                            val errorMessage = JSONObject(jsonResponse ?: "{}").optString("error", "Failed to delete post")
                            Toast.makeText(context, "Error: $errorMessage", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Post deleted successfully", Toast.LENGTH_SHORT).show()
                            bottomNav?.visibility = View.VISIBLE
                            parentFragmentManager.popBackStack()
                        }
                    }
                }
            })
        }
    }

    private fun followUser(userId: Int, followingId: Int, token: String) {
        val client = OkHttpClient()
        val url = "${getString(R.string.root_url)}/api/users/$userId/follow/$followingId"

        val request = Request.Builder()
            .url(url)
            .post(RequestBody.create(null, ByteArray(0)))
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { (requireActivity() as? Activity)?.runOnUiThread { } }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        (requireActivity() as? Activity)?.runOnUiThread {
                            checkFollowStatus(userId, followingId, token)
                        }
                    } else {
                        (requireActivity() as? Activity)?.runOnUiThread { }
                    }
                }
            }
        })
    }

    private fun checkFollowStatus(userId: Int, followingId: Int, token: String) {
        val client = OkHttpClient()
        val url = "${getString(R.string.root_url)}/api/users/$userId/follow/$followingId/status"

        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val jsonObject = JSONObject(responseBody)
                        val isFollowing = jsonObject.getBoolean("isFollowing")
                        withContext(Dispatchers.Main) {
                            follower.text = if (isFollowing) "Following" else "Follow"
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun fetchPostDetails(postId: Int, token: String, userId: Int, view: View) {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val url = getString(R.string.root_url) + getString(R.string.postdetail) + postId
            val api = "/api"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .build()

            try {
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val jsonObject = JSONObject(responseBody)

                        val postContent = jsonObject.getString("content")
                        val title = jsonObject.getString("Title")
                        val likeCount = jsonObject.getInt("like_count")
                        val commentCount = jsonObject.getInt("comment_count")
                        val username = jsonObject.getString("username")
                        followingId = jsonObject.getInt("user_id")
                        val time = jsonObject.getString("created_at")
                        val profileImage = jsonObject.getString("picture")
                        val profileUrl = getString(R.string.root_url) + api + profileImage
                        val productname = jsonObject.getString("ProductName")

                        // === NEW: เก็บไว้ใช้ตอน share ===
                        postTitle = title
                        postUserName = username

                        // Comments
                        comments = mutableListOf()
                        val commentsArray = jsonObject.getJSONArray("comments")
                        for (i in 0 until commentsArray.length()) {
                            val commentObject = commentsArray.getJSONObject(i)
                            val comment = Comment(
                                id = commentObject.getInt("id"),
                                user_id = commentObject.getInt("user_id"),
                                content = commentObject.getString("content"),
                                username = commentObject.getString("username"),
                                createdAt = commentObject.getString("created_at"),
                                profileImage = commentObject.getString("user_profile")
                            )
                            comments.add(comment)
                        }

                        val postImageUrls = jsonObject.getJSONArray("photo_url")
                        val postVideoUrls = jsonObject.getJSONArray("video_url")

                        val mediaUrls = mutableListOf<Pair<String, String>>()
                        for (i in 0 until postImageUrls.length()) {
                            val innerImageArray = postImageUrls.getJSONArray(i)
                            for (j in 0 until innerImageArray.length()) {
                                val imageUrl = innerImageArray.getString(j)
                                mediaUrls.add(Pair(getString(R.string.root_url) + "/api" + imageUrl, "photo"))
                            }
                        }
                        for (i in 0 until postVideoUrls.length()) {
                            val innerVideoArray = postVideoUrls.getJSONArray(i)
                            for (j in 0 until innerVideoArray.length()) {
                                val videoUrl = innerVideoArray.getString(j)
                                mediaUrls.add(Pair(getString(R.string.root_url) + "/api" + videoUrl, "video"))
                            }
                        }

                        withContext(Dispatchers.Main) {
                            if (view.isAttachedToWindow) {
                                if (comments.isNotEmpty()) {
                                    recyclerViewComments.adapter = CommentAdapter(comments, postId)
                                    recyclerViewComments.adapter?.notifyDataSetChanged()
                                }

                                view.findViewById<TextView>(R.id.username).text = username
                                view.findViewById<TextView>(R.id.title).text = title
                                view.findViewById<TextView>(R.id.detail).text = postContent
                                view.findViewById<TextView>(R.id.time).text = formatTime(time)
                                view.findViewById<TextView>(R.id.like_count).text = ": $likeCount"
                                view.findViewById<TextView>(R.id.comment_count).text = "$commentCount Comments"

                                val likeCountTextView = view.findViewById<TextView>(R.id.like_count)
                                likeCountTextView.text = ": $likeCount"
                                likeCountTextView.isClickable = true
                                likeCountTextView.setOnClickListener {
                                    val bundle = Bundle().apply { putInt("POST_ID", postId) }
                                    try {
                                        findNavController().navigate(R.id.likeListFragment, bundle)
                                    } catch (e: IllegalArgumentException) {
                                        Log.e("PostDetailFragment", "Navigation to likeListFragment failed: ${e.message}")
                                        Toast.makeText(requireContext(), "Cannot open likes (missing nav entry)", Toast.LENGTH_SHORT).show()
                                    }
                                }

                                checkFollowStatus(userId, followingId, token)
                                if (userId == followingId) {
                                    follower.visibility = View.GONE
                                } else {
                                    checkFollowStatus(userId, followingId, token)
                                }
                                checkBookmarkStatus(postId, userId, token)

                                Glide.with(this@PostDetailFragment)
                                    .load(profileUrl)
                                    .into(view.findViewById(R.id.Imgview))

                                val viewPager = view.findViewById<ViewPager2>(R.id.ShowImgpost)
                                val adapter = PhotoPagerAdapter(mediaUrls)
                                viewPager.adapter = adapter

                                setupPageIndicators(mediaUrls.size)
                                viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                                    override fun onPageSelected(position: Int) {
                                        super.onPageSelected(position)
                                        updatePageIndicators(position)
                                    }
                                })
                            }
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) { }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Log.e("PostDetailFragment", "Error: ${e.message}", e)
                }
            }
        }
    }

    private fun likeUnlikePost(postId: Int, userId: Int?, token: String) {
        val client = OkHttpClient()
        val url = requireContext().getString(R.string.root_url) + requireContext().getString(R.string.postlikeorunlike) + postId
        val requestBody = FormBody.Builder().add("user_id", userId.toString()).build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { (requireActivity() as? Activity)?.runOnUiThread { } }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        (requireActivity() as? Activity)?.runOnUiThread { }
                    } else {
                        val responseBody = response.body?.string()
                        val jsonObject = responseBody?.let { JSONObject(it) }
                        val newLikeCount = jsonObject?.getInt("likeCount") ?: 0
                        (requireActivity() as? Activity)?.runOnUiThread {
                            checkLikeStatus(postId, userId ?: 0, token, requireView())
                            val likeCountTextView = requireView().findViewById<TextView>(R.id.like_count)
                            likeCountTextView.text = ": $newLikeCount"
                        }
                    }
                }
            }
        })
    }

    private fun postComment(postId: Int, userId: Int, content: String, token: String, callback: (Int?) -> Unit) {
        val client = OkHttpClient()
        val url = getString(R.string.root_url) + "/api/posts/$postId/comment"

        val requestBody = FormBody.Builder().add("content", content).build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                (requireActivity() as? Activity)?.runOnUiThread { callback(null) }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        val jsonObject = responseBody?.let { JSONObject(it) }
                        val commentId = jsonObject?.getInt("comment_id")
                        (requireActivity() as? Activity)?.runOnUiThread {
                            Toast.makeText(requireContext(), "Comment successfully", Toast.LENGTH_SHORT).show()
                            callback(commentId)
                        }
                    } else {
                        (requireActivity() as? Activity)?.runOnUiThread { callback(null) }
                    }
                }
            }
        })
    }

    private fun sendNotification(postId: Int, userId: Int, commentId: Int?, actionType: String, token: String, context: Context) {
        val client = OkHttpClient()
        val url = "${context.getString(R.string.root_url)}/api/notifications"

        val requestBodyBuilder = FormBody.Builder()
            .add("user_id", userId.toString())
            .add("post_id", postId.toString())
            .add("action_type", actionType)
            .add("content", "User $userId performed action: $actionType on post $postId")

        commentId?.let { requestBodyBuilder.add("comment_id", it.toString()) }

        val request = Request.Builder()
            .url(url).post(requestBodyBuilder.build())
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { (context as? Activity)?.runOnUiThread { } }
            override fun onResponse(call: Call, response: Response) { (context as? Activity)?.runOnUiThread { } }
        })
    }

    private fun deleteNotification(postId: Int, userId: Int, commentId: Int?, actionType: String, token: String, context: Context) {
        val client = OkHttpClient()
        val url = "${context.getString(R.string.root_url)}/api/notifications"

        val requestBodyBuilder = FormBody.Builder()
            .add("user_id", userId.toString())
            .add("post_id", postId.toString())
            .add("action_type", actionType)

        commentId?.let { requestBodyBuilder.add("comment_id", it.toString()) }

        val request = Request.Builder()
            .url(url).delete(requestBodyBuilder.build())
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                (context as? Activity)?.runOnUiThread {
                    Toast.makeText(context, "Failed to delete notification: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onResponse(call: Call, response: Response) { (context as? Activity)?.runOnUiThread { } }
        })
    }

    private fun checkLikeStatus(postId: Int, userId: Int, token: String, view: View) {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val url = "${requireContext().getString(R.string.root_url)}${requireContext().getString(R.string.check_like_status)}$postId/$userId"

            val request = Request.Builder().url(url).get().addHeader("Authorization", "Bearer $token").build()

            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val jsonObject = JSONObject(responseBody)
                        isLiked = jsonObject.getBoolean("isLiked")
                        withContext(Dispatchers.Main) {
                            val likeButton = view.findViewById<ImageView>(R.id.like_button)
                            likeButton.setImageResource(if (isLiked) R.drawable.heartclick else R.drawable.heart)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { }
            }
        }
    }

    data class Comment(val id: Int,val user_id: Int,val content: String, val username: String, val createdAt: String, val profileImage: String)

    inner class CommentAdapter(private val comments: List<Comment>, private val postId: Int) :
        RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.comment_postdetail_item, parent, false)
            return CommentViewHolder(view)
        }

        override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
            val comment = comments[position]
            holder.username.text = comment.username
            holder.content.text = comment.content
            holder.createdAt.text = formatTime(comment.createdAt)

            Glide.with(this@PostDetailFragment)
                .load(requireContext().getString(R.string.root_url) +"/api"+ comment.profileImage)
                .into(holder.Imageprofile)

            Log.d("CommentAdapter", "id: $id")
            holder.Imageprofile.setOnClickListener { openUserProfile(comment.user_id) }

            val sharedPreferences = requireContext().getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
            val userId = sharedPreferences.getString("USER_ID", null)?.toIntOrNull()

            if (userId == comment.user_id) {
                holder.itemView.findViewById<ImageView>(R.id.comment_report).setOnClickListener {
                    val isCommentOwner = userId == comment.user_id
                    if (isCommentOwner) showDeleteMenu(requireContext(), it, comment.id, postId)
                }
            } else {
                holder.itemView.findViewById<ImageView>(R.id.comment_report).visibility = View.GONE
            }
        }

        override fun getItemCount(): Int = comments.size

        inner class CommentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val username: TextView = view.findViewById(R.id.comment_username)
            val content: TextView = view.findViewById(R.id.comment_content)
            val Imageprofile: ImageView = view.findViewById(R.id.comment_profile_image)
            val createdAt: TextView = view.findViewById(R.id.comment_created_at)
        }
    }

    private fun formatTime(timeString: String): String {
        val outputFormat = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("Asia/Bangkok")
        }
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (p in patterns) {
            try {
                val inf = SimpleDateFormat(p, Locale.getDefault()).apply {
                    timeZone = if (p.contains("'T'")) TimeZone.getTimeZone("UTC") else TimeZone.getDefault()
                }
                val d = inf.parse(timeString)
                if (d != null) return outputFormat.format(d)
            } catch (_: Exception) {}
        }
        return timeString
    }

    private fun recordInteraction(postId: Int? = null, actionType: String, content: String? = null, token: String, context: Context) {
        val client = OkHttpClient()
        val url = "${context.getString(R.string.root_url)}${context.getString(R.string.interactions)}"

        val requestBodyBuilder = FormBody.Builder().add("action_type", actionType)
        postId?.let { requestBodyBuilder.add("post_id", it.toString()) }
        content?.let { requestBodyBuilder.add("content", it) }

        val request = Request.Builder()
            .url(url)
            .post(requestBodyBuilder.build())
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { (context as? Activity)?.runOnUiThread { } }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    (context as? Activity)?.runOnUiThread { }
                }
            }
        })
    }

    // === NEW: แชร์โพสต์ออกไป + ใช้ข้อมูลชื่อ/ไตเติลถ้ามี ===
    private fun sharePost(postId: Int) {
        val rootUrl = requireContext().getString(R.string.root_url)
        val postUrl = "$rootUrl/posts/$postId"
        val displayUser = postUserName?.takeIf { it.isNotBlank() } ?: "ผู้ใช้รายนี้"
        val displayTitle = postTitle?.takeIf { it.isNotBlank() } ?: ""

        val shareText = buildString {
            append("ลองดูโพสต์นี้จาก $displayUser!\n")
            if (displayTitle.isNotEmpty()) append(displayTitle).append("\n\n")
            append(postUrl)
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, "แชร์โพสต์นี้ผ่าน..."))
    }

    data class Product(val productName: String, val price: String, val url: String)

    inner class ProductAdapter(private val productList: List<Product>) :
        RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

        inner class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val productNameTextView: TextView = itemView.findViewById(R.id.productname)
            val productPriceTextView: TextView = itemView.findViewById(R.id.price)
            val openLinkButton: Button = itemView.findViewById(R.id.open_link_button)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_pricedetail, parent, false)
            return ProductViewHolder(view)
        }

        override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
            val product = productList[position]
            if (product.productName == "Not found" || product.price == "Not found" || product.url == "Not found") {
                holder.itemView.visibility = View.GONE
                holder.itemView.layoutParams = RecyclerView.LayoutParams(0, 0)
            } else {
                holder.itemView.visibility = View.VISIBLE
                holder.itemView.layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                holder.productNameTextView.text = product.productName
                holder.productPriceTextView.text = product.price
                holder.productPriceTextView.visibility = View.VISIBLE
                holder.openLinkButton.setOnClickListener {
                    try {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(product.url))
                        holder.itemView.context.startActivity(browserIntent)
                    } catch (_: ActivityNotFoundException) {}
                }
            }
        }

        override fun getItemCount(): Int = productList.size
    }

    private fun reportPost(postId: Int, userId: Int, reason: String, token: String) {
        val client = OkHttpClient()
        val url = "${requireContext().getString(R.string.root_url)}/api/posts/$postId/report"

        val requestBody = FormBody.Builder()
            .add("user_id", userId.toString())
            .add("reason", reason)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { (requireActivity() as? Activity)?.runOnUiThread { } }
            override fun onResponse(call: Call, response: Response) {
                (requireActivity() as? Activity)?.runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(requireContext(), "Post reported successfully", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}
