package com.zappix.store

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load

class AppAdapter(
    private val onFocused: (StoreApp) -> Unit,
    private val onClicked: (StoreApp) -> Unit
) : ListAdapter<StoreApp, AppAdapter.AppViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        return AppViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_app_card, parent, false)
        )
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) = holder.bind(getItem(position))

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val artwork: ImageView = itemView.findViewById(R.id.appArtwork)
        private val name: TextView = itemView.findViewById(R.id.appName)
        private val frame: View = itemView.findViewById(R.id.focusFrame)

        fun bind(app: StoreApp) {
            name.text = app.name
            artwork.load(app.iconUrl) {
                crossfade(false)
                allowHardware(true)
                size(420, 420)
            }

            itemView.nextFocusDownId = R.id.installButton

            itemView.setOnFocusChangeListener { _, focused ->
                itemView.animate().cancel()
                itemView.animate()
                    .scaleX(if (focused) 1.075f else 1f)
                    .scaleY(if (focused) 1.075f else 1f)
                    .translationY(if (focused) -4f else 0f)
                    .setDuration(if (focused) 105L else 85L)
                    .start()

                itemView.elevation = if (focused) 24f else 2f
                frame.visibility = if (focused) View.VISIBLE else View.INVISIBLE
                name.alpha = if (focused) 1f else 0.78f
                if (focused) onFocused(app)
            }

            itemView.setOnClickListener { onClicked(app) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<StoreApp>() {
        override fun areItemsTheSame(oldItem: StoreApp, newItem: StoreApp) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: StoreApp, newItem: StoreApp) = oldItem == newItem
    }
}
