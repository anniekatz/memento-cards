package com.annie.memento.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.annie.memento.ui.theme.ButtonShape
import com.annie.memento.ui.theme.ChipShape
import com.annie.memento.ui.theme.InsetShape
import com.annie.memento.ui.theme.MementoGridLine
import com.annie.memento.ui.theme.MementoHazard
import com.annie.memento.ui.theme.PanelShape

// background grid
fun Modifier.MementoGrid(
    color: Color,
    cell: Dp = 30.dp,
    alpha: Float = 0.05f,
): Modifier = drawBehind {
    val c = color.copy(alpha = alpha)
    val step = cell.toPx().coerceAtLeast(1f)
    var x = 0f
    while (x <= size.width) {
        drawLine(c, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }
    var y = 0f
    while (y <= size.height) {
        drawLine(c, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
}

// crt looking scan lines
fun Modifier.MementoScanlines(
    color: Color = Color.White,
    alpha: Float = 0.022f,
    gap: Dp = 3.dp,
): Modifier = drawWithContent {
    drawContent()
    val c = color.copy(alpha = alpha)
    val step = gap.toPx().coerceAtLeast(1f)
    var y = 0f
    while (y < size.height) {
        drawLine(c, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
}

// l shaped brackets at card corners
fun Modifier.cornerBrackets(
    color: Color,
    length: Dp = 14.dp,
    thickness: Dp = 1.5.dp,
    inset: Dp = 12.dp,
    topStart: Boolean = true,
    topEnd: Boolean = true,
    bottomStart: Boolean = true,
    bottomEnd: Boolean = true,
): Modifier = drawWithContent {
    drawContent()
    val l = length.toPx()
    val t = thickness.toPx()
    val i = inset.toPx()
    val w = size.width
    val h = size.height
    if (topStart) {
        drawLine(color, Offset(i, i), Offset(i + l, i), t)
        drawLine(color, Offset(i, i), Offset(i, i + l), t)
    }
    if (topEnd) {
        drawLine(color, Offset(w - i, i), Offset(w - i - l, i), t)
        drawLine(color, Offset(w - i, i), Offset(w - i, i + l), t)
    }
    if (bottomStart) {
        drawLine(color, Offset(i, h - i), Offset(i + l, h - i), t)
        drawLine(color, Offset(i, h - i), Offset(i, h - i - l), t)
    }
    if (bottomEnd) {
        drawLine(color, Offset(w - i, h - i), Offset(w - i - l, h - i), t)
        drawLine(color, Offset(w - i, h - i), Offset(w - i, h - i - l), t)
    }
}

// caution stripes
@Composable
fun HazardStripes(
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    stripe: Dp = 12.dp,
    color: Color = MementoHazard,
    gap: Color = Color(0xFF15161A),
) {
    Canvas(modifier.fillMaxWidth().height(height).clipToBounds()) {
        val s = stripe.toPx()
        val h = size.height
        val w = size.width
        var x = -h
        var i = 0
        while (x < w) {
            val path = Path().apply {
                moveTo(x, h)
                lineTo(x + h, 0f)
                lineTo(x + h + s, 0f)
                lineTo(x + s, h)
                close()
            }
            drawPath(path, if (i % 2 == 0) color else gap)
            x += s
            i++
        }
    }
}

//cut corner panel
@Composable
fun MementoPanel(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = PanelShape,
        color = color,
        border = border,
        content = content,
    )
}

//terminal style header
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    trailing: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(width = 4.dp, height = 14.dp).background(accent))
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        if (trailing != null) {
            Text(
                trailing.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
//infopill
@Composable
fun StatusTag(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        modifier = modifier,
        shape = ChipShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(width = 3.dp, height = 11.dp).background(accent))
            Text(
                text.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun MementoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: String? = null,
    container: Color = MaterialTheme.colorScheme.primary,
    onContainer: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = onContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
    ) {
        MementoButtonContent(text, leading)
    }
}

@Composable
fun MementoOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: String? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ButtonShape,
        border = BorderStroke(1.dp, if (enabled) accent.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = accent,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
    ) {
        MementoButtonContent(text, leading)
    }
}

@Composable
private fun RowScope.MementoButtonContent(text: String, leading: String?) {
    if (leading != null) {
        Text(leading, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(8.dp))
    }
    Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
fun MementoIconButton(
    glyph: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(InsetShape)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), InsetShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, color = color)
    }
}

// divider
@Composable
fun TechDivider(
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 22.dp, height = 2.dp).background(accent))
        Box(Modifier.weight(1f).height(1.dp).background(MementoGridLine))
    }
}
