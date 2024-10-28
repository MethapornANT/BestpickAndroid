package com.example.reviewhub

import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

class PhotoPagerAdapter(private val mediaUrls: List<Pair<String, String>>) :
    RecyclerView.Adapter<PhotoPagerAdapter.MediaViewHolder>() {

    private val handler = Handler(Looper.getMainLooper())
    private var itemClickListener: ((Int, String) -> Unit)? = null

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

        // รีเซ็ตค่าเริ่มต้นของทุก View ใน MediaViewHolder
        resetView(holder)

        // ตั้งค่าการแสดงเนื้อหาแต่ละประเภท
        when (mediaType) {
            "video" -> setupVideo(holder, mediaUrl)
            else -> setupImage(holder, mediaUrl)
        }

        // อัปเดตตัวนับหน้า
        val currentPosition = position + 1
        val totalCount = mediaUrls.size
        holder.pageCount.text = "$currentPosition/$totalCount"
    }

    private fun resetView(holder: MediaViewHolder) {
        holder.imageView.visibility = View.GONE
        holder.videoView.visibility = View.GONE
        holder.playIcon.visibility = View.GONE
        holder.seekBar.visibility = View.GONE
        holder.timeVideo.visibility = View.GONE

        holder.imageView.setImageDrawable(null) // ล้างข้อมูลภาพใน ImageView
        holder.videoView.stopPlayback()
        holder.seekBar.progress = 0
        handler.removeCallbacksAndMessages(null)
    }

    private fun setupVideo(holder: MediaViewHolder, mediaUrl: String) {
        holder.videoView.visibility = View.VISIBLE
        holder.playIcon.visibility = View.GONE
        holder.seekBar.visibility = View.VISIBLE
        holder.timeVideo.visibility = View.VISIBLE

        val audioManager = holder.itemView.context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
        val userVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volumeRatio = userVolume.toFloat() / maxVolume.toFloat()

        holder.videoView.setVideoPath(mediaUrl)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
            holder.videoView.setAudioAttributes(audioAttributes)
        }

        holder.videoView.setOnPreparedListener { mediaPlayer ->
            mediaPlayer.setVolume(volumeRatio, volumeRatio)
            holder.videoView.start()
            holder.seekBar.max = holder.videoView.duration
            updateSeekBar(holder)
            val totalTime = holder.videoView.duration
            holder.timeVideo.text = formatTime(0) + " / " + formatTime(totalTime)
        }

        holder.videoView.setOnClickListener {
            if (holder.videoView.isPlaying) {
                holder.videoView.pause()
                holder.playIcon.visibility = View.VISIBLE
            } else {
                holder.playIcon.visibility = View.GONE
                holder.videoView.start()
                updateSeekBar(holder)
            }
        }

        holder.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    holder.videoView.seekTo(progress)
                    holder.timeVideo.text = formatTime(progress) + " / " + formatTime(holder.videoView.duration)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        holder.videoView.setOnCompletionListener {
            holder.playIcon.visibility = View.VISIBLE
            holder.seekBar.progress = 0
            holder.timeVideo.text = formatTime(0) + " / " + formatTime(holder.videoView.duration)
            handler.removeCallbacksAndMessages(null)
        }
    }

    private fun setupImage(holder: MediaViewHolder, mediaUrl: String) {
        holder.imageView.visibility = View.VISIBLE

        // ใช้การตั้งค่า Glide ที่ปรับปรุงแล้ว
        Glide.with(holder.itemView.context)
            .load("/api" +mediaUrl)
            .diskCacheStrategy(DiskCacheStrategy.ALL) // ใช้การจัดการ Cache
            .override(800, 800) // กำหนดความละเอียดของภาพ
            .fitCenter() // ปรับให้ภาพแสดงได้เต็มพื้นที่โดยไม่บิดเบี้ยว
            .into(holder.imageView)

        holder.imageView.setOnClickListener {
            itemClickListener?.invoke(holder.adapterPosition, "photo")
        }
    }

    override fun getItemCount(): Int = mediaUrls.size

    class MediaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.photo_slide_image_view)
        val videoView: VideoView = view.findViewById(R.id.video_view)
        val playIcon: ImageView = view.findViewById(R.id.play_icon)
        val seekBar: SeekBar = view.findViewById(R.id.video_seek_bar)
        val pageCount: TextView = view.findViewById(R.id.page_count)
        val timeVideo: TextView = view.findViewById(R.id.timevideo)
    }

    private fun updateSeekBar(holder: MediaViewHolder) {
        holder.seekBar.progress = holder.videoView.currentPosition
        holder.timeVideo.text = formatTime(holder.videoView.currentPosition) + " / " + formatTime(holder.videoView.duration)
        if (holder.videoView.isPlaying) {
            handler.postDelayed({ updateSeekBar(holder) }, 100)
        }
    }

    override fun onViewRecycled(holder: MediaViewHolder) {
        super.onViewRecycled(holder)
        holder.imageView.setImageDrawable(null) // ล้างข้อมูลเก่า
        if (holder.videoView.isPlaying) {
            holder.videoView.stopPlayback()
        }
        handler.removeCallbacksAndMessages(null)
    }

    private fun formatTime(milliseconds: Int): String {
        val minutes = milliseconds / 1000 / 60
        val seconds = milliseconds / 1000 % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}
