package com.lichong.calibrereader.data.opds

/** OPDS 1.2 / Atom 相关常量与数据模型 */

object OpdsRel {
    const val ACQUISITION = "http://opds-spec.org/acquisition"
    const val ACQUISITION_OPEN = "http://opds-spec.org/acquisition/open-access"
    const val ACQUISITION_BUY = "http://opds-spec.org/acquisition/buy"
    const val ACQUISITION_BORROW = "http://opds-spec.org/acquisition/borrow"
    const val ACQUISITION_SAMPLE = "http://opds-spec.org/acquisition/sample"
    const val IMAGE = "http://opds-spec.org/image"
    const val THUMBNAIL = "http://opds-spec.org/image/thumbnail"
    const val FACET = "http://opds-spec.org/facet"
    const val SUBSECTION = "subsection"
    const val SEARCH = "search"
    const val SORT_NEW = "http://opds-spec.org/sort/new"
    const val SORT_POPULAR = "http://opds-spec.org/sort/popular"
}

enum class BookFormat(val mime: String, val label: String, val extension: String) {
    EPUB("application/epub+zip", "EPUB", "epub"),
    PDF("application/pdf", "PDF", "pdf"),
    MOBI("application/x-mobipocket-ebook", "MOBI", "mobi"),
    AZW3("application/x-mobi8-ebook", "AZW3", "azw3"),
    FB2("application/fb2+zip", "FB2", "fb2"),
    CBZ("application/vnd.comicbook+zip", "CBZ", "cbz"),
    CBR("application/vnd.comicbook-rar", "CBR", "cbr"),
    DJVU("image/vnd.djvu", "DjVu", "djvu"),
    TXT("text/plain", "TXT", "txt"),
    HTML("text/html", "HTML", "html"),
    RTF("application/rtf", "RTF", "rtf"),
    KEPUB("application/kepub+zip", "KEPUB", "kepub.epub"),
    OTHER("", "文件", "");

    val isReadableInApp: Boolean
        get() = this == EPUB || this == KEPUB

    companion object {
        fun fromMime(mime: String?): BookFormat {
            if (mime.isNullOrBlank()) return OTHER
            val m = mime.substringBefore(';').trim().lowercase()
            return entries.firstOrNull { it.mime.isNotEmpty() && it.mime.lowercase() == m } ?: OTHER
        }

        fun fromUrl(url: String): BookFormat {
            val path = url.substringBefore('?').lowercase()
            return when {
                path.endsWith(".epub") -> EPUB
                path.endsWith(".pdf") -> PDF
                path.endsWith(".mobi") -> MOBI
                path.endsWith(".azw3") -> AZW3
                path.endsWith(".fb2") -> FB2
                path.endsWith(".cbz") -> CBZ
                path.endsWith(".txt") -> TXT
                else -> OTHER
            }
        }
    }
}

data class OpdsLink(
    val rel: String,
    val href: String,
    val type: String? = null,
    val title: String? = null,
    val length: Long? = null
) {
    val format: BookFormat get() = BookFormat.fromMime(type).takeIf { it != BookFormat.OTHER }
        ?: BookFormat.fromUrl(href)
}

data class OpdsEntry(
    val id: String,
    val title: String,
    val authors: List<String> = emptyList(),
    val summary: String? = null,
    val language: String? = null,
    val publisher: String? = null,
    val issued: String? = null,
    val updated: String? = null,
    val categories: List<String> = emptyList(),
    val links: List<OpdsLink> = emptyList(),
    /** 服务端原始 identifier（Calibre 常为 calibre:1234），用于进度同步 */
    val identifier: String? = null
) {
    val acquisitionLinks: List<OpdsLink>
        get() = links.filter {
            it.rel == OpdsRel.ACQUISITION ||
                it.rel.startsWith(OpdsRel.ACQUISITION + "/")
        }

    val navLinks: List<OpdsLink>
        get() = links.filter { it.rel == OpdsRel.SUBSECTION }

    val imageLink: OpdsLink?
        get() = links.firstOrNull { it.rel == OpdsRel.IMAGE }

    val thumbnailLink: OpdsLink?
        get() = links.firstOrNull { it.rel == OpdsRel.THUMBNAIL }

    /** 列表用封面：优先缩略图，节省流量 */
    val coverUrl: String?
        get() = (thumbnailLink ?: imageLink)?.href

    val largeCoverUrl: String?
        get() = (imageLink ?: thumbnailLink)?.href

    val isNavigation: Boolean
        get() = acquisitionLinks.isEmpty() && navLinks.isNotEmpty()

    val isBook: Boolean
        get() = acquisitionLinks.isNotEmpty()

    /**
     * Calibre-Web 的书籍 uuid。其 OPDS entry id 形如 `urn:uuid:1a2b3c4d-...`，
     * 正是 Kobo 阅读进度接口 `/kobo/<token>/v1/library/<uuid>/state` 需要的 id。
     */
    val koboUuid: String?
        get() {
            UUID_RE.find(id)?.let { return it.groupValues[1].lowercase() }
            UUID_RE.find(identifier.orEmpty())?.let { return it.groupValues[1].lowercase() }
            return null
        }

    /** Calibre 数字 id，如 calibre:42 / .../download/42/epub/x.epub -> 42 */
    val calibreId: String?
        get() {
            val candidates = buildList {
                identifier?.let { add(it) }
                add(id)
                acquisitionLinks.firstOrNull()?.href?.let { add(it) }
                navLinks.firstOrNull()?.href?.let { add(it) }
                links.firstOrNull()?.href?.let { add(it) }
            }
            val patterns = listOf(
                Regex("""(?:book[/:=]|get/[a-z0-9]+/|download/)(\d+)""", RegexOption.IGNORE_CASE),
                Regex("""calibre[:/](\d+)""", RegexOption.IGNORE_CASE)
            )
            for (c in candidates) {
                if (c.isBlank()) continue
                for (p in patterns) p.find(c)?.let { return it.groupValues[1] }
                val trimmed = c.trim()
                if (Regex("""^\d+$""").matches(trimmed)) return trimmed
            }
            return null
        }

    val authorLine: String
        get() = if (authors.isEmpty()) "" else authors.joinToString(" / ")

    companion object {
        private val UUID_RE = Regex(
            """([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})"""
        )
    }
}

data class OpdsFacet(
    val title: String,
    val href: String,
    val active: Boolean = false,
    val count: Int? = null
)

data class OpdsFacetGroup(
    val name: String,
    val facets: List<OpdsFacet>
)

enum class FeedKind { NAVIGATION, ACQUISITION, MIXED }

data class OpdsFeed(
    val title: String? = null,
    val id: String? = null,
    val selfUrl: String? = null,
    val startUrl: String? = null,
    val upUrl: String? = null,
    val nextUrl: String? = null,
    val prevUrl: String? = null,
    /** OpenSearch 模板，含 {searchTerms} 占位符 */
    val searchTemplate: String? = null,
    val entries: List<OpdsEntry> = emptyList(),
    val facets: List<OpdsFacetGroup> = emptyList()
) {
    val kind: FeedKind
        get() {
            if (entries.isEmpty()) return FeedKind.NAVIGATION
            val books = entries.count { it.isBook }
            return when {
                books == 0 -> FeedKind.NAVIGATION
                books == entries.size -> FeedKind.ACQUISITION
                else -> FeedKind.MIXED
            }
        }

    val isPaginated: Boolean get() = nextUrl != null
}
