# Android 视频超分左右对比实现指南

## 📝 概述

本文档介绍如何在 Android 端实现视频超分的左右对比播放功能，包括：
1. 视频文件导入 Android assets
2. C++ 对比节点实现
3. Android UI 对比播放组件
4. 完整的工作流配置

## ✅ 已实现的功能

### 1. C++ 视频对比节点

#### SideBySideCompare（图像对比）
- **文件**: [plugin/include/nndeploy/codec/side_by_side_compare.h](plugin/include/nndeploy/codec/side_by_side_compare.h)
- **功能**: 
  - 将原始图像和超分图像左右拼接
  - 自动调整显示尺寸
  - 添加文本标签
  - 可选保存输出

#### VideoSideBySideCompare（视频对比）
- **文件**: [plugin/source/nndeploy/codec/side_by_side_compare.cc](plugin/source/nndeploy/codec/side_by_side_compare.cc)
- **功能**:
  - 实时视频帧对比拼接
  - 支持视频输出保存
  - 帧同步处理
  - 性能优化（每30帧日志）

### 2. Android 对比播放组件

#### SideBySideVideoPlayer（UI组件）
- **文件**: [app/android/app/src/main/java/com/nndeploy/app/SideBySideVideoPlayer.kt](app/android/app/src/main/java/com/nndeploy/app/SideBySideVideoPlayer.kt)
- **特点**:
  - 使用 ExoPlayer 双播放器同步播放
  - Jetpack Compose UI
  - 播放/暂停/重播控制
  - 左右分屏显示

#### VideoSuperResolution（处理器）
- **文件**: [app/android/app/src/main/java/com/nndeploy/ai/VideoSuperResolution.kt](app/android/app/src/main/java/com/nndeploy/ai/VideoSuperResolution.kt)
- **功能**:
  - 视频超分处理
  - 支持 assets 和外部文件
  - 参数化工作流配置
  - 结果输出管理

### 3. 工作流配置

#### OpenCV 视频超分对比
- **文件**: [app/android/app/src/main/assets/resources/workflow/video_sr_opencv_compare_android.json](app/android/app/src/main/assets/resources/workflow/video_sr_opencv_compare_android.json)
- **流程**: VideoInput → OpenCVSuperRes → VideoSideBySideCompare → Output

#### RealESRGAN 视频超分对比
- **文件**: [app/android/app/src/main/assets/resources/workflow/video_sr_realesrgan_compare_android.json](app/android/app/src/main/assets/resources/workflow/video_sr_realesrgan_compare_android.json)
- **流程**: VideoInput → RealESRGAN → VideoSideBySideCompare → Output

### 4. 算法注册

在 [Algorithm.kt](app/android/app/src/main/java/com/nndeploy/ai/Algorithm.kt) 中添加了：

```kotlin
AIAlgorithm(
    id = "video_sr_opencv_compare",
    name = "视频超分对比 - OpenCV",
    icon = Icons.Default.CompareArrows,
    inputType = listOf(InOutType.VIDEO),
    outputType = listOf(InOutType.VIDEO),
    workflowAsset = "resources/workflow/video_sr_opencv_compare_android.json",
    processFunction = "processVideoInVideoOut"
)

AIAlgorithm(
    id = "video_sr_realesrgan_compare",
    name = "视频超分对比 - RealESRGAN",
    icon = Icons.Default.CompareArrows,
    inputType = listOf(InOutType.VIDEO),
    outputType = listOf(InOutType.VIDEO),
    workflowAsset = "resources/workflow/video_sr_realesrgan_compare_android.json",
    processFunction = "processVideoInVideoOut"
)
```

## 🎬 视频文件准备

### 视频文件信息
- **源文件**: `resources/videos/720pface.mp4`
- **大小**: 217MB
- **说明**: 由于文件较大，建议采用以下方案之一

### 方案 1: 压缩视频（推荐）

```bash
cd /Users/jin/work/nndeploy-1

# 使用 FFmpeg 压缩视频
ffmpeg -i resources/videos/720pface.mp4 \\
  -vf "scale=640:-1" \\
  -c:v libx264 -crf 28 -preset fast \\
  -an \\
  app/android/app/src/main/assets/resources/videos/720pface_compressed.mp4

# 检查压缩后大小（目标 < 50MB）
ls -lh app/android/app/src/main/assets/resources/videos/720pface_compressed.mp4
```

