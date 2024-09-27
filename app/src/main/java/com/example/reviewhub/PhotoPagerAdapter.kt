package com.example.reviewhub

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.VideoView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class PhotoPagerAdapter(private val mediaUrls: List<Pair<String, String>>) :
    RecyclerView.Adapter<PhotoPagerAdapter.MediaViewHolder>() {

    private val handler = Handler(Looper.getMainLooper())
    private var itemClickListener: ((Int, String) -> Unit)? = null

    // ฟังก์ชันสำหรับตั้งค่า Click Listener
    fun setOnItemClickListener(listener: (Int, String) -> Unit) {
        itemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.photo_slide_item, parent, false)
        return MediaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        val (mediaUrl, mediaType) = mediaUrls[position]

        if (mediaType == "video") {
            // แสดงวิดีโอ
            holder.imageView.visibility = View.GONE
            holder.videoView.visibility = View.VISIBLE
            holder.playIcon.visibility = View.VISIBLE
            holder.videoView.setVideoPath(mediaUrl)

            holder.playIcon.setOnClickListener {
                holder.playIcon.visibility = View.GONE
                holder.videoView.start()
            }

            // เพิ่ม Listener ให้ VideoView
            holder.videoView.setOnClickListener {
                itemClickListener?.invoke(position, "video") // ส่งตำแหน่งและประเภทสื่อ
            }
        } else {
            // แสดงรูปภาพ
            holder.imageView.visibility = View.VISIBLE
            holder.videoView.visibility = View.GONE
            holder.playIcon.visibility = View.GONE

            Glide.with(holder.itemView.context)
                .load(mediaUrl)
                .into(holder.imageView)

            // เพิ่ม Listener ให้ ImageView
            holder.imageView.setOnClickListener {
                itemClickListener?.invoke(position, "photo") // ส่งตำแหน่งและประเภทสื่อ
            }
        }

        // ตั้งค่า Page Indicator ในมุมขวาบน (เช่น 1/10)
        val currentPosition = position + 1
        val totalCount = mediaUrls.size
        holder.pageCount.text = "$currentPosition/$totalCount"
    }

    override fun getItemCount(): Int = mediaUrls.size

    class MediaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.photo_slide_image_view)
        val videoView: VideoView = view.findViewById(R.id.video_view)
        val playIcon: ImageView = view.findViewById(R.id.play_icon)
        val pageCount: TextView = view.findViewById(R.id.page_count) // TextView สำหรับแสดงตำแหน่ง
    }

    // Release resources for VideoView if needed
    override fun onViewRecycled(holder: MediaViewHolder) {
        super.onViewRecycled(holder)
        if (holder.videoView.isPlaying) {
            holder.videoView.stopPlayback()
        }
        handler.removeCallbacksAndMessages(null) // หยุดการอัปเดต seek bar
    }
}
