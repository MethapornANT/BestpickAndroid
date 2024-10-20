package com.example.reviewhub

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
import java.sql.Types.NULL


class PostDetailFragment : Fragment() {
    private lateinit var dotIndicatorLayout: LinearLayout
    private lateinit var follower: TextView
    private var bottomNav: BottomNavigationView? = null
    private lateinit var recyclerViewComments: RecyclerView
    private lateinit var recyclerViewProducts: RecyclerView
    private lateinit var comments: MutableList<Comment>


    private var followingId: Int = -1
    private var isLiked: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_post_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // กำหนดค่า View ให้กับตัวแปรต่างๆ
        follower = view.findViewById(R.id.follower)
        recyclerViewComments = view.findViewById(R.id.recycler_view_comments)
        recyclerViewProducts = view.findViewById(R.id.recycler_view_products)
        val postId = arguments?.getInt("POST_ID", -1) ?: -1

        // กำหนดค่า LayoutManager และ Adapter ให้กับ RecyclerView
        recyclerViewComments.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewComments.adapter = CommentAdapter(emptyList(), postId)


        recyclerViewProducts.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewProducts.adapter = ProductAdapter(emptyList())


        dotIndicatorLayout = view.findViewById(R.id.dot_indicator_layout)
        bottomNav = (activity as? MainActivity)?.findViewById(R.id.bottom_navigation)
        // ตั้งค่า Visibility ของ Bottom Navigation Bar เป็น GONE เมื่ออยู่ใน Fragment นี้
        bottomNav?.visibility = View.GONE

