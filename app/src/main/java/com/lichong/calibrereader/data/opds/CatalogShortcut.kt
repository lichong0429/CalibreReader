package com.lichong.calibrereader.data.opds

/** 从 OPDS 根导航里挑出的「书列表」直达入口。 */
data class CatalogShortcut(val href: String, val title: String)

/**
 * OPDS 根目录常常只是一串分类（By Author / By Series / ...），手机网页上多点一层还行，
 * App 里不该让用户每次都多点一次才看到书。
 *
 * 按标题保守挑选「全部书籍」类入口：命中不了就留在分类页由用户自己点，
 * 绝不把作者/系列/标签这类二级导航误当成书列表。
 * href 一律取 OPDS entry 的链接（rel="subsection" 或 Calibre-Web 那种不带 rel 的
 * `type="application/atom+xml;profile=opds-catalog"`），不硬编码服务端路由。
 */
fun pickAllBooksShortcut(feed: OpdsFeed): CatalogShortcut? {
    if (feed.kind != FeedKind.NAVIGATION) return null

    val blocked = listOf(
        "author", "作者", "series", "系列", "categor", "分类", "tag", "标签",
        "publisher", "出版", "language", "语言", "rating", "评分", "format", "格式",
        "shelf", "书架", "random", "随机", "unread", "未读"
    )
    // Calibre-Web 根 feed 的「Alphabetical Books」就是全量书库，优先于「Recently added」。
    // 顺序兼顾两类服务端：
    // · calibre-server 的「By Title」是全部书籍 → 优先于「By Newest」
    // · Calibre-Web 的「Alphabetical Books」其实是 A-Z 字母索引（还要再点一层），
    //   而「Recently added / Hot Books」直接返回书籍 feed → 排到字母索引前面
    val preferred = listOf(
        "all books", "全部书籍", "所有书籍", "全部图书", "所有图书", "全部书",
        "by title", "按标题", "title", "书名",
        "recently added", "recently", "最新添加",
        "newest", "latest", "最新", "最近", "新书",
        "hot books", "热门", "books", "书籍", "图书",
        "alphabetical", "字母", "new"
    )
    val candidates = feed.entries.filter { entry ->
        val t = entry.title.lowercase()
        entry.navigationHref != null && blocked.none { t.contains(it) }
    }
    for (keyword in preferred) {
        val hit = candidates.firstOrNull { it.title.lowercase().contains(keyword) }
        if (hit != null) {
            val href = hit.navigationHref ?: continue
            return CatalogShortcut(href, hit.title)
        }
    }
    return null
}
