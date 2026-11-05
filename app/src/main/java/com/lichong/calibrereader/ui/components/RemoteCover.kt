package com.lichong.calibrereader.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lichong.calibrereader.CalibreApp

/**
 * 带认证的封面图。无封面 / 加载中时用品牌渐变 + 书脊线做占位，
 * 而不是一块平涂色，书库网格整体会整齐很多。
 */
@Composable
fun RemoteCover(
    url: String?,
    fallbackText: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val container = remember(context) { (context.applicationContext as CalibreApp).container }

    val bitmap by produceState(initialValue = url?.let { container.covers.peek(it) }, url) {
        if (url.isNullOrBlank()) {
            value = null
            return@produceState
        }
        val cached = container.covers.peek(url)
        if (cached != null) {
            value = cached
            return@produceState
        }
        value = container.covers.load(url)
    }

    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    } else {
        val scheme = MaterialTheme.colorScheme
        Box(
            modifier = modifier.background(
                Brush.linearGradient(
                    listOf(
                        scheme.primary.copy(alpha = 0.82f),
                        scheme.primaryContainer
                    )
                )
            ),
            contentAlignment = Alignment.Center
        ) {
            // 书脊
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(Color.White.copy(alpha = 0.24f))
            )
            // 书页装饰线
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.28f))
            )
            Text(
                fallbackText.trim().take(2),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}
