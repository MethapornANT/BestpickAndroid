package com.bestpick.reviewhub

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bestpick.reviewhub.models.UserAd

class YourAdsAdapter(
    private val context: Context,
    private var ads: List<UserAd>,
    private val onAdClick: (UserAd) -> Unit
) : RecyclerView.Adapter<YourAdsAdapter.AdViewHolder>() {

    class AdViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.adImageView)
        val titleView: TextView = view.findViewById(R.id.adTitleTextView)
        val statusView: TextView = view.findViewById(R.id.adStatusTextView)
        val dateView: TextView = view.findViewById(R.id.adDateTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ad_card, parent, false)
        return AdViewHolder(view)
    }

    override fun onBindViewHolder(holder: AdViewHolder, position: Int) {
        val ad = ads[position]
        val rootUrl = context.getString(R.string.root_url)

        holder.itemView.setOnClickListener {
            onAdClick(ad)
        }

        holder.titleView.text = ad.title
        holder.statusView.text = ad.status.replaceFirstChar { it.uppercase() }

        val statusColor: Int
        when (ad.status.lowercase()) {
            "pending" -> {
                statusColor = ContextCompat.getColor(context, R.color.yellow)
                holder.dateView.text = "Waiting for Admin review"
            }
            "approved" -> {
                statusColor = ContextCompat.getColor(context, R.color.blue)
                holder.dateView.text = "Awaiting Payment"
            }
            "active" -> {
                statusColor = ContextCompat.getColor(context, R.color.green)
                holder.dateView.text = "Expires on: ${ad.expiration_date?.substring(0, 10)}"
            }
            "paid" -> {
                statusColor = ContextCompat.getColor(context, R.color.purple_200)
                holder.dateView.text = "Payment under review"
            }
            "rejected", "expired" -> {
                statusColor = ContextCompat.getColor(context, R.color.red)
                holder.dateView.text = "Status: ${ad.status}"
            }
            else -> {
                statusColor = ContextCompat.getColor(context, R.color.grey)
                holder.dateView.text = "Status: ${ad.status}"
            }
        }
        holder.statusView.background?.setTint(statusColor)

        Glide.with(context)
            .load("$rootUrl${ad.image}")
            .placeholder(R.color.grey)
            .error(R.drawable.right)
            .into(holder.imageView)
    }

    override fun getItemCount() = ads.size

    fun updateData(newAds: List<UserAd>) {
        this.ads = newAds
        notifyDataSetChanged()
    }
}