package com.lichong.calibrereader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import com.lichong.calibrereader.CalibreApp

/**
 * 带认证的封面图。无封面 / 加载中时显示书名首字占位。
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
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                fallbackText.trim().take(2),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
