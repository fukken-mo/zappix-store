package com.zappix.store

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load

enum class InstallState { NOT_INSTALLED, INSTALLED, UPDATE }

data class AppItem(val app: StoreApp, val state: InstallState)

class AppAdapter(
    private val onFocused: (StoreApp, Int) -> Unit,
    private val onClicked: (StoreApp) -> Unit,
    private val onUpPressed: () -> Boolean
) : ListAdapter<AppItem, AppAdapter.AppViewHolder>(Diff) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).app.id.toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        return AppViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_app_card, parent, false)
        )
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) =
        holder.bind(getItem(position), loadArtwork = true)

    override fun onBindViewHolder(holder: AppViewHolder, position: Int, payloads: MutableList<Any>) {
        // A state-only change (Installed/Update badge) must not reload the artwork.
        holder.bind(getItem(position), loadArtwork = payloads.isEmpty())
    }

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val artwork: ImageView = itemView.findViewById(R.id.appArtwork)
        private val name: TextView = itemView.findViewById(R.id.appName)
        private val glow: View = itemView.findViewById(R.id.focusFrame)
        private val badge: TextView = itemView.findViewById(R.id.installedBadge)
        private val density = itemView.resources.displayMetrics.density
        private var item: AppItem? = null

        init {
            // android:clipToOutline in XML only works on API 31+; this works on every version.
            artwork.clipToOutline = true
            itemView.setOnFocusChangeListener { _, focused ->
                applyFocusState(focused, animate = true)
                val position = bindingAdapterPosition
                if (focused && position != RecyclerView.NO_POSITION) {
                    item?.let { onFocused(it.app, position) }
                }
            }
            itemView.setOnClickListener { item?.let { onClicked(it.app) } }
            itemView.setOnKeyListener { _, keyCode, event ->
                keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN && onUpPressed()
            }
        }

        fun bind(item: AppItem, loadArtwork: Boolean) {
            this.item = item
            name.text = item.app.name
            when (item.state) {
                InstallState.INSTALLED -> {
                    badge.text = "Installed"
                    badge.setBackgroundResource(R.drawable.bg_installed_badge)
                    badge.setTextColor(0xFFD9FAFF.toInt())
                    badge.visibility = View.VISIBLE
                }
                InstallState.UPDATE -> {
                    badge.text = "Update"
                    badge.setBackgroundResource(R.drawable.bg_update_badge)
                    badge.setTextColor(0xFFFFEBB8.toInt())
                    badge.visibility = View.VISIBLE
                }
                InstallState.NOT_INSTALLED -> badge.visibility = View.GONE
            }
            if (loadArtwork) {
                artwork.load(item.app.iconUrl.ifBlank { null }) {
                    crossfade(false)
                    allowHardware(true)
                    size(420, 420)
                }
            }
            // Recycled views must never keep a half-finished focus animation.
            applyFocusState(itemView.hasFocus(), animate = false)
        }

        private fun applyFocusState(focused: Boolean, animate: Boolean) {
            val scale = if (focused) 1.08f else 1f
            val lift = if (focused) -4f * density else 0f
            // translationZ only changes drawing order here (the card has no outline, so no shadow).
            itemView.translationZ = if (focused) 8f * density else 0f
            itemView.animate().cancel()
            glow.animate().cancel()
            name.alpha = if (focused) 1f else 0.78f
            if (animate) {
                itemView.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .translationY(lift)
                    .setInterpolator(FOCUS_INTERPOLATOR)
                    .setDuration(if (focused) 150L else 110L)
                    .start()
                if (focused) {
                    glow.visibility = View.VISIBLE
                    glow.animate().alpha(1f).setDuration(150L).start()
                } else {
                    glow.animate().alpha(0f).setDuration(90L)
                        .withEndAction { if (!itemView.hasFocus()) glow.visibility = View.INVISIBLE }
                        .start()
                }
            } else {
                itemView.scaleX = scale
                itemView.scaleY = scale
                itemView.translationY = lift
                glow.alpha = if (focused) 1f else 0f
                glow.visibility = if (focused) View.VISIBLE else View.INVISIBLE
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<AppItem>() {
        override fun areItemsTheSame(oldItem: AppItem, newItem: AppItem) = oldItem.app.id == newItem.app.id
        override fun areContentsTheSame(oldItem: AppItem, newItem: AppItem) = oldItem == newItem
        override fun getChangePayload(oldItem: AppItem, newItem: AppItem): Any? =
            if (oldItem.app == newItem.app) PAYLOAD_STATE else null
    }

    private companion object {
        const val PAYLOAD_STATE = "state"
        val FOCUS_INTERPOLATOR = DecelerateInterpolator(1.6f)
    }
}
