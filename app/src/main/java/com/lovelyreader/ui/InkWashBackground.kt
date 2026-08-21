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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
            val branch = colors.roseDust.copy(alpha = 0.10f)
            val branchPath = Path().apply {
                moveTo(size.width * 0.82f, size.height * 0.02f)
                cubicTo(size.width * 0.86f, size.height * 0.10f, size.width * 0.93f, size.height * 0.14f, size.width, size.height * 0.16f)
            }
            drawPath(branchPath, branch, style = Stroke(width = 3f, cap = StrokeCap.Round))
            drawLine(branch, Offset(size.width * .86f, size.height * .07f), Offset(size.width * .93f, size.height * .02f), strokeWidth = 2f)
            drawLine(branch, Offset(size.width * .91f, size.height * .11f), Offset(size.width * .98f, size.height * .06f), strokeWidth = 2f)

            val mountain = colors.almond.copy(alpha = 0.20f)
            val mountainPath = Path().apply {
                moveTo(0f, size.height)
                lineTo(0f, size.height * .92f)
                cubicTo(size.width * .14f, size.height * .83f, size.width * .22f, size.height * .93f, size.width * .34f, size.height * .86f)
                cubicTo(size.width * .48f, size.height * .77f, size.width * .56f, size.height * .90f, size.width * .68f, size.height * .83f)
                cubicTo(size.width * .80f, size.height * .77f, size.width * .87f, size.height * .87f, size.width, size.height * .78f)
                lineTo(size.width, size.height)
                close()
            }
            drawPath(mountainPath, mountain)
        }
        content()
    }
}
