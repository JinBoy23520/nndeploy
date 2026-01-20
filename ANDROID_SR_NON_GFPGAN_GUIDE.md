# Android 端超分方案部署指南

## 概述

本文档介绍如何在 Android 端使用除 GFPGAN 外的其他超分方案，包括 RealESRGAN 和 OpenCV SuperRes。

## 已实现的超分方案

### 1. RealESRGAN 超分
- **算法ID**: `realesrgan_sr`
- **特点**: 高质量超分，基于深度学习，效果优异
- **放大倍数**: 2倍
- **处理速度**: 中等（取决于设备性能）
- **适用场景**: 需要高质量超分的图像处理

### 2. OpenCV 超分
- **算法ID**: `opencv_sr`  
- **特点**: 轻量快速，基于 Lanczos 插值+锐化
- **放大倍数**: 2倍
- **处理速度**: 快速
- **适用场景**: 实时处理、性能要求高的场景

## 工作流文件

已创建以下工作流文件：

1. **RealESRGAN Android工作流**
   - 文件路径: `app/android/app/src/main/assets/resources/workflow/realtime_sr_realesrgan_android.json`
   - 节点流程: ImageInput → RealESRGAN → ImageOutput
   - 模型路径: `resources/models/RealESRGAN_x2plus.pth` (64MB)

2. **OpenCV SuperRes Android工作流**
   - 文件路径: `app/android/app/src/main/assets/resources/workflow/realtime_sr_opencv_android.json`
   - 节点流程: ImageInput → OpenCVSuperRes → ImageOutput
   - 无需模型文件（使用算法实现）

## 算法注册

已在 `Algorithm.kt` 中注册以下算法：

```kotlin
// RealESRGAN 超分
AIAlgorithm(
    id = "realesrgan_sr",
    name = "RealESRGAN 超分",
    icon = Icons.Default.PhotoSizeSelectLarge,
    inputType = listOf(InOutType.IMAGE),
    outputType = listOf(InOutType.IMAGE),
    workflowAsset = "resources/workflow/realtime_sr_realesrgan_android.json",
    processFunction = "processImageInImageOut"
)

// OpenCV 超分
AIAlgorithm(
    id = "opencv_sr",
    name = "OpenCV 超分",
    icon = Icons.Default.HighQuality,
    inputType = listOf(InOutType.IMAGE),
    outputType = listOf(InOutType.IMAGE),
    workflowAsset = "resources/workflow/realtime_sr_opencv_android.json",
    processFunction = "processImageInImageOut"
)
```

## 模型文件准备

### RealESRGAN 模型
RealESRGAN 需要模型文件才能运行，有两种方式放置模型：

#### 方式1: 放入 APK assets（不推荐，会增大APK体积）
```bash
# 创建模型目录
mkdir -p app/android/app/src/main/assets/resources/models

# 复制模型文件 (64MB)
cp resources/models/RealESRGAN_x2plus.pth \\
   app/android/app/src/main/assets/resources/models/
```

**注意**: 此方法会使 APK 体积增加约 64MB。

#### 方式2: 首次运行时下载（推荐）
模型文件可以在应用首次运行时从网络下载到设备存储：

1. 将模型放到服务器或 GitHub Release
2. 在应用启动时检查模型是否存在
3. 如不存在则下载到 `/sdcard/Android/data/<package>/files/resources/models/`

工作流 JSON 中已配置模型下载 URL：
```json
"model_url_": [
    "github@xinntao/Real-ESRGAN:RealESRGAN_x2plus.pth"
]
```

### OpenCV 超分模型
OpenCV SuperRes 使用 Lanczos 插值算法，**无需模型文件**，开箱即用。

## 使用方法

### 1. 在 Android Studio 中编译

确保已加载超分插件：
```kotlin
// GraphRunner.kt 中已有
System.loadLibrary("nndeploy_plugin_super_resolution")
```

### 2. 使用算法

在应用中选择图像后，可以选择以下超分算法：
- **RealESRGAN 超分**: 高质量超分（需要模型文件）
- **OpenCV 超分**: 快速超分（无需模型）

### 3. 处理流程

```kotlin
// 1. 选择算法
val algorithm = AlgorithmFactory.getAlgorithmsById(algorithms, "opencv_sr")

// 2. 选择输入图像
val inputUri = ... // 用户选择的图像 URI

// 3. 执行处理
val result = processImageInImageOut(context, algorithm, inputUri)

// 4. 显示结果
result.outputUri?.let { uri ->
    // 显示超分后的图像
}
```

## Python 实现说明

⚠️ **重要**: 当前的超分方案（RealESRGAN、OpenCV SuperRes）都是 **Python 实现**，依赖以下库：
- `realesrgan`: RealESRGAN Python 库
- `basicsr`: BasicSR 训练框架  
- `torch`: PyTorch 深度学习框架
- `cv2`: OpenCV Python 绑定

### Android 运行方式

在 Android 上运行这些 Python 节点有以下几种方案：

#### 方案1: Python-for-Android（当前方案）
使用 Python 嵌入到 Android：
- 需要在 APK 中包含 Python 运行时
- 需要安装 Python 依赖库
- 体积较大（100MB+）

#### 方案2: C++ 原生实现（推荐）
将 Python 节点改写为 C++ 实现：
- RealESRGAN: 使用 ONNX Runtime 或 TensorRT 运行 ONNX 模型
- OpenCV: 直接使用 OpenCV C++ API
- 性能更好，体积更小

