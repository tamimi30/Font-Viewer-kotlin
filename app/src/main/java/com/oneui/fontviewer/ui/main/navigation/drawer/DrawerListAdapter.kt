package com.oneui.fontviewer.ui.main.navigation.drawer

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup

import androidx.recyclerview.widget.RecyclerView

import com.oneui.fontviewer.R

class DrawerListAdapter(
    private val mContext: Context,
    private val mDrawerListItems: List<DrawerListItem?>,
    private val mListener: DrawerListener?
) : RecyclerView.Adapter<DrawerListViewHolder>() {

    private var mSelectedDrawerListItem: DrawerListItem? = DrawerListItem.FONT_VIEWER


    fun interface DrawerListener {
        fun onDrawerItemSelected(drawerListItem: DrawerListItem): Boolean
    }


    override fun getItemCount(): Int {
        return mDrawerListItems.size
    }

    override fun getItemViewType(position: Int): Int {
        return if (mDrawerListItems[position] == null) VIEW_TYPE_SEPARATOR else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DrawerListViewHolder {
        val inflater = LayoutInflater.from(mContext)

        val isSeparator = (viewType == VIEW_TYPE_SEPARATOR)
        val layoutRes = if (isSeparator) R.layout.drawer_list_separator
        else R.layout.drawer_list_item
        val view = inflater.inflate(layoutRes, parent, false)
        return DrawerListViewHolder(view, isSeparator)
    }

    override fun onBindViewHolder(holder: DrawerListViewHolder, position: Int) {
        if (holder.isSeparator) return

        val drawerListItem = mDrawerListItems[position]
        if (drawerListItem == null) return

        val isSelected = (drawerListItem == mSelectedDrawerListItem)

        val iconRes = getIconForDrawerListItem(drawerListItem, isSelected)
        val title = getTitleForDrawerListItem(drawerListItem)

        if (iconRes != 0) holder.setIcon(iconRes)
        if (!title.isEmpty()) holder.setTitle(title)

        holder.setSelected(isSelected)

        holder.itemView.setOnClickListener {
            val adapterPos = holder.bindingAdapterPosition
            if (adapterPos == RecyclerView.NO_POSITION) return@setOnClickListener

            val clickedDrawerListItem = mDrawerListItems[adapterPos]
            if (clickedDrawerListItem == null) return@setOnClickListener

            var selectionChanged = false
            if (mListener != null) {
                selectionChanged = mListener.onDrawerItemSelected(clickedDrawerListItem)
            }

            if (selectionChanged) {
                setSelectedItem(clickedDrawerListItem)
            }
        }
    }


    private fun getIconForDrawerListItem(drawerListItem: DrawerListItem, selected: Boolean): Int {
        return when (drawerListItem) {
            DrawerListItem.HOME ->
                dev.oneuiproject.oneui.R.drawable.ic_oui_home_outline
            DrawerListItem.FONT_VIEWER ->
                R.drawable.ic_oui_text_style_default
            DrawerListItem.LOCAL_FONTS ->
                if (selected)
                    dev.oneuiproject.oneui.R.drawable.ic_oui_device
                else
                    dev.oneuiproject.oneui.R.drawable.ic_oui_device_outline
            DrawerListItem.SYSTEM_FONTS ->
                if (selected)
                    R.drawable.ic_android
                else
                    R.drawable.ic_android_outline
            DrawerListItem.FAVORITES ->
                if (selected)
                    dev.oneuiproject.oneui.R.drawable.ic_oui_favorite_on
                else
                    dev.oneuiproject.oneui.R.drawable.ic_oui_favorite_off
            DrawerListItem.TRASH ->
                if (selected)
                    dev.oneuiproject.oneui.R.drawable.ic_oui_delete
                else
                    dev.oneuiproject.oneui.R.drawable.ic_oui_delete_outline
        }
    }

    private fun getTitleForDrawerListItem(drawerListItem: DrawerListItem): String {
        return when (drawerListItem) {
            DrawerListItem.HOME ->
                mContext.getString(R.string.drawer_home)
            DrawerListItem.FONT_VIEWER ->
                mContext.getString(R.string.drawer_font_viewer)
            DrawerListItem.LOCAL_FONTS ->
                mContext.getString(R.string.drawer_local_fonts)
            DrawerListItem.SYSTEM_FONTS ->
                mContext.getString(R.string.drawer_system_fonts)
            DrawerListItem.FAVORITES ->
                mContext.getString(R.string.drawer_favorites)
            DrawerListItem.TRASH ->
                mContext.getString(R.string.drawer_trash)
        }
    }


    fun setSelectedItem(drawerListItem: DrawerListItem?) {
        if (drawerListItem == null || drawerListItem == mSelectedDrawerListItem) return

        val prev = mSelectedDrawerListItem
        mSelectedDrawerListItem = drawerListItem

        val prevPos = indexOfDrawerListItem(prev)
        val newPos = indexOfDrawerListItem(drawerListItem)

        if (prevPos >= 0) notifyItemChanged(prevPos)
        if (newPos >= 0) notifyItemChanged(newPos)
    }

    private fun indexOfDrawerListItem(drawerListItem: DrawerListItem?): Int {
        if (drawerListItem == null) return -1
        for (i in mDrawerListItems.indices) {
            if (mDrawerListItems[i] == drawerListItem) return i
        }
        return -1
    }

    companion object {
        private const val VIEW_TYPE_SEPARATOR = 0
        private const val VIEW_TYPE_ITEM = 1
    }

}
