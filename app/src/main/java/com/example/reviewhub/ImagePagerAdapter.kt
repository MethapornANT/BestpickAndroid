package com.bestpick.reviewhub

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatDelegate
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class ImagePagerAdapter(private val mediaUris: List<Uri>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val VIEW_TYPE_IMAGE = 0
    private val VIEW_TYPE_VIDEO = 1

    class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.image_item)
    }

    class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val playerView: PlayerView = view.findViewById(R.id.player_view)
        var player: ExoPlayer? = null
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

        if (holder is ImageViewHolder) {
            Glide.with(holder.imageView.context)
                .load(uri)
                .fitCenter()
                .into(holder.imageView)
        } else if (holder is VideoViewHolder) {
            // สร้าง ExoPlayer instance ใหม่
            val player = ExoPlayer.Builder(holder.playerView.context).build()
            holder.playerView.player = player
            holder.player = player // เก็บ reference ไว้เพื่อ release ทีหลัง

            // สร้าง MediaItem จาก URL
            val mediaItem = MediaItem.fromUri(uri)

            // ตั้งค่า MediaItem ให้กับ Player และเริ่มเตรียมเล่น
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play() // เริ่มเล่นวิดีโออัตโนมัติ
        }
    }

    override fun getItemCount(): Int {
        return mediaUris.size
    }

    override fun getItemViewType(position: Int): Int {
        val uriString = mediaUris[position].toString().lowercase()
        // ตรวจสอบนามสกุลไฟล์วิดีโอที่พบบ่อย
        return if (uriString.endsWith(".mp4") || uriString.endsWith(".mkv") || uriString.endsWith(".webm") || uriString.endsWith(".3gp")) {
            VIEW_TYPE_VIDEO
        } else {
            VIEW_TYPE_IMAGE
        }
    }

    // --- เพิ่มส่วนนี้เข้ามา (สำคัญมาก) ---
    // คืนทรัพยากร Player เมื่อ View ถูกนำกลับมาใช้ใหม่ (Recycled)
    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is VideoViewHolder) {
            holder.player?.release()
            holder.player = null
            holder.playerView.player = null
        }
    }
}