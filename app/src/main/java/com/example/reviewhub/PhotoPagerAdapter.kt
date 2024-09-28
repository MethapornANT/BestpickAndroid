package com.example.reviewhub

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

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

        if (mediaType == "video") {
            setupVideo(holder, mediaUrl)
        } else {
            setupImage(holder, mediaUrl)
        }

        val currentPosition = position + 1
        val totalCount = mediaUrls.size
        holder.pageCount.text = "$currentPosition/$totalCount"
    }

    private fun resetView(holder: MediaViewHolder) {
        holder.imageView.visibility = View.GONE
        holder.videoView.visibility = View.GONE
        holder.playIcon.visibility = View.GONE
        holder.seekBar.visibility = View.GONE
        holder.stopButton.visibility = View.GONE
        holder.skipButton.visibility = View.GONE

        holder.videoView.stopPlayback()
        handler.removeCallbacksAndMessages(null)
    }

    private fun setupVideo(holder: MediaViewHolder, mediaUrl: String) {
        holder.videoView.visibility = View.VISIBLE
        holder.playIcon.visibility = View.VISIBLE
        holder.seekBar.visibility = View.VISIBLE
        holder.stopButton.visibility = View.VISIBLE
        holder.skipButton.visibility = View.VISIBLE

        holder.videoView.setVideoPath(mediaUrl)

        holder.playIcon.setOnClickListener {
            holder.playIcon.visibility = View.GONE
            holder.videoView.start()
            updateSeekBar(holder)
        }

        holder.stopButton.setOnClickListener {
            if (holder.videoView.isPlaying) {
                holder.videoView.pause()
                holder.playIcon.visibility = View.VISIBLE
            }
        }

        holder.skipButton.setOnClickListener {
            if (holder.videoView.isPlaying) {
                val currentPosition = holder.videoView.currentPosition
                val duration = holder.videoView.duration
                val newPosition = currentPosition + 10000
                holder.videoView.seekTo(if (newPosition > duration) duration else newPosition)
            }
        }

        holder.videoView.setOnPreparedListener {
            holder.seekBar.max = holder.videoView.duration
            holder.seekBar.visibility = View.VISIBLE
        }

        holder.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    holder.videoView.seekTo(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        holder.videoView.setOnCompletionListener {
            holder.playIcon.visibility = View.VISIBLE
            holder.seekBar.progress = 0
            handler.removeCallbacksAndMessages(null)
        }
    }

    private fun setupImage(holder: MediaViewHolder, mediaUrl: String) {
        holder.imageView.visibility = View.VISIBLE
        Glide.with(holder.itemView.context)
            .load(mediaUrl)
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
        val stopButton: ImageView = view.findViewById(R.id.stop_button)
        val skipButton: ImageView = view.findViewById(R.id.skip_button)
        val pageCount: TextView = view.findViewById(R.id.page_count)
    }

    private fun updateSeekBar(holder: MediaViewHolder) {
        holder.seekBar.progress = holder.videoView.currentPosition
        if (holder.videoView.isPlaying) {
            handler.postDelayed({ updateSeekBar(holder) }, 100)
        }
    }

    override fun onViewRecycled(holder: MediaViewHolder) {
        super.onViewRecycled(holder)
        if (holder.videoView.isPlaying) {
            holder.videoView.stopPlayback()
        }
        handler.removeCallbacksAndMessages(null)
    }
}
