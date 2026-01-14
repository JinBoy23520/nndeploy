# nndeploy 完整部署指南

## 📖 项目概述

nndeploy 是一个高性能的AI推理部署框架，支持多种深度学习模型的后端推理引擎，包括：
- ONNX Runtime
- MNN (Mobile Neural Network)
- OpenVINO
- TensorRT
- PyTorch
- TensorFlow

框架提供了完整的Python绑定，支持图构建、推理执行、插件系统等功能。

## 🖥️ 系统要求

### 操作系统
- Windows 10/11 (推荐)
- Linux (Ubuntu 18.04+)
- macOS (10.15+)

### 编译环境
- **Visual Studio 2022** (Windows)
- **GCC 9.0+** (Linux)
- **Xcode 12.0+** (macOS)
- **CMake 3.20+**
- **Python 3.11+** (推荐，用于Python绑定)

### 硬件要求
- **CPU**: x86_64架构
- **内存**: 至少8GB RAM
- **存储**: 至少20GB可用空间
- **GPU**: NVIDIA GPU (可选，用于CUDA/TensorRT支持)

## 📦 环境准备

### 1. 安装Python 3.11

```bash
# 下载Python 3.11安装包
# https://www.python.org/downloads/release/python-3119/

# 创建虚拟环境
python311\python.exe -m venv venv311

# 激活虚拟环境
venv311\Scripts\activate
```

### 2. 安装项目依赖

```bash
# 升级pip
python -m pip install --upgrade pip

# 安装Python依赖
pip install -r requirements.txt
```

### 3. 安装系统依赖 (Windows)

```bash
# 安装Visual Studio Build Tools (如果还没有)
# 下载并安装 Visual Studio 2022 Build Tools
# 选择 "Desktop development with C++" 工作负载
```

## 🔨 构建过程

### 1. 克隆项目

```bash
git clone https://github.com/nndeploy/nndeploy.git
cd nndeploy
```

### 2. 创建构建目录

```bash
mkdir build
cd build
```

### 3. 配置CMake (Python 3.11绑定)

```bash
# 清理缓存
Remove-Item CMakeCache.txt, CMakeFiles -Recurse -Force -ErrorAction SilentlyContinue

# 配置构建 (启用Python绑定)
cmake .. ^
  -DCMAKE_BUILD_TYPE=Release ^
  -DENABLE_NNDEPLOY_PYTHON=ON ^
  -DPython_ROOT_DIR="path\to\venv311" ^
  -DPYBIND11_PYTHON_VERSION="3.11"
```

### 4. 构建项目

```bash
# 构建所有组件
cmake --build . --config Release --parallel

# 或者指定构建目标
cmake --build . --config Release --target nndeploy_framework
cmake --build . --config Release --target pynndeploy
```

### 5. 安装Python扩展

```bash
# 复制Python扩展到包目录
Copy-Item "python\Release\_nndeploy_internal.cp311-win_amd64.pyd" "python\nndeploy\_nndeploy_internal.pyd"
```

## 🐍 Python绑定配置

### ABI兼容性问题解决

如果遇到Python绑定的访问冲突，需要确保Python解释器和C++扩展使用相同的编译器：

1. **使用Python 3.11** (VS2022编译)
2. **修改CMake配置**强制使用venv311
3. **重新构建扩展**

```cmake
# 在 cmake/pybind11.cmake 中添加
set(Python_EXECUTABLE "path/to/venv311/Scripts/python.exe" CACHE FILEPATH "Python executable" FORCE)
```

### 测试Python绑定

```python
import sys
sys.path.insert(0, 'python')

import nndeploy

# 测试基本功能
print("nndeploy version:", nndeploy.get_version())

# 测试Graph创建
graph = nndeploy.dag.Graph("test_graph")
print("Graph created successfully")

# 测试初始化
graph.init()
print("Graph initialized successfully")
```

## 🚀 部署和运行

### 1. 服务器部署

```bash
# 启动nndeploy服务器 (默认端口8888)
python app.py

# 指定端口
python app.py --port 8000

# 指定主机和端口
python app.py --host 127.0.0.1 --port 9000

# 加载插件
python app.py --plugin plugin1.py plugin2.so

# 指定资源目录
python app.py --resources ./my_resources

# 指定日志文件
python app.py --log ./logs/server.log

# 指定前端版本
python app.py --front-end-version @latest

# 指定JSON配置文件
python app.py --json_file ./config/model1.json,./config/model2.json
```

