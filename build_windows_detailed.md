# nndeploy Windows 源码编译完整指南

本文档提供 nndeploy 在 Windows 11 上从源码编译的详细步骤，适合需要自定义推理后端（如 TensorRT、OpenVINO）或进行二次开发的用户。

---

## 📋 目录

- [为什么选择源码编译](#为什么选择源码编译)
- [环境要求](#环境要求)
- [准备工作](#准备工作)
- [编译步骤](#编译步骤)
- [配置选项详解](#配置选项详解)
- [常见问题](#常见问题)
- [验证和测试](#验证和测试)
- [进阶配置](#进阶配置)

---

## 为什么选择源码编译

### ✅ 适合以下场景

1. **需要 GPU 加速**
   - 使用 NVIDIA GPU（TensorRT、CUDA）
   - 使用 Intel 硬件（OpenVINO）
   - 使用 AMD GPU（ROCm）

2. **需要特定推理框架**
   - ncnn（移动端优化）
   - TNN（腾讯推理框架）
   - Paddle Lite（百度推理框架）
   - RKNN（瑞芯微 NPU）
   - AscendCL（华为昇腾 NPU）

3. **二次开发需求**
   - 修改核心代码
   - 添加自定义算子
   - 性能调优和调试
   - 集成到 C++ 项目

4. **生产环境部署**
   - 需要最优性能
   - 特定硬件优化
   - 定制化功能

### ❌ 不适合以下场景

- 只是想快速体验功能 → 使用 [pip 安装](./install_pip.md)
- 仅使用 ONNXRuntime/MNN → 使用 [pip 安装](./install_pip.md)
- 没有 C++ 开发经验 → 建议先使用 pip 版本

---

## 环境要求

### 必需软件

| 软件 | 版本要求 | 下载链接 | 说明 |
|------|---------|---------|------|
| **Windows** | 10/11 | - | 64 位系统 |
| **Visual Studio** | 2019/2022 | [下载](https://visualstudio.microsoft.com/zh-hans/downloads/) | 需要 C++ 开发工具 |
| **CMake** | 3.15+ | [下载](https://cmake.org/download/) | 构建工具 |
| **Git** | 2.0+ | [下载](https://git-scm.com/downloads) | 版本控制 |
| **Python** | 3.10+ | [下载](https://www.python.org/downloads/) | Python API 支持 |

### Visual Studio 安装要求

安装 Visual Studio 时，必须勾选以下组件：

✅ **工作负载**
- "使用 C++ 的桌面开发"

✅ **单个组件**（在"单个组件"标签页）
- MSVC v142/v143 - VS 2019/2022 C++ x64/x86 生成工具
- Windows 10/11 SDK
- CMake 工具（可选，也可单独安装）
- C++ CMake tools for Windows

### 可选软件（根据需要）

| 软件 | 用途 | 下载链接 |
|------|------|---------|
| **CUDA Toolkit** | NVIDIA GPU 加速 | [下载](https://developer.nvidia.com/cuda-downloads) |
| **cuDNN** | CUDA 深度学习库 | [下载](https://developer.nvidia.com/cudnn) |
| **Rust** | 编译 tokenizer-cpp（LLM 支持） | [下载](https://www.rust-lang.org/tools/install) |

### 硬件要求

| 项目 | 最低配置 | 推荐配置 |
|------|---------|---------|
| **CPU** | 4 核 | 8 核及以上 |
| **内存** | 8GB | 16GB 及以上 |
| **磁盘空间** | 10GB | 20GB 及以上 |
| **GPU** | 可选 | NVIDIA RTX 系列 |

---

## 准备工作

### 步骤 1：验证环境

打开 PowerShell 或命令提示符，执行以下命令验证环境：

```powershell
# 检查 Python 版本
python --version
# 预期输出: Python 3.10.x 或更高

# 检查 Git 版本
git --version
# 预期输出: git version 2.x.x

# 检查 CMake 版本
cmake --version
# 预期输出: cmake version 3.x.x

# 检查 Visual Studio（查找 MSBuild）
where msbuild
# 预期输出: C:\Program Files\...\MSBuild.exe
```

**如果任何命令失败，请先安装相应软件。**

### 步骤 2：配置 Visual Studio 环境

#### 方法 A：使用 Developer Command Prompt（推荐）

1. 打开"开始菜单"
2. 搜索"Developer Command Prompt for VS 2022"（或 VS 2019）
3. 以管理员身份运行
4. 在此终端中执行后续所有命令

#### 方法 B：手动配置环境变量

```powershell
# 运行 Visual Studio 环境配置脚本
# VS 2022
"C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvars64.bat"

# VS 2019
"C:\Program Files (x86)\Microsoft Visual Studio\2019\Community\VC\Auxiliary\Build\vcvars64.bat"
```

### 步骤 3：克隆源码（如果还没有）

```bash
cd D:\projects  # 选择你的工作目录
git clone https://github.com/nndeploy/nndeploy.git
cd nndeploy
```

### 步骤 4：初始化子模块

```bash
# 方法 1：使用 Git 命令
git submodule update --init --recursive

# 方法 2：如果网络不稳定，使用备用脚本
python clone_submodule.py
```

**预期输出：**
```
Submodule 'third_party/gflags' registered for path 'third_party/gflags'
Submodule 'third_party/googletest' registered for path 'third_party/googletest'
...
Submodule path 'third_party/tokenizers-cpp': checked out '...'
```

**常见问题：**
- 如果子模块拉取失败，可能是网络问题
- 解决方案：配置 Git 代理或使用 `clone_submodule.py` 脚本

---

## 编译步骤

### 🎯 方案 A：使用自动化脚本（推荐新手）

nndeploy 提供了自动化编译脚本，可以一键完成编译：

```bash
# 基础编译（OpenCV + ONNXRuntime + MNN）
python build_win.py

# 指定配置文件
python build_win.py --config config_opencv_ort_mnn.cmake

# 清理后重新编译
python build_win.py --clean

# 跳过依赖安装（如果已安装）
python build_win.py --skip-deps --skip-third-party

# 指定并行任务数
python build_win.py --jobs 8

# 完整参数示例
python build_win.py \
    --config config_opencv_ort_mnn_tokenizer.cmake \
    --build-type Release \
    --jobs 8 \
    --clean
```

**脚本会自动完成：**
1. ✅ 安装 Python 依赖
2. ✅ 安装 Rust（如需 tokenizer-cpp）
3. ✅ 下载/编译第三方库（OpenCV、ONNXRuntime、MNN）
4. ✅ 配置 CMake
5. ✅ 编译项目
6. ✅ 安装到 `build/install`
7. ✅ 安装 Python 包

**预计时间：30-60 分钟**（取决于网络和硬件）

---

### 🎯 方案 B：手动编译（推荐进阶用户）

手动编译可以更灵活地控制每个步骤。

#### 第 1 步：准备第三方库

你有三种选择：

##### 选项 1：使用预编译库（最快）

从以下地址下载预编译的第三方库：

- **HuggingFace**: https://huggingface.co/alwaysssss/nndeploy/tree/main/third_party
- **ModelScope**: https://www.modelscope.cn/models/nndeploy/third_party

下载后解压到合适的位置，例如：
```
D:\nndeploy_libs\
├── opencv\
│   ├── include\
│   ├── lib\
│   └── bin
├── onnxruntime\
│   ├── include\
│   ├── lib\
│   └── bin
└── MNN\
    ├── include\
    ├── lib\
    └── bin
```

##### 选项 2：使用安装脚本下载

```bash
cd tool/script

# 下载 OpenCV
python install_opencv.py --generator "Visual Studio 17 2022" --architecture x64

# 下载 ONNXRuntime
python install_onnxruntime.py --generator "Visual Studio 17 2022" --architecture x64

# 编译 MNN
python build_mnn.py --generator "Visual Studio 17 2022" --architecture x64
```

##### 选项 3：手动下载官方库

参考 [第三方库下载链接](#第三方库下载链接)

#### 第 2 步：配置编译选项

```bash
# 创建 build 目录
mkdir build
cd build

# 复制配置模板
copy ..\cmake\config.cmake config.cmake

# 使用文本编辑器打开 config.cmake
notepad config.cmake
# 或使用 VS Code
code config.cmake
```

**编辑 `config.cmake` 文件：**

```cmake
# ============================================
# 最小配置示例（仅核心框架）
# ============================================
# 所有选项保持默认 OFF

# ============================================
# 基础配置示例（OpenCV + ONNXRuntime）
# ============================================

# 启用 OpenCV（传统 CV 算法必需）
set(ENABLE_NNDEPLOY_OPENCV "D:/nndeploy_libs/opencv")
# 或使用系统安装的 OpenCV
# set(ENABLE_NNDEPLOY_OPENCV ON)

# 启用 ONNXRuntime
set(ENABLE_NNDEPLOY_INFERENCE_ONNXRUNTIME "D:/nndeploy_libs/onnxruntime")

# 启用检测算法插件
set(ENABLE_NNDEPLOY_PLUGIN_DETECT ON)

# 启用分割算法插件
set(ENABLE_NNDEPLOY_PLUGIN_SEGMENT ON)

# ============================================
# GPU 加速配置示例（NVIDIA）
# ============================================

# 启用 CUDA
set(ENABLE_NNDEPLOY_DEVICE_CUDA ON)

# 启用 cuDNN
set(ENABLE_NNDEPLOY_DEVICE_CUDNN ON)

# 启用 TensorRT
set(ENABLE_NNDEPLOY_INFERENCE_TENSORRT "D:/nndeploy_libs/TensorRT-8.6.0.12")

# 启用 OpenCV
set(ENABLE_NNDEPLOY_OPENCV "D:/nndeploy_libs/opencv")

# ============================================
# 完整配置示例（所有常用后端）
# ============================================

# OpenCV
set(ENABLE_NNDEPLOY_OPENCV "D:/nndeploy_libs/opencv")

# 推理后端
set(ENABLE_NNDEPLOY_INFERENCE_ONNXRUNTIME "D:/nndeploy_libs/onnxruntime")
set(ENABLE_NNDEPLOY_INFERENCE_MNN "D:/nndeploy_libs/MNN")
set(ENABLE_NNDEPLOY_INFERENCE_TNN "D:/nndeploy_libs/TNN")
set(ENABLE_NNDEPLOY_INFERENCE_NCNN "D:/nndeploy_libs/ncnn")
set(ENABLE_NNDEPLOY_INFERENCE_OPENVINO "D:/nndeploy_libs/openvino")

# GPU 支持
set(ENABLE_NNDEPLOY_DEVICE_CUDA ON)
set(ENABLE_NNDEPLOY_DEVICE_CUDNN ON)
set(ENABLE_NNDEPLOY_INFERENCE_TENSORRT "D:/nndeploy_libs/TensorRT")

# 算法插件
set(ENABLE_NNDEPLOY_PLUGIN_DETECT ON)
set(ENABLE_NNDEPLOY_PLUGIN_SEGMENT ON)
set(ENABLE_NNDEPLOY_PLUGIN_CLASSIFY ON)

# LLM 支持（需要 Rust）
set(ENABLE_NNDEPLOY_PLUGIN_TOKENIZER_CPP ON)
set(ENABLE_NNDEPLOY_PLUGIN_LLM ON)
```

**路径配置说明：**
- 使用正斜杠 `/` 或双反斜杠 `\\`
- 路径必须包含 `include`、`lib`、`bin` 子目录
- 示例：`D:/nndeploy_libs/opencv` 下应有 `D:/nndeploy_libs/opencv/include`

#### 第 3 步：生成 Visual Studio 项目

```bash
# 确保在 build 目录中
cd build

# 生成 Visual Studio 2022 项目
cmake -G "Visual Studio 17 2022" -A x64 ..

# 或使用 Visual Studio 2019
cmake -G "Visual Studio 16 2019" -A x64 ..

# 指定 Release 模式
cmake -G "Visual Studio 17 2022" -A x64 -DCMAKE_BUILD_TYPE=Release ..
```

**预期输出：**
```
-- The C compiler identification is MSVC 19.x.x
-- The CXX compiler identification is MSVC 19.x.x
-- Detecting C compiler ABI info
-- Detecting C compiler ABI info - done
...
-- Configuring done
-- Generating done
-- Build files have been written to: D:/jinwork/nndeploy/build
```

**如果出现错误：**
- 检查 `config.cmake` 中的路径是否正确
- 确认第三方库已正确安装
- 查看错误信息，通常会提示缺少哪个库

#### 第 4 步：编译项目

##### 方法 A：使用命令行（推荐）

```bash
# Release 模式编译（推荐）
cmake --build . --config Release --parallel 8

# Debug 模式编译（用于调试）
cmake --build . --config Debug --parallel 8

# 指定并行任务数（根据 CPU 核心数调整）
cmake --build . --config Release --parallel 16
```

**编译时间：**
- 首次编译：20-40 分钟
- 增量编译：1-5 分钟

##### 方法 B：使用 Visual Studio IDE

1. 打开 `build/nndeploy.sln`
2. 在顶部工具栏选择 "Release" 和 "x64"
3. 右键点击解决方案 → "生成解决方案"（或按 `Ctrl+Shift+B`）

**编译进度：**
- 可以在"输出"窗口查看编译进度
- 成功编译会显示 "生成成功"

#### 第 5 步：安装编译产物

```bash
# 安装到 build/install 目录
cmake --install . --config Release

# 或指定安装目录
cmake --install . --config Release --prefix D:/nndeploy_install
```

**安装后的目录结构：**
```
build/install/
├── bin/                    # 可执行文件和 DLL
│   ├── nndeploy_framework.dll
│   ├── nndeploy_plugin_detect.dll
│   ├── nndeploy_demo_detect.exe
│   └── ...
├── lib/                    # 静态库和导入库
│   ├── nndeploy_framework.lib
│   └── ...
└── include/                # 头文件
    └── nndeploy/
        ├── base/
        ├── dag/
        └── ...
```

#### 第 6 步：配置环境变量

将以下路径添加到系统 PATH 环境变量：

```
D:\jinwork\nndeploy\build\install\bin
D:\nndeploy_libs\opencv\bin
D:\nndeploy_libs\onnxruntime\bin
D:\nndeploy_libs\MNN\bin
```

**设置方法：**
1. 右键"此电脑" → "属性"
2. "高级系统设置" → "环境变量"
3. 在"系统变量"中找到 `Path` → "编辑"
4. 点击"新建"，添加上述路径
5. 点击"确定"保存

**或使用命令行临时设置：**
```powershell
$env:PATH = "D:\jinwork\nndeploy\build\install\bin;D:\nndeploy_libs\opencv\bin;$env:PATH"
```

#### 第 7 步：安装 Python 包（可选）

```bash
# 返回项目根目录
cd ..

# 进入 python 目录
cd python

# 以开发模式安装
pip install -e .

# 验证安装
python -c "import nndeploy; print(nndeploy.__version__)"
```

**预期输出：**
```
nndeploy version: 3.0.7
```

---

## 配置选项详解

### 推理后端配置

| 后端 | CMake 选项 | 说明 | 平台支持 |
|------|-----------|------|---------|
| **ONNXRuntime** | `ENABLE_NNDEPLOY_INFERENCE_ONNXRUNTIME` | 通用推理框架 | Windows/Linux/macOS |
| **TensorRT** | `ENABLE_NNDEPLOY_INFERENCE_TENSORRT` | NVIDIA GPU 加速 | Windows/Linux |
| **OpenVINO** | `ENABLE_NNDEPLOY_INFERENCE_OPENVINO` | Intel 硬件加速 | Windows/Linux |
| **MNN** | `ENABLE_NNDEPLOY_INFERENCE_MNN` | 移动端优化 | 全平台 |
| **ncnn** | `ENABLE_NNDEPLOY_INFERENCE_NCNN` | 移动端优化 | 全平台 |
| **TNN** | `ENABLE_NNDEPLOY_INFERENCE_TNN` | 腾讯推理框架 | 全平台 |
| **CoreML** | `ENABLE_NNDEPLOY_INFERENCE_COREML` | Apple 硬件加速 | macOS/iOS |
| **RKNN** | `ENABLE_NNDEPLOY_INFERENCE_RKNN_TOOLKIT_2` | 瑞芯微 NPU | Linux/Android |
| **AscendCL** | `ENABLE_NNDEPLOY_INFERENCE_ASCEND_CL` | 华为昇腾 NPU | Linux |

### 设备后端配置

| 设备 | CMake 选项 | 说明 | 依赖 |
|------|-----------|------|------|
| **CUDA** | `ENABLE_NNDEPLOY_DEVICE_CUDA` | NVIDIA GPU | CUDA Toolkit |
| **cuDNN** | `ENABLE_NNDEPLOY_DEVICE_CUDNN` | CUDA 深度学习库 | cuDNN |
| **OpenCL** | `ENABLE_NNDEPLOY_DEVICE_OPENCL` | 通用 GPU 加速 | OpenCL SDK |
| **Metal** | `ENABLE_NNDEPLOY_DEVICE_METAL` | Apple GPU | macOS/iOS |
| **Vulkan** | `ENABLE_NNDEPLOY_DEVICE_VULKAN` | 跨平台 GPU | Vulkan SDK |

### 算法插件配置

| 插件 | CMake 选项 | 说明 | 依赖 |
|------|-----------|------|------|
| **检测** | `ENABLE_NNDEPLOY_PLUGIN_DETECT` | 目标检测（YOLO 等） | OpenCV |
| **分割** | `ENABLE_NNDEPLOY_PLUGIN_SEGMENT` | 图像分割 | OpenCV |
| **分类** | `ENABLE_NNDEPLOY_PLUGIN_CLASSIFY` | 图像分类 | OpenCV |
| **LLM** | `ENABLE_NNDEPLOY_PLUGIN_LLM` | 大语言模型 | tokenizer-cpp |
| **Stable Diffusion** | `ENABLE_NNDEPLOY_PLUGIN_STABLE_DIFFUSION` | 文生图 | tokenizer-cpp |
| **Tokenizer** | `ENABLE_NNDEPLOY_PLUGIN_TOKENIZER_CPP` | C++ 分词器 | Rust |

### 配置示例

#### 示例 1：最小配置（仅核心框架）

```cmake
# 所有选项保持默认 OFF
# 仅编译核心框架，不依赖任何第三方库
```

**用途：**
- 学习 nndeploy 架构
- 开发自定义推理后端
- 最小化依赖

#### 示例 2：CPU 推理配置

```cmake
# OpenCV
set(ENABLE_NNDEPLOY_OPENCV "D:/libs/opencv")

# ONNXRuntime（CPU）
set(ENABLE_NNDEPLOY_INFERENCE_ONNXRUNTIME "D:/libs/onnxruntime")

# MNN（CPU）
set(ENABLE_NNDEPLOY_INFERENCE_MNN "D:/libs/MNN")

# 检测和分割插件
set(ENABLE_NNDEPLOY_PLUGIN_DETECT ON)
set(ENABLE_NNDEPLOY_PLUGIN_SEGMENT ON)
```

**用途：**
- 桌面应用
- CPU 服务器
- 不需要 GPU 加速

#### 示例 3：NVIDIA GPU 加速配置

```cmake
# CUDA 和 cuDNN
set(ENABLE_NNDEPLOY_DEVICE_CUDA ON)
set(ENABLE_NNDEPLOY_DEVICE_CUDNN ON)

# TensorRT
set(ENABLE_NNDEPLOY_INFERENCE_TENSORRT "D:/libs/TensorRT-8.6.0.12")

# ONNXRuntime（GPU 版本）
set(ENABLE_NNDEPLOY_INFERENCE_ONNXRUNTIME "D:/libs/onnxruntime-gpu")

# OpenCV
set(ENABLE_NNDEPLOY_OPENCV "D:/libs/opencv")

# 算法插件
set(ENABLE_NNDEPLOY_PLUGIN_DETECT ON)
set(ENABLE_NNDEPLOY_PLUGIN_SEGMENT ON)
```

**用途：**
- 高性能推理
- RTX 系列 GPU
- 实时视频处理

#### 示例 4：Intel 硬件加速配置

```cmake
# OpenVINO
set(ENABLE_NNDEPLOY_INFERENCE_OPENVINO "D:/libs/openvino")

# OpenCV
set(ENABLE_NNDEPLOY_OPENCV "D:/libs/opencv")

# 算法插件
set(ENABLE_NNDEPLOY_PLUGIN_DETECT ON)
set(ENABLE_NNDEPLOY_PLUGIN_SEGMENT ON)
```

**用途：**
- Intel CPU/GPU
- Intel Neural Compute Stick
- 边缘计算设备

#### 示例 5：大模型支持配置

```cmake
# Tokenizer-cpp（需要 Rust）
set(ENABLE_NNDEPLOY_PLUGIN_TOKENIZER_CPP ON)

# LLM 插件
set(ENABLE_NNDEPLOY_PLUGIN_LLM ON)

# Stable Diffusion 插件
set(ENABLE_NNDEPLOY_PLUGIN_STABLE_DIFFUSION ON)

# ONNXRuntime 或其他推理后端
set(ENABLE_NNDEPLOY_INFERENCE_ONNXRUNTIME "D:/libs/onnxruntime")
```

**用途：**
- 部署大语言模型
- 文生图应用
- AIGC 应用

---

## 常见问题

### 编译问题

#### Q1: CMake 配置失败，提示找不到第三方库

**错误示例：**
```
CMake Error: Could not find OpenCV
```

**解决方案：**

1. **检查路径配置**
   ```cmake
   # 确保路径正确，使用正斜杠
   set(ENABLE_NNDEPLOY_OPENCV "D:/libs/opencv")
   
   # 确认目录结构
   # D:/libs/opencv/
   #   ├── include/
   #   ├── lib/
   #   └── bin/
   ```

2. **使用绝对路径**
   ```cmake
   # 不推荐
   set(ENABLE_NNDEPLOY_OPENCV "../opencv")
   
   # 推荐
   set(ENABLE_NNDEPLOY_OPENCV "D:/nndeploy_libs/opencv")
   ```

3. **检查库版本兼容性**
   - OpenCV: 4.x
   - ONNXRuntime: 1.15.x
   - TensorRT: 8.6.x

#### Q2: 编译时出现 C++ 标准错误

**错误示例：**
```
error C2039: 'optional': is not a member of 'std'
```

**解决方案：**

1. **确认 Visual Studio 版本**
   - 需要 Visual Studio 2019 或更高版本
   - 确保安装了最新更新

2. **检查 C++ 标准设置**
   ```cmake
   # 在 CMakeLists.txt 中应该有
   set(CMAKE_CXX_STANDARD 17)
   ```

3. **清理并重新生成**
   ```bash
   cd build
   rm -rf *
   cmake -G "Visual Studio 17 2022" -A x64 ..
   ```

#### Q3: 链接错误，找不到符号

**错误示例：**
```
error LNK2019: unresolved external symbol
```

**解决方案：**

1. **检查库文件路径**
   ```cmake
   # 确保 lib 目录包含所需的 .lib 文件
   set(ENABLE_NNDEPLOY_OPENCV "D:/libs/opencv")
   ```

2. **检查库文件名**
   ```cmake
   # 对于 OpenCV，可能需要指定库名
   set(NNDEPLOY_OPENCV_LIBS "opencv_world4100")
   ```

3. **检查编译模式一致性**
   - 确保所有库都是 Release 或都是 Debug
   - 不要混用 Release 和 Debug 库

#### Q4: 子模块拉取失败

**错误示例：**
```
fatal: unable to access 'https://github.com/...': Failed to connect
```

**解决方案：**

1. **使用备用脚本**
   ```bash
   python clone_submodule.py
   ```

2. **配置 Git 代理**
   ```bash
   git config --global http.proxy http://127.0.0.1:7890
   git config --global https.proxy http://127.0.0.1:7890
   ```

3. **手动下载子模块**
   - 访问 GitHub 仓库
   - 下载 ZIP 文件
   - 解压到对应的 `third_party/` 目录

### 运行问题

#### Q5: 运行时提示找不到 DLL

**错误示例：**
```
The code execution cannot proceed because opencv_world4100.dll was not found.
```

**解决方案：**

1. **添加 DLL 路径到 PATH**
   ```powershell
   # 临时设置
   $env:PATH = "D:\libs\opencv\bin;$env:PATH"
   
   # 永久设置：通过系统环境变量
   ```

2. **复制 DLL 到可执行文件目录**
   ```bash
   copy D:\libs\opencv\bin\*.dll build\install\bin\
   copy D:\libs\onnxruntime\bin\*.dll build\install\bin\
   ```

3. **使用依赖检查工具**
   ```bash
   # 使用 Dependencies.exe 查看缺少哪些 DLL
   # 下载: https://github.com/lucasg/Dependencies
   ```

#### Q6: Python 导入 nndeploy 失败

**错误示例：**
```python
ImportError: DLL load failed while importing nndeploy_internal
```

**解决方案：**

1. **设置 DLL 搜索路径**
   ```python
   import os
   os.add_dll_directory(r"D:\jinwork\nndeploy\build\install\bin")
   os.add_dll_directory(r"D:\libs\opencv\bin")
   
   import nndeploy
   ```

2. **检查 Python 版本**
   ```bash
   # 必须是 3.10+
   python --version
   ```

3. **重新安装 Python 包**
   ```bash
   cd python
   pip uninstall nndeploy
   pip install -e .
   ```

#### Q7: CUDA/TensorRT 相关错误

**错误示例：**
```
CUDA error: no kernel image is available for execution
```

**解决方案：**

1. **检查 CUDA 版本兼容性**
   - TensorRT 8.6 需要 CUDA 11.x 或 12.x
   - 确认 GPU 驱动版本

2. **检查 GPU 计算能力**
   ```bash
   # 查看 GPU 信息
   nvidia-smi
   ```

3. **重新编译 TensorRT 引擎**
   - TensorRT 引擎文件（.engine）与 CUDA 版本绑定
   - 需要在目标机器上重新生成

### 性能问题

#### Q8: 编译速度很慢

**解决方案：**

1. **增加并行任务数**
   ```bash
   # 根据 CPU 核心数调整
   cmake --build . --config Release --parallel 16
   ```

2. **使用 SSD 硬盘**
   - 将项目和 build 目录放在 SSD 上

3. **关闭不需要的插件**
   ```cmake
   # 只启用需要的插件
   set(ENABLE_NNDEPLOY_PLUGIN_LLM OFF)
   set(ENABLE_NNDEPLOY_PLUGIN_STABLE_DIFFUSION OFF)
   ```

4. **使用增量编译**
   - 不要每次都清理 build 目录
   - 只在必要时使用 `--clean`

---

## 验证和测试

### 验证编译产物

#### 1. 检查生成的文件

```bash
# 查看 bin 目录
dir build\install\bin

# 应该包含
# - nndeploy_framework.dll
# - nndeploy_plugin_*.dll
# - nndeploy_demo_*.exe
```

#### 2. 运行测试程序

```bash
cd build\install\bin

# 运行检测 demo
nndeploy_demo_detect.exe --help

# 运行 DAG demo
nndeploy_demo_dag.exe
```

#### 3. 测试 Python 包

```python
import nndeploy

# 检查版本
print(f"nndeploy version: {nndeploy.__version__}")

# 检查可用后端
print(f"Available backends: {nndeploy.get_available_backends()}")

# 简单测试
graph = nndeploy.dag.Graph("")
print("Graph created successfully!")
```

### 性能测试

#### 1. 推理性能测试

```bash
# 运行性能测试
nndeploy_demo_benchmark.exe \
    --model yolov8n.onnx \
    --backend onnxruntime \
    --iterations 100
```

#### 2. 对比不同后端

```python
import nndeploy
import time

# 测试 ONNXRuntime
graph_ort = nndeploy.dag.Graph("")
graph_ort.set_inference_backend("onnxruntime")
# ... 运行测试

# 测试 TensorRT
graph_trt = nndeploy.dag.Graph("")
graph_trt.set_inference_backend("tensorrt")
# ... 运行测试
```

---

## 进阶配置

### 交叉编译

#### 为 ARM64 Windows 编译

```bash
cmake -G "Visual Studio 17 2022" -A ARM64 ..
cmake --build . --config Release
```

### 自定义安装路径

```bash
# 配置时指定安装前缀
cmake -G "Visual Studio 17 2022" -A x64 \
    -DCMAKE_INSTALL_PREFIX=D:/nndeploy_custom \
    ..

# 安装
cmake --install . --config Release
```

### 启用编译优化

```cmake
# 在 config.cmake 中添加
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} /O2 /Ob2")
set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} /O2 /Ob2")
```

### 生成安装包

```bash
# 在 build 目录中
cpack

# 生成的安装包
# - nndeploy-3.0.7-win64.zip
# - nndeploy-3.0.7-win64.exe (如果配置了 NSIS)
```

### 集成到 Visual Studio 项目

#### 1. 添加包含目录

```
项目属性 → C/C++ → 常规 → 附加包含目录
D:\jinwork\nndeploy\build\install\include
```

#### 2. 添加库目录

```
项目属性 → 链接器 → 常规 → 附加库目录
D:\jinwork\nndeploy\build\install\lib
```

#### 3. 添加依赖库

```
项目属性 → 链接器 → 输入 → 附加依赖项
nndeploy_framework.lib
```

#### 4. 示例代码

```cpp
#include <nndeploy/dag/graph.h>
#include <iostream>

int main() {
    auto graph = std::make_shared<nndeploy::dag::Graph>("");
    std::cout << "nndeploy initialized successfully!" << std::endl;
    return 0;
}
```

---

## 第三方库下载链接

### OpenCV

| 版本 | 平台 | 下载链接 |
|------|------|---------|
| 4.10.0 | Windows | [官网](https://opencv.org/releases/) |
| 预编译 | Windows x64 | [HuggingFace](https://huggingface.co/alwaysssss/nndeploy/tree/main/third_party) |

### ONNXRuntime

| 版本 | 平台 | 下载链接 |
|------|------|---------|
| 1.15.1 | Windows x64 | [GitHub](https://github.com/microsoft/onnxruntime/releases/tag/v1.15.1) |
| GPU 版本 | Windows x64 + CUDA | [GitHub](https://github.com/microsoft/onnxruntime/releases/tag/v1.15.1) |

### TensorRT

| 版本 | CUDA 版本 | 下载链接 |
|------|----------|---------|
| 8.6.0.12 | CUDA 11.x | [NVIDIA](https://developer.nvidia.com/tensorrt) |
| 8.6.0.12 | CUDA 12.x | [NVIDIA](https://developer.nvidia.com/tensorrt) |

**注意：** 需要注册 NVIDIA 开发者账号

### OpenVINO

| 版本 | 平台 | 下载链接 |
|------|------|---------|
| 2023.0.1 | Windows | [Intel](https://www.intel.com/content/www/us/en/developer/tools/openvino-toolkit/download.html) |

### MNN

| 版本 | 平台 | 下载链接 |
|------|------|---------|
| 2.6.2 | 源码 | [GitHub](https://github.com/alibaba/MNN/releases/tag/2.6.2) |
| 预编译 | Windows x64 | [HuggingFace](https://huggingface.co/alwaysssss/nndeploy/tree/main/third_party) |

### ncnn

| 版本 | 平台 | 下载链接 |
|------|------|---------|
| 20230816 | 源码 | [GitHub](https://github.com/Tencent/ncnn/releases/tag/20230816) |

### TNN

| 版本 | 平台 | 下载链接 |
|------|------|---------|
| 0.3.0 | 源码 | [GitHub](https://github.com/Tencent/TNN/releases/tag/v0.3.0) |

---

## 总结

### ✅ 编译成功标志

1. CMake 配置无错误
2. 编译过程无错误
3. 生成了所需的 DLL 和 EXE 文件
4. Python 包可以正常导入
5. 示例程序可以运行

### 📊 预计时间

| 步骤 | 时间 |
|------|------|
| 环境准备 | 30-60 分钟 |
| 下载第三方库 | 10-30 分钟 |
| 配置 CMake | 5-10 分钟 |
| 编译 | 20-40 分钟 |
| 安装和测试 | 10-20 分钟 |
| **总计** | **1.5-3 小时** |

### 🎯 下一步

编译完成后，你可以：

1. **运行示例程序** - 测试各种算法
2. **启动可视化界面** - `python app.py --port 8000`
3. **开发 C++ 应用** - 集成到你的项目
4. **性能优化** - 调整编译选项和运行参数
5. **贡献代码** - 提交 PR 到官方仓库

---

## 获取帮助

- **完整文档**: [编译文档](./build.md)
- **编译宏说明**: [编译宏文档](./build_macro.md)
- **GitHub Issues**: https://github.com/nndeploy/nndeploy/issues
- **Discord**: https://discord.gg/9rUwfAaMbr

---

**祝你编译顺利！** 🚀

如果遇到问题，请查阅 [常见问题](#常见问题) 部分，或在社区寻求帮助。
