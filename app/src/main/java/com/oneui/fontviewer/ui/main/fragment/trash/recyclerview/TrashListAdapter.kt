package com.oneui.fontviewer.ui.main.fragment.trash.recyclerview

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView

import com.oneui.fontviewer.R
import com.oneui.fontviewer.data.common.room.FontEntity
import com.oneui.fontviewer.ui.widget.layout.SelectableLinearLayout

class TrashListAdapter(private val context: Context) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var mItems: List<FontEntity> = ArrayList()

    // التحديد مرتبط بمعرّف العنصر (id) وليس بموضعه، حتى لا يتحدد عنصر آخر إذا تغيرت القائمة أثناء التحديد
    private val selectedIds: MutableSet<Long> = HashSet()

    // يُستدعى عندما يُحذف من التحديد عنصر لم يعد موجوداً في القائمة، ليُحدّث مدير التحديد العدّاد
    private var onSelectionPrunedListener: Runnable? = null

    private var selectionListener: OnSelectionListener? = null
    private var recyclerView: RecyclerView? = null

    var isSelectionMode: Boolean = false
        set(enabled) {
            field = enabled
            if (!enabled) selectedIds.clear()
            notifyItemRangeChanged(1, mItems.size, PAYLOAD_UPDATE_SELECTION)
        }


    interface OnSelectionListener {
        fun onStartSelection(adapterPosition: Int)
        fun onToggleSelection(adapterPosition: Int)
    }

    init {
        setHasStableIds(true)
    }


    fun setSelectionListener(listener: OnSelectionListener?) {
        this.selectionListener = listener
    }

    fun setOnSelectionPrunedListener(listener: Runnable?) {
        this.onSelectionPrunedListener = listener
    }


    fun submitList(newList: List<FontEntity>?) {
        val safeNew: List<FontEntity> = newList ?: emptyList()

        val result = DiffUtil.calculateDiff(
            TrashDiffCallback(mItems, safeNew)
        )
        mItems = ArrayList(safeNew)

        pruneSelection()

        result.dispatchUpdatesTo(object : ListUpdateCallback {
            override fun onInserted(position: Int, count: Int) {
                notifyItemRangeInserted(position + 1, count)
            }

            override fun onRemoved(position: Int, count: Int) {
                notifyItemRangeRemoved(position + 1, count)
            }

            override fun onMoved(fromPosition: Int, toPosition: Int) {
                notifyItemMoved(fromPosition + 1, toPosition + 1)
            }

            override fun onChanged(position: Int, count: Int, payload: Any?) {
                notifyItemRangeChanged(position + 1, count, payload)
            }
        })
    }

    // يزيل من التحديد أي عنصر لم يعد موجوداً في القائمة بعد تحديثها
    private fun pruneSelection() {
        if (selectedIds.isEmpty()) return

        val present: MutableSet<Long> = HashSet()
        for (font in mItems) {
            val id = font.id
            present.add(id)
        }

        val listener = onSelectionPrunedListener
        if (selectedIds.retainAll(present) && listener != null) {
            listener.run()
        }
    }


    override fun getItemCount(): Int {
        return mItems.size + 2
    }

    override fun getItemViewType(position: Int): Int {
        if (position == 0) return VIEW_TYPE_HEADER
        if (position == itemCount - 1) return VIEW_TYPE_SPACE
        return VIEW_TYPE_ITEM
    }

    override fun getItemId(position: Int): Long {
        if (position == 0) return Long.MIN_VALUE
        if (position == itemCount - 1) return Long.MAX_VALUE
        return mItems[position - 1].id
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> TrashHeaderViewHolder(
                inf.inflate(R.layout.trash_header_item, parent, false)
            )
            VIEW_TYPE_SPACE -> SpaceViewHolder(
                inf.inflate(R.layout.bottom_space_item, parent, false)
            )
            else -> TrashItemViewHolder(inf.inflate(R.layout.trash_list_item, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is TrashHeaderViewHolder) {
            holder.bind()
        } else if (holder is TrashItemViewHolder) {
            bindItem(holder, mItems[position - 1], position)
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder, position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isNotEmpty()) {
            if (payloads.contains(PAYLOAD_UPDATE_SELECTION) && holder is TrashItemViewHolder) {
                updateCheckBoxState(holder, position)
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }


    private fun bindItem(
        holder: TrashItemViewHolder,
        font: FontEntity,
        position: Int
    ) {
        holder.fontNameTextView.text = font.displayName

        holder.daysRemainingTextView.text = buildDaysRemainingText(font.deletedAt)

        updateCheckBoxState(holder, position)

        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            if (isSelectionMode) {
                selectionListener?.onToggleSelection(pos)
            } else {
                Toast.makeText(
                    context, R.string.toast_prepare_file_to_open,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        holder.itemView.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnLongClickListener false
            val listener = selectionListener
            if (listener != null) {
                if (!isSelectionMode) {
                    listener.onStartSelection(pos)
                } else {
                    listener.onToggleSelection(pos)
                }
            }
            recyclerView?.seslStartLongPressMultiSelection()
            true
        }
    }


    private fun updateCheckBoxState(holder: TrashItemViewHolder, position: Int) {
        val layout = holder.selectableLayout
        if (layout != null) {
            layout.isSelectionMode = isSelectionMode
            layout.setSelectedAnimate(isItemSelected(position))
        }
    }


    private fun buildDaysRemainingText(deletedAt: Long): String {
        if (deletedAt <= 0) {
            return context.resources.getQuantityString(
                R.plurals.trash_days_remaining, 30, 30
            )
        }
        val diffMs = System.currentTimeMillis() - deletedAt
        val daysElapsed = (diffMs / (24L * 60L * 60L * 1000L)).toInt()
        val daysRemaining = Math.max(0, 30 - daysElapsed)
        return context.resources.getQuantityString(
            R.plurals.trash_days_remaining, daysRemaining, daysRemaining
        )
    }


    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.recyclerView = null
    }


    fun setItemSelected(adapterPosition: Int, selected: Boolean) {
        val font = getItemAtAdapterPosition(adapterPosition) ?: return

        val id = font.id
        if (selected) selectedIds.add(id)
        else selectedIds.remove(id)

        notifyItemChanged(adapterPosition, PAYLOAD_UPDATE_SELECTION)
    }


    fun clearSelection() {
        selectedIds.clear()
        notifyItemRangeChanged(1, mItems.size, PAYLOAD_UPDATE_SELECTION)
    }

    fun isItemSelected(adapterPosition: Int): Boolean {
        val font = getItemAtAdapterPosition(adapterPosition) ?: return false

        val id = font.id
        return selectedIds.contains(id)
    }

    val selectedCount: Int
        get() = selectedIds.size

    // العناصر المحددة بترتيب ظهورها في القائمة
    val selectedFonts: List<FontEntity>
        get() {
            val result: MutableList<FontEntity> = ArrayList(selectedIds.size)
            if (selectedIds.isEmpty()) return result

            for (font in mItems) {
                val id = font.id
                if (selectedIds.contains(id)) result.add(font)
            }
            return result
        }


    val allFonts: List<FontEntity>
        get() = ArrayList(mItems)

    fun getItemAtAdapterPosition(adapterPosition: Int): FontEntity? {
        val itemIndex = adapterPosition - 1
        if (itemIndex < 0 || itemIndex >= mItems.size) return null
        return mItems[itemIndex]
    }


    private class TrashHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val messageTextView: TextView? = itemView.findViewById(R.id.trash_header_message)

        fun bind() {
            messageTextView?.setText(R.string.trash_items_expiry_info)
        }
    }

    private class TrashItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val selectableLayout: SelectableLinearLayout? = itemView.findViewById(R.id.selectable_layout)
        val fontNameTextView: TextView = itemView.findViewById(R.id.trash_item_font_name)
        val daysRemainingTextView: TextView = itemView.findViewById(R.id.trash_item_days_remaining)
    }

    private class SpaceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        init {
            itemView.isFocusable = false
            itemView.isClickable = false
        }
    }


    private class TrashDiffCallback(
        private val oldList: List<FontEntity>,
        private val newList: List<FontEntity>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int {
            return oldList.size
        }

        override fun getNewListSize(): Int {
            return newList.size
        }

        override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
            return oldList[oldPos].id == newList[newPos].id
        }

        override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
            val o = oldList[oldPos]
            val n = newList[newPos]
            return o.fileName == n.fileName
                    && o.deletedAt == n.deletedAt
                    && o.isTrashed == n.isTrashed
        }
    }

    companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_ITEM = 1
        const val VIEW_TYPE_SPACE = 2

        private const val PAYLOAD_UPDATE_SELECTION = "UPDATE_SELECTION"
    }
}
