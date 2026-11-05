package com.lichong.calibrereader.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** 对齐 weknora-mobile 的 18~24px 大圆角，替代 Material 3 默认的小圆角。 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Button / FilledCard 等不读 MaterialTheme.shapes 的组件需要显式指定。 */
val AppButtonShape = RoundedCornerShape(16.dp)
val AppCardShape = RoundedCornerShape(20.dp)
val AppCoverShape = RoundedCornerShape(12.dp)
