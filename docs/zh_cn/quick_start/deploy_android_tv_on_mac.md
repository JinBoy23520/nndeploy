# macOS 环境下 Android TV 开发环境搭建指南

本文档详细说明如何在 macOS 上搭建 Android TV 开发环境，并直接使用 nndeploy 现有的 `app/android` 项目进行开发和部署。

---

## 📋 目录

- [环境概述](#环境概述)
- [Step 1: 安装基础开发工具](#step-1-安装基础开发工具)
- [Step 2: 安装 Android 开发环境](#step-2-安装-android-开发环境)
- [Step 3: 编译 nndeploy Android 库](#step-3-编译-nndeploy-android-库)
- [Step 4: 配置 Android Studio 项目](#step-4-配置-android-studio-项目)
- [Step 5: 编译和运行应用](#step-5-编译和运行应用)
- [常见问题](#常见问题)

---

## 环境概述

### 硬件和系统要求

- **操作系统**: macOS 11.0 (Big Sur) 或更高版本
- **芯片架构**: Apple Silicon (M1/M2/M3) 或 Intel
- **内存**: 推荐 16GB 或以上
- **存储空间**: 至少 50GB 可用空间

### 将要安装的软件

| 软件 | 版本 | 用途 |
|------|------|------|
| Homebrew | 最新版 | macOS 包管理器 |
| Android Studio | Iguana 或更新 | Android 开发 IDE |
| Android NDK | r25c 或 r26+ | 原生代码编译 |
| CMake | 3.19+ | 构建系统 |
| Ninja | 最新版 | 快速构建工具 |
| Python | 3.10+ | 脚本和工具 |

---

## Step 1: 安装基础开发工具

### 1.1 安装 Homebrew

Homebrew 是 macOS 上最流行的包管理器。

```bash
# 安装 Homebrew（如果尚未安装）
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# 验证安装
brew --version
```

### 1.2 安装命令行工具

```bash
# 安装 Xcode Command Line Tools
xcode-select --install

# 验证安装
xcode-select -p
# 应该输出类似: /Library/Developer/CommandLineTools
```

### 1.3 安装必要的开发工具

```bash
# 安装 CMake、Ninja、Git 等工具
brew install cmake ninja git wget

# 安装 Python 3.10+
brew install python@3.11

# 验证安装
cmake --version
ninja --version
python3 --version
```

---

## Step 2: 安装 Android 开发环境

### 2.1 下载并安装 Android Studio

#### 方式一：从官网下载（推荐）

1. 访问 [Android Studio 官网](https://developer.android.com/studio)
2. 下载适合你芯片的版本：
   - **Apple Silicon (M1/M2/M3)**: `android-studio-*-mac_arm.dmg`
   - **Intel**: `android-studio-*-mac.dmg`
3. 打开 DMG 文件，将 Android Studio 拖入 Applications 文件夹
4. 启动 Android Studio

#### 方式二：使用 Homebrew Cask

```bash
# Apple Silicon
brew install --cask android-studio

# 启动 Android Studio
open -a "Android Studio"
```

### 2.2 首次启动配置

1. **欢迎界面**：选择 "Standard" 安装类型
2. **选择主题**：根据个人喜好选择
3. **下载组件**：等待 SDK、模拟器等组件下载完成
4. **完成安装**

### 2.3 安装 Android SDK 和 NDK

#### 通过 Android Studio UI 安装

1. 打开 Android Studio
2. 点击 `Android Studio` -> `Settings`（或 `Preferences`）
3. 导航到 `Appearance & Behavior` -> `System Settings` -> `Android SDK`

#### 安装 SDK Platforms

在 `SDK Platforms` 标签页：
- ✅ 勾选 `Android 14.0 (API 34)`（最新版本）
- ✅ 勾选 `Android 7.0 (API 24)`（最低支持版本）
- 点击 `Apply` 下载

#### 安装 SDK Tools

在 `SDK Tools` 标签页：
- ✅ `Android SDK Build-Tools` (最新版本)
- ✅ `NDK (Side by side)` - 选择版本 `25.2.9519653` 或更高
- ✅ `CMake` - 选择 `3.22.1` 或更高
- ✅ `Android SDK Platform-Tools`
- ✅ `Android SDK Command-line Tools`
- 点击 `Apply` 下载

#### 记录 SDK 和 NDK 路径

安装完成后，记录以下路径（在 SDK Location 中显示）：

```bash
# 通常路径为：
# Android SDK: ~/Library/Android/sdk
# Android NDK: ~/Library/Android/sdk/ndk/25.2.9519653
```

### 2.4 配置环境变量

编辑你的 shell 配置文件（`~/.zshrc` 或 `~/.bash_profile`）：

```bash
# 打开配置文件
nano ~/.zshrc  # 如果使用 zsh（macOS 默认）
# 或
nano ~/.bash_profile  # 如果使用 bash

# 添加以下内容：
export ANDROID_HOME=$HOME/Library/Android/sdk
export ANDROID_SDK=$ANDROID_HOME
export ANDROID_NDK=$ANDROID_HOME/ndk/25.2.9519653

# 添加到 PATH
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_NDK

# 保存并退出（Ctrl+X, Y, Enter）

# 重新加载配置
source ~/.zshrc  # 或 source ~/.bash_profile
```

验证环境变量：

```bash
# 验证 Android SDK
echo $ANDROID_HOME
ls $ANDROID_HOME

# 验证 Android NDK
echo $ANDROID_NDK
ls $ANDROID_NDK

# 验证 adb
adb --version
```

---

## Step 3: 编译 nndeploy Android 库

### 3.1 克隆或更新 nndeploy 源码

```bash
# 如果还没有克隆，先克隆仓库
cd ~/work  # 或者你的工作目录
git clone https://github.com/nndeploy/nndeploy.git
cd nndeploy

# 如果已经克隆，更新代码
cd ~/work/nndeploy
git pull

# 更新子模块
git submodule update --init --recursive
```

### 3.2 准备第三方依赖库

nndeploy 需要以下第三方库：

#### 选项 1：下载预编译库（推荐，快速）

从以下渠道下载预编译的 Android 第三方库：

- [Hugging Face](https://huggingface.co/alwaysssss/nndeploy/tree/main/third_party)
- [ModelScope](https://www.modelscope.cn/models/nndeploy/third_party)

下载并解压到 `tool/script/third_party/` 目录：

```bash
cd ~/work/nndeploy
mkdir -p tool/script/third_party

# 假设你已下载 onnxruntime1.18.0_android.zip 和 opencv4.10.0_Android.zip
# 解压到 third_party 目录
cd tool/script/third_party
unzip ~/Downloads/onnxruntime1.18.0_android.zip
unzip ~/Downloads/opencv4.10.0_Android.zip

# 验证目录结构
ls -la
# 应该看到:
# onnxruntime1.18.0_android/
# opencv4.10.0_Android/
```

#### 选项 2：自行下载 AAR 并整理（进阶）

**下载 ONNXRuntime Android AAR**:

```bash
cd ~/work/nndeploy/tool/script/third_party
mkdir -p temp && cd temp

# 下载 ONNXRuntime AAR
wget https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/1.18.0/onnxruntime-android-1.18.0.aar

# 解压 AAR
mkdir onnxruntime_aar
cd onnxruntime_aar
unzip ../onnxruntime-android-1.18.0.aar

# 整理目录结构
cd ..
mkdir -p ../onnxruntime1.18.0_android/{include,lib}
mkdir -p ../onnxruntime1.18.0_android/lib/{arm64-v8a,armeabi-v7a}

# 解压头文件
cd onnxruntime_aar
unzip headers.jar -d headers_extracted
cp -r headers_extracted/ai/onnxruntime/* ../../onnxruntime1.18.0_android/include/

# 复制库文件
cp jni/arm64-v8a/libonnxruntime.so ../../onnxruntime1.18.0_android/lib/arm64-v8a/
cp jni/armeabi-v7a/libonnxruntime.so ../../onnxruntime1.18.0_android/lib/armeabi-v7a/

# 清理临时文件
cd ../..
rm -rf temp
```

**下载 OpenCV Android SDK**:

```bash
cd ~/work/nndeploy/tool/script/third_party

# 下载 OpenCV Android SDK
wget https://github.com/opencv/opencv/releases/download/4.10.0/opencv-4.10.0-android-sdk.zip

# 解压
unzip opencv-4.10.0-android-sdk.zip

# 重命名为标准格式
mv OpenCV-android-sdk opencv4.10.0_Android

# 清理
rm opencv-4.10.0-android-sdk.zip
```

#### 选项 3：使用 nndeploy 提供的安装脚本

```bash
cd ~/work/nndeploy

# 安装 ONNXRuntime（需要网络访问 Maven）
python3 tool/script/install_onnxruntime.py

# 安装 OpenCV（需要网络访问 GitHub）
python3 tool/script/install_opencv.py
```

### 3.3 配置编译选项

```bash
cd ~/work/nndeploy
mkdir -p build_android_arm64
cp cmake/config_android.cmake build_android_arm64/config.cmake

# 编辑配置文件
code build_android_arm64/config.cmake  # 如果使用 VS Code
# 或
nano build_android_arm64/config.cmake
```

确保以下选项正确配置：

```cmake
# 推理后端 - ONNXRuntime
set(ENABLE_NNDEPLOY_INFERENCE_ONNXRUNTIME "tool/script/third_party/onnxruntime1.18.0_android")

# OpenCV
set(ENABLE_NNDEPLOY_OPENCV "tool/script/third_party/opencv4.10.0_Android")
set(NNDEPLOY_OPENCV_LIBS opencv_java4)

# MNN（可选，如果有）
set(ENABLE_NNDEPLOY_INFERENCE_MNN "tool/script/third_party/mnn3.2.4")

# 其他推理后端保持关闭
set(ENABLE_NNDEPLOY_INFERENCE_TENSORRT OFF)
set(ENABLE_NNDEPLOY_INFERENCE_NCNN OFF)
set(ENABLE_NNDEPLOY_INFERENCE_TNN OFF)

# 算法插件（根据需要）
set(ENABLE_NNDEPLOY_PLUGIN ON)
set(ENABLE_NNDEPLOY_DEMO ON)

# Java FFI（必需）
set(ENABLE_NNDEPLOY_FFI_JAVA ON)

# Python（Android 不需要）
set(ENABLE_NNDEPLOY_PYTHON OFF)

# Tokenizer（如果需要 LLM 支持）
set(ENABLE_NNDEPLOY_PLUGIN_TOKENIZER_CPP ON)
set(ENABLE_NNDEPLOY_PLUGIN_LLM ON)

# Stable Diffusion（如果需要）
set(ENABLE_NNDEPLOY_PLUGIN_STABLE_DIFFUSION ON)
```

### 3.4 编译 Android 库

```bash
cd ~/work/nndeploy/build_android_arm64

# 配置 CMake（Apple Silicon Mac）
cmake -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-24 \
    -DANDROID_STL=c++_shared \
    -DCMAKE_BUILD_TYPE=Release \
    ..

# 如果是 Intel Mac，路径可能略有不同，但命令相同

# 编译（使用所有 CPU 核心）
ninja -j$(sysctl -n hw.ncpu)

# 安装
ninja install
```

编译成功后，会在 `build_android_arm64` 目录下生成类似以下的安装目录：

```
nndeploy_2.6.2_Android_aarch64_Release_Clang/
├── include/
├── lib/
│   ├── libnndeploy.so
│   └── ...
└── third_party/
```

### 3.5 复制必要的共享库

```bash
# 复制 libc++_shared.so（必需）
cp $ANDROID_NDK/toolchains/llvm/prebuilt/darwin-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so \
   build_android_arm64/nndeploy_*/lib/

# 如果是 Apple Silicon Mac，路径可能是 darwin-aarch64，检查实际路径：
ls $ANDROID_NDK/toolchains/llvm/prebuilt/

# 根据实际路径调整，例如：
# cp $ANDROID_NDK/toolchains/llvm/prebuilt/darwin-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so \
#    build_android_arm64/nndeploy_*/lib/
```

### 3.6 使用自动化脚本（可选）

nndeploy 提供了 macOS 自动化构建脚本：

```bash
cd ~/work/nndeploy

# 查看脚本选项
python3 build_mac_arm64.py --help

# 使用脚本编译（适用于编译 macOS 本地版本，不是 Android）
# 如果要编译 Android，仍建议使用上述手动步骤
```

---

## Step 4: 配置 Android Studio 项目

### 4.1 在 Android Studio 中打开项目

1. 启动 Android Studio

2. 选择 `Open`（或 `File` -> `Open`）

3. 导航到 nndeploy 的 Android 项目目录：
   ```
   ~/work/nndeploy/app/android
   ```

4. 点击 `Open`

5. Android Studio 会自动识别 Gradle 项目并开始同步

6. 等待 Gradle 同步完成（首次可能需要下载依赖，耗时较长）

### 4.2 检查项目配置

#### 检查 Gradle 版本

打开 `gradle/wrapper/gradle-wrapper.properties`，确认 Gradle 版本：

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.7-bin.zip
```

#### 检查 Android Gradle Plugin 版本

打开 `build.gradle.kts` (Project)，检查插件版本：

```kotlin
plugins {
    id("com.android.application") version "8.5.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
```

#### 检查 compileSdk 和 targetSdk

打开 `app/build.gradle.kts`，确认 SDK 版本：

```kotlin
android {
    namespace = "com.nndeploy.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nndeploy.app"
        minSdk = 24
        targetSdk = 34
        // ...
    }
}
```

### 4.3 复制编译好的库文件到项目

使用 nndeploy 提供的脚本自动复制：

```bash
# 在终端中执行
cd ~/work/nndeploy

# 复制 .so 库文件
python3 tool/script/android_install_so.py \
    build_android_arm64/nndeploy_2.6.2_Android_aarch64_Release_Clang \
    app/android/app/src/main/jniLibs/arm64-v8a

# 复制资源文件（模型、工作流等）
python3 tool/script/android_install_resouces.py \
    -r resources/ \
    -a app/android/app/src/main/assets
```

或者手动复制：

```bash
# 手动复制库文件
mkdir -p app/android/app/src/main/jniLibs/arm64-v8a

cp build_android_arm64/nndeploy_*/lib/*.so \
   app/android/app/src/main/jniLibs/arm64-v8a/

# 如果有第三方库
cp build_android_arm64/nndeploy_*/third_party/*/lib/arm64-v8a/*.so \
   app/android/app/src/main/jniLibs/arm64-v8a/

# 验证文件
ls -lh app/android/app/src/main/jniLibs/arm64-v8a/
```

确保以下库文件存在：
- `libc++_shared.so`
- `libnndeploy.so`
- `libonnxruntime.so`
- `libopencv_java4.so`

### 4.4 验证 assets 资源

检查 `app/src/main/assets` 目录结构：

```bash
cd ~/work/nndeploy/app/android
tree app/src/main/assets

# 应该看到类似结构：
# app/src/main/assets/
# └── resources/
#     ├── workflow/
#     ├── template/
#     ├── models/
#     ├── images/
#     └── ...
```

### 4.5 同步项目

在 Android Studio 中：

1. 点击 `File` -> `Sync Project with Gradle Files`
2. 等待同步完成
3. 检查底部的 `Build` 窗口，确保没有错误

---

## Step 5: 编译和运行应用

### 5.1 连接 Android 设备或启动模拟器

#### 选项 1：使用 Android TV 模拟器

1. 在 Android Studio 中，点击 `Tools` -> `Device Manager`
2. 点击 `Create Device`
3. 选择 `TV` 类别
4. 选择设备配置（推荐 `Android TV (1080p)`）
5. 选择系统镜像：
   - 推荐 `API 34` (Android 14)
   - 或 `API 24` (Android 7.0，最低支持版本)
6. 点击 `Finish`
7. 在 Device Manager 中启动模拟器

#### 选项 2：使用真实的 Android TV 设备

1. **在 TV 上启用开发者选项**：
   - 进入 `设置` -> `关于`
   - 连续点击 `版本号` 7 次
   - 返回 `设置`，进入 `开发者选项`
   - 启用 `USB 调试` 和 `网络调试`

2. **通过网络连接 TV**（推荐）：
   ```bash
   # 在 Mac 终端中，替换为你的 TV IP 地址
   adb connect 192.168.1.100:5555
   
   # 验证连接
   adb devices
   # 应该看到: 192.168.1.100:5555  device
   ```

3. **通过 USB 连接**（如果 TV 支持）：
   - 使用 USB 线连接 Mac 和 TV
   - 在 TV 上允许 USB 调试授权

#### 选项 3：使用 Android 手机测试

```bash
# 启用手机的开发者选项和 USB 调试
# 使用 USB 线连接 Mac

# 验证连接
adb devices
```

### 5.2 构建项目

在 Android Studio 中：

1. 点击 `Build` -> `Make Project`（或按 `Cmd + F9`）
2. 等待编译完成
3. 检查 `Build` 窗口，确保编译成功

### 5.3 运行应用

1. 在顶部工具栏，从设备下拉列表中选择目标设备（模拟器或真机）

2. 点击绿色的 `Run` 按钮（或按 `Ctrl + R`）

3. 应用会自动安装并启动

4. 在 TV/设备屏幕上，你应该看到 nndeploy 应用界面

### 5.4 查看日志

在 Android Studio 底部，点击 `Logcat` 标签页：

1. **过滤日志**：
   - 在搜索框中输入 `nndeploy` 或 `com.nndeploy.app`
   - 选择 `Verbose` 级别查看所有日志

2. **常用日志标签**：
   - `nndeploy`: nndeploy 核心日志
   - `System.out`: 标准输出
   - `JNI`: JNI 调用日志

### 5.5 调试应用

#### 设置断点

1. 在 Kotlin/Java 代码中点击行号左侧设置断点
2. 点击 `Debug` 按钮（虫子图标）运行应用
3. 应用会在断点处暂停

#### 查看变量

在调试模式下，可以在 `Variables` 窗口查看变量值

#### Native 代码调试

如果需要调试 C++ 代码：

1. 打开 `Run` -> `Edit Configurations`
2. 在 `Debugger` 标签页，勾选 `Debug type: Dual (Java + Native)`
3. 点击 `Apply`

---

## 常见问题

### Q1: Gradle 同步失败

**症状**：
```
Could not download gradle-8.7-all.zip
```

**解决方案**：

1. **配置 Gradle 镜像**（国内用户）：

   创建或编辑 `~/.gradle/gradle.properties`：
   ```properties
   systemProp.http.proxyHost=127.0.0.1
   systemProp.http.proxyPort=7890
   systemProp.https.proxyHost=127.0.0.1
   systemProp.https.proxyPort=7890
   ```
   或使用阿里云镜像，编辑 `build.gradle.kts`：
   ```kotlin
   allprojects {
       repositories {
           maven { url = uri("https://maven.aliyun.com/repository/public") }
           maven { url = uri("https://maven.aliyun.com/repository/google") }
           google()
           mavenCentral()
       }
   }
   ```

2. **手动下载 Gradle**：
   ```bash
   cd ~/.gradle/wrapper/dists/gradle-8.7-all
   wget https://services.gradle.org/distributions/gradle-8.7-all.zip
   ```

### Q2: NDK 未找到

**症状**：
```
NDK is not configured
```

**解决方案**：

1. 在 Android Studio 中安装 NDK（参见 Step 2.3）

2. 或者在 `local.properties` 中手动指定：
   ```properties
   sdk.dir=/Users/你的用户名/Library/Android/sdk
   ndk.dir=/Users/你的用户名/Library/Android/sdk/ndk/25.2.9519653
   ```

### Q3: 库加载失败

**症状**：
```
java.lang.UnsatisfiedLinkError: dlopen failed: library "libonnxruntime.so" not found
```

**解决方案**：

1. 确认库文件在正确位置：
   ```bash
   ls app/android/app/src/main/jniLibs/arm64-v8a/
   ```

2. 重新复制库文件：
   ```bash
   python3 tool/script/android_install_so.py \
       build_android_arm64/nndeploy_*/  \
       app/android/app/src/main/jniLibs/arm64-v8a
   ```

3. Clean 并 Rebuild 项目：
   ```
   Build -> Clean Project
   Build -> Rebuild Project
   ```

### Q4: 编译 nndeploy 失败

**症状**：
```
CMake Error: Could not find toolchain file
```

**解决方案**：

1. 确认环境变量已设置：
   ```bash
   echo $ANDROID_NDK
   ls $ANDROID_NDK/build/cmake/android.toolchain.cmake
   ```

2. 重新加载环境变量：
   ```bash
   source ~/.zshrc
   ```

3. 使用绝对路径：
   ```bash
   cmake -G Ninja \
       -DCMAKE_TOOLCHAIN_FILE=/Users/你的用户名/Library/Android/sdk/ndk/25.2.9519653/build/cmake/android.toolchain.cmake \
       ...
   ```

### Q5: 应用在 TV 模拟器上崩溃

**症状**：
应用安装后立即崩溃

**解决方案**：

1. 检查 Logcat 中的崩溃堆栈

2. 确认 `AndroidManifest.xml` 中有 TV 声明：
   ```xml
   <uses-feature
       android:name="android.software.leanback"
       android:required="true" />
   ```

3. 检查是否有缺失的库文件

4. 尝试在手机上运行，排除 TV 特定问题

### Q6: ADB 无法连接 TV

**症状**：
```
unable to connect to 192.168.1.100:5555
```

**解决方案**：

1. 确认 Mac 和 TV 在同一网络

2. 检查 TV 的 IP 地址是否正确

3. 在 TV 上重新启用网络调试

4. 检查防火墙设置

5. 尝试重启 adb：
   ```bash
   adb kill-server
   adb start-server
   adb connect 192.168.1.100:5555
   ```

### Q7: Apple Silicon Mac 特定问题

**症状**：
某些工具或依赖在 M1/M2/M3 Mac 上不兼容

**解决方案**：

1. 确保使用 ARM64 版本的 Android Studio

2. 使用 Rosetta 运行 x86_64 工具（如果必要）：
   ```bash
   arch -x86_64 /bin/bash
   # 然后在这个 shell 中运行命令
   ```

3. 检查 Homebrew 架构：
   ```bash
   which brew
   # ARM64: /opt/homebrew/bin/brew
   # x86_64: /usr/local/bin/brew
   ```

### Q8: 资源文件过大

**症状**：
APK 体积过大或编译时内存不足

**解决方案**：

1. 只复制必要的模型文件

2. 使用量化或压缩的模型

3. 在 `build.gradle.kts` 中配置 APK 分包：
   ```kotlin
   android {
       splits {
           abi {
               isEnable = true
               reset()
               include("arm64-v8a")
               isUniversalApk = false
           }
       }
   }
   ```

---

## 快速参考命令

### 环境配置

```bash
# 安装 Homebrew
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# 安装工具
brew install cmake ninja git wget python@3.11

# 配置环境变量（添加到 ~/.zshrc）
export ANDROID_HOME=$HOME/Library/Android/sdk
export ANDROID_NDK=$ANDROID_HOME/ndk/25.2.9519653
export PATH=$PATH:$ANDROID_HOME/platform-tools
```

### 编译 nndeploy

```bash
cd ~/work/nndeploy
mkdir -p build_android_arm64
cp cmake/config_android.cmake build_android_arm64/config.cmake

cd build_android_arm64
cmake -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-24 \
    -DANDROID_STL=c++_shared \
    -DCMAKE_BUILD_TYPE=Release \
    ..

ninja -j$(sysctl -n hw.ncpu)
ninja install
```

### 复制文件到 Android 项目

```bash
# 复制库文件
python3 tool/script/android_install_so.py \
    build_android_arm64/nndeploy_2.6.2_Android_aarch64_Release_Clang \
    app/android/app/src/main/jniLibs/arm64-v8a

# 复制资源
python3 tool/script/android_install_resouces.py \
    -r resources/ \
    -a app/android/app/src/main/assets
```

### ADB 常用命令

```bash
# 连接 TV
adb connect 192.168.1.100:5555

# 查看设备
adb devices

# 安装 APK
adb install -r app-debug.apk

# 查看日志
adb logcat | grep nndeploy

# 清空日志
adb logcat -c

# 推送文件
adb push file.txt /sdcard/

# 拉取文件
adb pull /sdcard/file.txt .

# 重启 adb
adb kill-server && adb start-server
```

---

## 总结

### ✅ 完成的步骤

1. ✅ 安装 Homebrew 和命令行工具
2. ✅ 安装 Android Studio、SDK 和 NDK
3. ✅ 配置环境变量
4. ✅ 下载和配置第三方依赖库
5. ✅ 编译 nndeploy Android 库
6. ✅ 在 Android Studio 中打开项目
7. ✅ 复制库文件和资源到项目
8. ✅ 编译和运行应用

### 🎯 下一步

- 在 TV 上测试应用功能
- 优化性能和用户体验
- 添加自定义功能
- 发布应用

---

## 参考资源

- [nndeploy GitHub](https://github.com/nndeploy/nndeploy)
- [nndeploy 文档](https://nndeploy-zh.readthedocs.io/)
- [Android Studio 下载](https://developer.android.com/studio)
- [Android NDK 文档](https://developer.android.com/ndk)
- [Android TV 开发指南](https://developer.android.com/training/tv)
- [Homebrew](https://brew.sh/)

如有问题，欢迎在 GitHub Issues 提问或加入社区讨论！🚀
