package com.example.appdev.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.appdev.ui.theme.HeatColors
import kotlin.math.cos
import kotlin.math.sin

private const val START_ANGLE = 135f
private const val SWEEP_ANGLE = 270f

/**
 * A 270° arc gauge. The value arc animates between readings; tick marks show where the
 * orange band and the red threshold begin.
 */
@Composable
fun TemperatureGauge(
    temperatureC: Float?,
    thresholdC: Float,
    warningMarginC: Float,
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val targetFraction = temperatureC?.let { GaugeScale.fraction(it, thresholdC) } ?: 0f
    val fraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "gaugeFraction",
    )
    val arcColor by animateColorAsState(color, tween(600), label = "gaugeColor")
    val thresholdFraction = GaugeScale.fraction(thresholdC, thresholdC)
    val warningFraction = GaugeScale.fraction(thresholdC - warningMarginC, thresholdC)

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2f + 12.dp.toPx()
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = HeatColors.SurfaceHighest,
                startAngle = START_ANGLE, sweepAngle = SWEEP_ANGLE, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (temperatureC != null && fraction > 0f) {
                // Soft glow under the value arc.
                drawArc(
                    color = arcColor.copy(alpha = 0.18f),
                    startAngle = START_ANGLE, sweepAngle = SWEEP_ANGLE * fraction, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(width = stroke * 2.2f, cap = StrokeCap.Round),
                )
                drawArc(
                    color = arcColor,
                    startAngle = START_ANGLE, sweepAngle = SWEEP_ANGLE * fraction, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            val radius = arcSize.width / 2f
            drawTick(warningFraction, radius, stroke, HeatColors.Elevated)
            drawTick(thresholdFraction, radius, stroke, HeatColors.Critical)
        }
        content()
    }
}

private fun DrawScope.drawTick(fraction: Float, radius: Float, stroke: Float, color: Color) {
    val angle = Math.toRadians((START_ANGLE + SWEEP_ANGLE * fraction).toDouble())
    val inner = radius + stroke / 2f + 3.dp.toPx()
    val outer = radius + stroke / 2f + 11.dp.toPx()
    val c = center
    drawLine(
        color = color,
        start = Offset(c.x + inner * cos(angle).toFloat(), c.y + inner * sin(angle).toFloat()),
        end = Offset(c.x + outer * cos(angle).toFloat(), c.y + outer * sin(angle).toFloat()),
        strokeWidth = 3.dp.toPx(),
        cap = StrokeCap.Round,
    )
}
