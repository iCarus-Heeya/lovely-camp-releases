package com.lovelyreader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lovelyreader.domain.AppTheme

data class AppColors(
    val cream: Color,
    val paper: Color,
    val porcelain: Color,
    val almond: Color,
    val blush: Color,
    val mistPink: Color,
    val dawnPink: Color,
    val roseBeige: Color,
    val roseDust: Color,
    val cocoa: Color,
    val ink: Color,
    val sage: Color,
    val sageSoft: Color,
    val warmWhite: Color,
    val softGray: Color,
    val lineColor: Color
)

/* 玫瑰晨曦色彩系统 */
private val WarmPalette = AppColors(
    cream = Color(0xFFFFF8F1),
    paper = Color(0xFFFFFDF9),
    porcelain = Color(0xFFF7F3EF),
    almond = Color(0xFFE7D8C8),
    blush = Color(0xFFEAD3CC),
    mistPink = Color(0xFFEAD3CC),
    dawnPink = Color(0xFFB47D77),
    roseBeige = Color(0xFFB47D77),
    roseDust = Color(0xFF8E5F5A),
    cocoa = Color(0xFF46342F),
    ink = Color(0xFF2F2926),
    sage = Color(0xFF647866),
    sageSoft = Color(0xFFDCE5DB),
    warmWhite = Color(0xFFFFFDF9),
    softGray = Color(0xFF7B6B66),
    lineColor = Color(0xFF46342F).copy(alpha = 0.12f)
)

/* 紫红渐变色彩系统 */
private val PurpleMagentaPalette = AppColors(
    cream = Color(0xFFF5F3FF),
    paper = Color(0xFFFAF7FF),
    porcelain = Color(0xFFEDE9FE),
    almond = Color(0xFFDCD3FF),
    blush = Color(0xFFF5D0FE),
    mistPink = Color(0xFFF0ABFC),
    dawnPink = Color(0xFFC084FC),
    roseBeige = Color(0xFFA855F7),
    roseDust = Color(0xFF9333EA),
    cocoa = Color(0xFF2E1065),
    ink = Color(0xFF1E1B4B),
    sage = Color(0xFF7C3AED),
    sageSoft = Color(0xFFE9D5FF),
    warmWhite = Color(0xFFFBF7FF),
    softGray = Color(0xFF7C6F9A),
    lineColor = Color(0xFF2E1065).copy(alpha = 0.12f)
)

/* 薄荷奶绿色彩系统 */
private val MintCreamPalette = AppColors(
    cream = Color(0xFFF3FAF7),
    paper = Color(0xFFF7FCFA),
    porcelain = Color(0xFFE8F4EF),
    almond = Color(0xFFC9E4D8),
    blush = Color(0xFFB8E0D0),
    mistPink = Color(0xFFA3D5C2),
    dawnPink = Color(0xFF5FA98D),
    roseBeige = Color(0xFF4A9A7D),
    roseDust = Color(0xFF3B7A65),
    cocoa = Color(0xFF2A4A3F),
    ink = Color(0xFF1F3830),
    sage = Color(0xFF5FA98D),
    sageSoft = Color(0xFFD4EDE4),
    warmWhite = Color(0xFFF7FCFA),
    softGray = Color(0xFF6A8B7E),
    lineColor = Color(0xFF2A4A3F).copy(alpha = 0.12f)
)

/* 海雾清晨色彩系统 */
private val SeaFogPalette = AppColors(
    cream = Color(0xFFF0F5FA),
    paper = Color(0xFFF5F9FC),
    porcelain = Color(0xFFE5EEF5),
    almond = Color(0xFFC8D9E8),
    blush = Color(0xFFBDD5E8),
    mistPink = Color(0xFFA8C6DE),
    dawnPink = Color(0xFF6A9CC2),
    roseBeige = Color(0xFF5A8FB8),
    roseDust = Color(0xFF4A7391),
    cocoa = Color(0xFF263A4A),
    ink = Color(0xFF1C2D3A),
    sage = Color(0xFF6A9CC2),
    sageSoft = Color(0xFFD4E5F0),
    warmWhite = Color(0xFFF5F9FC),
    softGray = Color(0xFF6A7F8F),
    lineColor = Color(0xFF263A4A).copy(alpha = 0.12f)
)

val LocalAppColors = staticCompositionLocalOf { WarmPalette }

@Composable
fun appColors(): AppColors = LocalAppColors.current

fun AppColors.softPanelGradient(): Brush = Brush.linearGradient(
    colors = listOf(warmWhite, blush.copy(alpha = 0.32f), warmWhite),
    start = androidx.compose.ui.geometry.Offset(0f, 0f),
    end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
)

