package com.example.reviewhub

import android.animation.Animator
import android.animation.AnimatorInflater
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.animation.addListener
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import java.io.IOException

class HomeFragment : Fragment() {

    private lateinit var postAdapter: PostAdapter
    private val postList = mutableListOf<Post>()
    private val client = OkHttpClient()
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view_posts)
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout)
        progressBar = view.findViewById(R.id.progress_bar)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        val sharedPreferences = requireActivity().getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val picture = sharedPreferences.getString("PICTURE", null)
        val profileImg = view.findViewById<ImageView>(R.id.profile_image)
        val searchEditText = view.findViewById<EditText>(R.id.searchEditText)

        searchEditText.setOnClickListener {
            // สร้าง ValueAnimator สำหรับขยายขนาด
            val animator = ValueAnimator.ofInt(searchEditText.width, 750) // ขยายจากความกว้างปัจจุบันไป 800px
            animator.duration = 400 // ระยะเวลาของแอนิเมชัน (300ms)

            // กำหนดการเปลี่ยนแปลงของ layoutParams ขณะขยาย
            animator.addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                val layoutParams = searchEditText.layoutParams
                layoutParams.width = value
                searchEditText.layoutParams = layoutParams
            }

            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    val navController = findNavController()
                    navController.navigate(R.id.searchFragment)

                    // รอให้ Navigation เสร็จสิ้นแล้วค่อยกำหนดสถานะของเมนูใน BottomNavigationView
                    requireActivity().runOnUiThread {
                        val bottomNavigationView = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation)
                        bottomNavigationView.menu.findItem(R.id.search).isChecked = true
                    }
                }
            })



            // เริ่มการแอนิเมชัน
            animator.start()
        }
        searchEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val shrinkAnimator = ValueAnimator.ofInt(
                    searchEditText.width,
                    300
                ) // ขยายจากความกว้าง 800px กลับไป 300px
                shrinkAnimator.duration = 300

                shrinkAnimator.addUpdateListener { animation ->
                    val value = animation.animatedValue as Int
                    val layoutParams = searchEditText.layoutParams
                    layoutParams.width = value
                    searchEditText.layoutParams = layoutParams
                }

                shrinkAnimator.start()
            }
        }

        val menuImageView = view.findViewById<ImageView>(R.id.menuImageView)
        menuImageView.setOnClickListener {
            val popupMenu = PopupMenu(requireContext(), menuImageView)
            popupMenu.menuInflater.inflate(R.menu.navbar_home, popupMenu.menu)

            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.setting -> true
                    R.id.Theme -> true
                    R.id.logout -> {
                        performLogout()
                        true
                    }
                    else -> false
                }
            }
            popupMenu.show()
        }

        if (picture != null) {
            val url = getString(R.string.root_url) + picture
            context?.let {
                Glide.with(it)
                    .load(url)
                    .circleCrop()
                    .placeholder(R.drawable.ic_launcher_background)
                    .error(R.drawable.ic_error)
                    .into(profileImg)
            }
        }

        // Initialize the adapter with an empty list
        postAdapter = PostAdapter(postList)
        recyclerView.adapter = postAdapter

        // Fetch data from the API
        fetchPosts(showLoading = true)

        // Pull-to-refresh functionality
        swipeRefreshLayout.setOnRefreshListener {
            fetchPosts(showLoading = false) // Show swipe refresh only, not progress bar
        }
        return view
    }

    // ฟังก์ชัน refreshPosts ที่จะถูกเรียกเมื่อคลิก Home สองครั้ง
    fun refreshPosts() {
        Toast.makeText(requireContext(), "Refreshing posts...", Toast.LENGTH_SHORT).show()

        val recyclerView = view?.findViewById<RecyclerView>(R.id.recycler_view_posts)
        recyclerView?.smoothScrollToPosition(0)

        fetchPosts(showLoading = true) // ดึงข้อมูลใหม่

    }

    private fun fetchPosts(showLoading: Boolean) {
        if (showLoading) {
            progressBar.visibility = View.VISIBLE // Show progress bar only for the first load
        }
        swipeRefreshLayout.isRefreshing = false // Ensure swipe refresh icon is reset

        val sharedPreferences = context?.getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val token = sharedPreferences?.getString("TOKEN", null)

        if (token == null) {
            Toast.makeText(requireContext(), "Token not found. Please login again.", Toast.LENGTH_SHORT).show()
            return
        }

        val url = getString(R.string.root_url) + getString(R.string.Allpost)

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token") // ส่ง token ใน header
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    progressBar.visibility = View.GONE // Hide progress bar
                    swipeRefreshLayout.isRefreshing = false // Ensure refreshing is stopped
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    activity?.runOnUiThread {
                        progressBar.visibility = View.GONE // Hide progress bar
                        swipeRefreshLayout.isRefreshing = false // Ensure refreshing is stopped
                        Toast.makeText(requireContext(), "Failed to fetch posts: ${response.message}", Toast.LENGTH_SHORT).show()
                    }
                    return
                }

                response.body?.string()?.let { jsonResponse ->
                    try {
                        val gson = Gson()
                        val postType = object : TypeToken<List<Post>>() {}.type
                        val posts: List<Post> = gson.fromJson(jsonResponse, postType)

                        activity?.runOnUiThread {
                            postList.clear()
                            postList.addAll(posts)
                            postAdapter.notifyDataSetChanged()
                            progressBar.visibility = View.GONE
                            swipeRefreshLayout.isRefreshing = false
                        }
                    } catch (e: Exception) {
                        activity?.runOnUiThread {
                            progressBar.visibility = View.GONE
                            swipeRefreshLayout.isRefreshing = false
                            Toast.makeText(requireContext(), "Error parsing data: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } ?: run {
                    activity?.runOnUiThread {
                        progressBar.visibility = View.GONE
                        swipeRefreshLayout.isRefreshing = false
                        Toast.makeText(requireContext(), "Response body is null", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }


    private fun performLogout() {
        // Sign out from Firebase Authentication
        val firebaseAuth = FirebaseAuth.getInstance()
        firebaseAuth.signOut()
        // Clear shared preferences or any other local data
        clearLocalData()
        // Redirect to the login screen
        val intent = Intent(requireContext(), LoginActivity::class.java)
        startActivity(intent)
        // Close the current activity
        requireActivity().finish()
    }

    private fun clearLocalData() {
        // Clear shared preferences
        val sharedPreferences: SharedPreferences =
            requireContext().getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        sharedPreferences.edit().clear().apply()

        // Clear the token if stored separately
        val tokenPrefs: SharedPreferences = requireContext().getSharedPreferences("TokenPrefs", MODE_PRIVATE)
        tokenPrefs.edit().remove("TOKEN").apply()
        // Clear the user ID if stored separately
        val userIdPrefs: SharedPreferences = requireContext().getSharedPreferences("UserIdPrefs", MODE_PRIVATE)
        userIdPrefs.edit().remove("USER_ID").apply()
        // Clear the picture if stored separately
        val picturePrefs: SharedPreferences = requireContext().getSharedPreferences("PicturePrefs", MODE_PRIVATE)
        picturePrefs.edit().remove("PICTURE").apply()
    }

}
