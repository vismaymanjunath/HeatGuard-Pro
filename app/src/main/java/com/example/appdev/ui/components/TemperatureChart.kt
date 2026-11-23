package com.example.appdev.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.appdev.R
import com.example.appdev.domain.model.TemperatureUnit
import com.example.appdev.domain.model.Temperatures
import com.example.appdev.domain.thermal.TemperaturePoint
import com.example.appdev.ui.theme.HeatColors

/** A reference line drawn across the chart, e.g. the alert threshold or a recovery target. */
data class ChartReference(val valueC: Float, val color: Color)

/**
 * Line chart of temperature over time. All values are Celsius; axis labels are converted to [unit].
 * [startLabel]/[endLabel] caption the left and right ends of the time axis.
 */
@Composable
fun TemperatureChart(
    points: List<TemperaturePoint>,
    unit: TemperatureUnit,
    modifier: Modifier = Modifier,
    references: List<ChartReference> = emptyList(),
    lineColor: Color = HeatColors.Accent,
    startLabel: String = "",
    endLabel: String = "",
    emptyText: String = "",
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = HeatColors.TextTertiary, fontSize = MaterialTheme.typography.labelSmall.fontSize)
    val locale = Locale.current.platformLocale
    val axis = remember(points, references) {
        ChartScale.yAxis(points.map { it.temperatureC }, references.map { it.valueC })
    }
    val tickLabels = remember(axis, unit) {
        axis.ticks.map { String.format(locale, "%.0f", Temperatures.fromCelsius(it, unit)) }
    }

    val description = if (points.isEmpty()) {
        stringResource(R.string.a11y_chart_empty)
    } else {
        pluralStringResource(
            R.plurals.a11y_chart,
            points.size,
            points.size,
            Temperatures.format(points.minOf { it.temperatureC }, unit),
            Temperatures.format(points.maxOf { it.temperatureC }, unit),
            Temperatures.format(points.last().temperatureC, unit),
        )
    }
    Box(modifier.semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val leftPad = 34.dp.toPx()
            val bottomPad = if (startLabel.isNotEmpty() || endLabel.isNotEmpty()) 20.dp.toPx() else 4.dp.toPx()
            val topPad = 6.dp.toPx()
            val rightPad = 8.dp.toPx()
            val plotW = size.width - leftPad - rightPad
            val plotH = size.height - topPad - bottomPad
            if (plotW <= 0f || plotH <= 0f) return@Canvas

            fun yOf(c: Float) = topPad + plotH * (1f - axis.fraction(c))

            // Grid and Y labels.
            axis.ticks.forEachIndexed { i, tick ->
                val y = yOf(tick)
                drawLine(HeatColors.OutlineSubtle, Offset(leftPad, y), Offset(leftPad + plotW, y), strokeWidth = 1.dp.toPx())
                val layout = measurer.measure(tickLabels[i], labelStyle)
                drawText(layout, topLeft = Offset(leftPad - layout.size.width - 6.dp.toPx(), y - layout.size.height / 2f))
            }

            // Reference lines.
            references.forEach { ref ->
                val y = yOf(ref.valueC)
                drawLine(
                    color = ref.color,
                    start = Offset(leftPad, y),
                    end = Offset(leftPad + plotW, y),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx())),
                )
            }

            // X labels.
            if (startLabel.isNotEmpty()) {
                val layout = measurer.measure(startLabel, labelStyle)
                drawText(layout, topLeft = Offset(leftPad, size.height - layout.size.height))
            }
            if (endLabel.isNotEmpty()) {
                val layout = measurer.measure(endLabel, labelStyle)
                drawText(layout, topLeft = Offset(leftPad + plotW - layout.size.width, size.height - layout.size.height))
            }

            if (points.size < 2) return@Canvas

            val t0 = points.first().timestampMillis
            val span = (points.last().timestampMillis - t0).coerceAtLeast(1L)
            fun xOf(t: Long) = leftPad + plotW * ((t - t0).toFloat() / span)

            val line = Path()
            points.forEachIndexed { i, p ->
                val x = xOf(p.timestampMillis)
                val y = yOf(p.temperatureC)
                if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(xOf(points.last().timestampMillis), topPad + plotH)
                lineTo(xOf(points.first().timestampMillis), topPad + plotH)
                close()
            }
            drawPath(
                fill,
                Brush.verticalGradient(
                    listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0f)),
                    startY = topPad,
                    endY = topPad + plotH,
                ),
            )
            drawPath(line, lineColor, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            val last = points.last()
            val center = Offset(xOf(last.timestampMillis), yOf(last.temperatureC))
            drawCircle(lineColor.copy(alpha = 0.25f), radius = 8.dp.toPx(), center = center)
            drawCircle(lineColor, radius = 4.dp.toPx(), center = center)
        }
        if (points.size < 2 && emptyText.isNotEmpty()) {
            Text(emptyText, style = MaterialTheme.typography.bodySmall, color = HeatColors.TextTertiary)
        }
    }
}