### 2. Python API使用

```python
import nndeploy

# 初始化框架
nndeploy.framework_init()

try:
    # 创建推理图
    graph = nndeploy.dag.Graph("inference_graph")

    # 添加模型节点
    model_node = nndeploy.dag.create_node("Inference")
    model_node.set_param("model_path", "model.onnx")
    model_node.set_param("device_type", "cpu")

    # 添加到图中
    graph.add_node(model_node)

    # 初始化图
    graph.init()

    # 执行推理
    result = graph.run()

    print("Inference completed:", result)

finally:
    # 清理资源
    nndeploy.framework_deinit()
```

### 3. 插件系统

nndeploy支持多种插件：

- **分类插件**: `nndeploy_plugin_classification`
- **检测插件**: `nndeploy_plugin_detect`
- **分割插件**: `nndeploy_plugin_segment`
- **OCR插件**: `nndeploy_plugin_ocr`
- **LLM插件**: `nndeploy_plugin_llm`

## 🔧 故障排除

### Python绑定问题

**问题**: `ModuleNotFoundError: No module named 'nndeploy._nndeploy_internal'`

**解决**:
1. 检查Python扩展文件是否存在
2. 确保使用正确的Python版本 (3.11)
3. 重新构建Python扩展

**问题**: `AttributeError: module 'nndeploy' has no attribute 'Graph'`

**解决**: 使用正确的API路径 `nndeploy.dag.Graph`

### 构建问题

**问题**: CMake找不到Python

**解决**:
```bash
cmake .. -DPython_ROOT_DIR="path\to\venv311" -DPYBIND11_PYTHON_VERSION="3.11"
```

**问题**: 编译器版本不匹配

**解决**: 确保Python和nndeploy都使用VS2022编译

### 运行时问题

**问题**: DLL加载失败

**解决**:
1. 检查PATH环境变量包含必要的DLL路径
2. 确保所有依赖库正确安装
3. 使用Dependency Walker检查缺失依赖

## 📚 常用命令

### 环境管理
```bash
# 创建Python虚拟环境
python -m venv venv311

# 激活环境
venv311\Scripts\activate

# 安装依赖
pip install -r requirements.txt

# 升级pip
python -m pip install --upgrade pip
```

### 构建命令
```bash
# 完整构建流程
mkdir build && cd build
cmake .. -DCMAKE_BUILD_TYPE=Release -DENABLE_NNDEPLOY_PYTHON=ON
cmake --build . --config Release --parallel

# 仅构建Python扩展
cmake --build . --config Release --target pynndeploy

# 清理构建
cmake --build . --config Release --target clean
```

### 部署命令
```bash
# 启动服务器
python app.py --port 8000

# 测试API
curl http://localhost:8000/health

# 查看日志
tail -f logs/server.log
```

### 调试命令
```bash
# 测试Python导入
python -c "import nndeploy; print('Import successful')"

# 测试Graph创建
python -c "import nndeploy; graph = nndeploy.dag.Graph('test'); print('Graph created')"

# 检查Python版本
python --version

# 检查pip包
pip list
```

## 📁 项目结构

```
nndeploy/
├── app.py                 # 服务器启动脚本
├── CMakeLists.txt         # 主构建配置
├── requirements.txt       # Python依赖
├── python/               # Python包
│   └── nndeploy/
├── framework/            # 核心框架代码
├── plugin/               # 插件系统
├── demo/                 # 示例代码
├── test/                 # 测试代码
├── cmake/                # CMake配置
├── third_party/          # 第三方库
└── docs/                 # 文档
```

## 🔗 相关链接

- [项目主页](https://github.com/nndeploy/nndeploy)
- [文档](https://nndeploy.readthedocs.io/)
- [问题反馈](https://github.com/nndeploy/nndeploy/issues)

## 📞 支持

如果遇到问题，请：
1. 查看[故障排除](#故障排除)部分
2. 检查[GitHub Issues](https://github.com/nndeploy/nndeploy/issues)
3. 提交新的Issue描述问题详情

---

**最后更新**: 2025年12月8日</content>
<parameter name="filePath">d:\jinwork\nndeploy-1\DEPLOYMENT_GUIDE.md