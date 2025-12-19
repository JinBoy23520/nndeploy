# nndeploy Android TV 部署完成总结

**部署日期**: 2024年12月9日  
**目标平台**: Android TV (arm64-v8a, API 24+)  
**推理引擎**: ONNXRuntime 1.18.0  
**计算机视觉**: OpenCV 4.10.0  
**开发环境**: macOS + Android Studio + NDK 29.0.14206865

---

## ✅ 完成状态

### 1. 环境配置
- ✅ **Android NDK**: 29.0.14206865
- ✅ **CMake**: 4.2.0
- ✅ **Ninja**: 1.13.2
- ✅ **环境变量**: ANDROID_HOME, ANDROID_NDK 已配置到 `~/.zshrc`

### 2. 第三方依赖
- ✅ **ONNXRuntime**: 1.18.0 Android AAR (23.2 MB)
  - 位置: `/Users/jin/work/nndeploy/tool/script/third_party/onnxruntime1.18.0_android/`
  - 包含: arm64-v8a 和 armeabi-v7a 动态库
  
- ✅ **OpenCV**: 4.10.0 Android SDK (292 MB)
  - 位置: `/Users/jin/work/nndeploy/tool/script/third_party/opencv4.10.0_Android/`
  - 已创建符号链接: `lib -> sdk/native/libs`, `include -> sdk/native/jni/include`

### 3. 编译构建
- ✅ **Git 子模块**: 8个子模块已初始化（rapidjson, gflags 等）
- ✅ **CMake 配置**: 成功配置 Android arm64-v8a 交叉编译
- ✅ **CMake 兼容性修复**: 
  - `third_party/rapidjson/CMakeLists.txt`: VERSION 2.8.12 → 3.5
  - `third_party/gflags/CMakeLists.txt`: VERSION 3.0.2 → 3.5
- ✅ **ONNXRuntime 头文件修复**: 
  - `cmake/onnxruntime.cmake`: 添加 `include/onnxruntime` 路径
- ✅ **Ninja 编译**: 298个文件编译成功，生成15个 .so 库

### 4. Android 项目集成

#### JNI 库部署 (18个文件, 112 MB)
```
/Users/jin/work/nndeploy/app/android/app/src/main/jniLibs/arm64-v8a/
├── libc++_shared.so              (8.9 MB)   # NDK C++ 运行时
├── libnndeploy_framework.so      (36 MB)    # nndeploy 核心框架
├── libnndeploy_jni.so            (189 KB)   # JNI 接口层
├── libnndeploy_plugin_*.so       (×12)      # 各功能插件
├── libonnxruntime.so             (16 MB)    # ONNXRuntime 推理引擎
└── libopencv_java4.so            (20 MB)    # OpenCV 计算机视觉
```

**已部署插件**:
- `libnndeploy_plugin_basic.so` (2.7 MB)
- `libnndeploy_plugin_classification.so` (2.0 MB)
- `libnndeploy_plugin_codec.so` (3.2 MB)
- `libnndeploy_plugin_detect.so` (4.6 MB) - 目标检测（含 YOLO）
- `libnndeploy_plugin_infer.so` (786 KB)
- `libnndeploy_plugin_matting.so` (1.4 MB)
- `libnndeploy_plugin_ocr.so` (7.1 MB) - 文字识别
- `libnndeploy_plugin_preprocess.so` (3.7 MB)
- `libnndeploy_plugin_segment.so` (2.9 MB) - 图像分割
- `libnndeploy_plugin_super_resolution.so` (692 KB) - 超分辨率
- `libnndeploy_plugin_template.so` (803 KB)
- `libnndeploy_plugin_tokenizer.so` (103 KB)
- `libnndeploy_plugin_track.so` (2.3 MB) - 目标追踪

#### Assets 目录
- ✅ 已创建: `/Users/jin/work/nndeploy/app/android/app/src/main/assets/`
- 📌 **待添加**: ONNX 模型文件、配置 JSON 文件

---

## 📋 后续步骤

### 立即可执行
1. **启动 Android Studio**
   ```bash
   open -a "Android Studio" /Users/jin/work/nndeploy/app/android
   ```

2. **同步 Gradle 依赖**
   - 打开项目后等待 Gradle 同步完成
   - 检查 `build.gradle.kts` 配置

3. **连接 Android TV 设备**
   - 通过 ADB 连接物理设备: `adb connect <TV_IP>:5555`
   - 或创建 Android TV 模拟器（API 24+, arm64-v8a）

4. **构建并运行**
   - Build → Make Project
   - Run → Run 'app'

### 添加 AI 模型（示例）

