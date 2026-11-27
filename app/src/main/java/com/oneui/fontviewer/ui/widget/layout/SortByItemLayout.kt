package com.oneui.fontviewer.ui.widget.layout

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.TooltipCompat

import com.oneui.fontviewer.R

class SortByItemLayout : LinearLayout {

    private var mSortTextView: TextView? = null
    private var mTextContainer: View? = null
    private var mOrderIcon: ImageView? = null
    private var mOrderContainer: View? = null
    private var mListener: OnSortChangeListener? = null

    enum class SortType {
        NAME, DATE, SIZE
    }

    private var mCurrentSortType = SortType.NAME
    private var mIsAscending = true

    fun interface OnSortChangeListener {
        fun onSortChanged(type: SortType, ascending: Boolean)
    }

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context)
    }

    private fun init(context: Context) {
        LayoutInflater.from(context).inflate(R.layout.view_sort_by_item, this, true)

        mSortTextView = findViewById(R.id.sort_current_text)
        mTextContainer = findViewById(R.id.sort_text_container)
        mOrderIcon = findViewById(R.id.sort_order_icon)
        mOrderContainer = findViewById(R.id.sort_order_container)

        setupClickListeners()

        updateUI()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)

        mTextContainer?.isEnabled = enabled
        mOrderContainer?.isEnabled = enabled

        alpha = if (enabled) 1.0f else 0.4f
    }

    fun setupClickListeners() {
        val textContainer = mTextContainer
        if (textContainer != null) {
            textContainer.isClickable = true
            textContainer.isFocusable = true
            textContainer.setOnClickListener {
                if (isEnabled) showSortMenu()
            }
        }

        val orderContainer = mOrderContainer
        if (orderContainer != null) {
            orderContainer.isClickable = true
            orderContainer.isFocusable = true

            orderContainer.setOnClickListener {
                if (isEnabled) {
                    mIsAscending = !mIsAscending
                    updateUI()
                    notifyListener()
                }
            }
        }
    }

    private fun showSortMenu() {
        val textContainer = mTextContainer ?: return

        val popup = PopupMenu(context, textContainer)
        popup.menuInflater.inflate(R.menu.menu_sort_options, popup.menu)

        if (mCurrentSortType == SortType.NAME) popup.menu.findItem(R.id.sort_by_name).isChecked = true
        else if (mCurrentSortType == SortType.DATE) popup.menu.findItem(R.id.sort_by_date).isChecked = true
        else if (mCurrentSortType == SortType.SIZE) popup.menu.findItem(R.id.sort_by_size).isChecked = true

        popup.setOnMenuItemClickListener { item ->
            val id = item.itemId
            if (id == R.id.sort_by_name) {
                mCurrentSortType = SortType.NAME
            } else if (id == R.id.sort_by_date) {
                mCurrentSortType = SortType.DATE
            } else if (id == R.id.sort_by_size) {
                mCurrentSortType = SortType.SIZE
            }
            updateUI()
            notifyListener()
            true
        }

        popup.show()
    }

    private fun updateUI() {
        val sortTextView = mSortTextView
        val orderIcon = mOrderIcon
        if (sortTextView == null || orderIcon == null) return
        val orderContainer = mOrderContainer!!

        if (mCurrentSortType == SortType.NAME) {
            sortTextView.setText(R.string.sort_name)
        } else if (mCurrentSortType == SortType.DATE) {
            sortTextView.setText(R.string.sort_date)
        } else if (mCurrentSortType == SortType.SIZE) {
            sortTextView.setText(R.string.sort_size)
        }

        if (mIsAscending) {
            orderIcon.setImageResource(dev.oneuiproject.oneui.R.drawable.ic_oui_arrow_up)
            TooltipCompat.setTooltipText(orderContainer, context.getString(R.string.sort_ascending))
            orderContainer.contentDescription = context.getString(R.string.sort_ascending)
        } else {
            orderIcon.setImageResource(dev.oneuiproject.oneui.R.drawable.ic_oui_arrow_down)
            TooltipCompat.setTooltipText(orderContainer, context.getString(R.string.sort_descending))
            orderContainer.contentDescription = context.getString(R.string.sort_descending)
        }
    }

    fun setOnSortChangeListener(listener: OnSortChangeListener?) {
        this.mListener = listener
    }

    private fun notifyListener() {
        mListener?.onSortChanged(mCurrentSortType, mIsAscending)
    }

    fun setSortType(type: SortType) {
        this.mCurrentSortType = type
        updateUI()
    }

    fun setSortAscending(ascending: Boolean) {
        this.mIsAscending = ascending
        updateUI()
    }

}
