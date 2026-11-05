package com.lichong.calibrereader.data.opds

/** 从 OPDS 根导航里挑出的「书列表」直达入口。 */
data class CatalogShortcut(val href: String, val title: String)

/**
 * OPDS 根目录常常只是一串分类（By Author / By Series / ...），手机网页上多点一层还行，
 * App 里不该让用户每次都多点一次才看到书。
 *
 * 按标题保守挑选「全部书籍」类入口：命中不了就留在分类页由用户自己点，
 * 绝不把作者/系列/标签这类二级导航误当成书列表。
 * href 一律取 OPDS 的 <link rel="subsection">，不硬编码服务端路由。
 */
fun pickAllBooksShortcut(feed: OpdsFeed): CatalogShortcut? {
    if (feed.kind != FeedKind.NAVIGATION) return null

    val blocked = listOf(
        "author", "作者", "series", "系列", "categor", "分类", "tag", "标签",
        "publisher", "出版", "language", "语言", "rating", "评分", "format", "格式"
    )
    val preferred = listOf(
        "all books", "全部书籍", "所有书籍", "全部图书", "所有图书", "全部书",
        "by title", "按标题", "title", "书名",
        "newest", "recently", "latest", "最新", "最近", "新书",
        "books", "书籍", "图书", "new"
    )
    val candidates = feed.entries.filter { entry ->
        val t = entry.title.lowercase()
        entry.navLinks.isNotEmpty() && blocked.none { t.contains(it) }
    }
    for (keyword in preferred) {
        val hit = candidates.firstOrNull { it.title.lowercase().contains(keyword) }
        if (hit != null) return CatalogShortcut(hit.navLinks.first().href, hit.title)
    }
    return null
}
