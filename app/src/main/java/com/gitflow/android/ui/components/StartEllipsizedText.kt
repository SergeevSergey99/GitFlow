package com.gitflow.android.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.material3.Text

@Composable
fun StartEllipsizedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle,
    color: Color = Color.Unspecified
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        val maxWidthPx = with(density) { maxWidth.roundToPx() }
        val display = remember(text, maxWidthPx, style) {
            fitTextToWidth(
                text = text,
                maxWidthPx = maxWidthPx,
                textMeasurer = textMeasurer,
                style = style
            )
        }

        Text(
            text = display,
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
    }
}

private fun fitTextToWidth(
    text: String,
    maxWidthPx: Int,
    textMeasurer: TextMeasurer,
    style: TextStyle
): String {
    if (text.isEmpty() || maxWidthPx <= 0) return text

    fun fits(value: String): Boolean {
        if (value.isEmpty()) return true
        val result = textMeasurer.measure(
            text = value,
            style = style,
            maxLines = 1,
            softWrap = false,
            constraints = Constraints(maxWidth = maxWidthPx)
        )
        return !result.hasVisualOverflow
    }

    if (fits(text)) return text

    var left = 1
    var right = text.length - 1
    var best: String? = null

    while (left <= right) {
        val cut = (left + right) / 2
        val candidate = "…" + text.drop(cut)
        if (fits(candidate)) {
            best = candidate
            right = cut - 1
        } else {
            left = cut + 1
        }
    }

    return best ?: if (text.length == 1) "…" else "…" + text.takeLast(1)
}