#### 方案3: 服务器端处理
将超分处理放到服务器：
- Android 只负责上传/下载图像
- 服务器端运行 Python 超分算法
- 适合对实时性要求不高的场景

## C++ 原生实现建议

如果需要更好的性能和更小的 APK 体积，建议将超分算法改写为 C++ 实现：

### RealESRGAN C++ 实现
```cpp
// plugin/source/nndeploy/super_resolution/realesrgan.cc

namespace nndeploy {
namespace super_resolution {

class RealESRGAN : public dag::Node {
 public:
  base::Status init() {
    // 使用 ONNX Runtime 加载模型
    infer_ = infer::createInferenceParam();
    infer_->model_value_.push_back(model_path_);
    inference_ = infer::createInference(infer_type_, infer_);
    return base::kStatusCodeOk;
  }

  base::Status run() {
    // 获取输入
    device::Tensor *input = getInput(0);
    
    // 执行推理
    inference_->run(input);
    
    // 输出结果
    device::Tensor *output = inference_->getOutput(0);
    setOutput(0, output);
    
    return base::kStatusCodeOk;
  }
};

} // namespace super_resolution
} // namespace nndeploy
```

### OpenCV C++ 实现
```cpp
// plugin/source/nndeploy/super_resolution/opencv_superres.cc

base::Status OpenCVSuperRes::run() {
  cv::Mat input = getInputMat(0);
  cv::Mat output;
  
  // Lanczos 插值放大
  cv::resize(input, output, 
             cv::Size(input.cols * scale_, input.rows * scale_),
             0, 0, cv::INTER_LANCZOS4);
  
  // 应用锐化
  if (sharpen_) {
    cv::Mat blurred;
    cv::GaussianBlur(output, blurred, cv::Size(0, 0), 3);
    cv::addWeighted(output, 1.0 + sharpen_amount_, 
                    blurred, -sharpen_amount_, 0, output);
  }
  
  setOutputMat(0, output);
  return base::kStatusCodeOk;
}
```

## 性能优化建议

### 1. 模型优化
- 将 PyTorch 模型转换为 ONNX 格式
- 使用 ONNX Runtime 或 TensorRT 运行
- 启用 GPU 加速（如果设备支持）

### 2. 分辨率控制
- 对于大图像，先下采样再超分
- 使用 tile 模式处理大图（256x256 tiles）
- 设置合理的最大分辨率限制

### 3. 多线程处理
- 使用后台线程处理超分
- 避免阻塞 UI 线程
- 显示处理进度条

## 测试验证

### 单元测试
```bash
# 构建 Android APK
cd app/android
./gradlew assembleDebug

# 安装到设备
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 测试超分功能
adb shell am start -n com.nndeploy.ai/.MainActivity
```

### 性能测试
建议在不同设备上测试：
- 低端设备 (2GB RAM): 使用 OpenCV 超分
- 中端设备 (4GB RAM): 使用 RealESRGAN (tile=256)
- 高端设备 (6GB+ RAM): 使用 RealESRGAN (full)

## 故障排查

### 问题1: 找不到模型文件
**错误**: `FileNotFoundException: resources/models/RealESRGAN_x2plus.pth`

**解决方案**:
1. 检查模型文件是否存在于 assets 目录
2. 确认路径配置正确
3. 使用外部存储路径: `/sdcard/nndeploy/models/`

### 问题2: 内存不足
**错误**: `OutOfMemoryError`

**解决方案**:
1. 降低输入图像分辨率
2. 使用 tile 模式处理
3. 释放之前的处理结果

### 问题3: 处理速度慢
**解决方案**:
1. 切换到 OpenCV 超分（更快）
2. 降低分辨率或缩放倍数
3. 使用后台线程处理

## 与 GFPGAN 的区别

| 特性 | GFPGAN | RealESRGAN | OpenCV SuperRes |
|------|--------|------------|-----------------|
| 专注领域 | 人脸修复 | 通用图像超分 | 通用图像超分 |
| 模型大小 | 350MB | 64MB | 无需模型 |
| 处理速度 | 慢 | 中等 | 快 |
| 效果质量 | 人脸最好 | 通用场景好 | 基本提升 |
| 依赖 | PyTorch, GFPGAN | PyTorch, BasicSR | 仅 OpenCV |
| Android支持 | ❌ 不推荐 | ✅ 可用 | ✅ 推荐 |

## 后续计划

- [ ] 将 RealESRGAN 转换为 ONNX 模型
- [ ] 实现 C++ 原生超分节点
- [ ] 添加 GPU 加速支持
- [ ] 实现实时视频超分
- [ ] 添加更多超分算法（ESRGAN、SwinIR等）

## 参考资料

- [RealESRGAN GitHub](https://github.com/xinntao/Real-ESRGAN)
- [OpenCV DNN Super Resolution](https://docs.opencv.org/4.x/d5/d29/tutorial_dnn_superres_upscale_image_single.html)
- [ONNX Runtime Android](https://onnxruntime.ai/docs/tutorials/mobile/android.html)
- [nndeploy 文档](../README.md)

## 联系支持

如有问题，请参考：
- GitHub Issues: https://github.com/nndeploy/nndeploy/issues
- 文档: [ANDROID_REALTIME_SR_GUIDE.md](../../ANDROID_REALTIME_SR_GUIDE.md)
