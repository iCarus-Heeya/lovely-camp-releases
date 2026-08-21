package com.lovelyreader.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lovelyreader.ui.theme.appColors
import com.lovelyreader.ui.theme.softPanelGradient

@Composable
fun SoftPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = appColors().warmWhite),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, appColors().lineColor),
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 3.dp,
                shape = MaterialTheme.shapes.medium,
                ambientColor = appColors().blush.copy(alpha = 0.18f),
                spotColor = appColors().blush.copy(alpha = 0.1f)
            )
    ) {
        Column(
            modifier = Modifier
                .background(appColors().softPanelGradient())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
fun <T> SegmentedTextButtons(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit
) {
    Surface(
        color = appColors().warmWhite,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, appColors().lineColor),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            options.forEach { option ->
                val active = option == selected
                val containerColor by animateColorAsState(
                    targetValue = if (active) appColors().blush.copy(alpha = 0.85f) else Color.Transparent,
                    label = "segmentContainer"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (active) appColors().cocoa else appColors().cocoa.copy(alpha = 0.58f),
                    label = "segmentContent"
                )
                TextButton(
                    onClick = { onSelected(option) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = containerColor,
                        contentColor = contentColor
                    )
                ) {
                    Text(
                        label(option),
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun FilterChipText(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) appColors().blush.copy(alpha = 0.85f) else appColors().warmWhite,
        label = "chipContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) appColors().cocoa else appColors().cocoa.copy(alpha = 0.66f),
        label = "chipContent"
    )
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (selected) appColors().blush.copy(alpha = 0.5f) else appColors().lineColor),
        colors = ButtonDefaults.textButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Text(
            text,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
