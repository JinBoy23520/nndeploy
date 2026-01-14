# nndeploy Android TV 部署指南（基于ONNXRuntime）

本文档详细介绍如何将 nndeploy 部署到 Android TV 平台，并使用 ONNXRuntime 作为推理引擎运行 AI 模型。

## 目录

- [1. 概述](#1-概述)
- [2. 环境准备](#2-环境准备)
- [3. 下载和配置 ONNXRuntime Android 版本](#3-下载和配置-onnxruntime-android-版本)
- [4. 编译 nndeploy Android 库](#4-编译-nndeploy-android-库)
- [5. 创建 Android TV 应用](#5-创建-android-tv-应用)
- [6. TV 特定优化建议](#6-tv-特定优化建议)
- [7. 常见问题](#7-常见问题)

---

## 1. 概述

### 1.1 Android TV vs Android Mobile

Android TV 基于 Android 系统，但有以下特点需要注意：

- **界面交互**：主要使用遥控器（D-pad）而非触摸屏
- **性能考虑**：TV 盒子的 CPU/GPU 性能通常低于高端手机
- **内存限制**：部分 TV 盒子内存较小（1-2GB）
- **存储空间**：模型文件需要考虑存储限制
- **架构支持**：主流为 ARM64 (arm64-v8a)，少数为 ARMv7 (armeabi-v7a)

### 1.2 为什么选择 ONNXRuntime

- **跨平台**：支持 Android、iOS、Linux、Windows 等多平台
- **轻量级**：相比 TensorRT 等框架，体积更小，依赖更少
- **广泛支持**：支持大量 ONNX 模型，兼容性好
- **CPU 优化**：对 ARM CPU 有良好优化，适合 TV 盒子
- **易于集成**：提供预编译的 Android AAR 包

---

## 2. 环境准备

### 2.1 开发环境要求

#### 操作系统
- **推荐**：Linux (Ubuntu 20.04+) 或 macOS
- **可选**：Windows 10/11

#### 必需软件
```bash
# 1. Android NDK (推荐 r25c 或更高版本)
export ANDROID_NDK=/path/to/android-ndk-r25c

# 2. Android SDK
export ANDROID_SDK=/path/to/android-sdk

# 3. CMake (3.19+)
cmake --version

# 4. Ninja (可选，但推荐用于加速编译)
ninja --version

# 5. Git
git --version
```

#### 在 Ubuntu 上安装环境

```bash
# 安装基础工具
sudo apt update
sudo apt install -y build-essential cmake ninja-build git wget unzip

# 下载 Android NDK
wget https://dl.google.com/android/repository/android-ndk-r25c-linux.zip
unzip android-ndk-r25c-linux.zip -d ~/android
export ANDROID_NDK=~/android/android-ndk-r25c

# 安装 Android Studio (包含 SDK)
# 下载地址: https://developer.android.com/studio
```

#### 在 macOS 上安装环境

```bash
# 使用 Homebrew 安装工具
brew install cmake ninja git wget

# 下载 Android NDK
wget https://dl.google.com/android/repository/android-ndk-r25c-darwin.zip
unzip android-ndk-r25c-darwin.zip -d ~/android
export ANDROID_NDK=~/android/android-ndk-r25c

# 安装 Android Studio
# 下载地址: https://developer.android.com/studio
```

### 2.2 克隆 nndeploy 源码

```bash
# 克隆仓库
git clone https://github.com/nndeploy/nndeploy.git
cd nndeploy

# 拉取子模块
git submodule update --init --recursive

# 如果子模块拉取失败，使用备用脚本
python3 clone_submodule.py
```

---

## 3. 下载和配置 ONNXRuntime Android 版本

### 3.1 下载预编译的 ONNXRuntime Android 库

ONNXRuntime 官方提供了 Android AAR 包，可以从以下渠道获取：

#### 方式一：从 Maven 仓库下载（推荐）

访问 [Maven Central](https://mvnrepository.com/artifact/com.microsoft.onnxruntime/onnxruntime-android) 下载最新版本的 AAR。

#### 方式二：从 GitHub Releases 下载

```bash
# 设置版本号
ONNXRUNTIME_VERSION=1.18.0

# 下载 Android AAR
cd nndeploy
mkdir -p tool/script/third_party
cd tool/script/third_party

wget https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/${ONNXRUNTIME_VERSION}/onnxruntime-android-${ONNXRUNTIME_VERSION}.aar

# 解压 AAR 文件 (AAR 本质上是一个 ZIP 文件)
mkdir -p onnxruntime${ONNXRUNTIME_VERSION}_android
cd onnxruntime${ONNXRUNTIME_VERSION}_android
unzip ../onnxruntime-android-${ONNXRUNTIME_VERSION}.aar
```

#### 方式三：使用 nndeploy 预编译版本

nndeploy 提供了预编译的第三方库：

- [Hugging Face](https://huggingface.co/alwaysssss/nndeploy/tree/main/third_party)
- [ModelScope](https://www.modelscope.cn/models/nndeploy/third_party)

```bash
# 下载预编译的 ONNXRuntime Android 库
cd nndeploy/tool/script/third_party
# 从上述链接下载并解压到此目录
```

### 3.2 整理 ONNXRuntime 目录结构

解压后的 AAR 需要整理成以下目录结构，以便 CMake 能够正确链接：

```
onnxruntime1.18.0_android/
├── include/
│   └── onnxruntime/
│       ├── core/
│       │   └── session/
│       │       └── onnxruntime_cxx_api.h
│       └── onnxruntime_c_api.h
└── lib/
    ├── arm64-v8a/
    │   └── libonnxruntime.so
    └── armeabi-v7a/
        └── libonnxruntime.so
```

#### 整理脚本示例

```bash
# 假设在 onnxruntime1.18.0_android 目录下

# 创建目录结构
mkdir -p include/onnxruntime
mkdir -p lib/arm64-v8a
mkdir -p lib/armeabi-v7a

# 从 AAR 的 headers.jar 中提取头文件
cd headers
unzip headers.jar
cp -r ai/onnxruntime/* ../include/onnxruntime/
cd ..

# 复制库文件
cp jni/arm64-v8a/libonnxruntime.so lib/arm64-v8a/
cp jni/armeabi-v7a/libonnxruntime.so lib/armeabi-v7a/

# 清理不需要的文件
rm -rf classes.jar headers headers.jar jni AndroidManifest.xml res
```

### 3.3 配置环境变量

```bash
# 设置 ONNXRuntime 路径
export ONNXRUNTIME_ANDROID_PATH=/path/to/nndeploy/tool/script/third_party/onnxruntime1.18.0_android
```

---

## 4. 编译 nndeploy Android 库

### 4.1 配置编译选项

创建并编辑配置文件：

```bash
cd nndeploy
mkdir -p build_android_arm64
cp cmake/config_android.cmake build_android_arm64/config.cmake
cd build_android_arm64
```

编辑 `config.cmake`，启用 ONNXRuntime：

```cmake
# 推理后端选项 - 启用 ONNXRuntime
set(ENABLE_NNDEPLOY_INFERENCE_ONNXRUNTIME "tool/script/third_party/onnxruntime1.18.0_android") 

# 其他推理后端保持关闭
set(ENABLE_NNDEPLOY_INFERENCE_TENSORRT OFF)
set(ENABLE_NNDEPLOY_INFERENCE_OPENVINO OFF)
set(ENABLE_NNDEPLOY_INFERENCE_MNN OFF)
set(ENABLE_NNDEPLOY_INFERENCE_NCNN OFF)
set(ENABLE_NNDEPLOY_INFERENCE_TNN OFF)

# OpenCV 配置（根据需要选择）
set(ENABLE_NNDEPLOY_OPENCV "tool/script/third_party/opencv4.10.0_Android")
set(NNDEPLOY_OPENCV_LIBS opencv_java4)

# 算法插件（根据需要启用）
set(ENABLE_NNDEPLOY_PLUGIN ON)
set(ENABLE_NNDEPLOY_DEMO ON)

# 禁用 Python
set(ENABLE_NNDEPLOY_PYTHON OFF)

# 启用 Java FFI（用于 Android JNI）
set(ENABLE_NNDEPLOY_FFI_JAVA ON)

# 根据需要启用特定功能
set(ENABLE_NNDEPLOY_PLUGIN_TOKENIZER_CPP OFF)  # 如需 LLM 支持则设为 ON
set(ENABLE_NNDEPLOY_PLUGIN_LLM OFF)             # 如需 LLM 支持则设为 ON
set(ENABLE_NNDEPLOY_PLUGIN_STABLE_DIFFUSION OFF) # 根据需要设置
```

### 4.2 执行编译

#### 编译 ARM64 版本（推荐，适用于大多数现代 TV 盒子）

```bash
cd nndeploy/build_android_arm64

# 配置 CMake
cmake -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-24 \
    -DANDROID_STL=c++_shared \
    -DCMAKE_BUILD_TYPE=Release \
    ..

# 编译
ninja -j$(nproc)

# 安装到指定目录
ninja install
```

编译成功后，会在 `build_android_arm64` 目录下生成类似 `nndeploy_x.x.x_Android_aarch64_Release_Clang` 的安装目录。

#### 编译 ARMv7 版本（可选，适用于旧设备）

```bash
cd nndeploy
mkdir -p build_android_armv7
cp cmake/config_android.cmake build_android_armv7/config.cmake
cd build_android_armv7

# 编辑 config.cmake，同上述配置

cmake -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
    -DANDROID_ABI=armeabi-v7a \
    -DANDROID_PLATFORM=android-21 \
    -DANDROID_STL=c++_shared \
    -DCMAKE_BUILD_TYPE=Release \
    ..

ninja -j$(nproc)
ninja install
```

### 4.3 复制必要的共享库

Android NDK 的 `libc++_shared.so` 需要一起打包到应用中：

```bash
# ARM64
cp $ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so \
   build_android_arm64/nndeploy_*_Android_aarch64_Release_Clang/lib/

# ARMv7
cp $ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/arm-linux-androideabi/libc++_shared.so \
   build_android_armv7/nndeploy_*_Android_armeabi-v7a_Release_Clang/lib/
```

**macOS 用户**：将路径中的 `linux-x86_64` 替换为 `darwin-x86_64`。

---

## 5. 创建 Android TV 应用

### 5.1 创建 Android Studio 项目

1. 打开 Android Studio
2. 选择 `File` -> `New` -> `New Project`
3. 选择 `TV` -> `Empty Activity`
4. 配置项目：
   - **Name**: NNDeployTV
   - **Package name**: com.example.nndeploytv
   - **Language**: Java 或 Kotlin
   - **Minimum SDK**: API 24 (Android 7.0)

### 5.2 配置项目结构

#### 修改 `build.gradle.kts` (Module)

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.nndeploytv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.nndeploytv"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // 指定支持的 ABI
        ndk {
            abiFilters.add("arm64-v8a")
            // abiFilters.add("armeabi-v7a")  // 如需支持旧设备
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.leanback:leanback:1.0.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
}
```

### 5.3 复制库文件到项目

使用 nndeploy 提供的脚本自动复制：

```bash
# 设置变量
NNDEPLOY_BUILD_DIR=/path/to/nndeploy/build_android_arm64/nndeploy_*_Android_aarch64_Release_Clang
ANDROID_PROJECT_DIR=/path/to/NNDeployTV

# 复制 .so 库文件
python3 nndeploy/tool/script/android_install_so.py \
    $NNDEPLOY_BUILD_DIR \
    $ANDROID_PROJECT_DIR/app/src/main/jniLibs/arm64-v8a

# 复制资源文件（模型、配置等）
python3 nndeploy/tool/script/android_install_resouces.py \
    -r nndeploy/resources/ \
    -a $ANDROID_PROJECT_DIR/app/src/main/assets
```

手动复制库文件的方式：

```bash
# 创建 jniLibs 目录
mkdir -p app/src/main/jniLibs/arm64-v8a

# 复制所有 .so 文件
cp $NNDEPLOY_BUILD_DIR/lib/*.so app/src/main/jniLibs/arm64-v8a/
cp $NNDEPLOY_BUILD_DIR/third_party/*/lib/arm64-v8a/*.so app/src/main/jniLibs/arm64-v8a/
```

确保以下库文件存在：
- `libc++_shared.so`
- `libnndeploy.so`
- `libonnxruntime.so`
- `libopencv_java4.so` (如果启用了 OpenCV)

### 5.4 创建 JNI 接口

在 `app/src/main/java/com/example/nndeploytv/` 下创建 `NNDeployJNI.java`：

```java
package com.example.nndeploytv;

public class NNDeployJNI {
    static {
        // 按依赖顺序加载库
        System.loadLibrary("c++_shared");
        System.loadLibrary("onnxruntime");
        System.loadLibrary("opencv_java4");
        System.loadLibrary("nndeploy");
    }

    /**
     * 初始化推理引擎
     * @param modelPath 模型文件路径
     * @param configPath 配置文件路径
     * @return 0表示成功，非0表示失败
     */
    public native int initModel(String modelPath, String configPath);

    /**
     * 执行推理
     * @param inputData 输入数据
     * @return 推理结果
     */
    public native float[] inference(float[] inputData);

    /**
     * 释放资源
     */
    public native void release();
}
```

### 5.5 实现 C++ JNI 代码

在 nndeploy 项目的 `ffi/java/` 目录下可以找到 JNI 实现的参考代码。

参考示例（简化版）：

```cpp
#include <jni.h>
#include <string>
#include "nndeploy/dag/graph.h"
#include "nndeploy/base/log.h"

extern "C" JNIEXPORT jint JNICALL
Java_com_example_nndeploytv_NNDeployJNI_initModel(
    JNIEnv* env, jobject /* this */, jstring modelPath, jstring configPath) {
    
    const char* model_path = env->GetStringUTFChars(modelPath, nullptr);
    const char* config_path = env->GetStringUTFChars(configPath, nullptr);
    
    // 初始化模型
    // TODO: 实现具体的初始化逻辑
    
    env->ReleaseStringUTFChars(modelPath, model_path);
    env->ReleaseStringUTFChars(configPath, config_path);
    
    return 0;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_nndeploytv_NNDeployJNI_inference(
    JNIEnv* env, jobject /* this */, jfloatArray inputData) {
    
    // TODO: 实现推理逻辑
    
    return env->NewFloatArray(0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_nndeploytv_NNDeployJNI_release(
    JNIEnv* env, jobject /* this */) {
    
    // TODO: 释放资源
}
```

### 5.6 TV 界面开发

修改 `activity_main.xml` 以适配 TV 界面：

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:gravity="center"
    android:padding="48dp">

    <TextView
        android:id="@+id/titleText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="nndeploy AI Demo"
        android:textSize="48sp"
        android:textColor="@android:color/white"
        android:layout_marginBottom="32dp"/>

    <Button
        android:id="@+id/runInferenceButton"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="运行推理"
        android:textSize="24sp"
        android:focusable="true"
        android:clickable="true"/>

    <TextView
        android:id="@+id/resultText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text=""
        android:textSize="20sp"
        android:textColor="@android:color/white"
        android:layout_marginTop="32dp"/>

</LinearLayout>
```

在 `MainActivity` 中调用 JNI：

```kotlin
class MainActivity : AppCompatActivity() {
    
    private val nndeployJNI = NNDeployJNI()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        val runButton = findViewById<Button>(R.id.runInferenceButton)
        val resultText = findViewById<TextView>(R.id.resultText)
        
        // 从 assets 复制模型到内部存储
        val modelPath = copyAssetToFile("models/model.onnx")
        val configPath = copyAssetToFile("configs/config.json")
        
        // 初始化模型
        val initResult = nndeployJNI.initModel(modelPath, configPath)
        if (initResult != 0) {
            resultText.text = "模型初始化失败"
            return
        }
        
        runButton.setOnClickListener {
            // 准备输入数据
            val inputData = FloatArray(224 * 224 * 3) { 0.5f }
            
            // 运行推理
            val result = nndeployJNI.inference(inputData)
            
            // 显示结果
            resultText.text = "推理结果: ${result.contentToString()}"
        }
    }
    
    private fun copyAssetToFile(assetPath: String): String {
        val file = File(filesDir, assetPath)
        file.parentFile?.mkdirs()
        
        assets.open(assetPath).use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        return file.absolutePath
    }
    
    override fun onDestroy() {
        super.onDestroy()
        nndeployJNI.release()
    }
}
```

### 5.7 配置 TV 相关权限

修改 `AndroidManifest.xml`：

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- TV 特性声明 -->
    <uses-feature
        android:name="android.hardware.touchscreen"
        android:required="false" />
    <uses-feature
        android:name="android.software.leanback"
        android:required="true" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.Leanback"
        android:banner="@drawable/app_banner">
        
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="landscape">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

---

## 6. TV 特定优化建议

### 6.1 性能优化

#### 模型优化
```bash
# 使用量化模型减少内存和计算量
# 推荐使用 INT8 量化的 ONNX 模型

# 使用 onnxruntime 的优化工具
python -m onnxruntime.tools.optimize_model \
    --input model.onnx \
    --output model_optimized.onnx
```

#### 线程配置
```cpp
// 在初始化时配置 ONNXRuntime 线程数
// 根据 TV 盒子的 CPU 核心数调整（通常 2-4 个线程）
Ort::SessionOptions session_options;
session_options.SetIntraOpNumThreads(2);
session_options.SetInterOpNumThreads(2);
session_options.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);
```

#### 内存优化
```kotlin
// 在 Application 类中配置
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 请求更多堆内存
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        Log.d("Memory", "Max memory: ${maxMemory / 1024 / 1024} MB")
    }
}
```

### 6.2 用户体验优化

#### 加载指示器
```kotlin
// 在后台线程执行推理，避免 UI 卡顿
lifecycleScope.launch {
    progressBar.visibility = View.VISIBLE
    
    val result = withContext(Dispatchers.Default) {
        nndeployJNI.inference(inputData)
    }
    
    progressBar.visibility = View.GONE
    resultText.text = "结果: ${result.contentToString()}"
}
```

#### 遥控器适配
```xml
<!-- 确保按钮可以通过遥控器聚焦 -->
<Button
    android:id="@+id/runButton"
    android:focusable="true"
    android:focusableInTouchMode="false"
    android:nextFocusDown="@id/settingsButton"
    android:nextFocusUp="@id/exitButton" />
```

### 6.3 模型缓存策略

```kotlin
object ModelCache {
    private var cachedModel: ByteArray? = null
    
    fun loadModel(context: Context, assetPath: String): ByteArray {
        if (cachedModel == null) {
            cachedModel = context.assets.open(assetPath).use { it.readBytes() }
        }
        return cachedModel!!
    }
}
```

---

## 7. 常见问题

### 7.1 库加载失败

**问题**：`java.lang.UnsatisfiedLinkError: dlopen failed: library "libonnxruntime.so" not found`

**解决方案**：
1. 检查库文件是否在正确的 ABI 目录下（`jniLibs/arm64-v8a/`）
2. 确认库加载顺序正确（依赖库要先加载）
3. 检查设备的 ABI 架构：
   ```kotlin
   val supportedAbis = Build.SUPPORTED_ABIS
   Log.d("ABI", "Supported ABIs: ${supportedAbis.contentToString()}")
   ```

### 7.2 找不到模型文件

**问题**：模型文件路径错误

**解决方案**：
```kotlin
// 使用 assets 目录
val inputStream = assets.open("models/model.onnx")

// 或复制到内部存储
fun copyModelFromAssets(assetPath: String): String {
    val file = File(filesDir, assetPath)
    if (!file.exists()) {
        file.parentFile?.mkdirs()
        assets.open(assetPath).use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
    return file.absolutePath
}
```

### 7.3 内存不足

**问题**：在低配 TV 盒子上出现 OOM (Out of Memory)

**解决方案**：
1. 使用更小的模型或量化模型
2. 在 `AndroidManifest.xml` 中申请更多内存：
   ```xml
   <application
       android:largeHeap="true">
   ```
3. 及时释放不用的资源
4. 考虑分批处理数据

### 7.4 推理速度慢

**问题**：推理耗时过长

**解决方案**：
1. 使用 ONNXRuntime 的性能分析工具：
   ```cpp
   session_options.EnableProfiling("ort_profile.json");
   ```
2. 优化模型（剪枝、蒸馏、量化）
3. 调整线程数
4. 使用 GPU 加速（如果设备支持）：
   ```cpp
   // 需要编译支持 NNAPI 的 ONNXRuntime
   session_options.AppendExecutionProvider("Nnapi");
   ```

### 7.5 应用无法在 TV Launcher 显示

**问题**：应用在 TV 桌面找不到

**解决方案**：
确保 `AndroidManifest.xml` 配置正确：
```xml
<uses-feature
    android:name="android.software.leanback"
    android:required="true" />

<activity android:name=".MainActivity">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
    </intent-filter>
</activity>
```

---

## 8. 测试和调试

### 8.1 使用 ADB 调试

```bash
# 连接 TV 设备
adb connect <TV_IP_ADDRESS>:5555

# 安装应用
adb install -r app-debug.apk

# 查看日志
adb logcat | grep -i nndeploy

# 推送测试文件
adb push test_model.onnx /sdcard/
```

### 8.2 性能测试

在代码中添加性能统计：

```kotlin
val startTime = System.currentTimeMillis()
val result = nndeployJNI.inference(inputData)
val endTime = System.currentTimeMillis()
Log.d("Performance", "Inference time: ${endTime - startTime} ms")
```

### 8.3 使用 Android Profiler

在 Android Studio 中使用 Profiler 工具：
- CPU Profiler：分析 CPU 使用情况
- Memory Profiler：检测内存泄漏
- Network Profiler：监控网络请求

---

## 9. 参考资源

### 官方文档
- [nndeploy GitHub](https://github.com/nndeploy/nndeploy)
- [nndeploy 文档](https://nndeploy-zh.readthedocs.io/)
- [ONNXRuntime 文档](https://onnxruntime.ai/docs/)
- [Android TV 开发指南](https://developer.android.com/training/tv)

### 示例代码
- [nndeploy Android App 示例](../../app/android/)
- [nndeploy Java FFI](../../ffi/java/)

### 社区支持
- [nndeploy Discord](https://discord.gg/9rUwfAaMbr)
- 微信群：参考[文档](../knowledge_shared/wechat.md)

---

## 10. 总结

通过本文档，你应该能够：

✅ 配置好 Android TV 开发环境  
✅ 下载和集成 ONNXRuntime Android 库  
✅ 编译 nndeploy Android 版本  
✅ 创建并运行 Android TV 应用  
✅ 优化性能和用户体验  
✅ 解决常见问题  

如果遇到问题，欢迎在 GitHub Issues 提问或加入社区讨论。祝你部署顺利！🚀
