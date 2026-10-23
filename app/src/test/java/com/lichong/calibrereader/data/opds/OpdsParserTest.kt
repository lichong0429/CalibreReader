package com.lichong.calibrereader.data.opds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class OpdsParserTest {

    /** 取自 calibre-server 7.x 的实际结构（导航页） */
    private val navFeed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom" xmlns:dc="http://purl.org/dc/terms/" xmlns:opds="http://opds-spec.org/2010/catalog">
          <id>http://192.168.1.10:8080/opds</id>
          <title>Calibre Library</title>
          <updated>2026-10-23T10:00:00+00:00</updated>
          <link rel="self" href="http://192.168.1.10:8080/opds" type="application/atom+xml"/>
          <link rel="search" href="http://192.168.1.10:8080/opds/search?query={searchTerms}" type="application/atom+xml"/>
          <entry>
            <title>By Newest</title>
            <id>http://192.168.1.10:8080/opds/newest</id>
            <updated>2026-10-23T10:00:00+00:00</updated>
            <link rel="subsection" href="http://192.168.1.10:8080/opds/newest" type="application/atom+xml;profile=opds-catalog"/>
          </entry>
          <entry>
            <title>Authors</title>
            <id>http://192.168.1.10:8080/opds/authors</id>
            <link rel="subsection" href="/opds/authors" type="application/atom+xml;profile=opds-catalog"/>
          </entry>
        </feed>
    """.trimIndent()

    /** 实际书籍页，含 dc 字段、相对链接、缩略图、多格式、分页 */
    private val acqFeed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom" xmlns:dc="http://purl.org/dc/terms/" xmlns:opf="http://www.idpf.org/2007/opf" xmlns:opds="http://opds-spec.org/2010/catalog">
          <id>http://192.168.1.10:8080/opds/newest</id>
          <title>By Newest</title>
          <link rel="next" href="/opds/newest?start=30" type="application/atom+xml"/>
          <entry>
            <title>Zeolitic Imidazolate Frameworks</title>
            <id>urn:uuid:1234</id>
            <updated>2026-01-02T00:00:00+00:00</updated>
            <dc:identifier opf:scheme="calibre">42</dc:identifier>
            <dc:language>eng</dc:language>
            <dc:creator>Zhang, San</dc:creator>
            <dc:creator>Li, Si</dc:creator>
            <dc:publisher>Wiley</dc:publisher>
            <dc:date>2021-05-01</dc:date>
            <content type="text/html">&lt;p&gt;A book about &lt;b&gt;MOF&lt;/b&gt; membranes.&lt;/p&gt;</content>
            <link rel="http://opds-spec.org/image" href="/get/jpg/42/0.5" type="image/jpeg"/>
            <link rel="http://opds-spec.org/image/thumbnail" href="/get/jpg/42/0.25" type="image/jpeg"/>
            <link rel="http://opds-spec.org/acquisition" href="/get/epub/42" type="application/epub+zip" length="1048576"/>
            <link rel="http://opds-spec.org/acquisition" href="/get/pdf/42" type="application/pdf" length="2097152"/>
            <category term="Chemistry" label="Chemistry"/>
            <category term="MOF" label="MOF"/>
          </entry>
        </feed>
    """.trimIndent()

    private fun parse(xml: String, base: String = "http://192.168.1.10:8080/opds") =
        OpdsParser.parse(ByteArrayInputStream(xml.toByteArray()), base)

    @Test
    fun `导航目录解析出 subsection 链接`() {
        val feed = parse(navFeed)
        assertEquals(FeedKind.NAVIGATION, feed.kind)
        assertEquals("Calibre Library", feed.title)
        assertEquals(2, feed.entries.size)
        assertEquals(
            "http://192.168.1.10:8080/opds/newest",
            feed.entries[0].navLinks.first().href
        )
        // 相对链接基于 feed 地址解析，而非根域名
        assertEquals(
            "http://192.168.1.10:8080/opds/authors",
            feed.entries[1].navLinks.first().href
        )
        assertTrue(feed.searchTemplate!!.contains("{searchTerms}"))
    }

    @Test
    fun `书籍页解析出元数据与多格式`() {
        val feed = parse(acqFeed)
        assertEquals(FeedKind.ACQUISITION, feed.kind)
        val book = feed.entries.single()

        assertEquals("Zeolitic Imidazolate Frameworks", book.title)
        assertEquals(listOf("Zhang, San", "Li, Si"), book.authors)
        assertEquals("eng", book.language)
        assertEquals("Wiley", book.publisher)
        assertEquals(listOf("Chemistry", "MOF"), book.categories)
        assertEquals("42", book.calibreId)
        assertEquals("A book about MOF membranes.", book.summary)

        assertEquals(2, book.acquisitionLinks.size)
        assertEquals(BookFormat.EPUB, book.acquisitionLinks[0].format)
        assertEquals(BookFormat.PDF, book.acquisitionLinks[1].format)
        assertEquals(1048576L, book.acquisitionLinks[0].length)

        assertEquals("http://192.168.1.10:8080/get/jpg/42/0.25", book.coverUrl)
        assertTrue(book.isBook)
    }

    @Test
    fun `分页链接基于 feed 地址解析`() {
        val feed = parse(acqFeed)
        assertEquals("http://192.168.1.10:8080/opds/newest?start=30", feed.nextUrl)
        assertTrue(feed.isPaginated)
    }

    @Test
    fun `Calibre-Web 结构解析`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom" xmlns:dc="http://purl.org/dc/terms/" xmlns:opds="http://opds-spec.org/2010/catalog">
              <title>Calibre-Web</title>
              <entry>
                <title>Books</title>
                <id>/opds/books</id>
                <link rel="subsection" href="/opds/books" type="application/atom+xml"/>
              </entry>
              <entry>
                <title>Hot Books</title>
                <id>/opds/hot</id>
                <link rel="subsection" href="/opds/hot" type="application/atom+xml"/>
              </entry>
            </feed>
        """.trimIndent()
        val feed = parse(xml, "http://nas.local:8083/opds/")
        assertEquals(2, feed.entries.size)
        assertEquals("http://nas.local:8083/opds/books", feed.entries[0].navLinks.first().href)
    }

    @Test
    fun `非原子响应抛出可读异常`() {
        val ex = runCatching {
            parse("<html><body>login</body></html>")
        }.exceptionOrNull()
        assertNotNull(ex)
        assertTrue(ex is OpdsParseException)
    }

    @Test
    fun `Facet 分组解析`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom" xmlns:opds="http://opds-spec.org/2010/catalog">
              <title>Books</title>
              <link rel="http://opds-spec.org/facet" href="/opds/books?sort=title" title="Title" opds:facetGroup="Sort by" opds:activeFacet="true"/>
              <link rel="http://opds-spec.org/facet" href="/opds/books?sort=author" title="Author" opds:facetGroup="Sort by"/>
              <entry><title>X</title><id>1</id><link rel="subsection" href="/opds/x"/></entry>
            </feed>
        """.trimIndent()
        val feed = parse(xml)
        assertEquals(1, feed.facets.size)
        assertEquals("Sort by", feed.facets[0].name)
        assertEquals(2, feed.facets[0].facets.size)
        assertTrue(feed.facets[0].facets[0].active)
        assertNull(feed.nextUrl)
    }

    @Test
    fun `Calibre-Web 的 urn uuid 解析为 Kobo 进度 id`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom" xmlns:dc="http://purl.org/dc/terms/">
              <title>Books</title>
              <entry>
                <title>X</title>
                <id>urn:uuid:1A2B3C4D-5E6F-7A8B-9C0D-E1F2A3B4C5D6</id>
                <link rel="http://opds-spec.org/acquisition" href="/download/1/epub/x.epub" type="application/epub+zip"/>
              </entry>
            </feed>
        """.trimIndent()
        val entry = parse(xml).entries.first()
        // 大写需归一化为小写，Calibre-Web 的 state 接口只认小写 uuid
        assertEquals("1a2b3c4d-5e6f-7a8b-9c0d-e1f2a3b4c5d6", entry.koboUuid)
    }

    @Test
    fun `非 uuid 形态的 entry id 不产生 Kobo id`() {
        val entry = parse(acqFeed).entries.first()
        assertNull(entry.koboUuid)
        assertEquals("42", entry.calibreId)
    }
}
