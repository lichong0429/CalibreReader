package com.lichong.calibrereader.data.opds

import com.lichong.calibrereader.data.db.BookEntity
import com.lichong.calibrereader.ui.browse.BrowseUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookGroupsTest {

    private fun book(
        id: String,
        tags: List<String> = emptyList(),
        authors: List<String> = emptyList(),
        publisher: String? = null,
        language: String? = null,
        summary: String? = null
    ) = OpdsEntry(
        id = id,
        title = id,
        authors = authors,
        publisher = publisher,
        language = language,
        categories = tags,
        summary = summary,
        links = listOf(
            OpdsLink(
                rel = OpdsRel.ACQUISITION,
                href = "http://example.test/$id.epub",
                type = "application/epub+zip"
            )
        )
    )

    private val library = listOf(
        book("b1", tags = listOf("MOF"), authors = listOf("Zhang"), publisher = "RSC", language = "eng"),
        book("b2", tags = listOf("MOF"), authors = listOf("Li"), publisher = "RSC", language = "eng"),
        book("b3", tags = listOf("膜分离"), authors = listOf("Wang"), publisher = "Elsevier", language = "chi"),
        book("b4", authors = listOf("Chen"), summary = "SERIES: MOF 丛书 [2.00]<br/>TAGS: 未打标")
    )

    @Test
    fun `默认按标签分组，最接近文件夹`() {
        assertEquals(GroupDimension.TAG, BookGroups.defaultDimension(library))
        assertTrue(BookFormat.PDF.isReadableInApp)
        assertTrue(BookFormat.EPUB.readPriority < BookFormat.PDF.readPriority)
    }

    @Test
    fun `标签分组把未分类沉到最后`() {
        val sections = BookGroups.sections(library, GroupDimension.TAG)
        assertEquals(listOf("MOF", "膜分离", "未分类"), sections.map { it.name })
        assertEquals(listOf("b1", "b2"), sections[0].books.map { it.id })
        assertEquals(1, sections[2].books.size)
    }

    @Test
    fun `Calibre-Web 写在简介里的系列也能识别`() {
        assertEquals("MOF 丛书", BookGroups.seriesOf(library[3]))
        // DOM 的 textContent 会吃掉 <br/>，真实数据里这几段是拼在一起的
        val concatenated = book(
            "b5",
            summary = "RATING: ★★★TAGS: MOFSERIES: MOF 丛书 [1.00]2026 年出版"
        )
        assertEquals("MOF 丛书", BookGroups.seriesOf(concatenated))
        val sections = BookGroups.sections(library, GroupDimension.SERIES)
        assertTrue(sections.any { it.name == "MOF 丛书" })
    }

    @Test
    fun `作者与出版社维度可用，无法分组的维度不给用户选`() {
        val available = BookGroups.available(library)
        assertTrue(GroupDimension.AUTHOR in available)
        assertTrue(GroupDimension.PUBLISHER in available)
        assertTrue(GroupDimension.LANGUAGE in available)
        assertTrue(GroupDimension.SERIES in available)
        assertFalse("只有一种元数据时不该给这个维度", GroupDimension.PUBLISHER in
            BookGroups.available(listOf(book("x", publisher = "RSC"), book("y", publisher = "RSC"))))
    }

    @Test
    fun `选中某个分组后只展示该组书籍`() {
        val state = BrowseUiState(
            entries = library,
            groupBy = GroupDimension.TAG,
            selectedGroup = "MOF"
        )
        assertEquals(1, state.groupSections.size)
        assertEquals(2, state.groupSections[0].books.size)
        assertEquals(3, state.allGroupSections.size)
        assertEquals(4, state.bookEntries.size)
    }

    @Test
    fun `PDF 书籍走内置阅读器而不是外部应用`() {
        val entity = BookEntity(
            id = "s:1", serverId = "s", serverName = "test",
            title = "paper", filePath = "/tmp/p.pdf", format = "PDF"
        )
        assertTrue(entity.isPdf)
        assertTrue(entity.isReadableInApp)
        assertFalse(
            BookEntity(
                id = "s:2", serverId = "s", serverName = "test",
                title = "other", filePath = "/tmp/x.mobi", format = "MOBI"
            ).isReadableInApp
        )
    }

    @Test
    fun `分组不改动原始 OPDS 数据`() {
        val before = library.map { it.title to it.categories }
        BookGroups.sections(library, GroupDimension.TAG)
        BookGroups.sections(library, GroupDimension.AUTHOR)
        assertEquals(before, library.map { it.title to it.categories })
    }
}
