package com.lichong.calibrereader.data.opds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpdsLabelsTest {

    /** Calibre-Web cps/templates/index.xml 的全部 15 个根导航标题 */
    private val webTitles = listOf(
        "Alphabetical Books", "Hot Books", "Top Rated Books", "Recently added Books",
        "Random Books", "Read Books", "Unread Books", "Authors", "Publishers",
        "Categories", "Series", "Languages", "Ratings", "File formats", "Shelves"
    )

    /** 同一模板的全部 15 条描述 */
    private val webDescriptions = listOf(
        "Books sorted alphabetically",
        "Popular publications from this catalog based on Downloads.",
        "Popular publications from this catalog based on Rating.",
        "The latest Books", "Show Random Books", "Read Books", "Unread Books",
        "Books ordered by Author", "Books ordered by publisher",
        "Books ordered by category", "Books ordered by series",
        "Books ordered by Languages", "Books ordered by Rating",
        "Books ordered by file formats", "Books organized in shelves"
    )

    private fun hasChinese(s: String) = s.any { it.code > 0x7F }

    @Test
    fun `Calibre-Web 根导航标题全部翻译为中文`() {
        webTitles.forEach { raw ->
            val out = OpdsLabels.title(raw)
            assertTrue("$raw -> $out", out != raw && hasChinese(out))
        }
        assertEquals("最近添加", OpdsLabels.title("Recently added Books"))
        assertEquals("按分类浏览", OpdsLabels.title("Categories"))
        assertEquals("按格式浏览", OpdsLabels.title("File formats"))
    }

    @Test
    fun `Calibre-Web 分类描述全部翻译为中文`() {
        webDescriptions.forEach { raw ->
            val out = OpdsLabels.description(raw)
            assertTrue("$raw -> $out", out != raw && hasChinese(out))
        }
        assertEquals(
            "按下载量排序的热门书籍",
            OpdsLabels.description("Popular publications from this catalog based on Downloads.")
        )
        assertEquals("已读过的书籍", OpdsLabels.description("Read Books"))
        assertEquals("全部书籍", OpdsLabels.description("All books"))
    }

    @Test
    fun `二级字母索引与 calibre-server 导航也有中文`() {
        assertEquals("全部", OpdsLabels.title("All"))
        assertEquals("全部书籍", OpdsLabels.title("All Books"))
        assertEquals("最新添加", OpdsLabels.title("By Newest"))
        assertEquals("按书名", OpdsLabels.title("By Title"))
        assertEquals("按作者", OpdsLabels.title("By Author"))
        assertEquals("按标签", OpdsLabels.title("By Tag"))
        assertEquals("书库", OpdsLabels.title("Calibre Library"))
    }

    @Test
    fun `忽略大小写多空格与句末标点`() {
        assertEquals("最近添加", OpdsLabels.title("  RECENTLY   added Books  "))
        assertEquals(
            "按评分排序的热门书籍",
            OpdsLabels.description("popular publications FROM this catalog based on RATING!!!")
        )
    }

    @Test
    fun `未收录文案书籍标题与自定义分类保持原样`() {
        assertEquals("Zeolitic Imidazolate Frameworks", OpdsLabels.title("Zeolitic Imidazolate Frameworks"))
        assertEquals("MOF 膜分离", OpdsLabels.title("MOF 膜分离"))
        assertEquals("我的自定义分类", OpdsLabels.title("我的自定义分类"))
        assertEquals("", OpdsLabels.title(""))
        assertEquals("A book about MOF membranes.", OpdsLabels.description("A book about MOF membranes."))
    }

    @Test
    fun `翻译只在展示层不改数据也不影响自动直达判定`() {
        val feed = OpdsFeed(
            entries = listOf(
                OpdsEntry(
                    id = "1",
                    title = "Recently added Books",
                    links = listOf(OpdsLink(rel = OpdsRel.SUBSECTION, href = "https://x/opds/new"))
                ),
                OpdsEntry(
                    id = "2",
                    title = "By Author",
                    links = listOf(OpdsLink(rel = OpdsRel.SUBSECTION, href = "https://x/opds/author"))
                )
            )
        )
        // 判定逻辑拿到的仍是服务端英文原文
        assertEquals("Recently added Books", pickAllBooksShortcut(feed)?.title)
        assertEquals("Recently added Books", feed.entries[0].title)
        // 展示时才转成中文
        assertEquals("最近添加", OpdsLabels.title(feed.entries[0].title))
    }

    @Test
    fun `服务端描述文案变体与搜索结果标题也翻译`() {
        // 部分 Calibre-Web 版本省略 "from this catalog"
        assertEquals(
            "按下载量排序的热门书籍",
            OpdsLabels.description("Popular publications based on Downloads.")
        )
        assertEquals(
            "按评分排序的热门书籍",
            OpdsLabels.description("Popular publications based on Rating.")
        )
        assertEquals("搜索结果", OpdsLabels.title("Search Results"))
    }
}
