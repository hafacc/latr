package io.hafa.latr.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import io.hafa.latr.util.InlineStyle

private const val MARKER_ALPHA = 0.45f

/** [text] with its `*bold*` / `_italic_` spans styled and their markers dimmed relative to [baseColor]. */
fun styledTodoText(text: String, baseColor: Color): AnnotatedString {
    val spans = InlineStyle.parse(text)
    if (spans.isEmpty()) return AnnotatedString(text)
    val marker = SpanStyle(color = baseColor.copy(alpha = baseColor.alpha * MARKER_ALPHA))
    return buildAnnotatedString {
        append(text)
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
class InlineStyleTransformation(private val baseColor: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(styledTodoText(text.text, baseColor), OffsetMapping.Identity)

    override fun equals(other: Any?): Boolean =
        other is InlineStyleTransformation && other.baseColor == baseColor

    override fun hashCode(): Int = baseColor.hashCode()
}
