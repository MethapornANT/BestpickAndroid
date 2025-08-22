package com.bestpick.reviewhub

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import android.os.Handler
import android.os.Looper

class NotificationsAdapter(
    private val notificationList: List<Notification>,
    private val onNotificationClick: (Notification) -> Unit
) : RecyclerView.Adapter<NotificationsAdapter.NotificationViewHolder>() {

    // shared OkHttp client and in-memory cache for post contents
    private val httpClient = OkHttpClient()
    private val postContentCache = mutableMapOf<Int, String>()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_follow_you, parent, false)
        return NotificationViewHolder(v)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(notificationList[position], onNotificationClick)
    }

    override fun getItemCount() = notificationList.size

    inner class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val profileImage: ImageView = itemView.findViewById(R.id.comment_profile_image)
        private val userName: TextView = itemView.findViewById(R.id.comment_username)
        private val timeText: TextView = itemView.findViewById(R.id.comment_created_at)
        private val contentText: TextView = itemView.findViewById(R.id.comment_content)
        private val container: View = itemView.findViewById(R.id.notification_container)

        fun bind(n: Notification, onClick: (Notification) -> Unit) {
            // Top username (ยังโชว์ตามเดิม)
            val sender = n.sender_name?.takeIf { it.isNotBlank() } ?: "Bestpick"
            userName.text = sender
            timeText.text = formatTime(n.created_at)

            // enforce 2 lines and ellipsize end
            contentText.maxLines = 2
            contentText.ellipsize = TextUtils.TruncateAt.END

            // Build the message WITHOUT repeating the sender name
            val messageText = when (n.action_type?.lowercase(Locale.getDefault())) {
                "like" -> "ถูกใจโพสต์ของคุณ"
                "follow" -> "เริ่มติดตามคุณ"
                "comment" -> {
                    val raw = n.comment_content?.takeIf { it.isNotBlank() } ?: n.content?.takeIf { it.isNotBlank() }
                    val snippet = raw?.trim()?.let { sanitizeSnippet(it, sender) }?.let(::trim140)
                    if (!snippet.isNullOrEmpty()) "แสดงความคิดเห็น: $snippet" else "แสดงความคิดเห็น"
                }
                "bookmark" -> "บันทึกโพสต์ของคุณ"
                "ads_status_change" -> (n.content?.let { sanitizeSnippet(it, sender) } ?: "การแจ้งเตือนสถานะโฆษณา")
                "post" -> {
                    // PRIORITY: use post_id if exists; else try to extract ID from content
                    val postId = detectPostId(n)
                    if (postId != null) {
                        val cached = postContentCache[postId]
                        if (!cached.isNullOrEmpty()) {
                            buildPostMessageCached(cached)
                        } else {
                            // placeholder while fetching (no username)
                            fetchPostContentAndApply(postId, contentText, sender)
                            "ได้โพสต์เนื้อหาใหม่"
                        }
                    } else {
                        val rawCandidate = n.content?.trim().orEmpty()
                        val snippet = extractPostSnippetFromContent(rawCandidate)?.let { sanitizeSnippet(it, sender) }
                        if (!snippet.isNullOrBlank()) {
                            "ได้โพสต์เนื้อหาใหม่ : ${trim140(snippet)}"
                        } else {
                            "ได้โพสต์เนื้อหาใหม่"
                        }
                    }
                }
                else -> {
                    val raw = n.content?.trim()
                    if (!raw.isNullOrBlank() && !raw.matches(Regex("^\\d+\$"))) {
                        trim140(sanitizeSnippet(raw, sender))
                    } else {
                        n.content ?: "การแจ้งเตือน"
                    }
                }
            }

            contentText.text = messageText

            val rootUrl = try {
                itemView.context.getString(R.string.root_url) + "/api"
            } catch (_: Exception) {
                ""
            }
            val img: Any = if (!n.sender_picture.isNullOrEmpty()) "$rootUrl${n.sender_picture}" else R.drawable.profiletest2

            Glide.with(itemView)
                .load(img)
                .circleCrop()
                .placeholder(R.drawable.testpic)
                .into(profileImage)

            val bg = if (n.read_status == 1) R.color.gray_light else R.color.white
            container.setBackgroundColor(ContextCompat.getColor(itemView.context, bg))

            itemView.setOnClickListener { onClick(n) }
        }

        // ---------- helpers ----------

        // Try to use explicit post_id, otherwise extract numeric id from text patterns
        private fun detectPostId(n: Notification): Int? {
            try {
                val pid = n.post_id
                if (pid != null && pid != 0) return pid
            } catch (_: Exception) {}
            val c = n.content?.trim().orEmpty()
            val m = Regex("""on\s+post\s*(\d+)\b""", RegexOption.IGNORE_CASE).find(c)
            if (m != null && m.groupValues.size >= 2) return m.groupValues[1].toIntOrNull()
            val m2 = Regex("""post\s*[:]?\s*(\d+)\b""", RegexOption.IGNORE_CASE).find(c)
            if (m2 != null && m2.groupValues.size >= 2) return m2.groupValues[1].toIntOrNull()
            return null
        }

        // Fetch post content, cache it and update the target TextView when available
        private fun fetchPostContentAndApply(postId: Int, targetView: TextView, senderName: String) {
            if (postContentCache.containsKey(postId)) {
                val cached = postContentCache[postId]
                mainHandler.post { targetView.text = buildPostMessageCached(cached ?: "") }
                return
            }

            val root = try {
                itemView.context.getString(R.string.root_url) + "/api"
            } catch (_: Exception) { "" }
            val url = if (root.endsWith("/")) "$root/posts/$postId" else "$root/posts/$postId"

            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    // keep placeholder; optionally log
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        if (!resp.isSuccessful) return
                        val body = resp.body?.string() ?: return
                        try {
                            val json = JSONObject(body)
                            val postContent = json.optString("content", "").trim()
                            val final = if (postContent.isNotEmpty()) postContent else ""
                            postContentCache[postId] = final

                            mainHandler.post {
                                if (final.isNotEmpty()) {
                                    targetView.text = buildPostMessageCached(final)
                                } else {
                                    if (targetView.text.isNullOrBlank()) {
                                        targetView.text = "ได้โพสต์เนื้อหาใหม่"
                                    }
                                }
                            }
                        } catch (ex: Exception) {
                            // ignore parse errors
                        }
                    }
                }
            })
        }

        private fun buildPostMessageCached(postContent: String): String {
            val snippet = trim140(postContent)
            return if (snippet.isBlank()) "ได้โพสต์เนื้อหาใหม่" else "ได้โพสต์เนื้อหาใหม่ : $snippet"
        }

        // Remove redundant leading username or patterns like "User 12345" from a snippet
        private fun sanitizeSnippet(raw: String, senderName: String?): String {
            var out = raw.trim()

            // remove exact sender name at the start (case-insensitive)
            if (!senderName.isNullOrBlank()) {
                val escaped = Regex.escape(senderName.trim())
                out = out.replace(Regex("^\\s*$escaped[:\\s-]*", RegexOption.IGNORE_CASE), "").trim()
            }

            // remove leading "User 12345" or "ผู้ใช้งาน 12345" etc.
            out = out.replace(Regex("""(?i)^\s*User\s+\d+\b[:\s-]*"""), "").trim()
            out = out.replace(Regex("""^\s*ผู้ใช(?:้งาน|้ง)?\s*\d+\s*[:\s-]*"""), "").trim()

            // If after cleaning it's only a number, drop it
            if (out.matches(Regex("^\\d+\$"))) return ""

            return out
        }

        private fun formatTime(createdAt: String?): String {
            if (createdAt.isNullOrBlank()) return "N/A"
            val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss'Z'")
            for (p in patterns) try {
                val inFmt = SimpleDateFormat(p, Locale.getDefault()).apply {
                    if (p.contains("'Z'")) timeZone = TimeZone.getTimeZone("UTC")
                }
                val outFmt = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("Asia/Bangkok")
                }
                inFmt.parse(createdAt)?.let { return outFmt.format(it) }
            } catch (_: Exception) {}
            return createdAt
        }

        private fun trim140(s: String): String {
            val t = s.trim()
            return if (t.length > 140) t.take(140) + "…" else t
        }

        private fun extractPostSnippetFromContent(raw: String): String? {
            if (raw.isBlank()) return null
            val patterns = listOf(
                Regex("""(?i)^\s*User\s+\d+\s+just\s+posted:\s*(.+)"""),
                Regex("""(?i)^\s*User\s+\d+\s+just\s+posted\s+(.+)"""),
                Regex("""(?i)^\s*User\s+\d+\s+performed\s+action:\s*post(?:\s+on\s+post\s*\d+)?\s*:\s*(.+)"""),
                Regex("""(?i)^\s*User\s+\d+\s+performed\s+action:\s*post(?:\s+on\s+post\s*\d+)?\s*(.+)"""),
                Regex(""".*?:\s*(.+)""")
            )
            for (r in patterns) {
                val m = r.find(raw)
                if (m != null && m.groupValues.size >= 2) {
                    val candidate = m.groupValues[1].trim()
                    if (candidate.isNotEmpty() && !candidate.matches(Regex("^\\d+\$"))) return candidate
                }
            }
            var cleaned = raw.replace(Regex("""(?i)^\s*User\s+\d+\b[:\s-]*"""), "").trim()
            cleaned = cleaned.replace(Regex("""^\s*ผู้ใช(?:้งาน|้ง)?\s*\d+\s*[:\s-]*"""), "").trim()
            if (cleaned.isEmpty() || cleaned.matches(Regex("^\\d+\$"))) return null
            return cleaned
        }
    }
}
