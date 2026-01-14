# nndeploy Windows 编译部署指南

本文档详细记录了在 Windows 平台上编译和部署 nndeploy 的完整流程，包括环境配置、依赖安装、编译步骤和常用命令。

---

## 目录

1. [环境要求](#环境要求)
2. [依赖配置](#依赖配置)
3. [编译配置](#编译配置)
4. [编译流程](#编译流程)
5. [Python 包安装](#python-包安装)
6. [服务部署](#服务部署)
7. [常用命令速查](#常用命令速查)
8. [故障排除](#故障排除)

---

## 环境要求

### 必需软件

| 软件 | 版本要求 | 验证命令 |
|------|---------|---------|
| Visual Studio 2022 | BuildTools 或完整版 | `cl` |
| CMake | >= 3.20 | `cmake --version` |
| Python | 3.10.x | `python --version` |
| Git | >= 2.x | `git --version` |
| Rust | >= 1.70 (tokenizer-cpp 需要) | `rustc --version` |

### 验证环境

```powershell
# 验证所有工具
cmake --version
python --version
git --version
rustc --version
cargo --version
```

---

## 依赖配置

### 1. OpenCV 配置

下载官方预编译版本：
```powershell
# 下载 OpenCV 4.10.0
$opencv_url = "https://github.com/opencv/opencv/releases/download/4.10.0/opencv-4.10.0-windows.exe"
Invoke-WebRequest -Uri $opencv_url -OutFile "opencv-4.10.0-windows.exe"

# 解压到 tool/script/third_party/opencv_official
.\opencv-4.10.0-windows.exe
# 选择解压目录: D:\jinwork\nndeploy-1\tool\script\third_party\opencv_official
```

目录结构：
```
tool/script/third_party/opencv_official/
└── opencv/
    └── build/
        ├── x64/vc16/bin/    # DLL 文件
        ├── x64/vc16/lib/    # LIB 文件
        └── include/         # 头文件
```

### 2. ONNXRuntime 配置

```powershell
# 下载 ONNXRuntime 1.18.0
$ort_url = "https://github.com/microsoft/onnxruntime/releases/download/v1.18.0/onnxruntime-win-x64-1.18.0.zip"
Invoke-WebRequest -Uri $ort_url -OutFile "onnxruntime-win-x64-1.18.0.zip"

# 解压
Expand-Archive -Path "onnxruntime-win-x64-1.18.0.zip" -DestinationPath "tool/script/third_party/"

# 重命名
Rename-Item "tool/script/third_party/onnxruntime-win-x64-1.18.0" "onnxruntime1.18.0"
```

目录结构：
```
tool/script/third_party/onnxruntime1.18.0/
├── include/    # 头文件
└── lib/        # DLL 和 LIB 文件
```

### 3. MNN 配置（带 LLM 支持）

从源码编译 MNN 以启用 LLM 功能：

```powershell
cd tool/script/third_party

# 克隆 MNN 源码
git clone --depth 1 --branch 3.2.4 https://github.com/alibaba/MNN.git mnn_source

# 创建构建目录
cd mnn_source
mkdir build_windows; cd build_windows

# 配置 CMake（启用 LLM 支持）
cmake .. -G "Visual Studio 17 2022" -A x64 `
    -DCMAKE_BUILD_TYPE=Release `
    -DMNN_BUILD_SHARED_LIBS=ON `
    -DMNN_WIN_RUNTIME_MT=OFF `
    -DMNN_BUILD_LLM=ON `
    -DMNN_SUPPORT_TRANSFORMER_FUSE=ON `
    -DMNN_LOW_MEMORY=ON `
    -DMNN_BUILD_CONVERTER=OFF `
    -DMNN_BUILD_TRAIN=OFF `
    -DMNN_BUILD_DEMO=OFF `
    -DMNN_BUILD_TOOLS=OFF `
    -DMNN_BUILD_QUANTOOLS=OFF `
    -DMNN_BUILD_TEST=OFF

# 编译
cmake --build . --config Release -j 12

# 安装到目标目录
cmake --install . --prefix ../../mnn3.2.4
```

目录结构：
```
tool/script/third_party/mnn3.2.4/
├── include/
│   └── MNN/
│       ├── llm/llm.hpp    # LLM 头文件（重要）
│       └── ...
└── lib/
    ├── MNN.dll
    └── MNN.lib
```

---

## 编译配置

### 创建配置文件

在 `build/` 目录下创建 `config.cmake`：

```cmake
# build/config.cmake

# ============ 推理后端 ============
# OpenCV（必需）
set(ENABLE_NNDEPLOY_OPENCV "tool/script/third_party/opencv_official/opencv/build")

# ONNXRuntime
set(ENABLE_NNDEPLOY_INFERENCE_ONNXRUNTIME "tool/script/third_party/onnxruntime1.18.0")

# MNN（带 LLM 支持）
set(ENABLE_NNDEPLOY_INFERENCE_MNN "tool/script/third_party/mnn3.2.4")

# ============ 插件配置 ============
# 启用 LLM 插件
set(ENABLE_NNDEPLOY_PLUGIN_LLM ON)

# 启用 tokenizer-cpp（LLM 需要）
set(ENABLE_NNDEPLOY_PLUGIN_TOKENIZER_CPP ON)

# 其他插件（按需启用）
set(ENABLE_NNDEPLOY_PLUGIN_DETECT ON)
set(ENABLE_NNDEPLOY_PLUGIN_SEGMENT ON)
set(ENABLE_NNDEPLOY_PLUGIN_CLASSIFICATION ON)

# ============ 通用配置 ============
set(ENABLE_NNDEPLOY_BUILD_SHARED ON)
set(ENABLE_NNDEPLOY_PYTHON ON)
```

### 完整配置示例

```cmake
# 完整的 config.cmake 示例

# 设备支持
set(ENABLE_NNDEPLOY_DEVICE_CPU ON)
set(ENABLE_NNDEPLOY_DEVICE_ARM OFF)
set(ENABLE_NNDEPLOY_DEVICE_X86 ON)
set(ENABLE_NNDEPLOY_DEVICE_CUDA OFF)

# 推理后端
set(ENABLE_NNDEPLOY_OPENCV "tool/script/third_party/opencv_official/opencv/build")
set(ENABLE_NNDEPLOY_INFERENCE_ONNXRUNTIME "tool/script/third_party/onnxruntime1.18.0")
set(ENABLE_NNDEPLOY_INFERENCE_MNN "tool/script/third_party/mnn3.2.4")
set(ENABLE_NNDEPLOY_INFERENCE_OPENVINO OFF)
set(ENABLE_NNDEPLOY_INFERENCE_TENSORRT OFF)

# 插件
set(ENABLE_NNDEPLOY_PLUGIN_DETECT ON)
set(ENABLE_NNDEPLOY_PLUGIN_SEGMENT ON)
set(ENABLE_NNDEPLOY_PLUGIN_CLASSIFICATION ON)
set(ENABLE_NNDEPLOY_PLUGIN_LLM ON)
set(ENABLE_NNDEPLOY_PLUGIN_TOKENIZER_CPP ON)

# 构建选项
set(ENABLE_NNDEPLOY_BUILD_SHARED ON)
set(ENABLE_NNDEPLOY_PYTHON ON)
set(ENABLE_NNDEPLOY_DEMO ON)
set(ENABLE_NNDEPLOY_TEST OFF)
```

---

## 编译流程

### 一键编译（推荐）

```powershell
# 使用构建脚本
python build_win.py
```

### 手动编译

```powershell
# 1. 创建构建目录
mkdir build; cd build

# 2. 配置 CMake
cmake .. -G "Visual Studio 17 2022" -A x64 -DCMAKE_BUILD_TYPE=Release

# 3. 编译
cmake --build . --config Release -j 12
```

### 编译特定目标

```powershell
# 只编译 LLM 插件
cmake --build . --config Release --target nndeploy_plugin_llm -j 12

# 只编译 Python 绑定
cmake --build . --config Release --target pynndeploy -j 12

# 编译多个目标
cmake --build . --config Release --target nndeploy_plugin_llm pynndeploy -j 12
```

---

## Python 包安装

### 方法一：复制文件（开发模式）

```powershell
# 1. 复制 Python 包
Copy-Item -Path "python/nndeploy/*" -Destination "venv/Lib/site-packages/nndeploy" -Recurse -Force

# 2. 复制 pyd 文件
Copy-Item -Path "build/python/Release/_nndeploy_internal.cp310-win_amd64.pyd" `
    -Destination "venv/Lib/site-packages/nndeploy" -Force

# 3. 复制所有 DLL
Copy-Item -Path "build/Release/*.dll" -Destination "venv/Lib/site-packages/nndeploy" -Force

# 4. 复制第三方 DLL
Copy-Item -Path "tool/script/third_party/onnxruntime1.18.0/lib/*.dll" `
    -Destination "venv/Lib/site-packages/nndeploy" -Force
Copy-Item -Path "tool/script/third_party/opencv_official/opencv/build/x64/vc16/bin/*.dll" `
    -Destination "venv/Lib/site-packages/nndeploy" -Force
Copy-Item -Path "tool/script/third_party/mnn3.2.4/lib/*.dll" `
    -Destination "venv/Lib/site-packages/nndeploy" -Force
```

### 方法二：pip 安装

```powershell
cd python
pip install -e .
```

### 验证安装

```powershell
python -c "import nndeploy; print('nndeploy imported successfully')"
```

---

## 服务部署

### 启动服务

```powershell
# 激活虚拟环境
.\venv\Scripts\Activate.ps1

# 启动服务（默认端口 8000）
python app.py --port 8000

# 后台启动
Start-Process powershell -ArgumentList "-Command", "python app.py --port 8000"
```

### 访问服务

- **Web UI**: http://localhost:8000
- **API 文档**: http://localhost:8000/docs
- **健康检查**: http://localhost:8000/api/queue

### API 调用示例

```python
import requests
import json

# 获取模板列表
r = requests.get('http://localhost:8000/api/template')
print(r.json())

# 获取特定模板
tid = 'template-id-here'
r = requests.get(f'http://localhost:8000/api/template/{tid}')
template = r.json()['result']

# 提交任务
r = requests.post('http://localhost:8000/api/queue', json=template)
print(r.json())

# 检查队列状态
r = requests.get('http://localhost:8000/api/queue')
print(r.json())
```

---

## 常用命令速查

### 编译相关

```powershell
# 完整编译
cmake --build build --config Release -j 12

# 清理并重新编译
cmake --build build --config Release --clean-first -j 12

# 只编译特定目标
cmake --build build --config Release --target <target_name> -j 12

# 查看所有可用目标
cmake --build build --target help
```

### 依赖管理

```powershell
# 安装 Python 依赖
pip install -r requirements.txt

# 更新子模块
git submodule update --init --recursive
```

### 测试运行

```powershell
# 运行 C++ LLM demo
.\build\Release\nndeploy_demo_llm.exe --json_file resources/template/nndeploy-workflow/qwen/LLM_Qwen2.5_MNN.json

# 运行 MNN 原生 LLM demo（验证 MNN 是否正常）
.\tool\script\third_party\mnn_source\build_windows\Release\llm_demo.exe `
    "D:\jinwork\nndeploy-1\resources\models\qwen\Qwen2-0.5B-Instruct\mnn\"
```

### 服务管理

```powershell
# 启动服务
python app.py --port 8000

# 停止所有 Python 进程
Stop-Process -Name python -Force

# 检查端口占用
netstat -ano | findstr ":8000"

# 杀死占用端口的进程
taskkill /PID <pid> /F
```

### DLL 管理

```powershell
# 检查 DLL 数量
(Get-ChildItem "venv/Lib/site-packages/nndeploy/*.dll").Count

# 查看 DLL 更新时间
Get-ChildItem "venv/Lib/site-packages/nndeploy/*.dll" | Select-Object Name, LastWriteTime

# 复制更新后的 DLL
Copy-Item -Path "build/Release/*.dll" -Destination "venv/Lib/site-packages/nndeploy" -Force
```

---

## 故障排除

### 问题 1: CMake 找不到 Visual Studio

**症状**: `CMake Error: No CMAKE_CXX_COMPILER could be found`

**解决方案**:
```powershell
# 使用 Developer PowerShell for VS 2022
# 或手动设置环境
& "C:\Program Files\Microsoft Visual Studio\2022\BuildTools\Common7\Tools\VsDevCmd.bat"
```

### 问题 2: tokenizer-cpp 编译失败

**症状**: Rust 编译错误

**解决方案**:
```powershell
# 确保 Rust 已安装
rustup update
rustup default stable

# 安装 Visual Studio 的 C++ 工具链
rustup target add x86_64-pc-windows-msvc
```

### 问题 3: MNN LLM 头文件缺失

**症状**: `fatal error: MNN/llm/llm.hpp: No such file or directory`

**解决方案**:
MNN 需要从源码编译并启用 `-DMNN_BUILD_LLM=ON`，预编译版本不包含 LLM 头文件。

### 问题 4: Python 导入失败

**症状**: `ImportError: DLL load failed`

**解决方案**:
```powershell
# 确保所有 DLL 在同一目录
Copy-Item -Path "build/Release/*.dll" -Destination "venv/Lib/site-packages/nndeploy" -Force
Copy-Item -Path "tool/script/third_party/*/lib/*.dll" -Destination "venv/Lib/site-packages/nndeploy" -Force
```

### 问题 5: 服务启动端口被占用

**症状**: `[Errno 10048] error while attempting to bind on address`

**解决方案**:
```powershell
# 查找占用进程
netstat -ano | findstr ":8000"

# 杀死进程
taskkill /PID <pid> /F

# 或使用其他端口
python app.py --port 8001
```

### 问题 6: Worker 进程崩溃 (exitcode=3221225477)

**症状**: `Worker died (exitcode=3221225477)` (0xC0000005 访问违例)

**可能原因**:
1. DLL 版本不匹配
2. Python 绑定问题
3. 多进程环境下的初始化问题

**调试方法**:
```powershell
# 用 C++ demo 验证模型是否正常
.\build\Release\nndeploy_demo_llm.exe --json_file <json_path>

# 直接用 Python 测试（不通过服务器）
python -c "
import nndeploy.dag
graph = nndeploy.dag.Graph('test')
# ...
"
```

---

## 附录

### 目录结构

```
nndeploy-1/
├── build/                    # 编译输出
│   ├── Release/              # Release 构建产物
│   └── config.cmake          # 编译配置
├── framework/                # 核心框架
├── plugin/                   # 插件代码
├── python/                   # Python 绑定
├── resources/
│   ├── models/               # 模型文件
│   └── template/             # 工作流模板
├── server/                   # 服务器代码
├── tool/script/third_party/  # 第三方依赖
│   ├── opencv_official/
│   ├── onnxruntime1.18.0/
│   └── mnn3.2.4/
├── venv/                     # Python 虚拟环境
├── app.py                    # 服务入口
└── requirements.txt          # Python 依赖
```

### 版本信息

| 组件 | 版本 |
|------|------|
| nndeploy | 3.0.7 |
| OpenCV | 4.10.0 |
| ONNXRuntime | 1.18.0 |
| MNN | 3.2.4 |
| Python | 3.10.13 |
| CMake | 3.31.3 |
| Visual Studio | 2022 (MSVC 19.44) |

---

*文档更新时间: 2025年12月*
