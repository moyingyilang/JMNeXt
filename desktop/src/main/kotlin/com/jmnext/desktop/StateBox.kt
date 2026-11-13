package com.jmnext.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 空态 / 错误态，对应 Android 的 `ui/components/StateBox.kt`。
 *
 * Android 的版本带 Material 图标；桌面端没有引 material-icons 依赖，
 * 这里用一个描边圆 + 短横的占位图形表示「没有内容 / 拿不到内容」——
 * 形状本身不承载信息，靠标题与说明表达，所以不引依赖是划算的。
 */
@Composable
fun MessageState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Canvas(Modifier.size(40.dp)) {
            val stroke = size.minDimension * 0.06f
            drawCircle(color = c.onSurfaceVariant, radius = size.minDimension / 2f - stroke, style = Stroke(stroke))
            drawLine(
                color = c.onSurfaceVariant,
                start = Offset(size.width * 0.34f, size.height * 0.5f),
                end = Offset(size.width * 0.66f, size.height * 0.5f),
                strokeWidth = stroke * 1.2f,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = c.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.md),
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = c.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        if (onRetry != null) {
            TextButton(onClick = onRetry, modifier = Modifier.padding(top = Spacing.sm)) {
                Text(com.jmnext.desktop.i18n.tl("重试"), color = c.primary)
            }
        }
    }
}

/** 加载中：与 [MessageState] 同一套间距，仅换成转圈。 */
@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(36.dp),
        )
    }
}
