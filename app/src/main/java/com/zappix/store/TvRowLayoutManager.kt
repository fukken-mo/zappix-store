package com.zappix.store

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Horizontal row that keeps LEFT/RIGHT focus inside the row. When the D-pad is held and the next
 * card is not laid out yet, focus stays put (and the row scrolls to it) instead of escaping to
 * the tabs or the Refresh button. At either end of the row, focus simply stays on the end card.
 */
class TvRowLayoutManager(
    context: Context,
    private val onTargetNotLaidOut: (Int) -> Unit
) : LinearLayoutManager(context, RecyclerView.HORIZONTAL, false) {

    override fun onInterceptFocusSearch(focused: View, direction: Int): View? {
        if (direction != View.FOCUS_LEFT && direction != View.FOCUS_RIGHT) return null
        val itemView = findContainingItemView(focused) ?: return null
        val position = getPosition(itemView)
        val rtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
        val forward = (direction == View.FOCUS_RIGHT) != rtl
        val target = if (forward) position + 1 else position - 1
        if (target < 0 || target >= itemCount) return focused
        findViewByPosition(target)?.let { return it }
        onTargetNotLaidOut(target)
        return focused
    }
}
