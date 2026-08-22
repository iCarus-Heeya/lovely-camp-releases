package com.lovelyreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lovelyreader.ui.theme.appColors
import kotlin.math.absoluteValue

@Composable
private fun bookGradients(): List<Brush> {
    val colors = appColors()
    return listOf(
        Brush.linearGradient(listOf(colors.dawnPink, colors.roseDust)),
        Brush.linearGradient(listOf(colors.sage, colors.sageSoft)),
        Brush.linearGradient(listOf(colors.almond, colors.cream)),
        Brush.linearGradient(listOf(colors.blush, colors.warmWhite)),
        Brush.linearGradient(listOf(colors.porcelain, colors.almond))
    )
}

@Composable
private fun coverGradientFor(title: String): Brush {
    val gradients = bookGradients()
    val index = title.hashCode().absoluteValue % gradients.size
    return gradients[index]
}

@Composable
private fun textColorFor(title: String): Color {
    return appColors().ink
}

@Composable
fun RealBookCover(
    title: String,
    author: String,
    modifier: Modifier = Modifier,
    showAuthor: Boolean = true
) {
    val gradient = coverGradientFor(title)
    val textColor = textColorFor(title)
    val coverColors = appColors()

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
                .drawBehind {
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.White.copy(alpha = 0.28f), Color.Transparent),
                            start = Offset(0f, 0f),
                            end = Offset(size.width * 0.55f, size.height * 0.55f)
                        )
                    )
                    // Keep the no-network fallback visually rich instead of
                    // exposing a flat color block while a real cover loads.
                    drawCircle(
                        color = coverColors.cocoa.copy(alpha = .18f),
                        radius = size.minDimension * .34f,
                        center = Offset(size.width * .54f, size.height * .36f)
                    )
                    drawCircle(
                        color = coverColors.almond.copy(alpha = .40f),
                        radius = size.minDimension * .22f,
                        center = Offset(size.width * .54f, size.height * .32f)
                    )
                    drawOval(
                        color = coverColors.cocoa.copy(alpha = .20f),
                        topLeft = Offset(size.width * .10f, size.height * .64f),
                        size = androidx.compose.ui.geometry.Size(size.width * .80f, size.height * .22f)
                    )
                }
        )

        // Spine
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(7.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.08f)
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .padding(start = 7.dp)
                .background(Color.Black.copy(alpha = 0.1f))
        )

        // Page edges on the right
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 5.dp)
                .fillMaxHeight(0.62f),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.5.dp)
                        .background(Color.White.copy(alpha = 0.35f))
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp, end = 10.dp, top = 18.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = textColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            if (showAuthor && author.isNotBlank()) {
                Text(
                    text = author,
                    color = textColor.copy(alpha = 0.72f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun SmallBookCover(
    title: String,
    author: String,
    modifier: Modifier = Modifier
) {
    val gradient = coverGradientFor(title)
    val textColor = textColorFor(title)
    val coverColors = appColors()

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
                .drawBehind {
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.White.copy(alpha = 0.25f), Color.Transparent),
                            start = Offset(0f, 0f),
                            end = Offset(size.width * 0.5f, size.height * 0.5f)
                        )
                    )
                    drawCircle(
                        color = coverColors.cocoa.copy(alpha = .16f),
                        radius = size.minDimension * .30f,
                        center = Offset(size.width * .55f, size.height * .34f)
                    )
                    drawOval(
                        color = coverColors.cocoa.copy(alpha = .18f),
                        topLeft = Offset(size.width * .12f, size.height * .64f),
                        size = androidx.compose.ui.geometry.Size(size.width * .76f, size.height * .20f)
                    )
                }
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.3f), Color.Black.copy(alpha = 0.08f))
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .padding(start = 4.dp)
                .background(Color.Black.copy(alpha = 0.1f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 6.dp, top = 10.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 14.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (author.isNotBlank()) {
                Text(
                    text = author,
                    color = textColor.copy(alpha = 0.72f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
