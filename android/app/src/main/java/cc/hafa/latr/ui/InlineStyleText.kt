package cc.hafa.latr.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cc.hafa.latr.util.InlineStyle
import cc.hafa.latr.util.Tags

private const val MARKER_ALPHA = 0.45f
private const val CHIP_ALPHA = 0.12f

/**
 * [text] with its `*bold*` / `_italic_` spans styled, their markers dimmed relative to [baseColor],
 * and its `#tags` in [tagColor] (their chips are drawn by [tagChips]); [tagsOnly] leaves out the spans.
 */
fun styledTodoText(text: String, baseColor: Color, tagColor: Color, tagsOnly: Boolean = false): AnnotatedString {
    val spans = if (tagsOnly) emptyList() else InlineStyle.parse(text)
    val tags = Tags.parse(text)
    if (spans.isEmpty() && tags.isEmpty()) return AnnotatedString(text)
    val marker = SpanStyle(color = baseColor.copy(alpha = baseColor.alpha * MARKER_ALPHA))
    return buildAnnotatedString {
        append(text)
        for (tag in tags) addStyle(SpanStyle(color = tagColor), tag.start, tag.end)
        for (span in spans) {
            val style = when (span.style) {
                InlineStyle.Style.BOLD -> SpanStyle(fontWeight = FontWeight.SemiBold)
                InlineStyle.Style.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
            }
            addStyle(style, span.start, span.end)
            addStyle(marker, span.start, span.start + 1)
            addStyle(marker, span.end - 1, span.end)
        }
    }
}

/** The editor's twin of [styledTodoText]; markers stay in the text, so offsets map one to one. */
class InlineStyleTransformation(
    private val baseColor: Color,
    private val tagColor: Color,
    private val tagsOnly: Boolean = false,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(styledTodoText(text.text, baseColor, tagColor, tagsOnly), OffsetMapping.Identity)

    override fun equals(other: Any?): Boolean =
        other is InlineStyleTransformation && other.baseColor == baseColor &&
            other.tagColor == tagColor && other.tagsOnly == tagsOnly

    override fun hashCode(): Int = (31 * baseColor.hashCode() + tagColor.hashCode()) * 31 + tagsOnly.hashCode()
}

/**
 * Draw a rounded chip in a soft [tagColor] behind each `#tag` of [text], as laid out by [layout].
 *
 * Nothing is drawn while the text is wider than the element (a scrolled single-line field), since
 * the chips would not follow the scroll.
 */
fun Modifier.tagChips(text: String, layout: () -> TextLayoutResult?, tagColor: Color): Modifier = drawBehind {
    val result = layout() ?: return@drawBehind
    if (result.layoutInput.text.text != text || result.size.width > size.width) return@drawBehind
    val pad = 2.dp.toPx()
    val radius = CornerRadius(4.dp.toPx())
    val chip = tagColor.copy(alpha = tagColor.alpha * CHIP_ALPHA)
    for (tag in Tags.parse(text)) {
        var box: Rect? = null
        var boxLine = -1
        for (offset in tag.start until tag.end) {
            val line = result.getLineForOffset(offset)
            val charBox = result.getBoundingBox(offset)
            val current = box
            if (current != null && line == boxLine) {
                box = Rect(minOf(current.left, charBox.left), current.top, maxOf(current.right, charBox.right), current.bottom)
            } else {
                current?.let { drawRoundRect(chip, Offset(it.left - pad, it.top), Size(it.width + 2 * pad, it.height), radius) }
                box = charBox
                boxLine = line
            }
        }
        box?.let { drawRoundRect(chip, Offset(it.left - pad, it.top), Size(it.width + 2 * pad, it.height), radius) }
    }
}
