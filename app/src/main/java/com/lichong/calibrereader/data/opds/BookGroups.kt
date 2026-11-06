package com.lichong.calibrereader.data.opds

/**
 * 把 OPDS 书籍 feed 按元数据「大概分个类」。
 *
 * Calibre 的数据模型里没有磁盘文件夹字段，OPDS 也不返回路径；最接近「文件夹」的是
 * 标签（<category>）与服务端自己的书架/分类目录。因此这里提供多个维度，由用户在
 * 书库页切换，另外保留入口直达服务端分类树（书架、作者、系列、类别）。
 *
 * 纯展示层计算：不修改 OpdsEntry，也不影响自动直达与点击链。
 */
enum class GroupDimension(val label: String) {
    NONE("不分组"),
    TAG("按标签"),
    AUTHOR("按作者"),
    SERIES("按系列"),
    PUBLISHER("按出版社"),
    LANGUAGE("按语言");

    companion object {
        fun fromName(name: String): GroupDimension =
            entries.firstOrNull { it.name == name } ?: NONE
    }
}

data class BookSection(val name: String, val books: List<OpdsEntry>)

object BookGroups {
    const val UNKNOWN = "未分类"

    private val SERIES_MARK = Regex("""SERIES\s*:""", RegexOption.IGNORE_CASE)
    private val OTHER_MARK = Regex("""\b(?:TAGS|RATING|AUTHOR|PUBLISHER|LANGUAGE)\s*:""", RegexOption.IGNORE_CASE)
    private val SPACES = Regex("""\s+""")

    /**
     * Calibre-Web 把系列写在 content 里：`SERIES: Foo [1.00]<br/>TAGS: ...`。
     * DOM 的 textContent 会把 <br/> 吃掉，变成 `...SERIES: Foo [1.00]TAGS: ...`，
     * 所以不能按行找，只能定位标记后截到序号方括号或下一个元数据标记。
     */
    fun seriesOf(entry: OpdsEntry): String? {
        val raw = entry.summary ?: return null
        val mark = SERIES_MARK.find(raw) ?: return null
        val tail = raw.substring(mark.range.last + 1)
        val ends = buildList {
            tail.indexOf('[').takeIf { it >= 0 }?.let { add(it) }
            OTHER_MARK.find(tail)?.range?.first?.let { add(it) }
            tail.indexOf('\n').takeIf { it >= 0 }?.let { add(it) }
        }
        return tail.substring(0, ends.minOrNull() ?: tail.length)
            .replace(SPACES, " ")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private fun clean(raw: String?): String = raw
        ?.replace(SPACES, " ")
        ?.trim()
        ?.take(48)
        ?.takeIf { it.isNotBlank() }
        ?: UNKNOWN

    fun keyOf(entry: OpdsEntry, dimension: GroupDimension): String = when (dimension) {
        GroupDimension.NONE -> "全部"
        GroupDimension.TAG -> clean(entry.categories.firstOrNull())
        GroupDimension.AUTHOR -> clean(entry.authors.firstOrNull())
        GroupDimension.SERIES -> clean(seriesOf(entry))
        GroupDimension.PUBLISHER -> clean(entry.publisher)
        GroupDimension.LANGUAGE -> clean(entry.language)
    }

    fun sections(books: List<OpdsEntry>, dimension: GroupDimension): List<BookSection> {
        if (books.isEmpty()) return emptyList()
        return books.groupBy { keyOf(it, dimension) }
            .map { (name, list) -> BookSection(name, list) }
            // 有名字的组在前，未分类永远沉到最后
            .sortedWith(compareBy({ it.name == UNKNOWN }, { it.name }))
    }

    /** 只有能真正把书分成至少两堆的维度才给用户选 */
    fun available(books: List<OpdsEntry>): List<GroupDimension> = buildList {
        add(GroupDimension.NONE)
        GroupDimension.entries.filter { it != GroupDimension.NONE }.forEach { dim ->
            val sections = sections(books, dim)
            if (sections.size >= 2 && sections.any { it.name != UNKNOWN }) add(dim)
        }
    }

    /** 打开书库时的默认维度：标签最接近「文件夹」，其次作者/系列 */
    fun defaultDimension(books: List<OpdsEntry>): GroupDimension {
        val usable = available(books).filter { it != GroupDimension.NONE }
        return listOf(
            GroupDimension.TAG,
            GroupDimension.AUTHOR,
            GroupDimension.SERIES,
            GroupDimension.PUBLISHER,
            GroupDimension.LANGUAGE
        ).firstOrNull { it in usable } ?: GroupDimension.NONE
    }
}
