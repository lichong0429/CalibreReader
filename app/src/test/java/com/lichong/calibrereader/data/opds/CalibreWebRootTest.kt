package com.lichong.calibrereader.data.opds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * 回归用例：Calibre-Web `cps/templates/index.xml` 的根导航 entry 只有
 * `<link href="..." type="application/atom+xml;profile=opds-catalog"/>`，**没有 rel**。
 * 旧实现只认 rel="subsection"，导致整个分类页点不开（提示「该条目没有可打开的内容」）。
 */
class CalibreWebRootTest {

    /** 按官方模板逐条复刻，标题与截图一致 */
    private val rootFeed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <icon>/static/favicon.ico</icon>
          <id>urn:uuid:2853dacf-ed79-42f5-8e8a-a7bb3d1ae6a2</id>
          <updated>2026-11-06T03:00:00+00:00</updated>
          <link rel="self" href="/opds" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
          <link rel="start" title="Start" href="/opds" type="application/atom+xml;profile=opds-catalog;kind=navigation"/>
          <link rel="search" href="/opds/osd" type="application/opensearchdescription+xml"/>
          <link type="application/atom+xml" rel="search" title="Search" href="/opds/search/{searchTerms}" />
          <title>Calibre-Web</title>
          <author><name>Calibre-Web</name></author>
          <entry>
            <title>Alphabetical Books</title>
            <link href="/opds/booksindex" type="application/atom+xml;profile=opds-catalog"/>
            <id>/opds/booksindex</id>
            <content type="text">Books sorted alphabetically</content>
          </entry>
          <entry>
            <title>Hot Books</title>
            <link href="/opds/hot" type="application/atom+xml;profile=opds-catalog"/>
            <id>/opds/hot</id>
            <content type="text">Popular publications from this catalog based on Downloads.</content>
          </entry>
          <entry>
            <title>Top Rated Books</title>
            <link href="/opds/best_rated" type="application/atom+xml;profile=opds-catalog"/>
            <id>/opds/best_rated</id>
            <content type="text">Popular publications from this catalog based on Rating.</content>
          </entry>
          <entry>
            <title>Recently added Books</title>
            <link href="/opds/new" type="application/atom+xml;profile=opds-catalog"/>
            <id>/opds/new</id>
            <content type="text">The latest Books</content>
          </entry>
          <entry>
            <title>Random Books</title>
            <link href="/opds/discover" type="application/atom+xml;profile=opds-catalog"/>
            <id>/opds/discover</id>
            <content type="text">Show Random Books</content>
          </entry>
          <entry>
            <title>Read Books</title>
            <link href="/opds/read_books" type="application/atom+xml;profile=opds-catalog"/>
            <id>/opds/read_books</id>
            <content type="text">Read Books</content>
          </entry>
          <entry>
            <title>Unread Books</title>
            <link href="/opds/unread_books" type="application/atom+xml;profile=opds-catalog"/>
            <id>/opds/unread_books</id>
            <content type="text">Unread Books</content>
          </entry>
          <entry>
            <title>Authors</title>
            <link href="/opds/authorindex" type="application/atom+xml;profile=opds-catalog"/>
            <id>/opds/authorindex</id>
            <content type="text">Books ordered by Author</content>
          </entry>
          <entry>
            <title>Categories</title>
            <link href="/opds/categoryindex" type="application/atom+xml;profile=opds-catalog"/>
            <id>/opds/categoryindex</id>
            <content type="text">Books ordered by category</content>
          </entry>
          <entry>
            <title>Series</title>
            <link href="/opds/seriesindex" type="application/atom+xml;profile=opds-catalog"/>
            <id>/opds/seriesindex</id>
            <content type="text">Books ordered by series</content>
          </entry>
        </feed>
    """.trimIndent()

    /** Calibre-Web `cps/templates/feed.xml` 的书籍 entry：acquisition rel 是齐全的 */
    private val bookFeed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <title>Alphabetical Books</title>
          <link rel="self" href="/opds/booksindex" type="application/atom+xml;profile=opds-catalog"/>
          <link rel="next" href="/opds/booksindex?offset=30" type="application/atom+xml"/>
          <entry>
            <title>Zeolitic Imidazolate Frameworks</title>
            <id>urn:uuid:1a2b3c4d-5e6f-7a8b-9c0d-e1f2a3b4c5d6</id>
            <link type="image/jpeg" href="/opds/cover/1" rel="http://opds-spec.org/image"/>
            <link type="image/jpeg" href="/opds/cover/1" rel="http://opds-spec.org/image/thumbnail"/>
            <link rel="http://opds-spec.org/acquisition" href="/opds/download/1/epub" type="application/epub+zip"/>
            <link rel="http://opds-spec.org/acquisition" href="/opds/download/1/pdf" type="application/pdf"/>
            <link href="/opds/book/1" type="text/html"/>
          </entry>
        </feed>
    """.trimIndent()

