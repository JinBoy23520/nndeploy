# NNDeploy Android 快速学习指南

## 概述

NNDeploy 是一个跨平台的高性能神经网络推理框架，支持多种后端（MNN、ONNX Runtime、OpenVINO 等）。本指南帮助您快速上手 Android 平台上的 NNDeploy 开发。

## 环境准备

### 1. 开发环境要求
- **操作系统**: Windows/Linux/macOS
- **Android Studio**: 2022.3.1 或更高版本
- **Android SDK**: API 级别 21+ (Android 5.0+)
- **Android NDK**: r21e 或 r25c (推荐 r25c)
- **CMake**: 3.22.1 或更高版本
- **Python**: 3.8+ (用于构建脚本)

### 2. NNDeploy 源码获取
```bash
git clone https://github.com/microsoft/nndeploy.git
cd nndeploy
git submodule update --init --recursive
```

## Android 构建流程

### 1. 配置构建选项
创建 `build/android_config.cmake`：
```cmake
# Android 构建配置
set(CMAKE_SYSTEM_NAME Android)
set(CMAKE_SYSTEM_VERSION 21)  # Android API 级别
set(CMAKE_ANDROID_ARCH_ABI arm64-v8a)  # 或 armeabi-v7a
set(CMAKE_ANDROID_NDK /path/to/android-ndk-r25c)
set(CMAKE_ANDROID_STL c++_shared)  # 或 c++_static

# NNDeploy 选项
set(ENABLE_NNDEPLOY_BUILD_SHARED ON)
set(ENABLE_NNDEPLOY_PYTHON OFF)  # Android 上通常关闭 Python
set(ENABLE_NNDEPLOY_PLUGIN ON)

# 推理后端选择
set(ENABLE_NNDEPLOY_INFERENCE_MNN ON)    # MNN 后端
set(ENABLE_NNDEPLOY_INFERENCE_ONNX OFF)  # ONNX Runtime
set(ENABLE_NNDEPLOY_INFERENCE_OV OFF)    # OpenVINO

# 设备支持
set(ENABLE_NNDEPLOY_DEVICE_CPU ON)
set(ENABLE_NNDEPLOY_DEVICE_ARM ON)
set(ENABLE_NNDEPLOY_DEVICE_OPENCL OFF)  # 根据需要启用
```

### 2. 构建命令
```bash
# 创建构建目录
mkdir build_android && cd build_android

# 配置 CMake
cmake -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
      -DANDROID_ABI=arm64-v8a \
      -DANDROID_PLATFORM=android-21 \
      -DCMAKE_BUILD_TYPE=Release \
      -C../build/android_config.cmake \
      ..

# 构建
cmake --build . --config Release --parallel 8
```

### 3. 集成到 Android 项目

#### 3.1 添加依赖
在 `app/build.gradle` 中添加：
```gradle
android {
    defaultConfig {
        externalNativeBuild {
            cmake {
                arguments "-DANDROID_ABI=arm64-v8a",
                         "-DANDROID_PLATFORM=android-21",
                         "-DCMAKE_BUILD_TYPE=Release"
            }
        }
    }
    
    externalNativeBuild {
        cmake {
            path "src/main/cpp/CMakeLists.txt"
        }
    }
}

dependencies {
    implementation 'com.android.ndk.thirdparty:c++_runtime:version'
}
```

#### 3.2 创建 JNI 包装器
`app/src/main/cpp/native-lib.cpp`：
```cpp
#include <jni.h>
#include <string>
#include <nndeploy/nndeploy.h>

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_nndeploy_MainActivity_runInference(JNIEnv* env, jobject /* this */) {
    try {
        // 初始化 NNDeploy
        nndeploy::init();
        
        // 创建推理图
        auto graph = nndeploy::dag::Graph::create();
        
        // 加载模型
        auto model_desc = nndeploy::ir::ModelDesc::make_from_file("model.mnn");
        graph->init(model_desc);
        
        // 执行推理
        graph->run();
        
        return env->NewStringUTF("Inference completed successfully");
    } catch (const std::exception& e) {
        return env->NewStringUTF(e.what());
    }
}
```

#### 3.3 CMakeLists.txt 配置
`app/src/main/cpp/CMakeLists.txt`：
```cmake
cmake_minimum_required(VERSION 3.22.1)

project("nndeploy-android")

# 设置 NNDeploy 路径
set(NNDEPLOY_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../nndeploy)
set(NNDEPLOY_BUILD_DIR ${NNDEPLOY_ROOT}/build_android)

# 查找 NNDeploy 库
find_library(nndeploy-lib nndeploy
    PATHS ${NNDEPLOY_BUILD_DIR}/Release
    NO_DEFAULT_PATH)

# 添加头文件路径
include_directories(${NNDEPLOY_ROOT}/include)

# 创建共享库
add_library(native-lib SHARED native-lib.cpp)

# 链接 NNDeploy
target_link_libraries(native-lib ${nndeploy-lib})
```

## 模型部署示例

### 1. 模型准备
- 将训练好的模型转换为 MNN 格式
- 将模型文件放入 `app/src/main/assets/` 目录

### 2. 基本推理流程
```cpp
#include <nndeploy/nndeploy.h>
#include <nndeploy/dag/graph.h>

void runInference() {
    // 1. 初始化
    nndeploy::init();
    
    // 2. 创建图
    auto graph = nndeploy::dag::Graph::create();
    
    // 3. 加载模型
    nndeploy::ir::ModelDesc model_desc;
    model_desc.set_model_type(nndeploy::ir::ModelType::kModelTypeMNN);
    model_desc.set_model_path("path/to/model.mnn");
    
    // 4. 初始化图
    graph->init(model_desc);
    
    // 5. 准备输入数据
    auto input_tensor = graph->getInput(0);
    // 填充输入数据...
    
    // 6. 执行推理
    graph->run();
    
    // 7. 获取输出
    auto output_tensor = graph->getOutput(0);
    // 处理输出数据...
    
    // 8. 清理
    graph->deinit();
}
```

## 性能优化建议

### 1. 内存管理
- 使用 `nndeploy::device::Tensor` 进行高效内存管理
- 避免频繁的内存分配/释放

### 2. 多线程
- 启用 NNDeploy 的线程池：`set(ENABLE_NNDEPLOY_THREAD_POOL ON)`
- 使用异步推理接口

### 3. 量化
- 考虑使用量化模型减少内存占用和计算量
- MNN 支持 INT8 量化

### 4. GPU 加速
- 启用 OpenCL：`set(ENABLE_NNDEPLOY_DEVICE_OPENCL ON)`
- 确保设备支持相应扩展

## 常见问题

### Q: 构建时出现 "undefined reference" 错误
A: 确保所有依赖库都正确链接，检查 CMake 配置中的库路径。

### Q: 运行时崩溃
A: 检查模型文件是否存在，确认 ABI 匹配（arm64-v8a vs armeabi-v7a）。

### Q: 性能不佳
A: 启用优化选项，考虑使用量化模型，使用合适的线程数。

## 更多资源

- [NNDeploy 官方文档](https://github.com/microsoft/nndeploy)
- [MNN 文档](https://www.yuque.com/mnn/en)
- [Android NDK 指南](https://developer.android.com/ndk/guides)

## 快速开始模板

参考 `app/android/` 目录中的示例项目，包含：
- 完整的 CMake 配置
- JNI 包装器示例
- Gradle 构建脚本
- 模型推理演示