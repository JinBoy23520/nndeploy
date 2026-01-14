# OpenCvImshow 节点自定义完整指南

## 目录
1. [概述](#概述)
2. [需求分析](#需求分析)
3. [实现步骤](#实现步骤)
4. [代码修改详解](#代码修改详解)
5. [编译与部署](#编译与部署)
6. [前端显示配置](#前端显示配置)
7. [测试验证](#测试验证)
8. [常见问题](#常见问题)

---

## 概述

### 目标
为 nndeploy 框架添加一个自定义节点 `OpenCvImshow`，用于在 Windows 平台上实时显示视频流和图像，支持：
- 实时摄像头视频显示
- 视频文件播放显示
- 图像处理结果显示
- 集成到 DAG 工作流中

### 技术栈
- **C++ 后端**: OpenCV highgui 模块的 `cv::imshow` 和 `cv::waitKey`
- **Python 绑定**: pybind11
- **框架集成**: nndeploy DAG 节点系统
- **编译工具**: CMake + Visual Studio 2022

---

## 需求分析

### 功能需求
1. **输入**: 接收 `cv::Mat` 类型的图像数据
2. **输出**: 无输出（终端显示节点）
3. **配置**: 支持自定义窗口名称
4. **性能**: 低延迟，支持实时视频流（waitKey(1)）

### 节点类型
- **类别**: Encode（编码输出类节点）
- **IO类型**: Image
- **节点类型**: Output（终端节点）

---

## 实现步骤

### 步骤 1: 头文件声明
**文件**: `plugin/include/nndeploy/codec/opencv/opencv_codec.h`

在现有编解码器节点之后添加 `OpenCvImshow` 类声明：

```cpp
class NNDEPLOY_CC_API OpenCvImshow : public Encode {
 public:
  // 四个构造函数重载，匹配框架的创建模式
  OpenCvImshow(const std::string &name);
  OpenCvImshow(const std::string &name, std::vector<dag::Edge *> inputs,
               std::vector<dag::Edge *> outputs);
  OpenCvImshow(const std::string &name, base::CodecFlag flag);
  OpenCvImshow(const std::string &name, std::vector<dag::Edge *> inputs,
               std::vector<dag::Edge *> outputs, base::CodecFlag flag);
  
  virtual ~OpenCvImshow() {}

  // 生命周期方法
  virtual base::Status init();
  virtual base::Status deinit();

  // 配置方法
  virtual base::Status setRefPath(const std::string &ref_path) override;
  virtual base::Status setPath(const std::string &window_name);

  // 核心执行方法
  virtual base::Status run();

 private:
  std::string window_name_;  // 窗口名称
};
```

**关键点**:
- 继承自 `Encode` 类（输出节点基类）
- `key_` 设置为 `"nndeploy::codec::OpenCvImshow"`
- `desc_` 描述节点功能
- 设置输入类型为 `cv::Mat`
- 设置 IO 类型为 `kIOTypeImage`

---

### 步骤 2: 源文件实现
**文件**: `plugin/source/nndeploy/codec/opencv/opencv_codec.cc`

#### 2.1 构造函数实现
在 `opencv_codec.cc` 文件中添加构造函数实现（已在头文件中内联）。

#### 2.2 核心方法实现
在文件末尾、节点注册之前添加：

```cpp
base::Status OpenCvImshow::init() {
  return base::kStatusCodeOk;
}

base::Status OpenCvImshow::deinit() {
  return base::kStatusCodeOk;
}

base::Status OpenCvImshow::setPath(const std::string &window_name) {
  window_name_ = window_name;
  return base::kStatusCodeOk;
}

base::Status OpenCvImshow::setRefPath(const std::string &ref_path) {
  // For display, ref_path is not used
  return base::kStatusCodeOk;
}

base::Status OpenCvImshow::run() {
  cv::Mat *mat = inputs_[0]->getCvMat(this);
  if (mat != nullptr) {
    cv::imshow(window_name_, *mat);
    cv::waitKey(1);  // 处理窗口事件，实现非阻塞显示
  }
  return base::kStatusCodeOk;
}
```

**代码作用**:
- `init()`: 初始化方法，无需特殊操作
- `deinit()`: 清理方法，窗口由 OpenCV 自动管理
- `setPath()`: 设置窗口名称
- `setRefPath()`: 空实现，显示节点不需要参考路径
- `run()`: 
  - 从输入边获取 `cv::Mat` 数据
  - 使用 `cv::imshow()` 显示图像
  - 调用 `cv::waitKey(1)` 处理窗口事件（关键：实现非阻塞刷新）

---

### 步骤 3: 节点注册
在 `opencv_codec.cc` 文件末尾，添加节点注册（在其他 REGISTER_NODE 之后）：

```cpp
REGISTER_NODE("nndeploy::codec::OpenCvImageDecode", OpenCvImageDecode);
REGISTER_NODE("nndeploy::codec::OpenCvImagesDecode", OpenCvImagesDecode);
REGISTER_NODE("nndeploy::codec::OpenCvVideoDecode", OpenCvVideoDecode);
REGISTER_NODE("nndeploy::codec::OpenCvCameraDecode", OpenCvCameraDecode);
REGISTER_NODE("nndeploy::codec::OpenCvImageEncode", OpenCvImageEncode);
REGISTER_NODE("nndeploy::codec::OpenCvImagesEncode", OpenCvImagesEncode);
REGISTER_NODE("nndeploy::codec::OpenCvVideoEncode", OpenCvVideoEncode);
REGISTER_NODE("nndeploy::codec::OpenCvCameraEncode", OpenCvCameraEncode);
REGISTER_NODE("nndeploy::codec::OpenCvImshow", OpenCvImshow);  // 新增
```

**作用**:
- `REGISTER_NODE` 宏将节点注册到全局 `NodeFactory`
- 第一个参数是节点的唯一键（key），在 JSON 配置中使用
- 第二个参数是节点类名
- 宏会自动生成 `TypeNodeCreator<OpenCvImshow>` 创建器

---

### 步骤 4: Python 绑定
**文件**: `python/src/codec/codec.cc`

在文件末尾、`#endif` 之前添加 Python 绑定：

```cpp
  py::class_<OpenCvImshow, Encode>(m, "OpenCvImshow")
      .def(py::init<const std::string &>())
      .def(py::init<const std::string &, std::vector<dag::Edge *> &,
                    std::vector<dag::Edge *> &>())
      .def(py::init<const std::string &, base::CodecFlag>())
      .def(py::init<const std::string &, std::vector<dag::Edge *> &,
                    std::vector<dag::Edge *> &, base::CodecFlag>())
      .def("init", &OpenCvImshow::init)
      .def("deinit", &OpenCvImshow::deinit)
      .def("set_path", &OpenCvImshow::setPath, py::arg("window_name"))
      .def("run", &OpenCvImshow::run);
```

**作用**:
- 使用 pybind11 将 C++ 类暴露给 Python
- 绑定所有构造函数重载
- 绑定核心方法（init、deinit、set_path、run）
- `py::arg("window_name")` 为参数提供命名

---

### 步骤 5: Python 模块导入
**文件**: `python/nndeploy/codec/__init__.py`

在 `try` 块中添加导入：

```python
try:
    from nndeploy.codec.codec import Decode
    from nndeploy.codec.codec import Encode
    from nndeploy.codec.codec import create_decode_node
    from nndeploy.codec.codec import create_encode_node
    from nndeploy.codec.codec import OpenCvImageDecode
    from nndeploy.codec.codec import OpenCvImagesDecode
    from nndeploy.codec.codec import OpenCvVideoDecode
    from nndeploy.codec.codec import OpenCvCameraDecode
    from nndeploy.codec.codec import OpenCvImageEncode
    from nndeploy.codec.codec import OpenCvImagesEncode
    from nndeploy.codec.codec import OpenCvVideoEncode
    from nndeploy.codec.codec import OpenCvCameraEncode
    from nndeploy.codec.codec import OpenCvImshow  # 新增
    from nndeploy.codec.codec import create_opencv_decode
    from nndeploy.codec.codec import create_opencv_encode
    from nndeploy.codec.codec import BatchOpenCvDecode
    from nndeploy.codec.codec import BatchOpenCvEncode
except:
    pass
```

**作用**:
- 确保 `OpenCvImshow` 可以在 Python 中被导入
- 用户可以通过 `from nndeploy.codec import OpenCvImshow` 使用节点

---

## 代码修改详解

### 修改文件清单

| 文件路径 | 修改内容 | 代码行数 | 作用 |
|---------|---------|---------|------|
| `plugin/include/nndeploy/codec/opencv/opencv_codec.h` | 添加 `OpenCvImshow` 类声明 | ~50 行 | 定义节点接口 |
| `plugin/source/nndeploy/codec/opencv/opencv_codec.cc` | 实现节点方法 + 注册 | ~30 行 | 实现核心逻辑 |
| `python/src/codec/codec.cc` | 添加 pybind11 绑定 | ~10 行 | Python 接口 |
| `python/nndeploy/codec/__init__.py` | 添加导入语句 | 1 行 | 模块暴露 |

### 代码对应作用表

| 代码组件 | 功能描述 | 技术细节 |
|---------|---------|---------|
| **头文件声明** | 定义类接口和成员变量 | 继承 `Encode`，包含 4 个构造函数重载 |
| **构造函数** | 初始化节点元数据 | 设置 `key_`、`desc_`、输入类型、IO 类型 |
| **init()** | 节点初始化 | 空实现（无需初始化资源） |
| **deinit()** | 节点清理 | 空实现（OpenCV 自动管理窗口） |
| **setPath()** | 设置窗口名称 | 保存到 `window_name_` 成员变量 |
| **run()** | 核心执行逻辑 | 从边获取 Mat → imshow 显示 → waitKey 刷新 |
| **REGISTER_NODE** | 全局注册 | 将节点注册到 `NodeFactory`，支持 JSON 反序列化 |
| **pybind11 绑定** | Python 接口 | 暴露类和方法给 Python 环境 |
| **Python import** | 模块导入 | 使节点在 Python 中可用 |

---

## 编译与部署

### 编译流程

#### 方法 1: 使用自动化脚本（推荐）
```powershell
# 激活虚拟环境
& D:\jinwork\nndeploy-1\venv311\Scripts\Activate.ps1

# 运行 Windows 构建脚本
python build_win.py
```

**脚本功能**:
1. 安装 Python 依赖（pybind11、setuptools、wheel）
2. 检查并安装第三方库（OpenCV、ONNX Runtime、MNN）
3. 配置 CMake（生成 Visual Studio 2022 项目）
4. 编译项目（Release 配置，并行编译）
5. 安装到指定目录

#### 方法 2: 手动编译
```powershell
# 1. 配置 CMake
cd D:\jinwork\nndeploy-1\build
cmake -G "Visual Studio 17 2022" -A x64 -DCMAKE_BUILD_TYPE=Release ..

# 2. 编译
cmake --build . --config Release --parallel 4

# 3. 安装（可选）
cmake --install . --config Release
```

### 编译输出
- **DLL 文件**: `build/nndeploy_3.0.7_Windows_AMD64_Release_MSVC/bin/`
  - `nndeploy_framework.dll`: 核心框架
  - `nndeploy_plugin_codec.dll`: 编解码器插件（包含 OpenCvImshow）
- **Python 模块**: `python/nndeploy/`
  - 编译后的 `.pyd` 文件（Python 扩展模块）

### 部署到 Python 环境
```powershell
# 复制新的 DLL 文件到 Python 包目录
copy build\nndeploy_3.0.7_Windows_AMD64_Release_MSVC\bin\nndeploy_framework.dll python\nndeploy\
copy build\nndeploy_3.0.7_Windows_AMD64_Release_MSVC\bin\nndeploy_plugin_codec.dll python\nndeploy\
```

**重要**: 如果 DLL 被占用，需要先备份旧文件：
```powershell
move python\nndeploy\nndeploy_framework.dll python\nndeploy\nndeploy_framework.dll.bak
move python\nndeploy\nndeploy_plugin_codec.dll python\nndeploy\nndeploy_plugin_codec.dll.bak
```

---

## 前端显示配置

### JSON 工作流配置

#### 完整节点定义示例
```json
{
    "key_": "nndeploy::codec::OpenCvImshow",
    "name_": "OpenCvImshow_Display",
    "developer_": "nndeploy",
    "source_": "plugin",
    "desc_": "实时显示视频超分结果",
    "device_type_": "kDeviceTypeCodeCpu:0",
    "version_": "1.0.0",
    "required_params_": [],
    "is_dynamic_input_": false,
    "inputs_": [
        {
            "desc_": "输入图像",
            "name_": "GFPGAN_Output@result",
            "type_": "ndarray"
        }
    ],
    "is_dynamic_output_": false,
    "outputs_": [],
    "node_type_": "Output",
    "window_name_": "超分结果显示"
}
```

#### 字段说明

| 字段 | 必填 | 说明 | 示例值 |
|-----|------|------|--------|
| `key_` | ✅ | 节点唯一标识符 | `"nndeploy::codec::OpenCvImshow"` |
| `name_` | ✅ | 节点实例名称 | `"OpenCvImshow_Display"` |
| `developer_` | ✅ | 开发者标识 | `"nndeploy"` |
| `source_` | ✅ | 节点来源 | `"plugin"` |
| `desc_` | ✅ | 节点描述 | `"实时显示视频超分结果"` |
| `device_type_` | ✅ | 设备类型 | `"kDeviceTypeCodeCpu:0"` |
| `version_` | ✅ | 版本号 | `"1.0.0"` |
| `node_type_` | ✅ | 节点类型 | `"Output"` |
| `inputs_` | ✅ | 输入边列表 | 连接到上游节点输出 |
| `outputs_` | ✅ | 输出边列表 | 空数组（终端节点） |
| `window_name_` | ⚠️ | 窗口名称（可选） | `"超分结果显示"` |

### 实时视频超分工作流示例

**文件**: `resources/workflow/实时视频超分.json`

```json
{
    "key_": "nndeploy.dag.Graph",
    "name_": "实时视频超分",
    "node_repository_": [
        {
            "key_": "nndeploy::codec::OpenCvCameraDecode",
            "name_": "CameraInput",
            "outputs_": [
                {"name_": "CameraInput@output_0", "type_": "ndarray"}
            ],
            "camera_id_": 0
        },
        {
            "key_": "nndeploy.gan.GFPGAN",
            "name_": "GFPGAN_SR",
            "inputs_": [
                {"name_": "CameraInput@output_0", "type_": "ndarray"}
            ],
            "outputs_": [
                {"name_": "GFPGAN_SR@result", "type_": "ndarray"}
            ],
            "model_path": "models/GFPGANv1.4.pth"
        },
        {
            "key_": "nndeploy::codec::OpenCvImshow",
            "name_": "DisplayOutput",
            "inputs_": [
                {"name_": "GFPGAN_SR@result", "type_": "ndarray"}
            ],
            "outputs_": [],
            "window_name_": "实时超分显示"
        }
    ],
    "edge_repository_": [
        {
            "name_": "edge_camera_to_gfpgan",
            "source_": "CameraInput@output_0",
            "destination_": "GFPGAN_SR@input_0"
        },
        {
            "name_": "edge_gfpgan_to_display",
            "source_": "GFPGAN_SR@result",
            "destination_": "DisplayOutput@input_0"
        }
    ],
    "loop_count_": -1,
    "is_external_stream_": true
}
```

### 前端 UI 参数配置

如果需要在前端界面中自定义窗口名称，可以添加 UI 参数：

```json
{
    "ui_params_": [
        {
            "name": "window_name_",
            "label": "显示窗口名称",
            "type": "string",
            "default": "NNDeploy Display",
            "description": "OpenCV 显示窗口的标题"
        }
    ]
}
```

---

## 测试验证

### 测试 1: 简单节点创建测试

**测试文件**: `resources/workflow/test_opencv_imshow.json`

```json
{
    "key_": "nndeploy.dag.Graph",
    "name_": "OpenCvImshow测试",
    "node_repository_": [
        {
            "key_": "nndeploy::basic::InputCppNum",
            "name_": "InputNum",
            "outputs_": [
                {"name_": "InputNum@output_0", "type_": "int32"}
            ],
            "value_": 42
        },
        {
            "key_": "nndeploy::codec::OpenCvImshow",
            "name_": "OpenCvImshow_1",
            "inputs_": [
                {"name_": "InputNum@output_0", "type_": "ndarray"}
            ],
            "outputs_": [],
            "window_name_": "Test Window"
        }
    ],
    "edge_repository_": [
        {
            "name_": "edge_0",
            "source_": "InputNum@output_0",
            "destination_": "OpenCvImshow_1@input_0"
        }
    ],
    "loop_count_": 1
}
```

**运行测试**:
```powershell
python run_json.py --json resources/workflow/test_opencv_imshow.json
```

**预期输出**:
```
[INFO] Imported nndeploy.codec
run 0 times, time: 2.8e-05
TimeProfiler:
-------------------------------------------------------------------------------------
name                       call_times  sum cost_time(ms)  avg cost_time(ms)
-------------------------------------------------------------------------------------
OpenCvImshow_1 init()      1           0.002              0.002
OpenCvImshow_1 run()       1           0.006              0.006
-------------------------------------------------------------------------------------
[INFO] GraphRunner execution completed successfully.
```

### 测试 2: 实时视频超分工作流

**运行命令**:
```powershell
python run_json.py --json resources/workflow/实时视频超分.json
```

**验证点**:
1. ✅ 节点成功创建（无 "Failed to create node" 错误）
2. ✅ 摄像头正常打开
3. ✅ GFPGAN 推理正常运行
4. ✅ OpenCV 窗口显示超分结果
5. ✅ 实时刷新无卡顿

### 测试 3: 服务器集成测试

**启动服务器**:
```powershell
python app.py --port 8000
```

**验证**:
1. 访问 `http://localhost:8000/docs` 查看 API 文档
2. 上传包含 OpenCvImshow 的工作流 JSON
3. 执行工作流并观察显示窗口

---

## 常见问题

### Q1: 节点创建失败 "Failed to create node"

**症状**:
```
E/nndeploy_default_str: Failed to createNode node_xxx, node_key: nndeploy::codec::OpenCvImshow
```

**原因**:
- 节点未正确注册到 `NodeFactory`
- DLL 文件未更新

**解决方案**:
```powershell
# 1. 确认 REGISTER_NODE 已添加到 opencv_codec.cc
# 2. 重新编译项目
python build_win.py

# 3. 更新 DLL 文件
copy build\nndeploy_3.0.7_Windows_AMD64_Release_MSVC\bin\nndeploy_plugin_codec.dll python\nndeploy\
```

---

### Q2: Python 无法导入 OpenCvImshow

**症状**:
```python
ImportError: cannot import name 'OpenCvImshow' from 'nndeploy.codec'
```

**原因**:
- Python 绑定未添加
- `__init__.py` 未更新导入

**解决方案**:
1. 确认 `python/src/codec/codec.cc` 中有 OpenCvImshow 绑定
2. 确认 `python/nndeploy/codec/__init__.py` 中有导入语句
3. 重新编译 Python 模块

---

### Q3: 窗口无法显示或程序卡死

**症状**:
- 窗口创建但不刷新
- 程序无响应

**原因**:
- 缺少 `cv::waitKey()` 调用
- Windows 消息循环未处理

**解决方案**:
```cpp
base::Status OpenCvImshow::run() {
  cv::Mat *mat = inputs_[0]->getCvMat(this);
  if (mat != nullptr) {
    cv::imshow(window_name_, *mat);
    cv::waitKey(1);  // 必须调用，处理窗口事件
  }
  return base::kStatusCodeOk;
}
```

---

### Q4: 编译时出现链接错误

**症状**:
```
error LNK2019: unresolved external symbol "public: __cdecl OpenCvImshow::..."
```

**原因**:
- 头文件声明和实现不匹配
- 构造函数未正确实现

**解决方案**:
1. 确认所有构造函数都已实现（可在头文件中内联实现）
2. 确认头文件和源文件的类声明一致
3. 清理构建缓存后重新编译

---

### Q5: 相机无法打开

**症状**:
```
W/nndeploy_default_str: Invalid parameter error occurred. index[0] >=size_[0].
```

**原因**:
- 相机分辨率配置不正确
- 相机设备 ID 错误

**解决方案**:
```json
{
    "key_": "nndeploy::codec::OpenCvCameraDecode",
    "camera_id_": 0,
    "size": {"width": 640, "height": 480}  // 修改为合适的分辨率
}
```

---

## 总结

### 核心要点
1. **继承体系**: OpenCvImshow 继承自 `Encode` 类
2. **节点注册**: 使用 `REGISTER_NODE` 宏注册到框架
3. **Python 绑定**: 通过 pybind11 暴露给 Python
4. **关键方法**: `run()` 中必须调用 `cv::waitKey(1)` 实现非阻塞显示
5. **构造函数**: 需要实现 4 个重载以匹配框架的创建模式

### 扩展建议
- 添加窗口位置和大小配置
- 支持全屏显示模式
- 添加截图保存功能
- 支持多窗口同时显示
- 添加性能监控（FPS 显示）

### 参考资源
- nndeploy 文档: [官方仓库](https://github.com/nndeploy/nndeploy)
- OpenCV 文档: [highgui 模块](https://docs.opencv.org/4.x/d7/dfc/group__highgui.html)
- pybind11 文档: [官方文档](https://pybind11.readthedocs.io/)

---

**文档版本**: v1.0  
**最后更新**: 2026年1月5日  
**作者**: GitHub Copilot  
**适用版本**: nndeploy 3.0.7