#### 目标检测 (YOLOv8)
```bash
# 1. 下载/转换 ONNX 模型
# yolov8n.onnx → /app/android/app/src/main/assets/models/

# 2. 创建配置 JSON
cat > /Users/jin/work/nndeploy/app/android/app/src/main/assets/yolov8_config.json <<EOF
{
  "model_type": "kInferenceTypeOnnxRuntime",
  "model_path": "models/yolov8n.onnx",
  "input_size": [640, 640],
  "confidence_threshold": 0.5,
  "nms_threshold": 0.4
}
EOF
```

#### 图像分割 (Segment Anything)
```bash
# 部署 SAM 模型
# sam_mobile.onnx → /app/android/app/src/main/assets/models/
```

---

## 🔧 技术细节

### CMake 配置要点
```cmake
# /Users/jin/work/nndeploy/build_android_arm64/config.cmake

# 已启用功能
ENABLE_NNDEPLOY_INFERENCE_ONNXRUNTIME = "tool/script/third_party/onnxruntime1.18.0_android"
ENABLE_NNDEPLOY_OPENCV = "tool/script/third_party/opencv4.10.0_Android"

# 已禁用功能（按需启用）
ENABLE_NNDEPLOY_INFERENCE_MNN = OFF
ENABLE_NNDEPLOY_PLUGIN_LLM = OFF
ENABLE_NNDEPLOY_PLUGIN_STABLE_DIFFUSION = OFF
```

### JNI 加载机制
```kotlin
// /app/android/app/src/main/java/com/nndeploy/dag/GraphRunner.kt
companion object {
    init {
        System.loadLibrary("nndeploy_jni")  // 自动加载依赖的 .so
    }
}
```

### 编译警告（可忽略）
- `warning: 'override' missing`: 代码风格警告，不影响功能
- `warning: format specifies type 'long long'`: 日志格式警告，不影响运行

---

## 📁 关键路径速查

| 类型 | 路径 |
|------|------|
| **Android 项目** | `/Users/jin/work/nndeploy/app/android` |
| **JNI 库** | `/Users/jin/work/nndeploy/app/android/app/src/main/jniLibs/arm64-v8a/` |
| **Assets** | `/Users/jin/work/nndeploy/app/android/app/src/main/assets/` |
| **编译输出** | `/Users/jin/work/nndeploy/build_android_arm64/` |
| **源码** | `/Users/jin/work/nndeploy/framework/`, `/Users/jin/work/nndeploy/plugin/` |
| **第三方库** | `/Users/jin/work/nndeploy/tool/script/third_party/` |

---

## ⚡ 快速重新编译（修改源码后）

```bash
cd /Users/jin/work/nndeploy/build_android_arm64

# 仅重新编译修改的文件
/opt/homebrew/bin/ninja -j8

# 复制新生成的库到 Android 项目
cp libnndeploy_*.so \
   /Users/jin/work/nndeploy/app/android/app/src/main/jniLibs/arm64-v8a/

# 在 Android Studio 中重新运行
```

---

## 🎯 支持的 AI 功能

基于已部署的插件，当前支持：

1. **图像分类** (Classification)
2. **目标检测** (Detection - YOLO)
3. **图像分割** (Segmentation)
4. **抠图/Matting** (PPMatting)
5. **OCR 文字识别** (OCR)
6. **目标追踪** (Tracking - FairMOT)
7. **超分辨率** (Super Resolution)
8. **图像编解码** (Codec)
9. **图像预处理** (Preprocessing)

---

## 🐛 常见问题排查

### 1. JNI 库加载失败
```kotlin
// 检查错误日志
adb logcat | grep "UnsatisfiedLinkError"

// 验证库文件架构
adb shell "ls -l /data/app/*/lib/arm64/*.so"
```

### 2. 模型加载失败
- 确认 `.onnx` 文件已放入 `assets/models/`
- 检查模型路径是否正确（相对于 assets 根目录）
- 验证模型是否为 ONNX 格式且架构兼容

### 3. 运行时内存不足
- 降低模型输入分辨率
- 启用模型量化（INT8/FP16）
- 使用更轻量的模型版本（如 YOLOv8n 而非 YOLOv8x）

---

## 📞 技术支持

- **nndeploy 仓库**: https://github.com/nndeploy/nndeploy
- **文档**: `/Users/jin/work/nndeploy/README.md`
- **Android 示例**: `/Users/jin/work/nndeploy/app/android/README.md`

---

## ✨ 部署成功！

现在可以：
1. 打开 Android Studio 加载项目
2. 添加您的 ONNX 模型到 assets
3. 运行到 Android TV 设备测试
4. 开始开发 AI 应用！

**编译时间**: 约 5 分钟  
**部署库大小**: 112 MB  
**支持架构**: arm64-v8a  
**最低 Android 版本**: API 24 (Android 7.0)