    private fun parse(xml: String) = OpdsParser.parse(
        ByteArrayInputStream(xml.toByteArray()),
        "http://nas.local:8083/opds"
    )

    @Test
    fun `Calibre-Web 根导航的无 rel 链接全部可打开`() {
        val feed = parse(rootFeed)
        assertEquals(FeedKind.NAVIGATION, feed.kind)
        assertEquals(10, feed.entries.size)
        assertTrue(
            "每一项都必须解析出可点击的目录地址",
            feed.entries.all { it.navigationHref != null && it.isNavigation }
        )
        assertEquals(
            "http://nas.local:8083/opds/booksindex",
            feed.entries[0].navigationHref
        )
        assertEquals(
            "http://nas.local:8083/opds/seriesindex",
            feed.entries.last().navigationHref
        )
    }

    @Test
    fun `自动直达挑中直接返回书籍的入口而非字母索引或作者分类`() {
        val feed = parse(rootFeed)
        val shortcut = pickAllBooksShortcut(feed)
        assertNotNull(shortcut)
        // Calibre-Web 的 /opds/new 返回 feed.xml（书籍列表），
        // 而 /opds/books 只是 A-Z 字母索引，直达它仍然看不到书。
        assertEquals("Recently added Books", shortcut!!.title)
        assertEquals("http://nas.local:8083/opds/new", shortcut.href)
        assertFalse(shortcut.title.contains("Author"))
        assertFalse(shortcut.title.contains("Alphabetical"))
    }

    @Test
    fun `书籍 entry 的封面与下载链接不会被当成目录导航`() {
        val feed = parse(bookFeed)
        assertEquals(FeedKind.ACQUISITION, feed.kind)
        val book = feed.entries.single()
        assertTrue(book.isBook)
        assertEquals(2, book.acquisitionLinks.size)
        assertNotNull(book.coverUrl)
        assertNull("书籍不应被判成可进入的目录", book.navigationHref)
        assertFalse(book.isNavigation)
    }

    @Test
    fun `feed 级控制链接与搜索模板不受导航识别影响`() {
        val feed = parse(rootFeed)
        assertTrue(feed.searchTemplate!!.contains("{searchTerms}"))
        val bookFeedParsed = parse(bookFeed)
        assertEquals("http://nas.local:8083/opds/booksindex?offset=30", bookFeedParsed.nextUrl)
    }

    @Test
    fun `纯 subsection 的 calibre-server 导航仍然兼容`() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Calibre Library</title>
              <entry>
                <title>By Newest</title>
                <id>1</id>
                <link rel="subsection" href="/opds/newest" type="application/atom+xml;profile=opds-catalog"/>
              </entry>
            </feed>
        """.trimIndent()
        val entry = parse(xml).entries.single()
        assertEquals("http://nas.local:8083/opds/newest", entry.navigationHref)
        assertEquals("http://nas.local:8083/opds/newest", entry.navLinks.first().href)
    }
}
