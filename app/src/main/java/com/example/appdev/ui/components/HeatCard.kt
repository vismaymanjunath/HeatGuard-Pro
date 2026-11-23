package com.example.appdev.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.appdev.ui.theme.HeatColors

val CardShape = RoundedCornerShape(20.dp)

/** Standard elevated surface used across the app. Clickable only when [onClick] is provided. */
@Composable
fun HeatCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = HeatColors.Surface,
    border: Color = HeatColors.OutlineSubtle,
    contentPadding: Dp = 18.dp,
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val inner: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = CardShape,
            color = color,
            border = BorderStroke(1.dp, border),
            content = inner,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = CardShape,
            color = color,
            border = BorderStroke(1.dp, border),
            content = inner,
        )
    }
}
