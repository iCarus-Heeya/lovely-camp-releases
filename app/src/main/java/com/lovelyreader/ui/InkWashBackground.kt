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
    content: @Composable BoxScope.() -> Unit
) {
    val colors = appColors()
    Box(modifier = modifier.background(colors.cream)) {
        Canvas(Modifier.fillMaxSize()) {
            // The concept art uses a recognisable plum branch rather than two
            // placeholder strokes. Keep it vector-based so it scales cleanly
            // across 9:16 phones while preserving the quiet paper texture.
            val branch = colors.roseDust.copy(alpha = 0.12f)
            val branchPath = Path().apply {
                moveTo(size.width * 0.72f, size.height * 0.015f)
                cubicTo(size.width * 0.78f, size.height * 0.08f, size.width * 0.88f, size.height * 0.12f, size.width * 1.02f, size.height * 0.18f)
            }
            drawPath(branchPath, branch, style = Stroke(width = 5f, cap = StrokeCap.Round))
            val twigs = listOf(
                Offset(.78f, .075f) to Offset(.68f, .018f),
                Offset(.82f, .095f) to Offset(.92f, .025f),
                Offset(.87f, .125f) to Offset(.76f, .20f),
                Offset(.93f, .15f) to Offset(1.01f, .08f),
                Offset(.75f, .055f) to Offset(.84f, -.015f)
            )
            twigs.forEach { (from, to) ->
                drawLine(
                    branch,
                    Offset(size.width * from.x, size.height * from.y),
                    Offset(size.width * to.x, size.height * to.y),
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round
                )
            }
            val blossoms = listOf(
                .70f to .02f, .73f to .10f, .80f to .04f, .84f to .11f,
                .90f to .06f, .91f to .15f, .77f to .18f, .96f to .11f,
                .86f to .19f
            )
            blossoms.forEach { (x, y) ->
                val center = Offset(size.width * x, size.height * y)
                val petal = colors.roseBeige.copy(alpha = .16f)
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
                drawCircle(colors.roseDust.copy(alpha = .18f), radius = 3f, center = center)
            }

            // Pale leaves keep the decoration recognisable at a glance while
            // remaining behind content and readable on small 9:16 screens.
            val leaves = listOf(
                Triple(.76f, .03f, -34f), Triple(.80f, .07f, 26f),
                Triple(.86f, .10f, -22f), Triple(.91f, .13f, 38f),
                Triple(.95f, .17f, -32f), Triple(.82f, .16f, 28f),
                Triple(.70f, .10f, -42f), Triple(.88f, .04f, 30f)
            )
            leaves.forEach { (x, y, angle) ->
                val center = Offset(size.width * x, size.height * y)
                withTransform({ rotate(angle, center) }) {
                    drawOval(
                        colors.almond.copy(alpha = .22f),
                        topLeft = Offset(center.x - 5f, center.y - 12f),
                        size = Size(10f, 24f)
                    )
                    drawLine(
                        colors.roseDust.copy(alpha = .10f),
                        Offset(center.x, center.y - 10f),
                        Offset(center.x, center.y + 10f),
                        strokeWidth = 1.2f
                    )
                }
            }

            val mountain = colors.almond.copy(alpha = 0.24f)
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
            val distant = colors.almond.copy(alpha = .12f)
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
            val pavilion = colors.roseDust.copy(alpha = .10f)
            drawRect(pavilion, Offset(pavilionX - 12f, pavilionY - 30f), Size(24f, 30f))
            drawRect(pavilion, Offset(pavilionX - 18f, pavilionY - 34f), Size(36f, 4f))
            drawLine(pavilion, Offset(pavilionX - 24f, pavilionY - 38f), Offset(pavilionX + 24f, pavilionY - 38f), strokeWidth = 4f)
            drawLine(pavilion, Offset(pavilionX - 14f, pavilionY - 44f), Offset(pavilionX + 14f, pavilionY - 44f), strokeWidth = 3f)
        }
        content()
    }
}
