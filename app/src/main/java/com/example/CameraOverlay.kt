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
        
        // Passport Photo Frame (3.5cm x 4.5cm aspect ratio)
        val frameWidth = canvasWidth * 0.65f
        val frameHeight = frameWidth * (4.5f / 3.5f)
        val frameLeft = (canvasWidth - frameWidth) / 2
        val frameTop = (canvasHeight - frameHeight) / 3f
        
        // Clear the frame area
        drawRect(
            color = Color.Transparent,
            topLeft = Offset(frameLeft, frameTop),
            size = Size(frameWidth, frameHeight),
            blendMode = BlendMode.Clear
        )
        
        // Draw Frame Border
        drawRect(
            color = Color.White,
            topLeft = Offset(frameLeft, frameTop),
            size = Size(frameWidth, frameHeight),
            style = Stroke(width = 2.dp.toPx())
        )

        // Passport Oval (face position within the frame)
        val ovalWidth = frameWidth * 0.6f
        val ovalHeight = ovalWidth * 1.35f
        val ovalLeft = (canvasWidth - ovalWidth) / 2
        val ovalTop = frameTop + (frameHeight * 0.15f)
        
        val rect = Rect(
            offset = Offset(ovalLeft, ovalTop),
            size = Size(ovalWidth, ovalHeight)
        )
        
        // Draw Oval Guide
        drawOval(
            color = Color.White.copy(alpha = 0.8f),
            topLeft = rect.topLeft,
            size = rect.size,
            style = Stroke(width = 2.dp.toPx())
        )
        
        // Shoulder guides
        val shoulderWidth = frameWidth * 1.1f
        val shoulderTop = ovalTop + ovalHeight * 0.85f
        val shoulderLeft = (canvasWidth - shoulderWidth) / 2
        
        drawPath(
            path = Path().apply {
                moveTo(shoulderLeft, frameTop + frameHeight)
                quadraticTo(
                    shoulderLeft, shoulderTop,
                    canvasWidth / 2, shoulderTop
                )
                quadraticTo(
                    shoulderLeft + shoulderWidth, shoulderTop,
                    shoulderLeft + shoulderWidth, frameTop + frameHeight
                )
            },
            color = Color.White.copy(alpha = 0.6f),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}
