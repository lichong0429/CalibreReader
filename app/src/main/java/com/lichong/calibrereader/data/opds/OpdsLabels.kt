package com.lichong.calibrereader.data.opds

/**
 * OPDS 导航层文案的中文映射（Calibre-Web `cps/templates/index.xml` 的 30 条官方文案
 * + calibre-server 常见分类 + 字母索引页）。
 *
 * 只在 UI 展示时调用：解析出的 `OpdsEntry.title` 必须保留服务端原文，
 * `ServerProbe.detectKind()` 与 `pickAllBooksShortcut()` 都靠英文标题做判断。
 * 书籍标题、作者、简介、用户自定义分类一律不翻译；未收录的文案原样返回。
 */
object OpdsLabels {

    private val TITLES = mapOf(
        // ---- Calibre-Web 根导航（官方 index.xml） ----
        "alphabetical books" to "按书名浏览",
        "hot books" to "热门书籍",
        "top rated books" to "高分书籍",
        "recently added books" to "最近添加",
        "random books" to "随机推荐",
        "read books" to "已读书籍",
        "unread books" to "未读书籍",
        "authors" to "按作者浏览",
        "publishers" to "按出版社浏览",
        "categories" to "按分类浏览",
        "series" to "按系列浏览",
        "languages" to "按语言浏览",
        "ratings" to "按评分浏览",
        "file formats" to "按格式浏览",
        "shelves" to "书架",
        "archived books" to "已归档",
        "discover" to "发现",
        "start" to "首页",
        "search" to "搜索",
        "search results" to "搜索结果",

        // ---- 字母索引 / 全量入口 ----
        "all" to "全部",
        "all books" to "全部书籍",
        "books" to "全部书籍",

        // ---- calibre-server 常见导航 ----
        "by newest" to "最新添加",
        "by title" to "按书名",
        "by author" to "按作者",
        "by series" to "按系列",
        "by publisher" to "按出版社",
        "by rating" to "按评分",
        "by tag" to "按标签",
        "by language" to "按语言",
        "by date" to "按日期",
        "newest" to "最新添加",
        "title" to "书名",
        "author" to "作者",
        "tags" to "标签",
        "formats" to "格式",
        "calibre library" to "书库"
    )

    private val DESCRIPTIONS = mapOf(
        "books sorted alphabetically" to "按书名字母顺序排列",
        "popular publications from this catalog based on downloads" to "按下载量排序的热门书籍",
        "popular publications from this catalog based on rating" to "按评分排序的热门书籍",
        // 部分 Calibre-Web 版本/语言包会省略 "from this catalog"
        "popular publications based on downloads" to "按下载量排序的热门书籍",
        "popular publications based on rating" to "按评分排序的热门书籍",
        "the latest books" to "最近添加的书籍",
        "show random books" to "随机抽取若干书籍",
        "read books" to "已读过的书籍",
        "unread books" to "尚未读过的书籍",
        "books ordered by author" to "按作者分组浏览",
        "books ordered by publisher" to "按出版社分组浏览",
        "books ordered by category" to "按分类分组浏览",
        "books ordered by series" to "按系列分组浏览",
        "books ordered by languages" to "按语言分组浏览",
        "books ordered by rating" to "按评分分组浏览",
        "books ordered by file formats" to "按文件格式分组浏览",
        "books organized in shelves" to "按书架整理的书籍",
        "all books" to "全部书籍"
    )

    fun title(raw: String): String = TITLES[key(raw)] ?: raw

    fun description(raw: String): String = DESCRIPTIONS[key(raw)] ?: raw

    /** 归一化：忽略大小写、多余空白与句末标点，容忍服务端细微差异。 */
    private fun key(raw: String): String = raw.trim()
        .replace(Regex("\\s+"), " ")
        .lowercase()
        .trimEnd('.', '。', '!', '！')
        .trim()
}
