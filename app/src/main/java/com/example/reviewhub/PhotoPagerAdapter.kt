package com.example.reviewhub

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.VideoView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class PhotoPagerAdapter(private val mediaUrls: List<Pair<String, String>>) : RecyclerView.Adapter<PhotoPagerAdapter.MediaViewHolder>() {

    private val handler = Handler(Looper.getMainLooper())
    private var itemClickListener: ((Int, String) -> Unit)? = null // Listener สำหรับคลิก (ตำแหน่ง, ประเภทสื่อ)

    // ฟังก์ชันสำหรับตั้งค่า Click Listener
    fun setOnItemClickListener(listener: (Int, String) -> Unit) {
        itemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.photo_slide_item, parent, false)
        return MediaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        val (mediaUrl, mediaType) = mediaUrls[position]

        if (mediaType == "video") {
            holder.imageView.visibility = View.GONE
            holder.videoView.visibility = View.VISIBLE
            holder.playIcon.visibility = View.VISIBLE
            holder.stopButton.visibility = View.VISIBLE
            holder.skipButton.visibility = View.VISIBLE
            holder.seekBar.visibility = View.VISIBLE
            holder.videoView.layoutParams.height = 400 * holder.itemView.resources.displayMetrics.density.toInt()
            holder.videoView.layoutParams.width = holder.itemView.resources.displayMetrics.widthPixels


            holder.videoView.setVideoPath(mediaUrl)

            // Update SeekBar with video progress
            val updateSeekBar = object : Runnable {
                override fun run() {
                    if (holder.videoView.isPlaying) {
                        holder.seekBar.progress = holder.videoView.currentPosition
                        handler.postDelayed(this, 500)
                    }
                }
            }

            // Play video when play icon is clicked
            holder.playIcon.setOnClickListener {
                holder.playIcon.visibility = View.GONE
                holder.videoView.start()
                holder.seekBar.max = holder.videoView.duration
                handler.post(updateSeekBar) // Start updating the seek bar
            }

            // Stop video when stop button is clicked
            holder.stopButton.setOnClickListener {
                if (holder.videoView.isPlaying) {
                    holder.videoView.stopPlayback()
                    holder.videoView.resume()
                    holder.playIcon.visibility = View.VISIBLE
                    holder.seekBar.progress = 0
                }
            }

            // Skip forward 10 seconds when skip button is clicked
            holder.skipButton.setOnClickListener {
                if (holder.videoView.isPlaying) {
                    val currentPosition = holder.videoView.currentPosition
                    val skipPosition = currentPosition + 10000 // Skip forward 10 seconds
                    holder.videoView.seekTo(skipPosition)
                }
            }

            // SeekBar change listener
            holder.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        holder.videoView.seekTo(progress)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            // Handle video playback completion
            holder.videoView.setOnCompletionListener {
                holder.playIcon.visibility = View.VISIBLE
                holder.seekBar.progress = 0
            }

            // เพิ่ม Listener ให้ VideoView
            holder.videoView.setOnClickListener {
                itemClickListener?.invoke(position, "video") // ส่งตำแหน่งและประเภทสื่อ
            }
        } else {
            // Handle photo
            holder.imageView.visibility = View.VISIBLE
            holder.videoView.visibility = View.GONE
            holder.playIcon.visibility = View.GONE
            holder.stopButton.visibility = View.GONE
            holder.skipButton.visibility = View.GONE
            holder.seekBar.visibility = View.GONE

            Glide.with(holder.itemView.context)
                .load(mediaUrl)
                .placeholder(R.drawable.ic_photo_placeholder)
                .error(R.drawable.ic_error)
                .into(holder.imageView)

            // เพิ่ม Listener ให้ ImageView
            holder.imageView.setOnClickListener {
                itemClickListener?.invoke(position, "photo") // ส่งตำแหน่งและประเภทสื่อ
            }
        }
    }

    override fun getItemCount(): Int = mediaUrls.size

    class MediaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.photo_slide_image_view)
        val videoView: VideoView = itemView.findViewById(R.id.video_view)
        val playIcon: ImageView = itemView.findViewById(R.id.play_icon)
        val stopButton: ImageView = itemView.findViewById(R.id.stop_button)
        val skipButton: ImageView = itemView.findViewById(R.id.skip_button)
        val seekBar: SeekBar = itemView.findViewById(R.id.video_seek_bar)
    }

    // Release resources for VideoView if needed
    override fun onViewRecycled(holder: MediaViewHolder) {
        super.onViewRecycled(holder)
        if (holder.videoView.isPlaying) {
            holder.videoView.stopPlayback()
        }
        handler.removeCallbacksAndMessages(null) // Stop updating the seek bar
    }
}