fun AppColors.bookshelfHeaderGradient(): Brush = Brush.verticalGradient(
    colors = listOf(cream, cream.copy(alpha = 0.96f), porcelain.copy(alpha = 0.45f))
)

fun AppColors.roseDawnGradient(): Brush = Brush.linearGradient(
    colors = listOf(dawnPink.copy(alpha = 0.18f), sage.copy(alpha = 0.12f)),
    start = androidx.compose.ui.geometry.Offset(0f, 0f),
    end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
)

fun AppColors.purpleMagentaGradient(): Brush = Brush.linearGradient(
    colors = listOf(Color(0xFF7C3AED), Color(0xFFA855F7), Color(0xFFE879F9)),
    start = androidx.compose.ui.geometry.Offset(0f, 0f),
    end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, 0f)
)

private val RoseDawnColorScheme = lightColorScheme(
    primary = WarmPalette.dawnPink,
    onPrimary = Color.White,
    primaryContainer = WarmPalette.blush,
    onPrimaryContainer = WarmPalette.cocoa,
    secondary = WarmPalette.almond,
    onSecondary = WarmPalette.cocoa,
    background = WarmPalette.cream,
    onBackground = WarmPalette.cocoa,
    surface = WarmPalette.paper,
    onSurface = WarmPalette.cocoa,
    surfaceVariant = WarmPalette.porcelain,
    onSurfaceVariant = WarmPalette.softGray,
    error = WarmPalette.roseDust,
    outline = WarmPalette.almond
)

private val PurpleMagentaColorScheme = lightColorScheme(
    primary = PurpleMagentaPalette.roseBeige,
    onPrimary = Color.White,
    primaryContainer = PurpleMagentaPalette.blush,
    onPrimaryContainer = PurpleMagentaPalette.cocoa,
    secondary = PurpleMagentaPalette.almond,
    onSecondary = PurpleMagentaPalette.cocoa,
    background = PurpleMagentaPalette.cream,
    onBackground = PurpleMagentaPalette.cocoa,
    surface = PurpleMagentaPalette.paper,
    onSurface = PurpleMagentaPalette.cocoa,
    surfaceVariant = PurpleMagentaPalette.porcelain,
    onSurfaceVariant = PurpleMagentaPalette.softGray,
    error = PurpleMagentaPalette.roseDust,
    outline = PurpleMagentaPalette.almond
)

private val MintCreamColorScheme = lightColorScheme(
    primary = MintCreamPalette.roseBeige,
    onPrimary = Color.White,
    primaryContainer = MintCreamPalette.blush,
    onPrimaryContainer = MintCreamPalette.cocoa,
    secondary = MintCreamPalette.almond,
    onSecondary = MintCreamPalette.cocoa,
    background = MintCreamPalette.cream,
    onBackground = MintCreamPalette.cocoa,
    surface = MintCreamPalette.paper,
    onSurface = MintCreamPalette.cocoa,
    surfaceVariant = MintCreamPalette.porcelain,
    onSurfaceVariant = MintCreamPalette.softGray,
    error = MintCreamPalette.roseDust,
    outline = MintCreamPalette.almond
)

private val SeaFogColorScheme = lightColorScheme(
    primary = SeaFogPalette.roseBeige,
    onPrimary = Color.White,
    primaryContainer = SeaFogPalette.blush,
    onPrimaryContainer = SeaFogPalette.cocoa,
    secondary = SeaFogPalette.almond,
    onSecondary = SeaFogPalette.cocoa,
    background = SeaFogPalette.cream,
    onBackground = SeaFogPalette.cocoa,
    surface = SeaFogPalette.paper,
    onSurface = SeaFogPalette.cocoa,
    surfaceVariant = SeaFogPalette.porcelain,
    onSurfaceVariant = SeaFogPalette.softGray,
    error = SeaFogPalette.roseDust,
    outline = SeaFogPalette.almond
)

private val LovelyReaderTypography = Typography(
    displayMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 38.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

private val LovelyReaderShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
)

@Composable
fun LovelyReaderTheme(
    theme: AppTheme = AppTheme.Warm,
    content: @Composable () -> Unit
) {
    val colors = when (theme) {
        AppTheme.Warm -> WarmPalette
        AppTheme.PurpleMagenta -> PurpleMagentaPalette
        AppTheme.MintCream -> MintCreamPalette
        AppTheme.SeaFog -> SeaFogPalette
    }
    val colorScheme = when (theme) {
        AppTheme.Warm -> RoseDawnColorScheme
        AppTheme.PurpleMagenta -> PurpleMagentaColorScheme
        AppTheme.MintCream -> MintCreamColorScheme
        AppTheme.SeaFog -> SeaFogColorScheme
    }
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LovelyReaderTypography,
            shapes = LovelyReaderShapes,
            content = content
        )
    }
}
