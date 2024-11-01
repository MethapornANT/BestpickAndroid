package com.bestpick.reviewhub

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.MediaController
import android.widget.VideoView
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

// Adapter สำหรับแสดงรูปภาพและเล่นวิดีโอใน ViewPager2
class ImagePagerAdapter(private val mediaUris: List<Uri>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // ประเภทของ ViewHolder
    private val VIEW_TYPE_IMAGE = 0
    private val VIEW_TYPE_VIDEO = 1

    // ViewHolder สำหรับรูปภาพ
    class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.image_item)
    }

    // ViewHolder สำหรับวิดีโอ
    class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val videoView: VideoView = view.findViewById(R.id.video_item)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_IMAGE) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.image_item_layout, parent, false)
            ImageViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.video_item_layout, parent, false)
            VideoViewHolder(view)

        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val uri = mediaUris[position]
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // ตรวจสอบประเภทของ ViewHolder
        if (holder is ImageViewHolder) {
            // ใช้ Glide ในการโหลดรูปภาพ
            Glide.with(holder.imageView.context)
                .load(uri)
                .fitCenter()
                .into(holder.imageView)
        } else if (holder is VideoViewHolder) {
            // ตั้งค่าวิดีโอใน VideoView
            holder.videoView.setVideoURI(uri)
            holder.videoView.setMediaController(MediaController(holder.videoView.context)) // เพิ่ม MediaController
            holder.videoView.requestFocus()
            holder.videoView.start() // เริ่มเล่นวิดีโออัตโนมัติ
        }
    }

    override fun getItemCount(): Int {
        return mediaUris.size
    }

    // ตรวจสอบประเภทของ View
    override fun getItemViewType(position: Int): Int {
        val uri = mediaUris[position]
        return if (uri.toString().endsWith(".mp4") || uri.toString().endsWith(".mkv")) VIEW_TYPE_VIDEO else VIEW_TYPE_IMAGE
    }
}
