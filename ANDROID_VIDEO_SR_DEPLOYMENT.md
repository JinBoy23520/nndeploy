# Android 视频超分对比功能部署完成

## ✅ 已完成的任务

### 1. 视频文件准备
- ✅ 使用 `face.mp4` (1.6MB) 替代 720pface.mp4 (217MB)
- ✅ 已复制到 `app/android/app/src/main/assets/resources/videos/face.mp4`

### 2. 工作流配置更新
- ✅ [video_sr_opencv_compare_android.json](app/android/app/src/main/assets/resources/workflow/video_sr_opencv_compare_android.json) - 更新视频路径
- ✅ [video_sr_realesrgan_compare_android.json](app/android/app/src/main/assets/resources/workflow/video_sr_realesrgan_compare_android.json) - 更新视频路径

### 3. C++ 编译完成
- ✅ 修复 Android 平台 OpenCV GUI 兼容性问题（禁用 cv::imshow/waitKey）
- ✅ 编译 `libnndeploy_plugin_codec.so` (3.6MB) - 包含视频对比节点
- ✅ 编译 `libnndeploy_plugin_super_resolution.so` (698KB) - 包含 OpenCV/RealESRGAN 超分节点
- ✅ 总计 24 个 .so 库文件已复制到 `app/android/app/src/main/jniLibs/arm64-v8a/`

### 4. Android 库清单

| 库文件 | 大小 | 说明 |
|--------|------|------|
| libnndeploy_framework.so | 36MB | 核心框架 |
| libnndeploy_jni.so | 272KB | JNI 接口 |
| libnndeploy_plugin_codec.so | 3.6MB | **视频编解码+对比节点** |
| libnndeploy_plugin_super_resolution.so | 698KB | **OpenCV/RealESRGAN 超分** |
| libnndeploy_plugin_infer.so | - | ONNX Runtime 推理 |
| 其他插件 (20个) | - | 检测/分类/分割等 |

## 🚀 下一步操作

### 在 Android Studio 中编译和测试

```bash
# 1. 打开 Android Studio
open app/android

# 2. 同步 Gradle
# File -> Sync Project with Gradle Files

# 3. 编译 APK
# Build -> Build Bundle(s) / APK(s) -> Build APK(s)

# 4. 安装到设备
# Run -> Run 'app'
```

### 测试视频超分对比功能

在应用中：
1. 选择算法：**"视频超分对比 - OpenCV"** 或 **"视频超分对比 - RealESRGAN"**
2. 输入视频：自动使用 `resources/videos/face.mp4`
3. 查看效果：左右对比显示原始和超分视频

### 预期效果

- **左侧**: 原始 face.mp4 视频
- **右侧**: 超分处理后视频（分辨率提升 2 倍）
- **控制**: 同步播放/暂停/重播
- **标签**: "原始" / "超分"

## 🔧 修复的关键问题

### Android 平台 OpenCV GUI 兼容性

**问题**: Android 版 OpenCV 不支持 `cv::imshow()` 和 `cv::waitKey()`

**解决方案**: 在 [opencv_codec.cc](plugin/source/nndeploy/codec/opencv/opencv_codec.cc) 中添加平台判断：

```cpp
base::Status OpenCvImshow::run() {
#ifndef __ANDROID__
  cv::Mat *mat = inputs_[0]->getCvMat(this);
  if (mat != nullptr) {
    cv::imshow(window_name_, *mat);
    cv::waitKey(1);
  }
#else
  // Android does not support cv::imshow
  NNDEPLOY_LOGI("OpenCvImshow is not supported on Android platform.\\n");
#endif
  return base::kStatusCodeOk;
}

// 节点注册也添加平台判断
#ifndef __ANDROID__
REGISTER_NODE("nndeploy::codec::OpenCvImshow", OpenCvImshow);
#endif
```

## 📊 性能预期

### 处理速度（基于 face.mp4 小视频）

| 算法 | 高端设备 (8GB) | 中端设备 (4GB) | 低端设备 (2GB) |
|------|---------------|---------------|---------------|
| OpenCV 超分 | 30+ FPS | 15-30 FPS | 5-15 FPS |
| RealESRGAN 超分 | 5-10 FPS | 2-5 FPS | 1-2 FPS |

### APK 体积影响

| 组件 | 体积 |
|------|------|
| 所有 .so 库 (24个) | ~50MB |
| face.mp4 测试视频 | 1.6MB |
| **总增量** | **~52MB** |

## 📚 相关文档

- [ANDROID_VIDEO_SR_COMPARE_GUIDE.md](ANDROID_VIDEO_SR_COMPARE_GUIDE.md) - 完整使用指南
- [CPP_NATIVE_SR_IMPLEMENTATION.md](CPP_NATIVE_SR_IMPLEMENTATION.md) - C++ 实现详解
- [ANDROID_SR_NON_GFPGAN_GUIDE.md](ANDROID_SR_NON_GFPGAN_GUIDE.md) - Android 超分指南

## 🎯 验证清单

部署后请验证：

- [ ] APK 成功编译
- [ ] 应用启动无崩溃
- [ ] 视频文件 face.mp4 可访问
- [ ] OpenCV 超分对比算法可运行
- [ ] RealESRGAN 超分对比算法可运行
- [ ] 视频左右对比显示正常
- [ ] 播放控制（播放/暂停/重播）工作正常

## 🐛 故障排查

### 视频文件未找到
```bash
# 检查 APK 中是否包含视频文件
unzip -l app-debug.apk | grep face.mp4
```

### 库加载失败
```bash
# 查看日志
adb logcat | grep -E "nndeploy|loadLibrary"

# 检查 .so 文件
unzip -l app-debug.apk | grep "\.so$"
```

### 超分节点未注册
```bash
# 确认插件加载
adb logcat | grep "REGISTER_NODE"

# 重新编译
cd build_android_arm64
make -j8 nndeploy_plugin_codec nndeploy_plugin_super_resolution
```

---

**部署完成时间**: 2026年1月16日  
**编译环境**: macOS (M4)  
**目标平台**: Android arm64-v8a  
**状态**: ✅ 就绪，可在 Android Studio 中测试
