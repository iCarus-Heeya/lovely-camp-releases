package com.lovelyreader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.lovelyreader.ui.theme.appColors

/** Shared paper shell used by normal pages in the high-fidelity design. */
@Composable
fun InkWashBackground(
    modifier: Modifier = Modifier,
    decorationStyle: HighFidelityPaperDecorationStyle = HighFidelityPaperDecorationStyle.Plum,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = appColors()
    val decoration = highFidelityPaperDecoration()
    Box(modifier = modifier.background(colors.cream)) {
        Canvas(Modifier.fillMaxSize()) {
            // The concept art uses a recognisable plum branch rather than two
            // placeholder strokes. Keep it vector-based so it scales cleanly
            // across 9:16 phones while preserving the quiet paper texture.
            // Keep the decorative branch visible on a 9:16 phone viewport.  The
            // concept uses a quiet but recognisable ink wash; the old alpha was
            // so faint that the branch disappeared on real devices.
            // The concept uses a low-contrast ink branch, not a rose-colored
            // foreground stroke. Keep it neutral so the decoration recedes
            // behind readable content on every page.
            val branch = Color(0xFF8F877F).copy(alpha = decoration.branchAlpha)
            if (decorationStyle == HighFidelityPaperDecorationStyle.Willow) {
                val willowPath = Path().apply {
                    moveTo(size.width * .68f, size.height * .02f)
                    cubicTo(size.width * .78f, size.height * .08f, size.width * .87f, size.height * .18f, size.width * 1.06f, size.height * .31f)
                }
                drawPath(willowPath, branch, style = Stroke(width = 4f, cap = StrokeCap.Round))
                val willowLeaves = listOf(
                    Triple(.74f, .07f, -36f), Triple(.80f, .11f, 26f),
                    Triple(.86f, .16f, -28f), Triple(.91f, .20f, 38f),
                    Triple(.96f, .25f, -34f), Triple(.84f, .25f, 30f),
                    Triple(.72f, .15f, -44f), Triple(.99f, .15f, 24f),
                    Triple(.89f, .31f, -22f), Triple(.77f, .28f, 38f)
                )
                willowLeaves.forEach { (x, y, angle) ->
                    val center = Offset(size.width * x, size.height * y)
                    withTransform({ rotate(angle, center) }) {
                        drawOval(
                            Color(0xFF9B9C87).copy(alpha = .17f),
                            topLeft = Offset(center.x - 5f, center.y - 12f),
                            size = Size(10f, 24f)
                        )
                    }
                }
                listOf(.81f to .08f, .91f to .19f, .99f to .25f).forEach { (x, y) ->
                    drawCircle(Color(0xFFB77F6E).copy(alpha = .22f), radius = 5f, center = Offset(size.width * x, size.height * y))
                }
            }
            val branchPath = Path().apply {
                moveTo(size.width * 0.58f, size.height * 0.015f)
                cubicTo(size.width * 0.68f, size.height * 0.055f, size.width * 0.84f, size.height * 0.11f, size.width * 1.04f, size.height * 0.22f)
            }
            if (decorationStyle == HighFidelityPaperDecorationStyle.Plum) {
                drawPath(branchPath, branch, style = Stroke(width = 5f, cap = StrokeCap.Round))
            }
            val twigs = listOf(
                Offset(.68f, .065f) to Offset(.56f, -.005f),
                Offset(.76f, .085f) to Offset(.90f, .005f),
                Offset(.84f, .115f) to Offset(.72f, .22f),
                Offset(.94f, .15f) to Offset(1.04f, .075f),
                Offset(.63f, .045f) to Offset(.74f, -.02f)
            )
            if (decorationStyle == HighFidelityPaperDecorationStyle.Plum) {
                twigs.forEach { (from, to) ->
                    drawLine(
                        branch,
                        Offset(size.width * from.x, size.height * from.y),
                        Offset(size.width * to.x, size.height * to.y),
                        strokeWidth = 2.5f,
                        cap = StrokeCap.Round
                    )
                }
            }
            val blossoms = listOf(
                .58f to .005f, .68f to .08f, .77f to .035f, .85f to .105f,
                .93f to .06f, .98f to .15f, .73f to .18f, 1.02f to .12f,
                .88f to .20f
            )
            if (decorationStyle == HighFidelityPaperDecorationStyle.Plum) blossoms.forEach { (x, y) ->
                val center = Offset(size.width * x, size.height * y)
                val petal = Color(0xFFB8AAA0).copy(alpha = decoration.blossomAlpha)
                listOf(-0.9f, -0.2f, 0.5f, 1.15f, 1.85f).forEach { radians ->
                    val petalCenter = Offset(
                        center.x + kotlin.math.cos(radians) * 10f,
                        center.y + kotlin.math.sin(radians) * 10f
                    )
                    drawOval(
                        petal,
                        topLeft = Offset(petalCenter.x - 5f, petalCenter.y - 9f),
                        size = Size(10f, 18f)
                    )
                }
                drawCircle(Color(0xFF9C9189).copy(alpha = .18f), radius = 3.5f, center = center)
            }

            // Pale leaves keep the decoration recognisable at a glance while
            // remaining behind content and readable on small 9:16 screens.
            val leaves = listOf(
                Triple(.86f, .03f, -34f), Triple(.90f, .07f, 26f),
                Triple(.96f, .10f, -22f), Triple(1.01f, .13f, 38f),
                Triple(1.05f, .17f, -32f), Triple(.92f, .16f, 28f),
                Triple(.80f, .10f, -42f), Triple(.98f, .04f, 30f)
            )
            if (decorationStyle == HighFidelityPaperDecorationStyle.Plum) leaves.forEach { (x, y, angle) ->
                val center = Offset(size.width * x, size.height * y)
                withTransform({ rotate(angle, center) }) {
                    drawOval(
                        Color(0xFFB8AAA0).copy(alpha = .22f),
                        topLeft = Offset(center.x - 5f, center.y - 12f),
                        size = Size(10f, 24f)
                    )
                    drawLine(
                        Color(0xFF8F877F).copy(alpha = .08f),
                        Offset(center.x, center.y - 10f),
                        Offset(center.x, center.y + 10f),
                        strokeWidth = 1.2f
                    )
                }
            }

            val mountain = colors.almond.copy(alpha = decoration.mountainAlpha)
            val mountainPath = Path().apply {
                moveTo(0f, size.height)
                lineTo(0f, size.height * .94f)
                cubicTo(size.width * .10f, size.height * .84f, size.width * .18f, size.height * .93f, size.width * .30f, size.height * .86f)
                cubicTo(size.width * .43f, size.height * .75f, size.width * .53f, size.height * .91f, size.width * .65f, size.height * .83f)
                cubicTo(size.width * .77f, size.height * .74f, size.width * .86f, size.height * .88f, size.width, size.height * .78f)
                lineTo(size.width, size.height)
                close()
            }
            drawPath(mountainPath, mountain)
            val distant = colors.almond.copy(alpha = .16f)
            val distantPath = Path().apply {
                moveTo(0f, size.height)
                lineTo(0f, size.height * .975f)
                cubicTo(size.width * .18f, size.height * .88f, size.width * .28f, size.height * .96f, size.width * .42f, size.height * .91f)
                cubicTo(size.width * .58f, size.height * .84f, size.width * .73f, size.height * .96f, size.width, size.height * .86f)
                lineTo(size.width, size.height)
                close()
            }
            drawPath(distantPath, distant)

            // A quiet pagoda silhouette gives the lower paper area the same
            // visual anchor as the concept art without loading a bitmap.
            val pavilionX = size.width * .22f
            val pavilionY = size.height * .935f
            val pavilion = Color(0xFF8F877F).copy(alpha = .08f)
            drawRect(pavilion, Offset(pavilionX - 12f, pavilionY - 30f), Size(24f, 30f))
            drawRect(pavilion, Offset(pavilionX - 18f, pavilionY - 34f), Size(36f, 4f))
            drawLine(pavilion, Offset(pavilionX - 24f, pavilionY - 38f), Offset(pavilionX + 24f, pavilionY - 38f), strokeWidth = 4f)
            drawLine(pavilion, Offset(pavilionX - 14f, pavilionY - 44f), Offset(pavilionX + 14f, pavilionY - 44f), strokeWidth = 3f)
        }
        content()
    }
}
