package com.example.appdev.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.appdev.ui.theme.HeatColors

data class Bar(val value: Int, val label: String, val highlighted: Boolean = false)

/** Simple column chart for small counts (days, times of day). Bars are scaled to the largest value. */
@Composable
fun BarChart(
    bars: List<Bar>,
    modifier: Modifier = Modifier,
    color: Color = HeatColors.Accent,
    highlightColor: Color = HeatColors.Critical,
    barAreaHeight: androidx.compose.ui.unit.Dp = 84.dp,
) {
    val maxValue = (bars.maxOfOrNull { it.value } ?: 0).coerceAtLeast(1)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEach { bar ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { contentDescription = "${bar.label}: ${bar.value}" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    bar.value.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (bar.value > 0) HeatColors.TextPrimary else HeatColors.TextTertiary,
                )
                Box(Modifier.height(barAreaHeight), contentAlignment = Alignment.BottomCenter) {
                    val fraction = bar.value.toFloat() / maxValue
                    Box(
                        Modifier
                            .width(22.dp)
                            .height((barAreaHeight * fraction).coerceAtLeast(3.dp))
                            .background(
                                when {
                                    bar.value == 0 -> HeatColors.SurfaceHighest
                                    bar.highlighted -> highlightColor
                                    else -> color
                                },
                                RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                            ),
                    )
                }
                Text(bar.label, style = MaterialTheme.typography.labelSmall, color = HeatColors.TextSecondary, maxLines = 1)
            }
        }
    }
}
