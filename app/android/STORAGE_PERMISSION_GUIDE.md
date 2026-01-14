# Android 存储权限授权指南

## 问题说明

在 Android 11 (API 30) 及以上版本中，应用需要 `MANAGE_EXTERNAL_STORAGE` 权限才能访问 `/sdcard/` 目录中的文件。

## 自动引导

当你点击 "📦 Copy Models from Source" 按钮时，如果没有权限，应用会：

1. 显示 Toast 提示："需要存储权限，正在打开设置页面..."
2. 自动跳转到系统设置页面

## 手动授权步骤

如果自动跳转失败，请按以下步骤手动授权：

### Android 11+ (推荐方法)

1. 打开 **设置** (Settings)
2. 进入 **应用管理** (Apps)
3. 找到并点击 **NNDeploy** 
4. 点击 **权限** (Permissions)
5. 点击 **文件和媒体** (Files and media)
6. 选择 **允许管理所有文件** (Allow management of all files)

### 使用 ADB 授权（开发调试）

```bash
# 授予所有文件访问权限
adb shell appops set com.nndeploy.app MANAGE_EXTERNAL_STORAGE allow

# 验证权限
adb shell appops get com.nndeploy.app MANAGE_EXTERNAL_STORAGE
```

## 验证权限

授权后，重新打开应用：

1. 打开 Gemma3 Chat
2. 点击顶部 📁 按钮
3. 点击 "📦 Copy Models from Source"
4. 应该开始复制（不再提示权限错误）

## 故障排除

### 问题 1：仍然提示权限错误

**解决方案**：
```bash
# 1. 完全卸载应用
adb uninstall com.nndeploy.app

# 2. 重新安装
# 在 Android Studio 中 Run → Run 'app'

# 3. 使用 ADB 授权
adb shell appops set com.nndeploy.app MANAGE_EXTERNAL_STORAGE allow

# 4. 重新启动应用
```

### 问题 2：listFiles() 返回 0

**原因**：应用进程缓存了旧的权限状态

**解决方案**：
```bash
# 强制停止应用
adb shell am force-stop com.nndeploy.app

# 重新启动
adb shell am start -n com.nndeploy.app/.MainActivity
```

### 问题 3：源目录文件不可见

**验证文件确实存在**：
```bash
# 检查文件
adb shell "ls -la /sdcard/nndeploy_models/gemma3_source/"

# 如果为空，重新上传
cd /Users/jin/work/nndeploy/models/gemma3
adb push . /sdcard/nndeploy_models/gemma3_source/
```

## 临时解决方案

如果权限问题难以解决，可以使用 ADB 直接复制：

```bash
# 方法 1：使用 adb shell cp
adb shell "cp -r /sdcard/nndeploy_models/gemma3_source/* /sdcard/nndeploy_models/gemma3/"

# 方法 2：使用 adb push
cd /Users/jin/work/nndeploy/models/gemma3
adb push . /sdcard/nndeploy_models/gemma3/

# 验证复制结果
adb shell "ls -lh /sdcard/nndeploy_models/gemma3/"
```

## 权限说明

应用需要的权限（AndroidManifest.xml）：

```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
```

- `READ_EXTERNAL_STORAGE`: 读取外部存储
- `WRITE_EXTERNAL_STORAGE`: 写入外部存储  
- `MANAGE_EXTERNAL_STORAGE`: 管理所有文件（Android 11+）

## 相关日志

查看权限相关日志：

```bash
adb logcat -c
adb logcat | grep -E "ModelPathManager|MANAGE_EXTERNAL_STORAGE|Permission"
```

成功复制时应该看到：
```
I ModelPathManager: Copying 8 files from source to /storage/emulated/0/nndeploy_models/gemma3
D ModelPathManager: Copying model.onnx (1/8)
D ModelPathManager:   ✓ Copied successfully
...
I ModelPathManager: Successfully copied all 8 files for model gemma3
```
