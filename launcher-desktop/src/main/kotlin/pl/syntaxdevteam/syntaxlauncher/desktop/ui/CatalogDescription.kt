package pl.syntaxdevteam.syntaxlauncher.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.net.URI

@Composable
internal fun CatalogDescription(description: String) {
    val color = MaterialTheme.colorScheme.primary
    Text(remember(description, color) {
        formatCatalogDescription(description, TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline)))
    })
}

internal fun formatCatalogDescription(description: String, links: TextLinkStyles): AnnotatedString {
    val source = description.take(100_000)
    val flavour = GFMFlavourDescriptor()
    val html = HtmlGenerator(source, MarkdownParser(flavour).buildMarkdownTreeFromString(source), flavour).generateHtml()
    val document = Jsoup.parseBodyFragment(html)
    document.select("script, style, iframe, object, embed, svg, form, input").remove()
    val text = buildAnnotatedString {
        document.body().childNodes().forEach { appendDescription(it, links, 0) }
    }
    return text.subSequence(0, text.text.trimEnd().length)
}

private fun AnnotatedString.Builder.appendDescription(node: Node, links: TextLinkStyles, depth: Int) {
    if (depth > 64) return
    if (node is TextNode) {
        val value = if (node.parentsCode()) node.wholeText else node.text()
        if (value.isNotBlank() || node.parentsCode() || node.wholeText == " ") append(value)
    }
    if (node !is Element) return
    fun children() = node.childNodes().forEach { appendDescription(it, links, depth + 1) }
    val tag = node.normalName()
    when (tag) {
        "a" -> {
            val url = node.attr("href")
            val uri = runCatching { URI(url) }.getOrNull()
            if (uri?.scheme in setOf("https", "http") && uri?.host != null && node.parents().none { it.normalName() == "a" }) {
                withLink(LinkAnnotation.Url(url, links)) { children() }
            } else children()
        }
        "img" -> append(node.attr("alt"))
        "br", "hr" -> append('\n')
        "strong", "b", "summary" -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { children() }
        "em", "i" -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { children() }
        "del", "s" -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { children() }
        "code", "pre" -> {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { children() }
            if (tag == "pre") append("\n\n")
        }
        "h1", "h2", "h3", "h4", "h5", "h6" -> {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = if (tag in setOf("h1", "h2")) 22.sp else 18.sp)) { children() }
            append("\n\n")
        }
        "li" -> {
            val parent = node.parent()
            append(if (parent?.normalName() == "ol") "${node.elementSiblingIndex() + parent.attr("start").toIntOrNull().let { it ?: 1 }}. " else "• ")
            children()
            append('\n')
        }
        "p", "div", "blockquote", "ul", "ol", "details", "table", "tr" -> {
            children()
            append(if (tag == "tr") "\n" else "\n\n")
        }
        "td", "th" -> {
            if (node.elementSiblingIndex() > 0) append("  ·  ")
            children()
        }
        else -> children()
    }
}

private fun TextNode.parentsCode(): Boolean =
    generateSequence(parent()) { it.parent() }.any { it.normalName() == "pre" }
