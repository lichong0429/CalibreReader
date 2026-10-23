package com.lichong.calibrereader.data.opds

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory

class OpdsParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * OPDS 1.2 (Atom) 解析器。
 * 所有 URL 一律从 <link rel> 解析，不硬编码服务端路由 —— 同时兼容 calibre-server 与 Calibre-Web。
 */
object OpdsParser {

    private const val NS_OPDS = "http://opds-spec.org/2010/catalog"
    private const val MAX_BYTES = 16 * 1024 * 1024

    fun parse(input: InputStream, baseUrl: String): OpdsFeed {
        val bytes = sanitize(readLimited(input))
        if (bytes.isEmpty()) throw OpdsParseException("服务端返回空内容")

        val doc = try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
                isIgnoringComments = true
                isCoalescing = true
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            }
            factory.newDocumentBuilder().parse(InputSource(ByteArrayInputStream(bytes)))
        } catch (e: OpdsParseException) {
            throw e
        } catch (e: Exception) {
            throw OpdsParseException("OPDS 解析失败：${e.message ?: e.javaClass.simpleName}", e)
        }

        val root = doc.documentElement ?: throw OpdsParseException("OPDS 响应为空")
        val rootName = root.tagLocal()
        if (rootName != "feed" && rootName != "entry") {
            throw OpdsParseException("不是有效的 OPDS 响应（根节点：$rootName）")
        }

        val entryElements = if (rootName == "feed") root.childrenLocal("entry") else listOf(root)
        val feedLinks = if (rootName == "feed") root.childrenLocal("link") else emptyList()

        val links = feedLinks.mapNotNull { parseLink(it, baseUrl) }
        val entries = entryElements.mapIndexedNotNull { i, el -> parseEntry(el, baseUrl, i) }
        val facets = feedLinks
            .filter { it.attr("rel") == OpdsRel.FACET }
            .mapNotNull { parseFacet(it, baseUrl) }
            .groupBy { it.group }
            .map { (group, list) -> OpdsFacetGroup(group, list.map { it.facet }) }

        return OpdsFeed(
            title = root.childrenLocal("title").firstOrNull()?.text()?.stripHtml(),
            id = root.childrenLocal("id").firstOrNull()?.text(),
            selfUrl = links.firstOrNull { it.rel == "self" }?.href,
            startUrl = links.firstOrNull { it.rel == "start" }?.href,
            upUrl = links.firstOrNull { it.rel == "up" }?.href,
            nextUrl = links.firstOrNull { it.rel == "next" }?.href,
            prevUrl = links.firstOrNull { it.rel == "prev" }?.href,
            searchTemplate = links.firstOrNull { it.rel == OpdsRel.SEARCH && it.href.isNotBlank() }?.href,
            entries = entries,
            facets = facets
        )
    }

    private fun parseEntry(el: Element, baseUrl: String, index: Int): OpdsEntry? {
        val rawTitle = el.childrenLocal("title").firstOrNull()?.text().orEmpty()
        if (rawTitle.isBlank()) return null
        val title = rawTitle.stripHtml()

        val links = el.childrenLocal("link").mapNotNull { parseLink(it, baseUrl) }
        val identifier = el.childrenLocal("identifier").firstOrNull()?.text()

        val authors = buildList {
            el.childrenLocal("author").forEach { a ->
                a.childrenLocal("name").firstOrNull()?.text()?.let { add(it) }
            }
            if (isEmpty()) {
                el.childrenLocal("creator").forEach { c -> c.text()?.let { add(it) } }
            }
        }.map { it.stripHtml() }.filter { it.isNotBlank() }.distinct()

        val summary = (el.childrenLocal("content").firstOrNull()
            ?: el.childrenLocal("summary").firstOrNull())?.text()?.stripHtml()?.takeIf { it.isNotBlank() }

        val categories = el.childrenLocal("category").mapNotNull { c ->
            c.attr("label").ifBlank { c.attr("term") }.takeIf { it.isNotBlank() }
        }.distinct()

        return OpdsEntry(
            id = el.childrenLocal("id").firstOrNull()?.text()
                ?: identifier
                ?: "entry-$index-${title.hashCode()}",
            title = title,
            authors = authors,
            summary = summary,
            language = el.childrenLocal("language").firstOrNull()?.text()?.takeIf { it.isNotBlank() },
            publisher = el.childrenLocal("publisher").firstOrNull()?.text()?.takeIf { it.isNotBlank() },
            issued = (el.childrenLocal("issued").firstOrNull()
                ?: el.childrenLocal("date").firstOrNull())?.text()?.takeIf { it.isNotBlank() },
            updated = el.childrenLocal("updated").firstOrNull()?.text()?.takeIf { it.isNotBlank() },
            categories = categories,
            links = links,
            identifier = identifier
        )
    }

    private fun parseLink(el: Element, baseUrl: String): OpdsLink? {
        val href = el.attr("href").takeIf { it.isNotBlank() } ?: return null
        return OpdsLink(
            rel = el.attr("rel").ifBlank { "alternate" },
            href = resolveUrl(baseUrl, href),
            type = el.attr("type").takeIf { it.isNotBlank() },
            title = el.attr("title").takeIf { it.isNotBlank() },
            length = el.attr("length").toLongOrNull()
        )
    }

    private data class FacetRaw(val group: String, val facet: OpdsFacet)

    private fun parseFacet(el: Element, baseUrl: String): FacetRaw? {
        val href = el.attr("href").takeIf { it.isNotBlank() } ?: return null
        val active = el.attrNs("activeFacet").equals("true", true) ||
            el.attr("opds:activeFacet").equals("true", true) ||
            el.attr("activeFacet").equals("true", true)
        val group = el.attrNs("facetGroup").ifBlank { el.attr("opds:facetGroup") }.ifBlank { "排序" }
        return FacetRaw(
            group = group,
            facet = OpdsFacet(
                title = el.attr("title").ifBlank { "未命名" }.stripHtml(),
                href = resolveUrl(baseUrl, href),
                active = active,
                count = el.attrNs("count").toIntOrNull()
            )
        )
    }

    // ---------- DOM helpers ----------

    private fun Element.tagLocal(): String = localName ?: nodeName.substringAfter(':')

    private fun Element.childElements(): List<Element> {
        val nodes: Node = this
        val children = nodes.childNodes
        val out = ArrayList<Element>(children.length)
        for (i in 0 until children.length) {
            (children.item(i) as? Element)?.let { out.add(it) }
        }
        return out
    }

    private fun Element.childrenLocal(name: String): List<Element> =
        childElements().filter { it.tagLocal() == name }

    private fun Element.text(): String? = textContent?.trim()?.takeIf { it.isNotEmpty() }

    private fun Element.attr(name: String): String = getAttribute(name).orEmpty().trim()

    private fun Element.attrNs(name: String): String {
        val v = getAttributeNS(NS_OPDS, name)
        return if (v.isNullOrBlank()) getAttribute("opds:$name").orEmpty().trim() else v.trim()
    }

    // ---------- url / text / stream ----------

    fun resolveUrl(base: String, href: String): String {
        val h = href.trim()
        if (h.startsWith("http://", true) || h.startsWith("https://", true)) return h
        return try {
            val b = if (!base.endsWith("/") && !base.contains('?') &&
                !base.substringAfterLast('/').contains('.')
            ) "$base/" else base
            URI(b).resolve(h).toString()
        } catch (e: Exception) {
            manualResolve(base, h)
        }
    }

    private fun manualResolve(base: String, href: String): String = when {
        href.startsWith("//") -> base.substringBefore("//") + href
        href.startsWith("/") -> {
            val schemeEnd = base.indexOf("://")
            val pathStart = if (schemeEnd >= 0) base.indexOf('/', schemeEnd + 3) else -1
            if (pathStart > 0) base.substring(0, pathStart) + href else base.trimEnd('/') + href
        }
        href.startsWith("?") -> base.substringBefore('?') + href
        else -> base.trimEnd('/') + "/" + href
    }

    private fun String.stripHtml(): String {
        if (!contains('<') && !contains('&')) return this
        return replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p\\s*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]*>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun readLimited(input: InputStream, limit: Int = MAX_BYTES): ByteArray {
        val out = ByteArrayOutputStream(64 * 1024)
        val buf = ByteArray(16 * 1024)
        var total = 0
        input.use { ins ->
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                total += n
                if (total > limit) throw OpdsParseException("响应超过 ${limit / 1024 / 1024}MB 上限")
                out.write(buf, 0, n)
            }
        }
        return out.toByteArray()
    }

    /** 去掉 BOM 与前导空白，部分服务端会在 XML 声明前输出空行 */
    private fun sanitize(bytes: ByteArray): ByteArray {
        var start = 0
        while (start < bytes.size) {
            val b = bytes[start].toInt() and 0xFF
            val isBom = start + 2 < bytes.size && b == 0xEF &&
                (bytes[start + 1].toInt() and 0xFF) == 0xBB && (bytes[start + 2].toInt() and 0xFF) == 0xBF
            when {
                isBom -> start += 3
                b == 0x20 || b == 0x09 || b == 0x0A || b == 0x0D -> start++
                else -> {
                    return if (start == 0) bytes else bytes.copyOfRange(start, bytes.size)
                }
            }
        }
        return ByteArray(0)
    }
}
