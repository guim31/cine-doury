package fr.guim.cinedoury.core.update

/** A block of a changelog, as the update screen draws it. */
sealed interface ChangelogBlock {
    data class Heading(
        val text: String,
    ) : ChangelogBlock

    data class Bullet(
        val text: String,
    ) : ChangelogBlock

    data class Paragraph(
        val text: String,
    ) : ChangelogBlock
}

/**
 * The body of a release in simple Markdown (`SPEC.md` § 4.18): headings (`#`), bullets (`-`, `*`,
 * `+`), paragraphs. Emphasis, code and links keep their text only; a TV has nothing to click.
 */
object Changelog {
    private val HEADING = Regex("""^#{1,6}\s+(.*)$""")
    private val BULLET = Regex("""^\s*[-*+]\s+(.*)$""")
    private val LINK = Regex("""!?\[([^\]]*)]\([^)]*\)""")
    private val EMPHASIS = Regex("""(\*\*|__|\*|_|`)(.+?)\1""")

    fun parse(markdown: String): List<ChangelogBlock> {
        val blocks = mutableListOf<ChangelogBlock>()
        val paragraph = StringBuilder()

        fun flush() {
            if (paragraph.isNotBlank()) blocks += ChangelogBlock.Paragraph(paragraph.toString().trim())
            paragraph.clear()
        }
        markdown.replace("\r\n", "\n").lines().forEach { raw ->
            val line = raw.trimEnd()
            val heading = HEADING.matchEntire(line.trim())
            val bullet = BULLET.matchEntire(line)
            when {
                line.isBlank() -> {
                    flush()
                }

                heading != null -> {
                    flush()
                    blocks += ChangelogBlock.Heading(inline(heading.groupValues[1]))
                }

                bullet != null -> {
                    flush()
                    blocks += ChangelogBlock.Bullet(inline(bullet.groupValues[1]))
                }

                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(inline(line.trim()))
                }
            }
        }
        flush()
        return blocks
    }

    private fun inline(text: String): String {
        var result = LINK.replace(text) { it.groupValues[1] }
        while (true) {
            val next = EMPHASIS.replace(result) { it.groupValues[2] }
            if (next == result) break
            result = next
        }
        return result.trim()
    }
}
