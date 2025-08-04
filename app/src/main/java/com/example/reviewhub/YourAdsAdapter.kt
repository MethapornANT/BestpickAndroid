package com.bestpick.reviewhub

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class YourAdsAdapter(private val adsList: List<YourAd>) : RecyclerView.Adapter<YourAdsAdapter.AdViewHolder>() {

    class AdViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val adImageView: ImageView = view.findViewById(R.id.adImageView)
        val captionTextView: TextView = view.findViewById(R.id.captionTextView)
        val urlTextView: TextView = view.findViewById(R.id.urlTextView)
        val statusTextView: TextView = view.findViewById(R.id.statusTextView)
        val deleteButton: Button = view.findViewById(R.id.deleteButton)
        val paymentLayout: LinearLayout = view.findViewById(R.id.paymentLayout)
        val payButton: Button = view.findViewById(R.id.payButton)
        val activeLayout: LinearLayout = view.findViewById(R.id.activeLayout)
        val packageInfoText: TextView = view.findViewById(R.id.packageInfoText)
        val dateInfoText: TextView = view.findViewById(R.id.dateInfoText)
        val renewButton: Button = view.findViewById(R.id.renewButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_your_ad, parent, false)
        return AdViewHolder(view)
    }

    override fun onBindViewHolder(holder: AdViewHolder, position: Int) {
        val currentAd = adsList[position]

        holder.captionTextView.text = currentAd.caption
        holder.urlTextView.text = currentAd.url
        Glide.with(holder.itemView.context).load(currentAd.imageUrl).into(holder.adImageView)

        when (currentAd.status) {
            "pending_approval" -> {
                holder.statusTextView.text = "Status: Pending Approval"
                holder.paymentLayout.visibility = View.GONE
                holder.activeLayout.visibility = View.GONE
                holder.deleteButton.visibility = View.VISIBLE
            }
            "pending_payment" -> {
                holder.statusTextView.text = "Status: Pending Payment"
                holder.paymentLayout.visibility = View.VISIBLE
                holder.activeLayout.visibility = View.GONE
                holder.deleteButton.visibility = View.VISIBLE
            }
            "active" -> {
                holder.statusTextView.text = "Status: Active"
                holder.paymentLayout.visibility = View.GONE
                holder.activeLayout.visibility = View.VISIBLE
                holder.deleteButton.visibility = View.VISIBLE

                holder.packageInfoText.text = "${currentAd.packageType}, ${currentAd.duration} Day"
                holder.dateInfoText.text = "Start: ${currentAd.startDate} - End: ${currentAd.endDate}"
            }
            else -> {
                holder.statusTextView.text = "Status: Expired"
                holder.paymentLayout.visibility = View.GONE
                holder.activeLayout.visibility = View.GONE
                holder.deleteButton.visibility = View.VISIBLE
            }
        }
    }

    override fun getItemCount() = adsList.size
}