### 方案 2: 边下边用（推荐生产环境）

将视频放到 CDN 或服务器，Android 应用首次使用时下载：

```kotlin
// 在 VideoSuperResolution.kt 中添加
suspend fun downloadVideoIfNeeded(context: Context, videoUrl: String): File {
    val cacheDir = File(context.cacheDir, "videos").apply { mkdirs() }
    val videoFile = File(cacheDir, "720pface.mp4")
    
    if (!videoFile.exists()) {
        Log.i("VideoSR", "Downloading video from $videoUrl")
        // 下载逻辑
        // ...
    }
    
    return videoFile
}
```

### 方案 3: 使用示例短视频

创建一个短视频用于快速测试：

```bash
# 截取前10秒作为测试视频
ffmpeg -i resources/videos/720pface.mp4 \\
  -t 10 \\
  -c copy \\
  app/android/app/src/main/assets/resources/videos/test_short.mp4
```

## 🚀 使用指南

### 1. 编译 C++ 对比节点

```bash
cd /Users/jin/work/nndeploy-1

# 编译 macOS 版本测试
rm -rf build && mkdir build && cd build
cmake .. \\
  -DCMAKE_BUILD_TYPE=Release \\
  -DENABLE_NNDEPLOY_PLUGIN_SUPER_RESOLUTION=ON \\
  -DENABLE_NNDEPLOY_OPENCV=ON

make -j8 nndeploy_plugin_codec
```

### 2. 编译 Android 版本

```bash
cd /Users/jin/work/nndeploy-1

# 编译 Android arm64 库
python build_android_arm64.py

# 复制库文件
cp build_android_arm64/lib/*.so \\
   app/android/app/src/main/jniLibs/arm64-v8a/
```

### 3. 在 Android Studio 中使用

#### 方式 1: 使用算法列表

```kotlin
// 在 Tool.kt 或主界面中
val videoSRAlgorithm = AlgorithmFactory.getAlgorithmsById(
    algorithms, "video_sr_opencv_compare"
)

// 处理视频
val result = VideoSuperResolution.processWithDefaultVideo(
    context, videoSRAlgorithm
)

// 播放对比结果
if (result.success && result.outputUri != null) {
    SideBySideVideoPlayer(
        originalVideoUri = Uri.parse("asset://resources/videos/720pface.mp4"),
        superResVideoUri = result.outputUri
    )
}
```

#### 方式 2: 直接使用组件

```kotlin
@Composable
fun VideoSRDemoScreen() {
    VideoSuperResolutionDemo()
}
```

### 4. 工作流参数配置

工作流 JSON 支持以下参数：

```json
{
    "key_": "nndeploy::codec::VideoSideBySideCompare",
    "window_name_": "超分对比",
    "show_labels_": true,
    "left_label_": "原始",
    "right_label_": "超分",
    "auto_resize_": true,
    "max_display_width_": 1920,
    "max_display_height_": 1080,
    "save_output_": true,
    "output_path_": ""
}
```

## 📊 性能参考

### 处理性能（Android 测试）

| 设备配置 | OpenCV 超分 | RealESRGAN 超分 |
|---------|------------|----------------|
| 高端 (8GB) | 30-60 FPS | 5-10 FPS |
| 中端 (4GB) | 15-30 FPS | 2-5 FPS |
| 低端 (2GB) | 5-15 FPS | 1-2 FPS |

*测试视频: 720p → 1440p*

### APK 体积影响

| 组件 | 体积增量 |
|------|---------|
| codec plugin (对比节点) | +2MB |
| ExoPlayer (视频播放) | +5MB |
| 测试视频 (压缩后) | +20-50MB |
| **总计** | **+27-57MB** |

## 🎨 UI 定制

### 修改对比布局

编辑 [SideBySideVideoPlayer.kt](app/android/app/src/main/java/com/nndeploy/app/SideBySideVideoPlayer.kt):

