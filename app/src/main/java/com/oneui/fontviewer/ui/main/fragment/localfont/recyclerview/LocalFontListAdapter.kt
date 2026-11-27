package com.oneui.fontviewer.ui.main.fragment.localfont.recyclerview

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SectionIndexer

import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SortedList

import com.oneui.fontviewer.R
import com.oneui.fontviewer.data.common.list.FontFileInfo
import com.oneui.fontviewer.data.localfont.LocalFontCache
import com.oneui.fontviewer.data.localfont.LocalFontPreferenceManager
import com.oneui.fontviewer.ui.main.list.search.SearchHighlighter
import com.oneui.fontviewer.ui.main.list.sort.SortHeaderViewHolder
import com.oneui.fontviewer.ui.widget.layout.SortByItemLayout
import com.oneui.fontviewer.data.settings.SettingsRepository
import com.oneui.fontviewer.data.localfont.model.FontFileInfoWithMetadata

import java.util.concurrent.ExecutorService

class LocalFontListAdapter @JvmOverloads constructor(
    private val context: Context,
    private val executor: ExecutorService?,
    isFavoritesList: Boolean = false
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), SectionIndexer {

    private val preferenceManager: LocalFontPreferenceManager =
        LocalFontPreferenceManager(context, isFavoritesList)
    private val highlighter: SearchHighlighter = SearchHighlighter(context)
    private val mainHandler: Handler = Handler(Looper.getMainLooper())
    private var recyclerView: RecyclerView? = null

    private var mIsFontPreviewEnabled: Boolean = SettingsRepository.isFontPreviewEnabled(context)

    private var fontsMetadataMap: HashMap<String, FontFileInfoWithMetadata> = HashMap()
    private var currentSearchQuery: String = ""

    private val sections: MutableList<String> = ArrayList()
    private val sectionPositions: MutableList<Int> = ArrayList()
    private val positionSections: MutableList<Int> = ArrayList()

    private var currentSortType: SortByItemLayout.SortType = SortByItemLayout.SortType.NAME
    private var currentSortAscending: Boolean = true

    // التحديد مرتبط بمسار الملف وليس بموضعه في القائمة، حتى لا يتحدد عنصر آخر
    // إذا تغير ترتيب القائمة أثناء وضع التحديد (مثلاً بعد استخراج الأسماء الحقيقية وإعادة الفرز)
    private val selectedPathsSet: MutableSet<String> = HashSet()

    // يُستدعى عندما يُحذف من التحديد عنصر لم يعد موجوداً في القائمة، ليُحدّث مدير التحديد العدّاد
    private var onSelectionPrunedListener: Runnable? = null

    private var fontClickListener: OnFontClickListener? = null
    private var sortChangeListener: SortByItemLayout.OnSortChangeListener? = null
    private var selectionListener: OnSelectionListener? = null

    private var favoriteStatusProvider: FavoriteStatusProvider? = null

    private val mSortedList: SortedList<FontFileInfo> =
        SortedList(FontFileInfo::class.java, FontSortedListCallback())

    var isSelectionMode: Boolean = false
        set(enabled) {
            field = enabled
            if (!enabled) selectedPathsSet.clear()
            notifyItemRangeChanged(0, itemCount, PAYLOAD_UPDATE_SELECTION)
        }

    private inner class FontSortedListCallback : SortedList.Callback<FontFileInfo>() {

        override fun compare(a: FontFileInfo?, b: FontFileInfo?): Int {
            return compareItems(a, b)
        }

        override fun areItemsTheSame(a: FontFileInfo?, b: FontFileInfo?): Boolean {
            return a!!.path == b!!.path
        }

        override fun areContentsTheSame(a: FontFileInfo?, b: FontFileInfo?): Boolean {
            return a!!.name == b!!.name &&
                    a.size == b.size &&
                    a.lastModified == b.lastModified
        }

        override fun onInserted(position: Int, count: Int) {
            notifyItemRangeInserted(position + 1, count)
        }

        override fun onRemoved(position: Int, count: Int) {
            notifyItemRangeRemoved(position + 1, count)
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            notifyItemMoved(fromPosition + 1, toPosition + 1)
        }

        override fun onChanged(position: Int, count: Int) {
            notifyItemRangeChanged(position + 1, count)
        }
    }

    private fun compareItems(a: FontFileInfo?, b: FontFileInfo?): Int {
        if (a == null) return 1
        if (b == null) return -1
        val result: Int = when (currentSortType) {
            SortByItemLayout.SortType.DATE ->
                a.lastModified.compareTo(b.lastModified)
            SortByItemLayout.SortType.SIZE ->
                a.size.compareTo(b.size)
            SortByItemLayout.SortType.NAME -> {
                val nameA = a.displayName ?: ""
                val nameB = b.displayName ?: ""
                nameA.compareTo(nameB, ignoreCase = true)
            }
        }
        return if (currentSortAscending) result else -result
    }

    class SpaceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        init {
            itemView.isFocusable = false
            itemView.isClickable = false
        }
    }

    fun interface OnFontClickListener {
        fun onFontClick(
            fontPath: String, realName: String?, fileName: String?,
            ttcIndex: Int, weightWidthLabel: String?
        )
    }

    interface OnSelectionListener {
        fun onStartSelection(position: Int)
        fun onToggleSelection(position: Int)
    }

    fun interface FavoriteStatusProvider {
        fun isFavorited(fontPath: String): Boolean
    }

    init {
        setHasStableIds(true)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.recyclerView = null
    }


    fun setFontClickListener(listener: OnFontClickListener?) { this.fontClickListener = listener }
    fun setSortChangeListener(l: SortByItemLayout.OnSortChangeListener?) { this.sortChangeListener = l }
    fun setSelectionListener(listener: OnSelectionListener?) { this.selectionListener = listener }

    fun setFavoriteStatusProvider(provider: FavoriteStatusProvider?) {
        this.favoriteStatusProvider = provider
    }

    fun setOnSelectionPrunedListener(listener: Runnable?) {
        this.onSelectionPrunedListener = listener
    }


    fun setFontPreviewEnabled(enabled: Boolean) {
        if (this.mIsFontPreviewEnabled != enabled) {
            this.mIsFontPreviewEnabled = enabled
            val rv = recyclerView
            if (rv != null && !rv.isComputingLayout) {
                notifyItemRangeChanged(1, mSortedList.size())
            } else {
                notifyDataSetChanged()
            }
        }
    }

    fun saveLastOpenedAndUpdate(path: String?) {
        preferenceManager.saveLastOpenedFont(path)
        smartUpdate()
    }


    fun setItemSelected(position: Int, selected: Boolean) {
        val path = getFilePath(position) ?: return
        if (selected) selectedPathsSet.add(path)
        else selectedPathsSet.remove(path)
        notifyItemChanged(position, PAYLOAD_UPDATE_SELECTION)
    }

    fun clearSelection() { selectedPathsSet.clear() }

    fun isItemSelected(position: Int): Boolean {
        val path = getFilePath(position)
        return path != null && selectedPathsSet.contains(path)
    }

    val selectedCount: Int
        get() = selectedPathsSet.size

    // مسارات العناصر المحددة بترتيب ظهورها في القائمة
    val selectedPaths: List<String>
        get() {
            val result: MutableList<String> = ArrayList(selectedPathsSet.size)
            if (selectedPathsSet.isEmpty()) return result
            for (i in 0 until mSortedList.size()) {
                val path = mSortedList.get(i).path
                if (selectedPathsSet.contains(path)) result.add(path)
            }
            return result
        }

    // يزيل من التحديد أي مسار لم يعد موجوداً في القائمة بعد تحديثها
    private fun pruneSelection() {
        if (selectedPathsSet.isEmpty()) return

        val present: MutableSet<String> = HashSet()
        for (i in 0 until mSortedList.size()) {
            present.add(mSortedList.get(i).path)
        }

        val listener = onSelectionPrunedListener
        if (selectedPathsSet.retainAll(present) && listener != null) {
            listener.run()
        }
    }

    fun getFilePath(position: Int): String? {
        if (position > 0 && position <= mSortedList.size())
            return mSortedList.get(position - 1).path
        return null
    }

    fun findPositionByPath(path: String?): Int {
        if (path == null) return -1
        for (i in 0 until mSortedList.size())
            if (path == mSortedList.get(i).path) return i + 1
        return -1
    }


    fun notifyAllFavoritesChanged() {
        val size = mSortedList.size()
        if (size > 0) notifyItemRangeChanged(1, size, PAYLOAD_UPDATE_FAVORITE)
    }

    fun updateFilteredFonts(fonts: List<FontFileInfo>?, searchQuery: String?) {
        val oldQuery = this.currentSearchQuery
        this.currentSearchQuery = searchQuery ?: ""
        val newList: List<FontFileInfo> = fonts ?: ArrayList()

        mSortedList.replaceAll(newList)
        pruneSelection()
        buildSections()

        if (this.currentSearchQuery != oldQuery) {
            refreshSearchHighlight()
        }
    }

    /**
     * يعيد تمييز نص البحث على العناصر الباقية بعد تغيّر الاستعلام، دون أن يُلغي أنيميشن ظهور العناصر الجديدة.
     *
     * السبب: أي notifyItemRangeChanged على عنصر يجري إظهاره الآن يجعل DefaultItemAnimator ينفّذ
     * animateChange ← animateMove ← resetAnimation ← endAnimation، فيُلغي أنيميشن الظهور (alpha من 0 إلى 1)
     * ويضبط الشفافية على 1 فوراً، فتظهر العناصر فجأة بينما تختفي تدريجياً بشكل صحيح.
     * لذلك:
     *  - العناصر المعروضة حالياً تُحدَّث مباشرة على الـ ViewHolder دون أي إشعار للـ RecyclerView.
     *  - العناصر غير المعروضة (قد تكون محفوظة في الـ cache بتمييز قديم) ليس لها View الآن،
     *    فيُرسل لها إشعار عادي ولا يتأثر به أي أنيميشن.
     *  - العناصر الجديدة لا تحتاج شيئاً، لأنها تُربط (bind) بالاستعلام الجديد مباشرة.
     */
    private fun refreshSearchHighlight() {
        val size = mSortedList.size()
        val rv = recyclerView
        if (size == 0 || rv == null || rv.isComputingLayout) return

        // مواضع العناصر المعروضة حالياً (1..size)، المحسوبة مع التعديلات المعلّقة على الـ Adapter
        val isAttached = BooleanArray(size + 1)

        var i = 0
        val count = rv.childCount
        while (i < count) {
            val vh = rv.getChildViewHolder(rv.getChildAt(i))
            i++
            if (vh !is LocalFontViewHolder) continue

            val position = vh.bindingAdapterPosition
            if (position < 1 || position > size) continue

            isAttached[position] = true
            applyHighlight(vh, mSortedList.get(position - 1))
        }

        var runStart = -1
        for (position in 1..size + 1) {
            val needsNotify = position <= size && !isAttached[position]
            if (needsNotify) {
                if (runStart == -1) runStart = position
            } else if (runStart != -1) {
                notifyItemRangeChanged(runStart, position - runStart, PAYLOAD_UPDATE_HIGHLIGHT)
                runStart = -1
            }
        }
    }

    private fun applyHighlight(holder: LocalFontViewHolder, fontInfo: FontFileInfo) {
        val isSearchActive = !currentSearchQuery.isEmpty()
        val displayName = fontInfo.displayName ?: ""

        if (isSearchActive) {
            val highlighted = highlighter.highlightText(displayName, currentSearchQuery)
            holder.fontNameTextView.text = highlighted
        } else {
            val curr: CharSequence = holder.fontNameTextView.text
            if (curr is android.text.Spanned || !displayName.contentEquals(curr)) {
                holder.fontNameTextView.text = displayName
            }
        }

        var isLastOpened = preferenceManager.isLastOpenedFont(fontInfo.path)
        if (isSearchActive) isLastOpened = false
        holder.updateLastOpenedHighlight(isLastOpened)
    }

    fun setSortOptions(sortType: SortByItemLayout.SortType, ascending: Boolean) {
        this.currentSortType = sortType
        this.currentSortAscending = ascending

        val size = mSortedList.size()
        val snapshot: MutableList<FontFileInfo> = ArrayList(size)
        for (i in 0 until size) snapshot.add(mSortedList.get(i))

        mSortedList.replaceAll(snapshot)
        buildSections()

        recyclerView?.post {
            val rv = recyclerView
            if (rv != null && !rv.isComputingLayout) {
                notifyItemChanged(0)
            }
        }
    }

    fun updateSortOptionsOnly(sortType: SortByItemLayout.SortType, ascending: Boolean) {
        this.currentSortType = sortType
        this.currentSortAscending = ascending
        notifyItemChanged(0)
    }

    fun setAllFontsMetadata(metadata: List<FontFileInfoWithMetadata>?) {
        fontsMetadataMap.clear()
        if (metadata != null)
            for (item in metadata)
                fontsMetadataMap[item.path] = item
    }


    fun smartUpdate() {
        buildSections()
        val size = mSortedList.size()
        if (size > 0) {
            val rv = recyclerView
            if (rv != null) {
                rv.post {
                    val current = recyclerView
                    if (current != null && !current.isComputingLayout) {
                        notifyItemRangeChanged(1, size, PAYLOAD_UPDATE_LAST_OPENED)
                        notifyItemRangeChanged(1, size, PAYLOAD_UPDATE_HIGHLIGHT)
                    }
                }
            } else {
                notifyItemRangeChanged(1, size, PAYLOAD_UPDATE_LAST_OPENED)
                notifyItemRangeChanged(1, size, PAYLOAD_UPDATE_HIGHLIGHT)
            }
        } else {
            notifyDataSetChanged()
        }
    }


    private fun buildSections() {
        sections.clear()
        sectionPositions.clear()
        positionSections.clear()

        for (i in 0 until mSortedList.size()) {
            val name = mSortedList.get(i).displayName
            var letter = if (name != null && !name.isEmpty()) name.substring(0, 1).uppercase() else "#"
            if (!Character.isLetter(letter[0])) letter = "#"

            if (sections.isEmpty() || sections[sections.size - 1] != letter) {
                sections.add(letter)
                sectionPositions.add(i + 1)
            }
            positionSections.add(sections.size - 1)
        }
    }

    private fun getFontMetadataForPath(path: String): FontFileInfoWithMetadata? {
        return fontsMetadataMap[path]
    }


    override fun getItemCount(): Int { return mSortedList.size() + 2 }

    override fun getItemViewType(position: Int): Int {
        if (position == 0) return VIEW_TYPE_HEADER
        if (position == itemCount - 1) return VIEW_TYPE_SPACE
        return VIEW_TYPE_FONT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(context)

        if (viewType == VIEW_TYPE_HEADER) {
            return SortHeaderViewHolder(inf.inflate(R.layout.sort_header_item, parent, false))
        }

        if (viewType == VIEW_TYPE_SPACE) {
            return SpaceViewHolder(inf.inflate(R.layout.bottom_space_item, parent, false))
        }

        return LocalFontViewHolder(inf.inflate(R.layout.font_list_item, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is SortHeaderViewHolder) {
            holder.bind(currentSortType, currentSortAscending, sortChangeListener)
            holder.setSortEnabled(!isSelectionMode)
        } else if (holder is LocalFontViewHolder) {
            bindLocalFontViewHolder(holder, mSortedList.get(position - 1), position)
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder, position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isNotEmpty()) {
            val isSearchActive = !currentSearchQuery.isEmpty()
            // التمييز يُعاد فقط عند طلبه صراحةً عبر PAYLOAD_UPDATE_HIGHLIGHT،
            // حتى لا يُعاد ضبط النص مع كل تحديث للتحديد أو المفضلة
            if (payloads.contains(PAYLOAD_UPDATE_HIGHLIGHT) && holder is LocalFontViewHolder) {
                applyHighlight(holder, mSortedList.get(position - 1))
            }

            if (payloads.contains(PAYLOAD_UPDATE_FAVORITE) && holder is LocalFontViewHolder) {
                val fontInfo = mSortedList.get(position - 1)
                val provider = favoriteStatusProvider
                val isFavorited = provider != null && provider.isFavorited(fontInfo.path)
                holder.setFavoriteIndicator(isFavorited, true)
            }

            if (payloads.contains(PAYLOAD_UPDATE_SELECTION)) {
                if (holder is LocalFontViewHolder) {
                    val layout = holder.selectableLayout
                    if (layout != null) {
                        layout.isSelectionMode = isSelectionMode
                        layout.setSelectedAnimate(isItemSelected(position))
                    }
                } else if (holder is SortHeaderViewHolder) {
                    holder.setSortEnabled(!isSelectionMode)
                }
            }

            if (payloads.contains(PAYLOAD_UPDATE_LAST_OPENED) && holder is LocalFontViewHolder) {
                val fontInfo = mSortedList.get(position - 1)
                var isLastOpened = preferenceManager.isLastOpenedFont(fontInfo.path)

                if (isSearchActive) isLastOpened = false

                holder.updateLastOpenedHighlight(isLastOpened)
            }

        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    private fun bindLocalFontViewHolder(holder: LocalFontViewHolder, fontInfo: FontFileInfo, position: Int) {
        val fileName = fontInfo.name
        val path = fontInfo.path
        val displayName = fontInfo.displayName

        val metadata = getFontMetadataForPath(path)
        val realName = metadata?.realName

        val weightWidthLabel = metadata?.weightWidthLabel

        val isSearchActive = !currentSearchQuery.isEmpty()
        var isLastOpened = preferenceManager.isLastOpenedFont(path)

        if (isSearchActive) isLastOpened = false

        holder.bind(
            displayName, path, isSearchActive, currentSearchQuery,
            isLastOpened, highlighter, isSelectionMode, isItemSelected(position),
            weightWidthLabel
        )

        val provider = favoriteStatusProvider
        val isFavorited = provider != null && provider.isFavorited(path)
        holder.setFavoriteIndicator(isFavorited, false)

        if (mIsFontPreviewEnabled) loadFontPreview(holder, path)
        else holder.setDefaultTypeface(null)

        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            if (isSelectionMode) {
                selectionListener?.onToggleSelection(pos)
            } else {
                fontClickListener?.onFontClick(path, realName, fileName, 0, weightWidthLabel)
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

    private fun loadFontPreview(holder: LocalFontViewHolder, path: String) {
        val cache = LocalFontCache.getInstance()
        val cached = cache.getIfCached(path)

        if (cached != null) {
            holder.setTypeface(cached)
        } else {
            holder.setDefaultTypeface(null)
            val exec = executor
            if (exec != null && !exec.isShutdown) {
                exec.execute {
                    val loaded: Typeface? = cache.getTypeface(path)
                    if (loaded != null) {
                        mainHandler.post {
                            if (!SettingsRepository.isFontPreviewEnabled(context)) return@post
                            if (path == holder.tag) holder.setTypeface(loaded)
                        }
                    }
                }
            }
        }
    }

    override fun getItemId(position: Int): Long {
        if (position == 0) return "HEADER".hashCode().toLong()
        if (position == itemCount - 1) return "SPACE".hashCode().toLong()
        return mSortedList.get(position - 1).path.hashCode().toLong()
    }


    override fun getSections(): Array<Any> {
        return Array<Any>(sections.size) { sections[it] }
    }

    override fun getPositionForSection(sectionIndex: Int): Int {
        if (sectionIndex < 0 || sectionIndex >= sectionPositions.size) return 0
        return sectionPositions[sectionIndex]
    }

    override fun getSectionForPosition(position: Int): Int {
        if (position <= 0) return 0
        if (position >= itemCount - 1)
            return if (positionSections.isEmpty()) 0 else positionSections[positionSections.size - 1]
        val adj = position - 1
        if (adj < 0 || adj >= positionSections.size) return 0
        return positionSections[adj]
    }

    companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_FONT = 1
        const val VIEW_TYPE_SPACE = 2

        private const val PAYLOAD_UPDATE_HIGHLIGHT = "UPDATE_HIGHLIGHT"

        private const val PAYLOAD_UPDATE_FAVORITE = "UPDATE_FAVORITE"

        private const val PAYLOAD_UPDATE_SELECTION = "UPDATE_SELECTION"

        private const val PAYLOAD_UPDATE_LAST_OPENED = "UPDATE_LAST_OPENED"
    }
}