        // กำหนดการทำงานของปุ่ม Back
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            bottomNav?.visibility = View.VISIBLE
            parentFragmentManager.popBackStack()
        }

        // ดึงข้อมูลจาก arguments

        val sharedPreferences = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val token = sharedPreferences.getString("TOKEN", null)
        val userId = sharedPreferences.getString("USER_ID", null)?.toIntOrNull()


        val bookmarkButton = view.findViewById<ImageView>(R.id.bookmark_button)
        var isBookmark = false
        bookmarkButton.setOnClickListener{
            isBookmark = !isBookmark
            if (isBookmark) {
                bookmarkButton.setImageResource(R.drawable.bookmarkclick)
            } else {
                bookmarkButton.setImageResource(R.drawable.bookmark)
            }
        }
        // ตั้งค่า Listener ให้กับปุ่มไลค์
        val likeButton = view.findViewById<ImageView>(R.id.like_button)
        likeButton.setOnClickListener {
            if (token != null && userId != null) {
                if (isLiked) {
                    // หากกดไลค์แล้ว ให้ unlike
                    likeUnlikePost(postId, userId, token)
                    deleteNotification(postId, userId, null,"like", token, requireContext())
                    recordInteraction(postId, "unlike", null, token, requireContext())
                } else {
                    // หากยังไม่ไลค์ ให้กดไลค์
                    likeUnlikePost(postId, userId, token)
                    sendNotification(postId, userId, null,"like", token, requireContext())
                    recordInteraction(postId, "like", null, token, requireContext())
                }
            } else {
                Toast.makeText(requireContext(), "Token not available", Toast.LENGTH_SHORT).show()
            }
        }

        // ตั้งค่า Listener ให้กับปุ่ม follow
        follower.setOnClickListener {
            if (token != null && userId != null) {
                followUser(userId.toInt(), followingId, token)
                val actionType = if (follower.text == "Following") "unfollow" else "follow"
                recordInteraction(postId, actionType, null, token, requireContext())
            }
        }
        val back = view.findViewById<ImageView>(R.id.back_button)
        back.setOnClickListener {
            bottomNav?.visibility = View.VISIBLE
            parentFragmentManager.popBackStack()
        }
        val report = view.findViewById<ImageView>(R.id.report)
        report.setOnClickListener {
            // ตรวจสอบว่าเป็นเจ้าของโพสต์หรือไม่
            val isUserPost = userId == followingId
            showReportMenu(requireContext(), it, postId, isUserPost)
        }
        val Imgview = view.findViewById<ImageView>(R.id.Imgview)
        Imgview.setOnClickListener {
            openUserProfile(followingId)
        }

        val commentButton = view.findViewById<ImageView>(R.id.send_button)
        val commentEditText = view.findViewById<EditText>(R.id.comment_input)

        // กำหนดการทำงานเมื่อคลิกปุ่มส่งคอมเมนต์
        commentButton.setOnClickListener {
            if (token != null && userId != null) {
                val commentContent = commentEditText.text.toString().trim()
                if (commentContent.isNotEmpty()) {
                    // โพสต์คอมเมนต์และรอรับ commentId จาก response
                    postComment(postId, userId.toInt(), commentContent, token) { commentId ->
                        // ส่ง notification พร้อมกับ commentId ที่ได้รับ
                        sendNotification(postId, userId.toInt(), commentId, "comment", token, requireContext())
                        commentEditText.text.clear() // ล้างข้อมูลหลังส่งคอมเมนต์สำเร็จ
                        fetchPostDetails(postId, token, userId.toInt(), view)
                    }
                } else {
                    Toast.makeText(requireContext(), "Comment cannot be empty", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Please login to comment", Toast.LENGTH_SHORT).show()
            }
        }



        if (postId != -1 && token != null && userId != null) {
            // เรียกข้อมูล Post
            CoroutineScope(Dispatchers.Main).launch {
                fetchPostDetails(postId, token, userId.toInt(), view)
                checkLikeStatus(postId, userId, token, view)
            }
        } else {
            Toast.makeText(requireContext(), "Invalid Post ID", Toast.LENGTH_SHORT).show()
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

    private fun fetchProductData(productName: String, callback: (List<Product>) -> Unit) {
        val client = OkHttpClient()
        val rooturl = getString(R.string.root_url).substring(0, getString(R.string.root_url).length - 5) + ":5000"
        val url = "$rooturl/search?productname=$productName"

        Log.d("fetchProductData", "Requesting URL: $url")

        // Show the ProgressBar
        activity?.runOnUiThread {
            view?.findViewById<ProgressBar>(R.id.progressBar)?.visibility = View.VISIBLE
        }

        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    Log.e("fetchProductData", "Failed to fetch data: ${e.message}")
                    Toast.makeText(
                        requireContext(),
                        "Failed to fetch data: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    // Hide the ProgressBar
                    view?.findViewById<ProgressBar>(R.id.progressBar)?.visibility = View.GONE
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseBody = it.body?.string()
                    if (responseBody != null) {
                        val jsonObject = JSONObject(responseBody)

                        // Extract prices and URLs from all shops
                        val products = mutableListOf<Product>()

                        // Extracting data from each shop
                        jsonObject.keys().forEach { key ->
                            val shopDetails = jsonObject.getJSONObject(key)

                            // Advice
                            val adviceArray = shopDetails.optJSONArray("Advice")
                            if (adviceArray != null && adviceArray.length() > 0) {
                                val adviceProduct = adviceArray.getJSONObject(0)
                                products.add(Product(adviceProduct.getString("name"), adviceProduct.getString("price"), adviceProduct.getString("url")))
                            }

                            // Banana
                            val bananaArray = shopDetails.optJSONArray("Banana")
                            if (bananaArray != null && bananaArray.length() > 0) {
                                val bananaProduct = bananaArray.getJSONObject(0)
                                products.add(Product(bananaProduct.getString("name"), bananaProduct.getString("price"), bananaProduct.getString("url")))
                            }

                            // JIB
                            val jibArray = shopDetails.optJSONArray("JIB")
                            if (jibArray != null && jibArray.length() > 0) {
                                val jibProduct = jibArray.getJSONObject(0)
                                products.add(Product(jibProduct.getString("name"), jibProduct.getString("price"), jibProduct.getString("url")))
                            }
                        }

                        activity?.runOnUiThread {
                            if (isAdded && view != null) {
                                if (products.isEmpty()) {
                                    Log.d("fetchProductData", "No products found")
                                    Toast.makeText(requireContext(), "No products found", Toast.LENGTH_SHORT).show()
                                } else {
                                    Log.d("fetchProductData", "Products fetched: $products")
                                    callback(products) // Send the list of products back
                                }
                            }
                        }
                    } else {
                        Log.e("fetchProductData", "Response body is null")
                    }

                    // Hide the ProgressBar after response handling
                    activity?.runOnUiThread {
                        view?.findViewById<ProgressBar>(R.id.progressBar)?.visibility = View.GONE
                    }
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

    // ฟังก์ชันสำหรับเปิดหน้าโปรไฟล์ผู้ใช้คนนั้น
    private fun openUserProfile(userId: Int) {
        val sharedPreferences = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val currentUserId = sharedPreferences.getString("USER_ID", null)?.toIntOrNull()
        val token = sharedPreferences.getString("TOKEN", null)
        if (userId == currentUserId) {
            // ลิงก์ไปที่หน้าโปรไฟล์ของตัวเอง
            val profileFragment = ProfileFragment().apply {
                arguments = Bundle().apply {
                    putBoolean("isSelfProfile", true) // หรือ false หากไม่ใช่โปรไฟล์ตัวเอง
                }
            }
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, profileFragment)
                .addToBackStack(null)
                .commit()

        } else {
            // สร้าง AnotherUserFragment และส่ง USER_ID
            val anotherUserFragment = AnotherUserFragment()
            val bundle = Bundle()
            bundle.putInt("USER_ID", userId)  // ใช้ userId ที่ถูกส่งเข้ามา
            anotherUserFragment.arguments = bundle

            // บันทึกการทำงานของผู้ใช้ ถ้า token ไม่เป็น null
            token?.let {
                recordInteraction(null, "view_profile", null, it, requireContext())
            }

            // แทนที่ Fragment ปัจจุบันด้วย AnotherUserFragment
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, anotherUserFragment)
                .addToBackStack(null)
                .commit()
        }
    }

    private fun animateDot(dot: ImageView, isSelected: Boolean) {
        val scale = if (isSelected) 1.4f else 1.0f
        ObjectAnimator.ofFloat(dot, "scaleX", scale).apply {
            duration = 300
            start()
        }
        ObjectAnimator.ofFloat(dot, "scaleY", scale).apply {
            duration = 300
            start()
        }
    }


    private fun showReportMenu(context: Context, anchorView: View, postId: Int, isUserPost: Boolean) {
        val popupMenu = PopupMenu(context, anchorView)
        popupMenu.menuInflater.inflate(R.menu.menu_report, popupMenu.menu)

        // Show edit and delete options only for user's own posts
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

                        // Create an AlertDialog to show the options
                        val builder = AlertDialog.Builder(context, R.style.CustomAlertDialog)
                        builder.setTitle("Report Post")
                        builder.setSingleChoiceItems(reportOptions, -1) { dialog, which ->
                            val selectedReason = reportOptions[which]
                            reportPost(postId, userId, selectedReason, token) // Call reportPost with the selected reason
                            dialog.dismiss() // Close the dialog after selection
                        }
                        builder.setNegativeButton("Cancel") { dialog, _ ->
                            dialog.dismiss()
                        }
                        builder.show() // Display the dialog
                    } else {
                        Toast.makeText(context, "User not authenticated", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.edit_post -> {
                    Toast.makeText(context, "Edit Post selected", Toast.LENGTH_SHORT).show()
                    val sharedPreferences = context.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
                    val token = sharedPreferences.getString("TOKEN", null)

                    token?.let {
                        // Create the PostDetailFragment and pass the post ID
                        val EditpostFragment = EditpostFragment()
                        val bundle = Bundle().apply {
                            putInt("POST_ID", postId)
                            putString("From", "post_detail")
                        }
                        EditpostFragment.arguments = bundle
                        // Navigate to the PostDetailFragment
                        (context as? FragmentActivity)?.supportFragmentManager?.beginTransaction()
                            ?.replace(R.id.nav_host_fragment, EditpostFragment)
                            ?.addToBackStack(null)
                            ?.commit()
                    } ?: run {
                        // Handle the case when token is null
                        Toast.makeText(context, "User not authenticated", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.delete_post -> {
                    // Show confirmation dialog before deleting the post
                    val confirmDeleteBuilder = AlertDialog.Builder(context)
                    confirmDeleteBuilder.setTitle("Confirm Deletion")
                    confirmDeleteBuilder.setMessage("Are you sure you want to delete this post?")

                    confirmDeleteBuilder.setPositiveButton("Yes") { dialog, _ ->
                        deletePost(postId, context)
                        dialog.dismiss()
                    }

                    confirmDeleteBuilder.setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss() // Close the dialog if user cancels
                    }

                    confirmDeleteBuilder.show() // Display the confirmation dialog
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

        // ตั้งค่าให้ทำงานเมื่อลบคอมเมนต์
        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.delete_comment -> {
                    deleteComment(commentId, postId, context) // เรียกฟังก์ชันลบคอมเมนต์
                    true
                }
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
            val url = "${context.getString(R.string.root_url)}/posts/$postId/comment/$commentId"
            val request = Request.Builder()
                .url(url)
                .delete()
                .addHeader("Authorization", "Bearer $token")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    (context as? Activity)?.runOnUiThread {
                        Toast.makeText(context, "Failed to delete comment: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    (context as? Activity)?.runOnUiThread {
                        if (!response.isSuccessful) {
                            Toast.makeText(context, "Failed to delete comment", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Comment deleted successfully", Toast.LENGTH_SHORT).show()
                            // อัปเดตรายการคอมเมนต์หลังจากลบสำเร็จ
                            if (userId != null) {
                                fetchPostDetails(postId, token, userId, requireView())
                            }
                        }
                    }
                }
            })
        } else {
            Toast.makeText(context, "User not authenticated", Toast.LENGTH_SHORT).show()
        }
    }




    private fun deletePost(postId: Int, context: Context) {
        val client = OkHttpClient()
        val sharedPreferences = context.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences.getString("TOKEN", null)
        val userId = sharedPreferences.getString("USER_ID", null)

        if (token != null && userId != null) {
            val url = "${context.getString(R.string.root_url)}/posts/$postId"
            val requestBody = FormBody.Builder()
                .add("user_id", userId)
                .build()

            val request = Request.Builder()
                .url(url)
                .delete(requestBody)
                .addHeader("Authorization", "Bearer $token")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    (context as? Activity)?.runOnUiThread {
                        Toast.makeText(context, "Failed to delete post: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val jsonResponse = response.body?.string()
                    (context as? Activity)?.runOnUiThread {
                        if (!response.isSuccessful) {
                            val errorMessage = JSONObject(jsonResponse ?: "{}").optString("error", "Failed to delete post")
                            Toast.makeText(context, "Error: $errorMessage", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Post deleted successfully", Toast.LENGTH_SHORT).show()
                            // Instead of adding a callback, simply pop the back stack
                            bottomNav?.visibility = View.VISIBLE
                            parentFragmentManager.popBackStack()
                        }
                    }
                }
            })
        } else {
            Toast.makeText(context, "User not authenticated", Toast.LENGTH_SHORT).show()
        }
    }


    // ฟังก์ชันสำหรับเรียก API ติดตาม/เลิกติดตาม
    private fun followUser(userId: Int, followingId: Int, token: String) {
        val client = OkHttpClient()
        val url = "${getString(R.string.root_url)}/api/users/$userId/follow/$followingId"

        val request = Request.Builder()
            .url(url)
            .post(RequestBody.create(null, ByteArray(0))) // ส่ง Body ว่างสำหรับการ POST
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                (requireActivity() as? Activity)?.runOnUiThread {
                    Toast.makeText(requireContext(), "Failed to follow/unfollow user", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        (requireActivity() as? Activity)?.runOnUiThread {
                            Toast.makeText(requireContext(), "Follow status changed successfully", Toast.LENGTH_SHORT).show()
                            checkFollowStatus(userId, followingId, token)
                        }
                    } else {
                        (requireActivity() as? Activity)?.runOnUiThread {
                            Toast.makeText(requireContext(), "Failed: ${response.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })
    }

    // ฟังก์ชันสำหรับเช็คสถานะการติดตามผู้ใช้
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
                            // อัปเดตข้อความของ `follower` ตามสถานะการติดตาม
                            follower.text = if (isFollowing) "Following" else "Follow"
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error checking follow status", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")

    private fun fetchPostDetails(postId: Int, token: String, userId: Int, view: View) {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val url = getString(R.string.root_url) + getString(R.string.postdetail) + postId

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
                        val time = jsonObject.getString("updated_at")
                        val profileImage = jsonObject.getString("picture")
                        val profileUrl = getString(R.string.root_url) + profileImage
                        val productname = jsonObject.getString("ProductName")

                        Log.d("PostDetailFragment", "Product Name: $productname")

                        fetchProductData(productname) { products ->
                            // Update UI with the list of products from all shops
                            updateProductDetailsUI(products)
                        }

                        // Initialize comments list
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
                                mediaUrls.add(Pair(getString(R.string.root_url) + imageUrl, "photo"))
                            }
                        }

                        for (i in 0 until postVideoUrls.length()) {
                            val innerVideoArray = postVideoUrls.getJSONArray(i)
                            for (j in 0 until innerVideoArray.length()) {
                                val videoUrl = innerVideoArray.getString(j)
                                mediaUrls.add(Pair(getString(R.string.root_url) + videoUrl, "video"))
                            }
                        }

                        withContext(Dispatchers.Main) {
                            if (view.isAttachedToWindow) {
                                // Update comments adapter
                                if (comments.isEmpty()) {
                                    Toast.makeText(requireContext(), "No comments found", Toast.LENGTH_SHORT).show()
                                } else {
                                    recyclerViewComments.adapter = CommentAdapter(comments, postId)
                                    recyclerViewComments.adapter?.notifyDataSetChanged()
                                }

                                // Update other post details
                                view.findViewById<TextView>(R.id.username).text = username
                                view.findViewById<TextView>(R.id.title).text = title
                                view.findViewById<TextView>(R.id.detail).text = postContent
                                view.findViewById<TextView>(R.id.time).text = formatTime(time)
                                view.findViewById<TextView>(R.id.like_count).text = ": $likeCount"
                                view.findViewById<TextView>(R.id.comment_count).text = "$commentCount Comments"

                                checkFollowStatus(userId, followingId, token)

                                if (userId == followingId) {
                                    follower.visibility = View.GONE
                                } else {
                                    checkFollowStatus(userId, followingId, token)
                                }

                                // Load profile image
                                Glide.with(this@PostDetailFragment)
                                    .load(profileUrl)
                                    .into(view.findViewById(R.id.Imgview))

                                // Load media (images/videos)
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
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Failed to load post details", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("PostDetailFragment", "Error: ${e.message}", e)
                }
            }
        }
    }


    private fun likeUnlikePost(postId: Int, userId: Int?, token: String) {
        val client = OkHttpClient()
        val url = requireContext().getString(R.string.root_url) + requireContext().getString(R.string.postlikeorunlike) + postId
        val requestBody = FormBody.Builder()
            .add("user_id", userId.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                (requireActivity() as? Activity)?.runOnUiThread {
                    Toast.makeText(requireContext(), "Failed to like/unlike post: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        (requireActivity() as? Activity)?.runOnUiThread {
                            Toast.makeText(requireContext(), "Error: ${response.message}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // ดึงข้อมูลไลค์ใหม่จาก JSON response
                        val responseBody = response.body?.string()
                        val jsonObject = responseBody?.let { JSONObject(it) }
                        val newLikeCount = jsonObject?.getInt("likeCount") ?: 0

                        (requireActivity() as? Activity)?.runOnUiThread {
                            // อัปเดตสถานะการไลค์ใน UI
                            checkLikeStatus(postId, userId ?: 0, token, requireView())

                            // อัปเดตจำนวนไลค์ใน TextView
                            val likeCountTextView = requireView().findViewById<TextView>(R.id.like_count)
                            likeCountTextView.text = ": $newLikeCount"
                        }
                    }
                }
            }
        })
    }

    // ฟังก์ชันสำหรับการส่งคอมเมนต์ไปยัง API
    private fun postComment(postId: Int, userId: Int, content: String, token: String, callback: (Int?) -> Unit) {
        val client = OkHttpClient()
        val url = getString(R.string.root_url) + "/posts/$postId/comment"

        val requestBody = FormBody.Builder()
            .add("content", content)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                (requireActivity() as? Activity)?.runOnUiThread {
                    Toast.makeText(requireContext(), "Failed to post comment: ${e.message}", Toast.LENGTH_SHORT).show()
                    callback(null) // ส่ง null ในกรณีที่ล้มเหลว
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        val jsonObject = responseBody?.let { JSONObject(it) }
                        val commentId = jsonObject?.getInt("comment_id") // ดึง commentId จาก response
                        (requireActivity() as? Activity)?.runOnUiThread {
                            Toast.makeText(requireContext(), "Comment posted successfully", Toast.LENGTH_SHORT).show()
                            callback(commentId) // ส่ง commentId กลับ
                        }
                    } else {
                        (requireActivity() as? Activity)?.runOnUiThread {
                            Toast.makeText(requireContext(), "Failed: ${response.message}", Toast.LENGTH_SHORT).show()
                            callback(null) // ส่ง null หากไม่สำเร็จ
                        }
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

        // หากมี comment_id ให้เพิ่มลงไปใน request body
        commentId?.let {
            requestBodyBuilder.add("comment_id", it.toString())
        }

        val requestBody = requestBodyBuilder.build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                (context as? Activity)?.runOnUiThread {
                    Toast.makeText(context, "Failed to send notification: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                (context as? Activity)?.runOnUiThread {
                    if (!response.isSuccessful) {
                        Toast.makeText(context, "Error: ${response.message}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Notification sent successfully", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun deleteNotification(postId: Int, userId: Int, commentId: Int?, actionType: String, token: String, context: Context) {
        val client = OkHttpClient()
        val url = "${context.getString(R.string.root_url)}/api/notifications"

        val requestBodyBuilder = FormBody.Builder()
            .add("user_id", userId.toString())
            .add("post_id", postId.toString())
            .add("action_type", actionType)

        commentId?.let {
            requestBodyBuilder.add("comment_id", it.toString())
        }

        val requestBody = requestBodyBuilder.build()

        val request = Request.Builder()
            .url(url)
            .delete(requestBody)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                (context as? Activity)?.runOnUiThread {
                    Toast.makeText(context, "Failed to delete notification: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                (context as? Activity)?.runOnUiThread {
                    if (!response.isSuccessful) {
                        Toast.makeText(context, "Error: ${response.message}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Notification deleted successfully", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }



    private fun checkLikeStatus(postId: Int, userId: Int, token: String, view: View) {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val url = "${requireContext().getString(R.string.root_url)}${requireContext().getString(R.string.check_like_status)}$postId/$userId"

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", "Bearer $token")
                .build()

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
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to check like status: ${e.message}", Toast.LENGTH_SHORT).show()
                }
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
                .load(requireContext().getString(R.string.root_url) + comment.profileImage)
                .into(holder.Imageprofile)


            Log.d("CommentAdapter", "id: $id")
            // กำหนดการคลิกที่โปรไฟล์ของผู้แสดงความคิดเห็น
            holder.Imageprofile.setOnClickListener {
                openUserProfile(comment.user_id)
            }
            val sharedPreferences = requireContext().getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
            val userId = sharedPreferences.getString("USER_ID", null)?.toIntOrNull()

            holder.itemView.findViewById<ImageView>(R.id.comment_report).setOnClickListener {
                val isCommentOwner = userId == comment.user_id
                if (isCommentOwner) {
                    // ถ้าเป็นเจ้าของคอมเมนต์ให้แสดงเมนูลบ
                    showDeleteMenu(requireContext(), it, comment.id, postId)
                }
            }
        }

        override fun getItemCount(): Int {
            return comments.size
        }

        inner class CommentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val username: TextView = view.findViewById(R.id.comment_username)
            val content: TextView = view.findViewById(R.id.comment_content)
            val Imageprofile: ImageView = view.findViewById(R.id.comment_profile_image)
            val createdAt: TextView = view.findViewById(R.id.comment_created_at)
        }
    }



    private fun formatTime(timeString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC") // ตั้งค่า inputFormat เป็น UTC
            }
            val outputFormat = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("Asia/Bangkok") // ตั้งค่า outputFormat เป็น Asia/Bangkok
            }
            val date = inputFormat.parse(timeString ?: "")
            date?.let { outputFormat.format(it) } ?: "N/A"

        } catch (e: Exception) {
            timeString
        }
    }

    private fun recordInteraction(postId: Int? = null, actionType: String, content: String? = null, token: String, context: Context) {
        val client = OkHttpClient()
        val url = "${context.getString(R.string.root_url)}${context.getString(R.string.interactions)}"

        // ตรวจสอบค่า postId ถ้าเป็น null ไม่ต้องใส่ลงใน FormBody
        val requestBodyBuilder = FormBody.Builder()
            .add("action_type", actionType)

        postId?.let {
            requestBodyBuilder.add("post_id", it.toString())
        }

        content?.let {
            requestBodyBuilder.add("content", it)
        }

        val requestBody = requestBodyBuilder.build()

        // สร้าง request พร้อมแนบ token ใน header
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                (context as? Activity)?.runOnUiThread {
                    Toast.makeText(context, "Failed to record interaction: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        (context as? Activity)?.runOnUiThread {
                            Toast.makeText(context, "Failed to record interaction: ${response.message}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val jsonResponse = response.body?.string()
                        val message = JSONObject(jsonResponse).getString("message")
                        (context as? Activity)?.runOnUiThread {
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })
    }

    private fun updateProductDetailsUI(products: List<Product>) {
        recyclerViewProducts.adapter = ProductAdapter(products)
        recyclerViewProducts.adapter?.notifyDataSetChanged()
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

            // If any of the product data is invalid (null or "Not found"), hide the entire item
            if (product.productName == null || product.productName == "Not found" ||
                product.price == null || product.price == "Not found" ||
                product.url == null || product.url == "Not found") {

                holder.itemView.visibility = View.GONE // Hide the entire item
                holder.itemView.layoutParams = RecyclerView.LayoutParams(0, 0) // Remove the item's layout space

            } else {
                // Show the item and set the data if it's valid
                holder.itemView.visibility = View.VISIBLE
                holder.itemView.layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )

                holder.productNameTextView.text = product.productName
                holder.productPriceTextView.text = product.price
                holder.productPriceTextView.visibility = View.VISIBLE // Ensure visibility in case it was hidden

                holder.openLinkButton.setOnClickListener {
                    try {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(product.url))
                        holder.itemView.context.startActivity(browserIntent)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(holder.itemView.context, "No application found to open this URL", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }



        override fun getItemCount(): Int {
            return productList.size
        }
    }

    // Updated reportPost function with an additional reason parameter
    private fun reportPost(postId: Int, userId: Int, reason: String, token: String) {
        val client = OkHttpClient()
        val url = "${requireContext().getString(R.string.root_url)}/posts/$postId/report"

        // Prepare request body with user ID and reason for reporting
        val requestBody = FormBody.Builder()
            .add("user_id", userId.toString())
            .add("reason", reason)
            .build()

        // Build the HTTP request
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $token")
            .build()

        // Send the request asynchronously
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                (requireActivity() as? Activity)?.runOnUiThread {
                    Toast.makeText(requireContext(), "Failed to report post: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                (requireActivity() as? Activity)?.runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(requireContext(), "Post reported successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Error reporting post: ${response.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }




}
