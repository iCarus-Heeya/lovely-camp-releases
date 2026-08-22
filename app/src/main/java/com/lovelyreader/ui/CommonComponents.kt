package com.lovelyreader.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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

/** Header used by the high-fidelity 9:16 phone layouts. */
@Composable
fun HighFidelityHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    onNotes: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Row(
                modifier = Modifier.clickable(onClick = onBack),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = appColors().ink,
                    modifier = Modifier.size(26.dp)
                )
                Text("返回", style = MaterialTheme.typography.titleMedium, color = appColors().ink)
            }
        } else {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = appColors().ink,
                modifier = Modifier.weight(1f)
            )
        }
        if (onBack != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = appColors().ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = (if (trailing != null) Modifier.width(120.dp) else Modifier.weight(1f))
                    .padding(horizontal = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        if (trailing != null) {
            Box(modifier = Modifier.width(140.dp), contentAlignment = Alignment.Center) {
                trailing()
            }
        } else if (onNotes != null) {
            Row(
                modifier = Modifier.clickable(onClick = onNotes),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.EditNote,
                    contentDescription = "小纸条",
                    tint = appColors().ink,
                    modifier = Modifier.size(27.dp)
                )
                Text("小纸条", style = MaterialTheme.typography.titleMedium, color = appColors().ink)
            }
        } else if (onBack != null) {
            Spacer(modifier = Modifier.width(24.dp))
        }
    }
}

@Composable
fun HighFidelityDiscoveryTabs(
    selected: String,
    labels: List<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEach { label ->
            val active = label == selected
            TextButton(
                onClick = { onSelected(label) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(0.dp),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = if (active) appColors().roseDust else appColors().ink
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .height(2.dp)
                            .fillMaxWidth()
                            .background(if (active) appColors().roseDust else Color.Transparent)
                    )
                }
            }
        }
    }
}

@Composable
fun HighFidelitySearchEntry(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = appColors().warmWhite.copy(alpha = .78f),
        border = BorderStroke(1.dp, appColors().lineColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(shape = RoundedCornerShape(50), color = appColors().porcelain) {
                Icon(Icons.Outlined.Search, contentDescription = "找书", modifier = Modifier.padding(10.dp), tint = appColors().ink)
            }
            Text("找书", style = MaterialTheme.typography.titleLarge, color = appColors().ink, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.KeyboardArrowRight, contentDescription = null, tint = appColors().softGray)
        }
    }
}

@Composable
fun HighFidelitySectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = appColors().ink,
        modifier = modifier
    )
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