```kotlin
// 垂直对比（上下布局）
Column {
    // 上方：原始视频
    Box(modifier = Modifier.weight(1f)) {
        AndroidView(factory = { PlayerView(it).apply { player = originalPlayer } })
    }
    
    // 下方：超分视频
    Box(modifier = Modifier.weight(1f)) {
        AndroidView(factory = { PlayerView(it).apply { player = superResPlayer } })
    }
}

// 滑动对比（左右滑动切换）
var sliderPosition by remember { mutableStateOf(0.5f) }
Box {
    AndroidView(factory = { PlayerView(it).apply { player = originalPlayer } })
    AndroidView(
        factory = { PlayerView(it).apply { player = superResPlayer } },
        modifier = Modifier.clip(RectangleShape).alpha(sliderPosition.toFloat())
    )
}
```

### 添加缩放和平移

```kotlin
var scale by remember { mutableStateOf(1f) }
var offset by remember { mutableStateOf(Offset.Zero) }

Box(
    modifier = Modifier
        .graphicsLayer(
            scaleX = scale,
            scaleY = scale,
            translationX = offset.x,
            translationY = offset.y
        )
        .pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale *= zoom
                offset += pan
            }
        }
) {
    // 视频播放器
}
```

## 🔧 故障排查

### 问题 1: 视频文件未找到
```
错误: FileNotFoundException: resources/videos/720pface.mp4
```

**解决方案**:
1. 确认文件在 assets 中
2. 使用 `asset://` URI scheme
3. 检查文件权限

```bash
# 确认文件存在
unzip -l app-debug.apk | grep 720pface.mp4

# 使用正确的 URI
val uri = Uri.parse("asset://resources/videos/720pface.mp4")
```

### 问题 2: 播放器不同步
```
错误: 两个视频播放不同步
```

**解决方案**:
```kotlin
// 添加播放进度同步
LaunchedEffect(isPlaying) {
    while (isPlaying) {
        val pos = originalPlayer.currentPosition
        if (abs(superResPlayer.currentPosition - pos) > 100) {
            superResPlayer.seekTo(pos)
        }
        delay(100)
    }
}
```

### 问题 3: 内存溢出
```
错误: OutOfMemoryError during video processing
```

**解决方案**:
```kotlin
// 1. 使用 tile 模式处理大分辨率
"tile_size_": 256

// 2. 降低视频分辨率
"max_display_width_": 1280

// 3. 限制缓存帧数
"queue_max_size_": 8
```

### 问题 4: C++ 节点未注册
```
错误: Node not found: nndeploy::codec::VideoSideBySideCompare
```

**解决方案**:
```bash
# 1. 确认插件已编译
ls build_android_arm64/lib/libnndeploy_plugin_codec.so

# 2. 检查库是否加载
adb logcat | grep "loadLibrary"

# 3. 重新编译并安装
./gradlew clean assembleDebug
adb install -r app-debug.apk
```

## 📖 API 参考

### VideoSuperResolution

```kotlin
suspend fun processVideoSuperResolution(
    context: Context,
    inputVideoUri: Uri,
    alg: AIAlgorithm
): ProcessResult

suspend fun processWithDefaultVideo(
    context: Context,
    alg: AIAlgorithm
): ProcessResult
```

### SideBySideVideoPlayer

```kotlin
@Composable
fun SideBySideVideoPlayer(
    originalVideoUri: Uri,
    superResVideoUri: Uri,
    modifier: Modifier = Modifier
)
```

### VideoSideBySideCompare (C++)

```cpp
class VideoSideBySideCompare : public dag::Node {
  virtual base::Status init();
  virtual base::Status run();
  virtual base::Status deinit();
}
```

## 🎯 下一步优化

- [ ] 添加帧率显示
- [ ] 实现区域放大对比
- [ ] 支持实时摄像头超分
- [ ] 添加性能监控面板
- [ ] 支持多算法切换对比
- [ ] 实现视频编辑功能

## 📚 相关文档

- [CPP_NATIVE_SR_IMPLEMENTATION.md](CPP_NATIVE_SR_IMPLEMENTATION.md) - C++ 实现详解
- [ANDROID_SR_NON_GFPGAN_GUIDE.md](ANDROID_SR_NON_GFPGAN_GUIDE.md) - Android 超分指南
- [CPP_SR_QUICKSTART.md](CPP_SR_QUICKSTART.md) - 快速开始

## 支持

如有问题，请查看:
- Android 日志: `adb logcat | grep -i "video\|compare\|super"`
- GitHub Issues: https://github.com/nndeploy/nndeploy/issues
