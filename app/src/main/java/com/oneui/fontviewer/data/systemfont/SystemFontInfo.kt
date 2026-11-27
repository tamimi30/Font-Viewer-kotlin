package com.oneui.fontviewer.data.systemfont

class SystemFontInfo(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val weight: Int,
    slant: Int,
    val ttcIndex: Int,
    private val axes: String?
) {

    var realName: String? = null

    var weightWidthLabel: String? = null

    val isVariableFont: Boolean
        get() = axes != null && axes.isNotEmpty()
}
