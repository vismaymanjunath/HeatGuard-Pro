package com.example.appdev.ui.assistant

/** A run of text with inline formatting. */
data class Span(val text: String, val bold: Boolean = false, val code: Boolean = false)

enum class BlockKind { PARAGRAPH, BULLET, NUMBERED, HEADING }

data class Block(val kind: BlockKind, val spans: List<Span>, val number: Int? = null)

/**
 * Renders the small subset of Markdown that chat answers actually use: paragraphs, "-" / "*" bullets,
 * numbered lists, headings (shown bold), **bold** and `code`. Anything else is shown as plain text,
 * and unfinished markers (common while an answer is still streaming) are left as typed rather than
 * hidden, so partial text never looks broken.
 */
object MarkdownLite {
    private val bullet = Regex("^[-*•]\\s+(.*)$")
    private val numbered = Regex("^(\\d{1,3})[.)]\\s+(.*)$")
    private val heading = Regex("^#{1,6}\\s+(.*)$")

    fun parse(text: String): List<Block> {
        val blocks = mutableListOf<Block>()
        for (raw in text.replace("\r\n", "\n").lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            heading.matchEntire(line)?.let { blocks += Block(BlockKind.HEADING, inline(it.groupValues[1]).map { s -> s.copy(bold = true) }); continue }
            bullet.matchEntire(line)?.let { blocks += Block(BlockKind.BULLET, inline(it.groupValues[1])); continue }
            numbered.matchEntire(line)?.let {
                blocks += Block(BlockKind.NUMBERED, inline(it.groupValues[2]), number = it.groupValues[1].toInt())
                continue
            }
            blocks += Block(BlockKind.PARAGRAPH, inline(line))
        }
        return blocks
    }

    fun inline(text: String): List<Span> {
        val spans = mutableListOf<Span>()
        val plain = StringBuilder()
        fun flush() {
            if (plain.isNotEmpty()) {
                spans += Span(plain.toString())
                plain.clear()
            }
        }
        var i = 0
        while (i < text.length) {
            if (text.startsWith("**", i)) {
                val end = text.indexOf("**", i + 2)
                if (end > i + 2) {
                    flush()
                    spans += Span(text.substring(i + 2, end), bold = true)
                    i = end + 2
                    continue
                }
            } else if (text[i] == '`') {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    flush()
                    spans += Span(text.substring(i + 1, end), code = true)
                    i = end + 1
                    continue
                }
            }
            plain.append(text[i])
            i++
        }
        flush()
        return spans
    }
}
