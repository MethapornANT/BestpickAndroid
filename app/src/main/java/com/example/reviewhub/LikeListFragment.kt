package com.bestpick.reviewhub

import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.*
import org.json.JSONArray
import org.json.JSONException
import java.io.IOException

class LikeListFragment : Fragment() {
    private val client = OkHttpClient()
    private lateinit var recyclerViewLikes: RecyclerView
    private lateinit var backButton: ImageView
    private lateinit var titleText: TextView
    private lateinit var likeAdapter: LikeAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_like_list, container, false)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        recyclerViewLikes = view.findViewById(R.id.recycler_view_likes)
        backButton = view.findViewById(R.id.back_button_like)
        titleText = view.findViewById(R.id.title_like)

        // ตั้ง Adapter หนึ่งครั้ง ใช้ updateList เมื่อได้ข้อมูล
        likeAdapter = LikeAdapter(mutableListOf())
        recyclerViewLikes.layoutManager = LinearLayoutManager(context)
        recyclerViewLikes.adapter = likeAdapter

        backButton.setOnClickListener {
            // ปลอดภัย: กลับไปหน้าก่อนหน้า
            findNavController().navigateUp()
        }

        // รับ postId จาก arguments (caller ต้องใส่)
        val postId = arguments?.getInt("POST_ID") ?: -1
        if (postId == -1) {
            Toast.makeText(activity, "Post ID not provided", Toast.LENGTH_SHORT).show()
        } else {
            val sharedPreferences = requireActivity().getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
            val token = sharedPreferences.getString("TOKEN", null)
            // อัพ title ถ้ามี (option)
            titleText.text = "Likes"
            fetchLikes(postId, token)
        }

        return view
    }

    /**
     * เรียก API เพื่อดึงรายชื่อผู้กด like
     * API คาดว่าจะคืน JSONArray ของ objects ที่มี fields: userId, username, profileImageUrl (หรือชื่อฟิลด์รูปที่ API คืน)
     * แปลงเป็น List<LikeUser> แล้วเรียก likeAdapter.updateList(...)
     */
    private fun fetchLikes(postId: Int, token: String?) {
        val baseUrl = requireContext().getString(R.string.root_url).trimEnd('/')
        // endpoint ที่มึงทำไว้บนเซิร์ฟเวอร์: /api/posts/{postId}/likes
        val url = "$baseUrl/api/posts/$postId/likes?limit=200&offset=0"

        val requestBuilder = Request.Builder()
            .url(url)
        token?.let { requestBuilder.addHeader("Authorization", "Bearer $it") }
        val request = requestBuilder.build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("LikeListFragment", "Failed fetch likes: ${e.message}")
                activity?.runOnUiThread {
                    Toast.makeText(activity, "Failed to load likes", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        Log.e("LikeListFragment", "Server error: ${response.message}")
                        activity?.runOnUiThread {
                            Toast.makeText(activity, "Server error: ${response.message}", Toast.LENGTH_SHORT).show()
                        }
                        return
                    }

                    val body = response.body?.string()
                    if (body == null) {
                        Log.e("LikeListFragment", "Empty response")
                        activity?.runOnUiThread {
                            Toast.makeText(activity, "Empty response", Toast.LENGTH_SHORT).show()
                        }
                        return
                    }

                    try {
                        // คาดว่า body เป็น JSONArray
                        val jsonArray = JSONArray(body)
                        val likes = mutableListOf<LikeUser>()

                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            // พยายามอ่านชื่อฟิลด์ที่เป็นไปได้หลายแบบ (robust)
                            val userId = when {
                                obj.has("userId") -> obj.optInt("userId", -1)
                                obj.has("id") -> obj.optInt("id", -1)
                                else -> obj.optInt("user_id", -1)
                            }

                            val username = when {
                                obj.has("username") -> obj.optString("username", "Unknown")
                                obj.has("name") -> obj.optString("name", "Unknown")
                                else -> obj.optString("user_name", "Unknown")
                            }

                            val profileImageUrl = when {
                                obj.has("profileImageUrl") -> obj.optString("profileImageUrl", null)
                                obj.has("profile_image") -> obj.optString("profile_image", null)
                                obj.has("picture") -> obj.optString("picture", null)
                                obj.has("avatar") -> obj.optString("avatar", null)
                                else -> null
                            }

                            if (userId != -1) {
                                likes.add(LikeUser(userId, username, profileImageUrl))
                            }
                        }

                        // อัปเดต UI ด้วย list ที่ได้ (เฉพาะรูป+ชื่อ ไม่มีเวลา)
                        activity?.runOnUiThread {
                            likeAdapter.updateList(likes)
                        }
                    } catch (e: JSONException) {
                        Log.e("LikeListFragment", "JSON parse error: ${e.message}")
                        activity?.runOnUiThread {
                            Toast.makeText(activity, "Parse error", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })
    }
}
