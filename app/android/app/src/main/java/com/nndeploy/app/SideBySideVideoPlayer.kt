package com.nndeploy.app

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch

/**
 * 视频左右对比播放器组件
 * Side-by-Side Video Comparison Player
 * 
 * 用于展示原始视频和超分后视频的对比效果
 */
@Composable
fun SideBySideVideoPlayer(
    originalVideoUri: Uri,
    superResVideoUri: Uri,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // 创建两个播放器实例
    val originalPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(originalVideoUri))
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
        }
    }
    
    val superResPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(superResVideoUri))
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
        }
    }
    
    // 播放状态控制
    var isPlaying by remember { mutableStateOf(false) }
    
    // 同步播放控制
    fun syncPlayPause() {
        if (isPlaying) {
            originalPlayer.play()
            superResPlayer.play()
        } else {
            originalPlayer.pause()
            superResPlayer.pause()
        }
    }
    
    // 释放资源
    DisposableEffect(Unit) {
        onDispose {
            originalPlayer.release()
            superResPlayer.release()
        }
    }
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 顶部标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(
                text = "原始视频",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                text = "超分视频",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        
        // 视频播放区域（左右对比）
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // 左侧：原始视频
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = originalPlayer
                            useController = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                // 标签
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "Original",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            
            // 分隔线
            Divider(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(2.dp),
                color = MaterialTheme.colorScheme.primary
            )
            
            // 右侧：超分视频
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = superResPlayer
                            useController = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                // 标签
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "Super-Resolution",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
        
        // 底部控制栏
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 播放/暂停按钮
                IconButton(
                    onClick = {
                        isPlaying = !isPlaying
                        syncPlayPause()
                    }
                ) {
                    Icon(
                        imageVector = if (isPlaying) {
                            Icons.Default.Pause
                        } else {
                            Icons.Default.PlayArrow
                        },
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // 重新开始按钮
                IconButton(
                    onClick = {
                        originalPlayer.seekTo(0)
                        superResPlayer.seekTo(0)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = "重新开始",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // 信息文本
                Column {
                    Text(
                        text = "左右对比播放",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "同步播放超分效果",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 视频超分对比演示页面
 */
@Composable
fun VideoSuperResolutionDemo(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var originalVideoUri by remember { mutableStateOf<Uri?>(null) }
    var superResVideoUri by remember { mutableStateOf<Uri?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 顶部标题
        @OptIn(ExperimentalMaterial3Api::class)
        TopAppBar(
            title = { Text("视频超分对比") }
        )
        
        when {
            isProcessing -> {
                // 处理中
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("正在处理视频超分...")
                    }
                }
            }
            
            originalVideoUri != null && superResVideoUri != null -> {
                // 显示对比播放器
                SideBySideVideoPlayer(
                    originalVideoUri = originalVideoUri!!,
                    superResVideoUri = superResVideoUri!!,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            else -> {
                // 初始状态，显示开始按钮
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (errorMessage != null) {
                            Text(
                                text = "错误: $errorMessage",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        
                        Button(
                            onClick = {
                                scope.launch {
                                    isProcessing = true
                                    errorMessage = null
                                    
                                    try {
                                        // TODO: 调用视频超分处理
                                        // val result = VideoSuperResolution.processWithDefaultVideo(context, algorithm)
                                        
                                        // 暂时使用模拟数据
                                        kotlinx.coroutines.delay(2000)
                                        
                                        // 设置视频URI（需要实际处理后的结果）
                                        // originalVideoUri = ...
                                        // superResVideoUri = result.outputUri
                                        
                                    } catch (e: Exception) {
                                        errorMessage = e.message
                                    } finally {
                                        isProcessing = false
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("开始视频超分对比")
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "将使用内置测试视频",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
