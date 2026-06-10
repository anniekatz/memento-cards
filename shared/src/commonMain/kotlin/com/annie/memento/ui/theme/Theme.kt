package com.annie.memento.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

// dark mode inspired by evangelion (nerv) UIs

val MementoAmber = Color(0xFFFF6A1A)
private val MementoAmberBright = Color(0xFFFF8A3D)
private val MementoAmberDeep = Color(0xFF3A1B06)
private val MementoAmberPale = Color(0xFFFFC79B)

val MementoGreen = Color(0xFF54D06A)
private val MementoGreenDeep = Color(0xFF0E2A18)
private val MementoGreenPale = Color(0xFFA6EEB1)

val MementoCyan = Color(0xFF38C6E6)

val MementoRed = Color(0xFFFF453A)

val MementoHazard = Color(0xFFF5B81E)

private val Void = Color(0xFF07080A) // app bg
private val Surface = Color(0xFF0D0F13) // base panels
private val SurfaceElevated = Color(0xFF13161C) // elevated panels/rows
private val SurfaceHigh = Color(0xFF181C23)
private val SurfaceHighest = Color(0xFF1F242D)

val MementoGridLine = Color(0xFF202632)
private val OutlineBright = Color(0xFF333B47)

private val TextHi = Color(0xFFE6EAE6)
private val TextMid = Color(0xFF99A0A0)
val MementoTextDim = Color(0xFF626C6E)


val MementoColorScheme = darkColorScheme(
    primary = MementoAmber,
    onPrimary = Color(0xFF160800),
    primaryContainer = MementoAmberDeep,
    onPrimaryContainer = MementoAmberPale,
    secondary = MementoGreen,
    onSecondary = Color(0xFF04140A),
    secondaryContainer = MementoGreenDeep,
    onSecondaryContainer = MementoGreenPale,
    tertiary = MementoCyan,
    onTertiary = Color(0xFF002731),
    background = Void,
    onBackground = TextHi,
    surface = Surface,
    onSurface = TextHi,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextMid,
    surfaceContainerLowest = Color(0xFF050608),
    surfaceContainerLow = Color(0xFF0B0D11),
    surfaceContainer = SurfaceElevated,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = SurfaceHighest,
    outline = OutlineBright,
    outlineVariant = MementoGridLine,
    error = MementoRed,
    onError = Color(0xFF230200),
    errorContainer = Color(0xFF3A0C08),
    onErrorContainer = Color(0xFFFFB4AC),
    scrim = Color(0xFF000000),
)

// always dark
@Composable
fun MementoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MementoColorScheme,
        typography = MementoTypography,
        shapes = MementoShapes,
        content = content,
    )
}


fun Long.toColor(): Color = Color(this.toInt())
fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

val SwatchColors: List<Color> = listOf(
    Color(0xFFEF5350),
    Color(0xFFEC407A),
    Color(0xFFAB47BC),
    Color(0xFF7E57C2),
    Color(0xFF5C6BC0),
    Color(0xFF42A5F5),
    Color(0xFF29B6F6),
    Color(0xFF26C6DA),
    Color(0xFF26A69A),
    Color(0xFF66BB6A),
    Color(0xFF9CCC65),
    Color(0xFFD4E157),
    Color(0xFFFFCA28),
    Color(0xFFFFA726),
    Color(0xFFFF7043),
    Color(0xFF8D6E63),
    Color(0xFF78909C),
)

val NeutralChipColor: Color = Color(0xFF8A93A6)
