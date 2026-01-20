# Android 超分方案快速开始

本文档提供在 Android 端使用 RealESRGAN 和 OpenCV SuperRes 的快速开始指南。

## 前提条件

- Android Studio 已安装
- nndeploy Android 项目已配置
- 已加载 `nndeploy_plugin_super_resolution` 原生库

## 快速使用

### 1. 验证算法已注册

打开 Android 应用，在算法列表中应该能看到：
- **RealESRGAN 超分** (图标: PhotoSizeSelectLarge)
- **OpenCV 超分** (图标: HighQuality)

### 2. 选择测试图像

准备一张测试图像，推荐：
- 分辨率: 640x480 或更小
- 格式: JPG/PNG
- 内容: 清晰的人物或风景照

### 3. 执行超分处理

#### OpenCV 超分（推荐先测试）
1. 选择 "OpenCV 超分" 算法
2. 选择输入图像
3. 点击处理
4. 查看 2倍放大的结果

**特点**: 快速、无需模型文件

#### RealESRGAN 超分
⚠️ **注意**: 需要先准备模型文件

1. 下载模型文件 (64MB):
   ```bash
   # 从项目根目录复制
   cp resources/models/RealESRGAN_x2plus.pth \\
      /path/to/device/storage/
   ```

2. 选择 "RealESRGAN 超分" 算法
3. 选择输入图像  
4. 点击处理
5. 查看高质量 2倍放大结果

**特点**: 高质量、需要模型文件

## 工作流配置

### RealESRGAN 工作流
```json
{
  "name_": "RealESRGAN超分（Android）",
  "node_repository_": [
    {
      "key_": "nndeploy::codec::OpenCvImageDecode",
      "name_": "ImageInput"
    },
    {
      "key_": "nndeploy.super_resolution.RealESRGAN",
      "name_": "RealESRGAN",
      "model_path_": "resources/models/RealESRGAN_x2plus.pth",
      "scale_": 2
    },
    {
      "key_": "nndeploy::codec::OpenCvImageEncode",
      "name_": "ImageOutput"
    }
  ]
}
```

### OpenCV 工作流
```json
{
  "name_": "OpenCV超分（Android）",
  "node_repository_": [
    {
      "key_": "nndeploy::codec::OpenCvImageDecode",
      "name_": "ImageInput"
    },
    {
      "key_": "nndeploy.super_resolution.OpenCVSuperRes",
      "name_": "OpenCVSuperRes",
      "scale_": 2,
      "sharpen_": true
    },
    {
      "key_": "nndeploy::codec::OpenCvImageEncode",
      "name_": "ImageOutput"
    }
  ]
}
```

## 模型文件说明

### 自动下载（推荐）
应用首次运行时会自动从 GitHub 下载模型：
- URL: `https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.1/RealESRGAN_x2plus.pth`
- 大小: 64MB
- 位置: `/sdcard/Android/data/com.nndeploy.ai/files/resources/models/`

### 手动复制
如果网络不好，可以手动复制模型文件：
```bash
# 1. 从项目复制到电脑
cp resources/models/RealESRGAN_x2plus.pth ~/Downloads/

# 2. 推送到 Android 设备
adb push ~/Downloads/RealESRGAN_x2plus.pth \\
  /sdcard/Android/data/com.nndeploy.ai/files/resources/models/
```

## 性能参考

| 设备类型 | RealESRGAN | OpenCV SuperRes |
|---------|------------|-----------------|
| 低端 (2GB) | 5-10s/张 | 0.5-1s/张 |
| 中端 (4GB) | 2-5s/张 | 0.2-0.5s/张 |
| 高端 (6GB+) | 1-2s/张 | 0.1-0.2s/张 |

*测试图像: 640x480 → 1280x960*

## 故障排查

### 问题: 找不到算法
**解决**: 确认 [Algorithm.kt](app/android/app/src/main/java/com/nndeploy/ai/Algorithm.kt) 已添加算法定义

### 问题: 模型文件加载失败
**解决**: 
1. 检查文件路径
2. 确认文件完整性 (64MB)
3. 查看 logcat 错误信息

### 问题: 处理速度慢
**解决**:
1. 使用 OpenCV 超分（更快）
2. 降低输入图像分辨率
3. 关闭其他后台应用

## 下一步

- 查看完整文档: [ANDROID_SR_NON_GFPGAN_GUIDE.md](ANDROID_SR_NON_GFPGAN_GUIDE.md)
- 学习 C++ 原生实现
- 测试其他超分算法

## 支持

遇到问题请查看:
- [Android 部署指南](app/android/ANDROID_REALTIME_SR_GUIDE.md)
- [GitHub Issues](https://github.com/nndeploy/nndeploy/issues)
