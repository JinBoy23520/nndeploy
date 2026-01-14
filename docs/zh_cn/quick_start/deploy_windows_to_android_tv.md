# 从 Windows 部署迁移到 Android TV 完整指南

本文档详细说明如何将在 Windows 上已经部署成功的 nndeploy 模型和工作流迁移到 Android TV 平台运行。文档分为三个部分：Windows 电脑操作、VS Code 操作、Android Studio 操作。

---

## 📋 目录

- [前置条件](#前置条件)
- [Part 1: Windows 电脑操作](#part-1-windows-电脑操作)
- [Part 2: VS Code 操作](#part-2-vs-code-操作)
- [Part 3: Android Studio 操作](#part-3-android-studio-操作)
- [Part 4: 部署到 Android TV](#part-4-部署到-android-tv)
- [常见问题](#常见问题)

---

## 前置条件

### ✅ 已完成的准备工作

假设你已经在 Windows 上成功：

- ✅ 安装了 nndeploy（通过 `pip install nndeploy`）
- ✅ 在可视化界面中创建并调试好了工作流
- ✅ 模型能够正常运行和推理
- ✅ 导出了工作流 JSON 文件

### 📦 需要准备的内容

从 Windows 部署中需要提取以下内容：

1. **工作流 JSON 文件**（从可视化界面导出）
2. **模型文件**（ONNX 模型或其他格式）
3. **测试数据**（图片、视频等）
4. **配置文件**（如果有自定义配置）

### 🔧 需要的软件和工具

- **Windows 电脑**：已安装 nndeploy 和 Python 3.10+
- **VS Code**：用于编辑和管理代码
- **Android Studio**：用于开发和调试 Android TV 应用
- **Android NDK**：r25c 或更高版本
- **Android TV 设备或模拟器**：用于测试

---

## Part 1: Windows 电脑操作

### 步骤 1.1：导出工作流

#### 通过可视化界面导出

1. 启动 nndeploy 可视化界面：
   ```cmd
   nndeploy-app --port 8000
   ```

2. 在浏览器中打开 `http://localhost:8000`

3. 加载你已经调试好的工作流

4. 点击右上角的**导出**按钮，选择**导出 JSON**

5. 保存 JSON 文件到工作目录，例如：
   ```
   C:\nndeploy\workflows\my_workflow.json
   ```

#### 验证工作流

在 Windows 上测试导出的 JSON 工作流是否能正常运行：

```cmd
# 使用命令行测试
nndeploy-run-json --json_file C:\nndeploy\workflows\my_workflow.json
```

### 步骤 1.2：整理资源文件

创建一个资源文件夹，集中管理所有需要迁移到 Android 的文件：

```cmd
# 创建迁移资源目录
mkdir C:\nndeploy\android_deploy
cd C:\nndeploy\android_deploy

# 创建子目录
mkdir workflows
mkdir models
mkdir test_data
mkdir configs
```

#### 复制文件

```cmd
# 复制工作流 JSON
copy C:\nndeploy\workflows\my_workflow.json workflows\

# 复制模型文件（根据实际路径调整）
copy C:\nndeploy\resources\models\*.onnx models\

# 复制测试数据
copy C:\nndeploy\resources\images\test.jpg test_data\
copy C:\nndeploy\resources\videos\test.mp4 test_data\

# 如果有自定义配置文件
copy C:\nndeploy\configs\*.json configs\
```

### 步骤 1.3：记录模型信息

创建一个 `model_info.txt` 文件，记录模型的关键信息：

```cmd
cd C:\nndeploy\android_deploy
notepad model_info.txt
```

在文件中记录：

```text
=== 模型信息 ===
模型名称: YOLOv8s / RMBGv1.4 / 其他
模型文件: model.onnx
模型大小: 25.6 MB
输入尺寸: 640x640 / 224x224
输入格式: RGB / BGR
推理框架: ONNXRuntime / MNN / TNN
线程数: 4
是否使用GPU: 否

=== 工作流信息 ===
工作流名称: object_detection_pipeline
输入类型: 图片 / 视频 / 摄像头
输出类型: 检测框 / 分割掩码 / 分类结果
预处理: Resize, Normalize
后处理: NMS, DrawBox

=== 性能信息 ===
Windows CPU推理时间: 50ms
内存占用: 200MB
```

### 步骤 1.4：下载 Android 依赖库

如果你的 Windows 电脑上还没有下载 Android 版本的第三方库，需要下载：

#### 下载 ONNXRuntime Android AAR

```cmd
# 创建第三方库目录
mkdir C:\nndeploy\third_party_android
cd C:\nndeploy\third_party_android

# 使用浏览器下载或使用 PowerShell
# ONNXRuntime Android AAR
# 下载地址: https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/1.18.0/
```

也可以使用 PowerShell 下载：

```powershell
# 在 PowerShell 中执行
$url = "https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/1.18.0/onnxruntime-android-1.18.0.aar"
$output = "C:\nndeploy\third_party_android\onnxruntime-android-1.18.0.aar"
Invoke-WebRequest -Uri $url -OutFile $output
```

#### 下载 OpenCV Android SDK（如果需要）

```powershell
$url = "https://github.com/opencv/opencv/releases/download/4.10.0/opencv-4.10.0-android-sdk.zip"
$output = "C:\nndeploy\third_party_android\opencv-android-sdk.zip"
Invoke-WebRequest -Uri $url -OutFile $output

# 解压
Expand-Archive -Path $output -DestinationPath "C:\nndeploy\third_party_android\"
```

### 步骤 1.5：准备传输文件

将所有需要的文件压缩，便于传输到 Android 开发环境：

```cmd
# 使用 Windows 自带的压缩功能或 7-Zip
# 右键点击 android_deploy 文件夹 -> 发送到 -> 压缩(zipped)文件夹
```

或者使用 PowerShell：

```powershell
Compress-Archive -Path "C:\nndeploy\android_deploy\*" -DestinationPath "C:\nndeploy\android_deploy.zip"
```

---

## Part 2: VS Code 操作

VS Code 主要用于编辑配置文件、编写 JNI 代码、管理项目文件。

### 步骤 2.1：安装 VS Code 扩展

打开 VS Code，安装以下扩展：

```
- C/C++ (Microsoft)
- CMake (twxs)
- CMake Tools (Microsoft)
- Android iOS Emulator (DiemasMichiels)
- XML (Red Hat)
- Gradle for Java (Microsoft)
```

### 步骤 2.2：打开 nndeploy 项目

在 VS Code 中打开 nndeploy 源码目录：

```
File -> Open Folder -> 选择 nndeploy 目录
```

### 步骤 2.3：配置 Android 编译环境

#### 创建编译配置文件

在 VS Code 中打开终端（Terminal -> New Terminal），在 Windows 上需要使用 **Git Bash** 或 **PowerShell**：

```bash
# 在 nndeploy 根目录下
cd nndeploy
mkdir build_android_arm64
cp cmake/config_android.cmake build_android_arm64/config.cmake
```

#### 编辑配置文件

在 VS Code 中打开 `build_android_arm64/config.cmake`：

```cmake
# 推理后端 - 启用 ONNXRuntime
set(ENABLE_NNDEPLOY_INFERENCE_ONNXRUNTIME "C:/nndeploy/third_party_android/onnxruntime1.18.0_android")

# 或者使用相对路径
# set(ENABLE_NNDEPLOY_INFERENCE_ONNXRUNTIME "tool/script/third_party/onnxruntime1.18.0_android")

# OpenCV（如果需要）
set(ENABLE_NNDEPLOY_OPENCV "C:/nndeploy/third_party_android/opencv4.10.0_Android")
set(NNDEPLOY_OPENCV_LIBS opencv_java4)

# 其他推理后端保持关闭
set(ENABLE_NNDEPLOY_INFERENCE_TENSORRT OFF)
set(ENABLE_NNDEPLOY_INFERENCE_MNN OFF)
set(ENABLE_NNDEPLOY_INFERENCE_NCNN OFF)

# 算法插件（根据你的工作流需要）
set(ENABLE_NNDEPLOY_PLUGIN ON)
set(ENABLE_NNDEPLOY_DEMO ON)

# 禁用 Python
set(ENABLE_NNDEPLOY_PYTHON OFF)

# 启用 Java FFI
set(ENABLE_NNDEPLOY_FFI_JAVA ON)
```

### 步骤 2.4：配置 NDK 环境变量

#### 在 Git Bash 中（推荐）

```bash
# 设置 Android NDK 路径（根据实际安装路径调整）
export ANDROID_NDK=/c/Android/ndk/25.2.9519653
export ANDROID_SDK=/c/Android/Sdk

# 验证路径
ls $ANDROID_NDK
```

#### 在 PowerShell 中

```powershell
$env:ANDROID_NDK = "C:\Android\ndk\25.2.9519653"
$env:ANDROID_SDK = "C:\Android\Sdk"

# 验证
Test-Path $env:ANDROID_NDK
```

#### 在 CMD 中

```cmd
set ANDROID_NDK=C:\Android\ndk\25.2.9519653
set ANDROID_SDK=C:\Android\Sdk
```

### 步骤 2.5：编译 Android 库（在 Windows 上）

如果你的 Windows 电脑上已经安装了 Android NDK 和 CMake，可以直接在 Windows 上编译 Android 库。

#### 使用 Git Bash 或 WSL

```bash
cd build_android_arm64

# 配置 CMake
cmake -G "Ninja" \
    -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-24 \
    -DANDROID_STL=c++_shared \
    -DCMAKE_BUILD_TYPE=Release \
    ..

# 编译
ninja -j8

# 安装
ninja install
```

#### 或者使用 CMake GUI（Windows 友好方式）

1. 打开 CMake GUI
2. 设置源码目录：`C:/nndeploy/nndeploy`
3. 设置构建目录：`C:/nndeploy/nndeploy/build_android_arm64`
4. 点击 **Configure**
5. 选择生成器：**Ninja** 或 **Visual Studio**
6. 选择 **Specify toolchain file for cross-compiling**
7. Toolchain file：`C:/Android/ndk/25.2.9519653/build/cmake/android.toolchain.cmake`
8. 点击 **Finish**
9. 设置以下变量：
   - `ANDROID_ABI` = `arm64-v8a`
   - `ANDROID_PLATFORM` = `android-24`
   - `ANDROID_STL` = `c++_shared`
   - `CMAKE_BUILD_TYPE` = `Release`
10. 再次点击 **Configure**，然后点击 **Generate**
11. 在命令行中运行：
    ```cmd
    cmake --build build_android_arm64 --config Release
    cmake --install build_android_arm64
    ```

### 步骤 2.6：整理编译产物

编译成功后，在 `build_android_arm64` 目录下会生成安装目录，例如：

```
build_android_arm64/
└── nndeploy_2.6.2_Android_aarch64_Release_Clang/
    ├── include/
    ├── lib/
    │   ├── libnndeploy.so
    │   ├── libonnxruntime.so
    │   └── libopencv_java4.so
    └── third_party/
```

复制 `libc++_shared.so`：

```bash
# Git Bash
cp $ANDROID_NDK/toolchains/llvm/prebuilt/windows-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so \
   build_android_arm64/nndeploy_*/lib/
```

```cmd
# CMD
copy %ANDROID_NDK%\toolchains\llvm\prebuilt\windows-x86_64\sysroot\usr\lib\aarch64-linux-android\libc++_shared.so ^
     build_android_arm64\nndeploy_2.6.2_Android_aarch64_Release_Clang\lib\
```

### 步骤 2.7：查看和编辑 JNI 代码（可选）

在 VS Code 中浏览 JNI 相关代码：

```
nndeploy/
└── ffi/
    └── java/
        ├── nndeploy_jni.cc
        └── README.md
```

如果需要自定义 JNI 接口，可以在这里编辑 C++ 代码。

---

## Part 3: Android Studio 操作

### 步骤 3.1：创建 Android TV 项目

1. **启动 Android Studio**

2. **创建新项目**：
   - `File` -> `New` -> `New Project`
   - 选择 `TV` -> `Empty Activity`
   - 配置：
     - **Name**: `NNDeployTV`
     - **Package name**: `com.example.nndeploytv`
     - **Save location**: `C:\AndroidProjects\NNDeployTV`
     - **Language**: `Java` 或 `Kotlin`
     - **Minimum SDK**: `API 24 (Android 7.0)`
   - 点击 **Finish**

### 步骤 3.2：配置项目 Gradle

#### 修改 `build.gradle.kts` (Module: app)

在 Android Studio 中打开 `app/build.gradle.kts`：

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

    // 配置 JNI 库路径
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
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
```

点击右上角的 **Sync Now** 同步项目。

### 步骤 3.3：导入 nndeploy 库文件

#### 方法一：使用 nndeploy 提供的脚本（推荐）

如果你在 Linux/macOS 环境下或使用 WSL：

```bash
# 在 nndeploy 根目录
python3 tool/script/android_install_so.py \
    build_android_arm64/nndeploy_2.6.2_Android_aarch64_Release_Clang \
    C:/AndroidProjects/NNDeployTV/app/src/main/jniLibs/arm64-v8a
```

#### 方法二：手动复制（Windows 友好）

1. 在 Android Studio 中，右键点击 `app` -> `New` -> `Folder` -> `JNI Folder`

2. 创建目录结构：
   ```
   app/src/main/jniLibs/arm64-v8a/
   ```

3. 复制库文件：

   打开 Windows 资源管理器，从以下位置复制所有 `.so` 文件：
   
   ```
   C:\nndeploy\nndeploy\build_android_arm64\nndeploy_2.6.2_Android_aarch64_Release_Clang\lib\*.so
   ```
   
   粘贴到：
   
   ```
   C:\AndroidProjects\NNDeployTV\app\src\main\jniLibs\arm64-v8a\
   ```

4. 确保以下库文件都已复制：
   - `libc++_shared.so`
   - `libnndeploy.so`
   - `libonnxruntime.so`
   - `libopencv_java4.so`（如果启用了 OpenCV）

#### 验证库文件

在 Android Studio 的项目视图中，应该看到：

```
app/
└── src/
    └── main/
        └── jniLibs/
            └── arm64-v8a/
                ├── libc++_shared.so
                ├── libnndeploy.so
                ├── libonnxruntime.so
                └── libopencv_java4.so
```

### 步骤 3.4：导入资源文件

#### 创建 assets 目录

1. 在 Android Studio 中，右键点击 `app/src/main` -> `New` -> `Folder` -> `Assets Folder`

2. 创建子目录：
   ```
   app/src/main/assets/
   ├── workflows/
   ├── models/
   └── test_data/
   ```

#### 复制资源文件

使用 Windows 资源管理器，将之前准备的文件复制到 assets：

```
从: C:\nndeploy\android_deploy\workflows\my_workflow.json
到: C:\AndroidProjects\NNDeployTV\app\src\main\assets\workflows\

从: C:\nndeploy\android_deploy\models\*.onnx
到: C:\AndroidProjects\NNDeployTV\app\src\main\assets\models\

从: C:\nndeploy\android_deploy\test_data\*
到: C:\AndroidProjects\NNDeployTV\app\src\main\assets\test_data\
```

或使用 nndeploy 脚本：

```bash
python3 tool/script/android_install_resouces.py \
    -r C:\nndeploy\android_deploy \
    -a C:\AndroidProjects\NNDeployTV\app\src\main\assets
```

### 步骤 3.5：创建 JNI 接口

#### 创建 Java 类

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
     * 从 JSON 文件初始化工作流
     * @param jsonPath JSON 工作流文件路径
     * @return 0表示成功，非0表示失败
     */
    public native int initFromJson(String jsonPath);

    /**
     * 设置输入数据（图片路径）
     * @param inputPath 输入图片路径
     * @return 0表示成功，非0表示失败
     */
    public native int setInput(String inputPath);

    /**
     * 运行推理
     * @return 0表示成功，非0表示失败
     */
    public native int run();

    /**
     * 获取输出结果
     * @return 结果字符串（JSON 格式）
     */
    public native String getOutput();

    /**
     * 释放资源
     */
    public native void release();
}
```

### 步骤 3.6：配置 AndroidManifest.xml

打开 `app/src/main/AndroidManifest.xml`，添加 TV 相关配置：

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- TV 特性声明 -->
    <uses-feature
        android:name="android.hardware.touchscreen"
        android:required="false" />
    <uses-feature
        android:name="android.software.leanback"
        android:required="true" />

    <!-- 读取存储权限（如果需要从外部存储读取文件） -->
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.Leanback"
        android:banner="@drawable/app_banner"
        android:largeHeap="true">
        
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

### 步骤 3.7：实现 MainActivity

打开 `MainActivity.kt`（或 `MainActivity.java`），实现界面和逻辑：

#### Kotlin 版本

```kotlin
package com.example.nndeploytv

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private val nndeployJNI = NNDeployJNI()
    private lateinit var resultText: TextView
    private lateinit var runButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        resultText = findViewById(R.id.resultText)
        runButton = findViewById(R.id.runInferenceButton)

        // 初始化
        initModel()

        // 运行推理
        runButton.setOnClickListener {
            runInference()
        }
    }

    private fun initModel() {
        lifecycleScope.launch {
            resultText.text = "正在初始化模型..."

            val result = withContext(Dispatchers.IO) {
                // 从 assets 复制文件到内部存储
                val jsonPath = copyAssetToFile("workflows/my_workflow.json")
                val modelPath = copyAssetToFile("models/model.onnx")
                val testImagePath = copyAssetToFile("test_data/test.jpg")

                // 初始化工作流
                nndeployJNI.initFromJson(jsonPath)
            }

            if (result == 0) {
                resultText.text = "模型初始化成功！"
                runButton.isEnabled = true
            } else {
                resultText.text = "模型初始化失败: $result"
            }
        }
    }

    private fun runInference() {
        lifecycleScope.launch {
            resultText.text = "正在运行推理..."
            runButton.isEnabled = false

            val result = withContext(Dispatchers.IO) {
                val testImagePath = File(filesDir, "test_data/test.jpg").absolutePath

                // 设置输入
                nndeployJNI.setInput(testImagePath)

                // 运行推理
                val runResult = nndeployJNI.run()
                if (runResult == 0) {
                    // 获取输出
                    nndeployJNI.getOutput()
                } else {
                    "推理失败: $runResult"
                }
            }

            resultText.text = "推理结果:\n$result"
            runButton.isEnabled = true
        }
    }

    private fun copyAssetToFile(assetPath: String): String {
        val file = File(filesDir, assetPath)
        file.parentFile?.mkdirs()

        if (!file.exists()) {
            assets.open(assetPath).use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
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

#### Java 版本

```java
package com.example.nndeploytv;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private NNDeployJNI nndeployJNI = new NNDeployJNI();
    private TextView resultText;
    private Button runButton;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        resultText = findViewById(R.id.resultText);
        runButton = findViewById(R.id.runInferenceButton);

        initModel();

        runButton.setOnClickListener(v -> runInference());
    }

    private void initModel() {
        resultText.setText("正在初始化模型...");
        
        executor.execute(() -> {
            try {
                // 从 assets 复制文件
                String jsonPath = copyAssetToFile("workflows/my_workflow.json");
                String modelPath = copyAssetToFile("models/model.onnx");
                String testImagePath = copyAssetToFile("test_data/test.jpg");

                // 初始化工作流
                int result = nndeployJNI.initFromJson(jsonPath);

                runOnUiThread(() -> {
                    if (result == 0) {
                        resultText.setText("模型初始化成功！");
                        runButton.setEnabled(true);
                    } else {
                        resultText.setText("模型初始化失败: " + result);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    resultText.setText("初始化错误: " + e.getMessage());
                });
            }
        });
    }

    private void runInference() {
        resultText.setText("正在运行推理...");
        runButton.setEnabled(false);

        executor.execute(() -> {
            try {
                File testImage = new File(getFilesDir(), "test_data/test.jpg");
                
                // 设置输入
                nndeployJNI.setInput(testImage.getAbsolutePath());

                // 运行推理
                int runResult = nndeployJNI.run();
                
                String output;
                if (runResult == 0) {
                    // 获取输出
                    output = nndeployJNI.getOutput();
                } else {
                    output = "推理失败: " + runResult;
                }

                final String finalOutput = output;
                runOnUiThread(() -> {
                    resultText.setText("推理结果:\n" + finalOutput);
                    runButton.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    resultText.setText("推理错误: " + e.getMessage());
                    runButton.setEnabled(true);
                });
            }
        });
    }

    private String copyAssetToFile(String assetPath) throws Exception {
        File file = new File(getFilesDir(), assetPath);
        file.getParentFile().mkdirs();

        if (!file.exists()) {
            try (InputStream in = getAssets().open(assetPath);
                 OutputStream out = new FileOutputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
        }

        return file.getAbsolutePath();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        nndeployJNI.release();
        executor.shutdown();
    }
}
```

### 步骤 3.8：创建 UI 布局

打开 `app/src/main/res/layout/activity_main.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:gravity="center"
    android:padding="48dp"
    android:background="#000000">

    <TextView
        android:id="@+id/titleText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="nndeploy AI Demo"
        android:textSize="48sp"
        android:textColor="#FFFFFF"
        android:layout_marginBottom="32dp"/>

    <Button
        android:id="@+id/runInferenceButton"
        android:layout_width="300dp"
        android:layout_height="80dp"
        android:text="运行推理"
        android:textSize="24sp"
        android:focusable="true"
        android:enabled="false"
        android:clickable="true"/>

    <TextView
        android:id="@+id/resultText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="等待初始化..."
        android:textSize="20sp"
        android:textColor="#FFFFFF"
        android:layout_marginTop="32dp"
        android:gravity="center"/>

</LinearLayout>
```

### 步骤 3.9：编译和测试

1. **连接 Android TV 设备或启动模拟器**

   - 物理设备：通过 ADB 连接
     ```cmd
     adb connect <TV_IP>:5555
     ```
   
   - 模拟器：在 Android Studio 中启动 TV 模拟器
     - `Tools` -> `Device Manager` -> `Create Device`
     - 选择 `TV` 类别
     - 选择合适的硬件配置（推荐 1080p API 24+）

2. **构建项目**

   - 点击 `Build` -> `Make Project`
   - 等待构建完成

3. **运行应用**

   - 点击绿色的运行按钮（Run 'app'）
   - 选择目标设备（TV 设备或模拟器）
   - 等待应用安装和启动

4. **调试**

   - 查看 Logcat 输出：
     - `View` -> `Tool Windows` -> `Logcat`
     - 过滤标签：`nndeploy` 或 `System.out`

---

## Part 4: 部署到 Android TV

### 步骤 4.1：通过 ADB 安装到真实设备

#### 启用 TV 开发者选项

1. 在 Android TV 上，进入 `设置` -> `关于`
2. 连续点击 `版本号` 7 次，启用开发者选项
3. 返回设置，进入 `开发者选项`
4. 启用 `USB 调试` 和 `网络调试`
5. 记下 TV 的 IP 地址（在 `设置` -> `网络` 中查看）

#### 通过网络连接

在 Windows 命令行中：

```cmd
# 连接到 TV（替换为你的 TV IP）
adb connect 192.168.1.100:5555

# 验证连接
adb devices

# 应该看到类似输出：
# 192.168.1.100:5555  device
```

#### 安装 APK

```cmd
# 方法一：从 Android Studio 直接运行（推荐）
# 在 Android Studio 中选择连接的设备，点击 Run

# 方法二：手动安装编译好的 APK
cd C:\AndroidProjects\NNDeployTV\app\build\outputs\apk\debug
adb install -r app-debug.apk
```

### 步骤 4.2：查看日志和调试

```cmd
# 实时查看日志
adb logcat | findstr "nndeploy"

# 或者查看所有日志
adb logcat

# 查看崩溃信息
adb logcat | findstr "FATAL"

# 清空日志
adb logcat -c
```

### 步骤 4.3：推送文件到设备（可选）

如果需要在设备上测试新的模型或数据：

```cmd
# 推送模型文件
adb push C:\nndeploy\models\new_model.onnx /sdcard/

# 推送测试图片
adb push C:\nndeploy\test_data\test2.jpg /sdcard/

# 从设备拉取文件
adb pull /sdcard/output.jpg C:\nndeploy\output\
```

### 步骤 4.4：性能测试

在应用中添加性能统计代码，然后通过 Logcat 查看：

```kotlin
val startTime = System.currentTimeMillis()
nndeployJNI.run()
val endTime = System.currentTimeMillis()
Log.d("Performance", "Inference time: ${endTime - startTime} ms")
```

在 Logcat 中过滤 `Performance` 标签查看推理时间。

---

## 常见问题

### Q1: 库加载失败：UnsatisfiedLinkError

**症状**：
```
java.lang.UnsatisfiedLinkError: dlopen failed: library "libonnxruntime.so" not found
```

**解决方案**：

1. 检查库文件是否在正确位置：
   ```
   app/src/main/jniLibs/arm64-v8a/libonnxruntime.so
   ```

2. 检查设备架构：
   ```kotlin
   Log.d("ABI", "Supported ABIs: ${Build.SUPPORTED_ABIS.contentToString()}")
   ```

3. 确认加载顺序正确（依赖库先加载）

4. 清理并重新构建项目：
   ```
   Build -> Clean Project
   Build -> Rebuild Project
   ```

### Q2: 找不到 assets 中的文件

**症状**：
```
java.io.FileNotFoundException: workflows/my_workflow.json
```

**解决方案**：

1. 确认文件已正确放置在 `app/src/main/assets/` 目录下

2. 在 Android Studio 中，切换到 `Project` 视图查看文件结构

3. 重新同步 Gradle：
   ```
   File -> Sync Project with Gradle Files
   ```

4. 检查文件路径大小写（Android 文件系统区分大小写）

### Q3: 模型初始化失败

**症状**：
```
initFromJson 返回非 0 值
```

**解决方案**：

1. 确认模型文件完整且未损坏

2. 检查 JSON 工作流配置是否正确

3. 查看 Logcat 中的详细错误信息

4. 确认模型路径使用绝对路径：
   ```kotlin
   val absolutePath = File(filesDir, "models/model.onnx").absolutePath
   ```

### Q4: 内存不足 (OOM)

**症状**：
```
java.lang.OutOfMemoryError: Failed to allocate a XXX byte allocation
```

**解决方案**：

1. 在 `AndroidManifest.xml` 中启用 `largeHeap`：
   ```xml
   <application android:largeHeap="true">
   ```

2. 使用更小的模型或量化模型

3. 及时释放不用的资源

4. 分批处理数据

### Q5: 推理速度慢

**解决方案**：

1. 调整线程数（在 C++ 代码中配置 ONNXRuntime）

2. 使用量化模型（INT8）

3. 优化模型结构

4. 考虑使用 GPU 加速（如果设备支持 NNAPI）

### Q6: TV 遥控器无法操作按钮

**解决方案**：

1. 确保按钮设置了 `focusable="true"`

2. 配置焦点导航：
   ```xml
   <Button
       android:nextFocusDown="@id/nextButton"
       android:nextFocusUp="@id/prevButton" />
   ```

3. 测试遥控器按键响应：
   ```kotlin
   override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
       Log.d("KeyEvent", "Key code: $keyCode")
       return super.onKeyDown(keyCode, event)
   }
   ```

---

## 总结

### ✅ Windows 电脑完成的任务

- ✅ 导出工作流 JSON
- ✅ 整理模型和资源文件
- ✅ 下载 Android 依赖库
- ✅ （可选）编译 Android 库

### ✅ VS Code 完成的任务

- ✅ 配置 Android 编译环境
- ✅ 编辑 CMake 配置文件
- ✅ 编译 nndeploy Android 库
- ✅ （可选）编辑 JNI 代码

### ✅ Android Studio 完成的任务

- ✅ 创建 Android TV 项目
- ✅ 导入库文件和资源
- ✅ 实现 JNI 接口和应用逻辑
- ✅ 编译、调试和部署到设备

### 🎯 下一步

1. 优化应用性能和用户体验
2. 添加更多功能（摄像头输入、视频处理等）
3. 美化 TV 界面
4. 发布到 Google Play（TV 应用商店）

---

## 参考资源

- [nndeploy GitHub](https://github.com/nndeploy/nndeploy)
- [nndeploy 文档](https://nndeploy-zh.readthedocs.io/)
- [Android TV 开发指南](https://developer.android.com/training/tv)
- [ONNXRuntime Android 文档](https://onnxruntime.ai/docs/tutorials/mobile/)
- [Android NDK 文档](https://developer.android.com/ndk)

如有问题，欢迎在 GitHub Issues 提问或加入 Discord 社区讨论！🚀
