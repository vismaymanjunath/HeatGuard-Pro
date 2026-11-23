package com.example.appdev.domain

import com.example.appdev.ui.assistant.BlockKind
import com.example.appdev.ui.assistant.MarkdownLite
import com.example.appdev.ui.assistant.Span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownLiteTest {

    @Test
    fun `plain text is one paragraph`() {
        val blocks = MarkdownLite.parse("Your phone is fine.")
        assertEquals(1, blocks.size)
        assertEquals(BlockKind.PARAGRAPH, blocks[0].kind)
        assertEquals(listOf(Span("Your phone is fine.")), blocks[0].spans)
    }

    @Test
    fun `bullets of every common style are recognised`() {
        val blocks = MarkdownLite.parse("- one\n* two\n• three")
        assertEquals(List(3) { BlockKind.BULLET }, blocks.map { it.kind })
        assertEquals(listOf("one", "two", "three"), blocks.map { it.spans.single().text })
    }

    @Test
    fun `numbered lists keep their numbers`() {
        val blocks = MarkdownLite.parse("1. first\n2) second\n10. tenth")
        assertEquals(listOf(1, 2, 10), blocks.map { it.number })
        assertEquals(List(3) { BlockKind.NUMBERED }, blocks.map { it.kind })
    }

    @Test
    fun `bold and code are parsed inline`() {
        val spans = MarkdownLite.inline("It is **warm** at `38.4` degrees")
        assertEquals(
            listOf(Span("It is "), Span("warm", bold = true), Span(" at "), Span("38.4", code = true), Span(" degrees")),
            spans,
        )
    }

    @Test
    fun `headings are shown bold without the hash marks`() {
        val block = MarkdownLite.parse("## Summary").single()
        assertEquals(BlockKind.HEADING, block.kind)
        assertEquals(listOf(Span("Summary", bold = true)), block.spans)
    }

    @Test
    fun `unfinished markers while streaming stay as typed`() {
        assertEquals(listOf(Span("It is **warm")), MarkdownLite.inline("It is **warm"))
        assertEquals(listOf(Span("a `b")), MarkdownLite.inline("a `b"))
        assertEquals(listOf(Span("****")), MarkdownLite.inline("****"))
    }

    @Test
    fun `blank lines and windows line endings are handled`() {
        val blocks = MarkdownLite.parse("First.\r\n\r\n- a\r\n\r\nLast.")
        assertEquals(listOf(BlockKind.PARAGRAPH, BlockKind.BULLET, BlockKind.PARAGRAPH), blocks.map { it.kind })
        assertTrue(MarkdownLite.parse("   \n\n").isEmpty())
        assertTrue(MarkdownLite.parse("").isEmpty())
    }

    @Test
    fun `a dash inside a sentence is not a bullet`() {
        assertEquals(BlockKind.PARAGRAPH, MarkdownLite.parse("38.4 - 40.0 is the gap").single().kind)
        assertEquals(BlockKind.PARAGRAPH, MarkdownLite.parse("-5 degrees").single().kind)
    }
}
