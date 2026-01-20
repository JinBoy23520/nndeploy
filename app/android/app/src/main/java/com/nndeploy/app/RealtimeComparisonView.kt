package com.nndeploy.app

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 实时对比视图组件
 * 左边显示原始视频帧，右边显示超分后的帧
 */
@Composable
fun RealtimeComparisonView(
    originalFrame: Bitmap?,
    superResFrame: Bitmap?,
    frameIndex: Int,
    totalFrames: Int,
    isPlaying: Boolean,
    algorithmName: String = "Super-Res",
    onPlayPauseClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "原始视频",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            Divider(
                modifier = Modifier
                    .width(2.dp)
                    .height(24.dp),
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "超分视频",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        
        // 视频帧显示区域
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black)
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val halfWidth = canvasWidth / 2
                
                // 绘制左侧原始帧
                originalFrame?.let { bitmap ->
                    drawBitmap(
                        bitmap = bitmap,
                        destOffset = Offset(0f, 0f),
                        destSize = Size(halfWidth, canvasHeight)
                    )
                }
                
                // 绘制中间分隔线
                drawLine(
                    color = Color.Yellow,
                    start = Offset(halfWidth, 0f),
                    end = Offset(halfWidth, canvasHeight),
                    strokeWidth = 4f
                )
                
                // 绘制右侧超分帧
                superResFrame?.let { bitmap ->
                    drawBitmap(
                        bitmap = bitmap,
                        destOffset = Offset(halfWidth, 0f),
                        destSize = Size(halfWidth, canvasHeight)
                    )
                }
            }
            
            // 左上角标签：原始
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                color = Color.Black.copy(alpha = 0.7f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "Original",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            
            // 右上角标签：超分
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                color = Color.Black.copy(alpha = 0.7f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "$algorithmName 2x",
                    color = Color.Cyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            
            // 底部帧信息
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
                color = Color.Black.copy(alpha = 0.7f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "帧: $frameIndex / $totalFrames",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        
        // 控制栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 播放/暂停按钮
            FilledIconButton(
                onClick = onPlayPauseClick,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            
            // 停止按钮
            FilledIconButton(
                onClick = onStopClick,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "停止",
                    tint = MaterialTheme.colorScheme.onError
                )
            }
            
            // 进度文本
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearProgressIndicator(
                    progress = if (totalFrames > 0) frameIndex.toFloat() / totalFrames else 0f,
                    modifier = Modifier.width(200.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${(frameIndex.toFloat() / totalFrames * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * DrawScope 扩展函数：绘制 Bitmap
 */
private fun DrawScope.drawBitmap(
    bitmap: Bitmap,
    destOffset: Offset,
    destSize: Size
) {
    val imageBitmap = bitmap.asImageBitmap()
    drawImage(
        image = imageBitmap,
        dstOffset = androidx.compose.ui.unit.IntOffset(
            destOffset.x.toInt(),
            destOffset.y.toInt()
        ),
        dstSize = androidx.compose.ui.unit.IntSize(
            destSize.width.toInt(),
            destSize.height.toInt()
        )
    )
}
