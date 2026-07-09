package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun PassportOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        
        // Dark overlay
        drawRect(
            color = Color.Black.copy(alpha = 0.5f),
            size = size
        )
        
        // Passport Oval (approximate face position)
        val ovalWidth = canvasWidth * 0.38f
        val ovalHeight = ovalWidth * 1.4f
        val ovalLeft = (canvasWidth - ovalWidth) / 2
        val ovalTop = (canvasHeight - ovalHeight) / 3.2f
        
        val rect = Rect(
            offset = Offset(ovalLeft, ovalTop),
            size = Size(ovalWidth, ovalHeight)
        )
        
        // Clear the oval area
        drawOval(
            color = Color.Transparent,
            topLeft = rect.topLeft,
            size = rect.size,
            blendMode = BlendMode.Clear
        )
        
        // Draw the guide border
        drawOval(
            color = Color.White,
            topLeft = rect.topLeft,
            size = rect.size,
            style = Stroke(width = 2.dp.toPx())
        )
        
        // Shoulder guides
        val shoulderWidth = canvasWidth * 0.8f
        val shoulderTop = ovalTop + ovalHeight * 0.8f
        val shoulderLeft = (canvasWidth - shoulderWidth) / 2
        
        drawPath(
            path = Path().apply {
                moveTo(shoulderLeft, canvasHeight)
                quadraticTo(
                    shoulderLeft, shoulderTop,
                    canvasWidth / 2, shoulderTop
                )
                quadraticTo(
                    shoulderLeft + shoulderWidth, shoulderTop,
                    shoulderLeft + shoulderWidth, canvasHeight
                )
            },
            color = Color.White,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}
