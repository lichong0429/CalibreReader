package com.lichong.calibrereader.data.opds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「打开即见书」的直达逻辑：根 feed 只是一串分类时，挑一个书列表入口。
 * 关键是不能把作者/系列/标签这类二级导航误判成书列表。
 */
class CatalogShortcutTest {

    private fun nav(vararg titles: String) = OpdsFeed(
        entries = titles.map { title ->
            OpdsEntry(
                id = title,
                title = title,
                links = listOf(
                    OpdsLink(
                        rel = OpdsRel.SUBSECTION,
                        href = "https://calibre.local/opds/${title.hashCode()}"
                    )
                )
            )
        }
    )

    private fun bookFeed() = OpdsFeed(
        entries = listOf(
            OpdsEntry(
                id = "book-1",
                title = "Sample Book",
                links = listOf(
                    OpdsLink(
                        rel = OpdsRel.ACQUISITION,
                        href = "https://calibre.local/opds/book/1.epub",
                        type = BookFormat.EPUB.mime
                    )
                )
            )
        )
    )

    @Test
    fun calibreServerRootPicksByTitle() {
        val feed = nav("By Author", "By Series", "By Title", "By Newest")
        assertEquals("By Title", pickAllBooksShortcut(feed)?.title)
    }

    @Test
    fun calibreWebRootPicksAllBooksOverHotBooks() {
        val feed = nav("Hot Books", "All Books", "Recently Added", "By Author")
        assertEquals("All Books", pickAllBooksShortcut(feed)?.title)
    }

    @Test
    fun chineseRootPicksAllBooks() {
        val feed = nav("按作者", "按系列", "全部书籍", "最新添加")
        assertEquals("全部书籍", pickAllBooksShortcut(feed)?.title)
    }

    @Test
    fun newestIsUsedWhenNoTitleOrAllBooksEntry() {
        val feed = nav("By Author", "By Newest", "By Rating")
        assertEquals("By Newest", pickAllBooksShortcut(feed)?.title)
    }

    @Test
    fun pureCategoryNavigationStaysOnCatalogPage() {
        val feed = nav("Authors", "Series", "Categories", "Publishers", "Tags")
        assertNull(pickAllBooksShortcut(feed))
    }

    @Test
    fun acquisitionRootNeedsNoShortcut() {
        assertNull(pickAllBooksShortcut(bookFeed()))
    }

    @Test
    fun hrefComesFromOpdsSubsectionLink() {
        val feed = nav("By Title")
        assertEquals(
            "https://calibre.local/opds/${"By Title".hashCode()}",
            pickAllBooksShortcut(feed)?.href
        )
    }
}
