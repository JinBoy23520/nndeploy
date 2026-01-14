# Android端实时视频超分实现指南

## 概述

nndeploy 支持在Android平台上部署AI工作流，包括实时视频超分。通过集成摄像头捕获、GFPGAN超分模型和实时显示，可以实现边处理边播放的实时超分功能。

## 支持情况

- **平台支持**: Android (arm64-v8a)
- **模型支持**: GFPGAN v1.4 (人脸超分)
- **输入支持**: 摄像头实时视频流
- **输出支持**: 实时显示超分结果
- **性能**: 取决于设备GPU/CPU，典型帧率10-30 FPS (视分辨率而定)

## 实现建议

### 1. 架构设计

```
摄像头捕获 → 帧预处理 → GFPGAN超分 → 显示输出
    ↑                                       ↓
    └───────────── 循环处理 ────────────────┘
```

### 2. 技术栈

- **框架**: nndeploy (DAG工作流引擎)
- **语言**: Kotlin (Android app)
- **摄像头**: CameraX 或 Camera2 API
- **显示**: Jetpack Compose 或 SurfaceView
- **模型**: ONNX Runtime (内置于nndeploy)

### 3. 实现步骤

#### 步骤1: 添加实时超分算法

在 `AlgorithmFactory.createDefaultAlgorithms()` 中添加新算法：

```kotlin
AIAlgorithm(
    id = "realtime_super_resolution",
    name = "实时视频超分",
    description = "实时摄像头视频超分，提升人脸清晰度",
    icon = Icons.Default.CameraAlt,
    inputType = listOf(InOutType.CAMERA),
    outputType = listOf(InOutType.IMAGE),
    category = AlgorithmCategory.COMPUTER_VISION.displayName,
    workflowAsset = "resources/workflow/realtime_sr_android.json",
    tags = listOf("super resolution", "face enhancement", "real-time"),
    parameters = mapOf(
        "camera_id" to 0,
        "upscale" to 2,
        "preview_resolution" to "640x480"
    ),
    processFunction = "processCameraRealtime"
)
```

#### 步骤2: 创建工作流文件

创建 `resources/workflow/realtime_sr_android.json`：

```json
{
    "key_": "nndeploy.dag.Graph",
    "name_": "实时视频超分",
    "device_type_": "kDeviceTypeCodeCpu:0",
    "is_external_stream_": true,
    "is_graph_node_share_stream_": true,
    "loop_count_": -1,
    "node_repository_": [
        {
            "key_": "nndeploy::codec::OpenCvCameraDecode",
            "name_": "CameraInput",
            "device_type_": "kDeviceTypeCodeCpu:0",
            "outputs_": [{"name_": "CameraInput@output_0", "type_": "ndarray"}],
            "camera_id_": 0
        },
        {
            "key_": "nndeploy.gan.GFPGAN",
            "name_": "SuperResolution",
            "device_type_": "kDeviceTypeCodeCpu:0",
            "inputs_": [{"name_": "CameraInput@output_0", "type_": "ndarray"}],
            "outputs_": [{"name_": "SuperResolution@output_0", "type_": "ndarray"}],
            "model_path_": "resources/models/face_swap/GFPGANv1.4.pth",
            "upscale_": 2
        }
    ]
}
```

#### 步骤3: 实现实时处理逻辑

扩展 `ImageInImageOut.kt`，添加摄像头实时处理：

```kotlin
suspend fun processCameraRealtime(context: Context, alg: AIAlgorithm): ProcessResult {
    // 1. 初始化摄像头
    val cameraProvider = ProcessCameraProvider.getInstance(context).get()
    
    // 2. 配置摄像头预览
    val preview = Preview.Builder().build()
    
    // 3. 配置图像分析
    val imageAnalyzer = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
    
    imageAnalyzer.setAnalyzer(cameraExecutor) { imageProxy ->
        // 转换为Bitmap
        val bitmap = imageProxy.toBitmap()
        
        // 运行nndeploy工作流
        val result = runRealtimeInference(bitmap, alg)
        
        // 显示结果
        updatePreview(result)
        
        imageProxy.close()
    }
    
    // 4. 绑定生命周期
    cameraProvider.bindToLifecycle(
        lifecycleOwner,
        CameraSelector.DEFAULT_BACK_CAMERA,
        preview,
        imageAnalyzer
    )
    
    return ProcessResult.Success("实时处理启动")
}
```

#### 步骤4: 性能优化

- **分辨率**: 使用较低输入分辨率 (640x480)，超分后放大显示
- **帧率**: 限制处理帧率 (15-30 FPS)
- **硬件加速**: 优先使用GPU推理 (device_type_ = kDeviceTypeCodeGpu:0)
- **内存管理**: 及时释放帧数据，避免内存泄漏

### 4. 部署步骤

#### 编译nndeploy库

```bash
# 设置环境变量
export ANDROID_NDK=/path/to/ndk
export ANDROID_SDK=/path/to/sdk

# 编译
cd build
cmake -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-21 \
    -DCMAKE_BUILD_TYPE=Release \
    ..
ninja install
```

#### 构建Android APK

```bash
cd app/android
./gradlew assembleRelease
```

#### 安装和运行

```bash
adb install app/build/outputs/apk/release/app-release.apk
adb shell am start -n com.nndeploy.app/.MainActivity
```

### 5. 测试和调试

- **性能测试**: 使用Android Profiler监控CPU/GPU/内存使用
- **日志**: 查看Logcat中的nndeploy日志
- **兼容性**: 在不同Android版本和设备上测试

### 6. 潜在问题和解决方案

| 问题 | 解决方案 |
|------|----------|
| 摄像头权限 | 在AndroidManifest.xml中添加权限 |
| 模型加载慢 | 预加载模型，使用异步初始化 |
| 帧率低 | 降低分辨率，优化模型参数 |
| 内存不足 | 实现帧池复用，及时GC |
| 发热严重 | 添加帧率控制，设备散热管理 |

### 7. 扩展功能

- **多摄像头支持**: 支持前后摄像头切换
- **参数调节**: 实时调整超分强度
- **录制功能**: 保存超分视频
- **滤镜组合**: 结合其他AI效果

## 结论

Android端实时视频超分是可行的，通过nndeploy的工作流引擎可以高效实现。建议从小分辨率开始测试，逐步优化性能。完整实现需要约1-2周开发时间，取决于经验水平。

如需源码示例或进一步协助，请提供更多细节。