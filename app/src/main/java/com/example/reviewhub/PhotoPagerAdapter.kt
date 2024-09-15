import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.reviewhub.R

class PhotoPagerAdapter(private val photoUrls: List<String>) : RecyclerView.Adapter<PhotoPagerAdapter.PhotoViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.photo_slide_item, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photoUrl = photoUrls[position]
        Glide.with(holder.itemView.context)
            .load(photoUrl)
            .placeholder(R.drawable.ic_photo_placeholder)
            .error(R.drawable.ic_error)
            .into(holder.imageView)
    }

    override fun getItemCount(): Int = photoUrls.size

    class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.photo_slide_image_view)
    }
}
