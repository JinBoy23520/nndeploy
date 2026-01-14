# Android使用自定义节点详细指南

## 目录
- [概述](#概述)
- [原生节点的使用方式](#原生节点的使用方式)
- [Python自定义节点在Android中的运行原理](#python自定义节点在android中的运行原理)
- [完整使用流程](#完整使用流程)
- [实战案例](#实战案例)
- [常见问题](#常见问题)

---

## 概述

nndeploy框架支持在Android端使用Python编写的自定义节点（如 `DetailZoomCompare`、`SideBySideCompare` 等）。Android端通过工作流JSON文件调用这些节点，无需重新编译Android代码。

### 关键特性
- ✅ **跨平台支持**：Python节点在桌面端和Android端通用
- ✅ **零代码修改**：Android端无需编写Java/Kotlin代码即可使用新节点
- ✅ **动态加载**：通过JSON工作流配置，动态调用已注册的节点
- ✅ **完整功能**：支持参数配置、序列化/反序列化、多输入输出

---

## 原生节点的使用方式

### 1. C++原生节点（Framework层）

**位置**：`framework/source/nndeploy/` 

**特点**：
- 编译到 `libnndeploy.so` 中
- 通过JNI直接调用
- 性能最优

**示例**：
```cpp
// framework/source/nndeploy/codec/opencv.cc
REGISTER_NODE("nndeploy::codec::OpenCvImageDecode", OpenCvImageDecode);
REGISTER_NODE("nndeploy::codec::OpenCvImageEncode", OpenCvImageEncode);
```

### 2. Python自定义节点（Plugin层）

**位置**：`python/nndeploy/` 

**特点**：
- 在Python进程中运行
- 通过C++桥接层与Framework交互
- 灵活性高，易于开发和调试

**示例**：
```python
# python/nndeploy/codec/detail_zoom_compare.py
class DetailZoomCompare(nndeploy.dag.Node):
    def __init__(self, name, inputs=None, outputs=None):
        super().set_key("nndeploy.codec.DetailZoomCompare")
        # ...

nndeploy.dag.register_node("nndeploy.codec.DetailZoomCompare", creator)
```

---

## Python自定义节点在Android中的运行原理

### 架构图

```
┌─────────────────────────────────────────────────────────┐
│                    Android Application                   │
│  ┌──────────────────────────────────────────────────┐   │
│  │          Kotlin/Java Layer                       │   │
│  │  - MainActivity.kt                               │   │
│  │  - ImageInImageOut.kt (算法处理器)               │   │
│  │  - GraphRunner.kt (工作流运行器)                 │   │
│  └────────────┬─────────────────────────────────────┘   │
│               │ JNI 调用                                 │
│  ┌────────────▼─────────────────────────────────────┐   │
│  │          Native C++ Layer (libnndeploy.so)       │   │
│  │  - dag::Graph                                    │   │
│  │  - dag::GraphRunner                              │   │
│  │  - C++节点 (OpenCvImageDecode等)                 │   │
│  └────────────┬─────────────────────────────────────┘   │
│               │ Python桥接                               │
│  ┌────────────▼─────────────────────────────────────┐   │
│  │     Python Runtime (通过so加载)                  │   │
│  │  - DetailZoomCompare (Python节点)               │   │
│  │  - SideBySideCompare (Python节点)               │   │
│  │  - RealESRGAN (Python节点)                       │   │
│  │  - GFPGAN (Python节点)                           │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

### 执行流程

1. **Android应用启动**
   ```kotlin
   // MainActivity.kt
   val runner = GraphRunner() // 创建运行器
   ```

2. **加载工作流JSON**
   ```kotlin
   val rawJson = context.assets.open("resources/workflow/XXX.json")
       .bufferedReader().use { it.readText() }
   ```

3. **C++层解析并构建DAG**
   ```cpp
   // GraphRunner::run()
   graph->deserialize(jsonStr); // 根据key创建节点
   ```

4. **Python节点自动注册和创建**
   ```python
   # python/nndeploy/codec/__init__.py
   from .detail_zoom_compare import detail_zoom_compare_creator
   # 节点已通过 register_node() 注册到全局注册表
   ```

5. **执行工作流**
   ```cpp
   graph->init();
   graph->run(); // 按照DAG拓扑顺序执行各节点
   ```

---

## 完整使用流程

### 步骤1：编写Python自定义节点

创建文件：`python/nndeploy/custom/my_node.py`

```python
import numpy as np
import json
import nndeploy.base
import nndeploy.dag

class MyCustomNode(nndeploy.dag.Node):
    """自定义图像处理节点"""
    
    def __init__(self, name, inputs=None, outputs=None):
        super().__init__(name, inputs, outputs)
        # 1. 设置节点唯一标识符（必须）
        super().set_key("nndeploy.custom.MyCustomNode")
        
        # 2. 设置节点描述
        super().set_desc("我的自定义图像处理节点")
        
        # 3. 设置输入输出类型
        self.set_input_type(np.ndarray)   # 输入: numpy数组
        self.set_output_type(np.ndarray)  # 输出: numpy数组
        
        # 4. 定义可配置参数
        self.threshold_ = 0.5       # 阈值参数
        self.enable_filter_ = True  # 开关参数
        self.model_path_ = ""       # 模型路径（必需参数）
        
    def init(self):
        """节点初始化：加载模型、分配资源"""
        print(f"[MyCustomNode] 初始化...")
        print(f"  - 阈值: {self.threshold_}")
        print(f"  - 过滤开关: {self.enable_filter_}")
        print(f"  - 模型路径: {self.model_path_}")
        
        # 在这里加载模型或初始化资源
        # self.model = load_model(self.model_path_)
        
        return nndeploy.base.Status.ok()
    
    def run(self):
        """节点执行：处理输入数据"""
        # 1. 获取输入数据
        input_data = self.get_input(0).get(self)
        
        if input_data is None:
            print("[MyCustomNode] 输入数据为空")
            return nndeploy.base.Status(
                nndeploy.base.StatusCode.kStatusCodeErrorInvalidParam
            )
        
        # 2. 执行自定义处理逻辑
        output_data = self.process(input_data)
        
        # 3. 设置输出数据
        self.get_output(0).set(output_data)
        
        return nndeploy.base.Status.ok()
    
    def process(self, input_data):
        """自定义处理逻辑"""
        # 示例：简单的图像阈值处理
        if self.enable_filter_:
            processed = input_data * self.threshold_
            return processed.astype(np.uint8)
        return input_data
    
    def serialize(self):
        """序列化参数到JSON（保存工作流时调用）"""
        # 标记必需参数
        self.add_required_param("model_path_")
        
        json_str = super().serialize()
        json_obj = json.loads(json_str)
        
        # 将所有参数写入JSON
        json_obj["threshold_"] = self.threshold_
        json_obj["enable_filter_"] = self.enable_filter_
        json_obj["model_path_"] = self.model_path_
        
        return json.dumps(json_obj)
    
    def deserialize(self, target: str):
        """从JSON反序列化参数（加载工作流时调用）"""
        json_obj = json.loads(target)
        
        # 从JSON读取参数
        self.threshold_ = json_obj.get("threshold_", 0.5)
        self.enable_filter_ = json_obj.get("enable_filter_", True)
        self.model_path_ = json_obj.get("model_path_", "")
        
        return super().deserialize(target)


# 创建节点创建器
class MyCustomNodeCreator(nndeploy.dag.NodeCreator):
    def __init__(self):
        super().__init__()
    
    def create_node(self, name: str, inputs, outputs):
        self.node = MyCustomNode(name, inputs, outputs)
        return self.node


# 注册节点到全局注册表
my_custom_node_creator = MyCustomNodeCreator()
nndeploy.dag.register_node("nndeploy.custom.MyCustomNode", my_custom_node_creator)
```

### 步骤2：注册节点到模块

编辑 `python/nndeploy/custom/__init__.py`：

```python
# python/nndeploy/custom/__init__.py
import warnings

# 导入自定义节点，自动触发注册
try:
    from .my_node import my_custom_node_creator
    print("MyCustomNode 加载成功")
except Exception as e:
    warnings.warn(f"MyCustomNode 导入失败: {e}")
```

编辑 `python/nndeploy/__init__.py`，确保导入自定义模块：

```python
# python/nndeploy/__init__.py
# ... 其他导入 ...

# 导入自定义节点模块
try:
    from . import custom
except Exception as e:
    print(f"custom 模块导入失败: {e}")
```

### 步骤3：在桌面端测试节点

创建测试工作流 `resources/workflow/test_custom_node.json`：

```json
{
  "name_": "TestCustomNode",
  "is_path_": true,
  "nodes_": [
    {
      "key_": "nndeploy::codec::OpenCvImageDecode",
      "name_": "ImageDecode_1",
      "inputs_": [],
      "outputs_": [
        {
          "name_": "output_0",
          "type_": "Mat"
        }
      ],
      "path_": "resources/images/test.jpg"
    },
    {
      "key_": "nndeploy.custom.MyCustomNode",
      "name_": "MyCustomNode_2",
      "inputs_": [
        {
          "name_": "ImageDecode_1@output_0",
          "type_": "Mat"
        }
      ],
      "outputs_": [
        {
          "name_": "output_0",
          "type_": "Mat"
        }
      ],
      "threshold_": 0.8,
      "enable_filter_": true,
      "model_path_": "resources/models/my_model.pth"
    },
    {
      "key_": "nndeploy::codec::OpenCvImageEncode",
      "name_": "ImageEncode_3",
      "inputs_": [
        {
          "name_": "MyCustomNode_2@output_0",
          "type_": "Mat"
        }
      ],
      "outputs_": [],
      "path_": "output/result.jpg"
    }
  ]
}
```

运行测试：

```bash
cd nndeploy
python -m nndeploy.server.app --port 8000
```

在前端界面中加载 `test_custom_node.json` 并运行，验证节点功能。

### 步骤4：编译Android版本

#### 4.1 编译C++库（含Python支持）

在Linux环境下编译：

```bash
# 设置环境变量
export ANDROID_NDK=/path/to/android-ndk-r25c
export ANDROID_SDK=/path/to/android-sdk

# 安装Rust Android目标
rustup target add aarch64-linux-android

# 编译
cd nndeploy/build
cmake -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-21 \
    -DCMAKE_BUILD_TYPE=Release \
    -DENABLE_NNDEPLOY_PYTHON=ON \
    ..
ninja
ninja install
```

#### 4.2 拷贝库文件到Android项目

```bash
# 拷贝libc++_shared.so
cp $ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so \
   /path/to/nndeploy/build/lib/

# 拷贝所有.so文件到Android项目
python3 tool/script/android_install_so.py \
    /path/to/nndeploy/build/nndeploy_x.x.x_Android_aarch64_Release_Clang \
    /path/to/nndeploy/app/android/app/src/main/jniLibs/arm64-v8a
```

#### 4.3 拷贝资源文件

```bash
# 拷贝模型、工作流、Python代码等资源
python3 tool/script/android_install_resources.py \
    -r /path/to/nndeploy/resources/ \
    -a /path/to/nndeploy/app/android/app/src/main/assets
```

**重要**：确保Python自定义节点代码也被拷贝到assets中：

```
app/android/app/src/main/assets/
├── resources/
│   ├── models/
│   │   └── my_model.pth
│   └── workflow/
│       └── test_custom_node.json
└── python/
    └── nndeploy/
        └── custom/
            ├── __init__.py
            └── my_node.py
```

### 步骤5：在Android端使用

#### 5.1 创建算法定义

编辑 `app/android/app/src/main/java/com/nndeploy/ai/Algorithm.kt`：

```kotlin
fun createDefaultAlgorithms(): List<AIAlgorithm> {
    return listOf(
        // ... 其他算法 ...
        
        // 添加自定义节点算法
        AIAlgorithm(
            id = "my_custom_processing",
            name = "My Custom Processing",
            description = "使用自定义节点处理图像",
            icon = Icons.Default.AutoAwesome,
            inputType = listOf(InOutType.IMAGE),
            outputType = listOf(InOutType.IMAGE),
            category = AlgorithmCategory.COMPUTER_VISION.displayName,
            workflowAsset = "resources/workflow/test_custom_node.json",
            tags = listOf("custom", "processing"),
            parameters = mapOf(
                "input_node" to mapOf("ImageDecode_1" to "path_"),
                "output_node" to mapOf("ImageEncode_3" to "path_")
            ),
            processFunction = "processImageInImageOut"
        )
    )
}
```

#### 5.2 运行工作流

Android端代码会自动：

1. 从assets加载工作流JSON
2. 替换资源路径为外部存储路径
3. 通过JNI调用GraphRunner执行工作流
4. Python节点自动被调用

```kotlin
// ImageInImageOut.kt
suspend fun processImageInImageOut(
    context: Context, 
    inputUri: Uri, 
    alg: AIAlgorithm
): ProcessResult {
    // 1. 读取工作流JSON
    val rawJson = context.assets.open(alg.workflowAsset)
        .bufferedReader().use { it.readText() }
    
    // 2. 替换资源路径
    val resolvedJson = rawJson.replace(
        "resources/", 
        "${extResDir.absolutePath}/"
    )
    
    // 3. 运行工作流
    val runner = GraphRunner()
    runner.setNodeValue("ImageDecode_1", "path_", inputFile.absolutePath)
    runner.setNodeValue("ImageEncode_3", "path_", outputFile.absolutePath)
    
    val success = runner.run(workflowFile.absolutePath, alg.id, taskId)
    
    return if (success) {
        ProcessResult.Success(Uri.fromFile(outputFile))
    } else {
        ProcessResult.Error("处理失败")
    }
}
```

---

## 实战案例

### 案例1：DetailZoomCompare节点

这是一个实际的对比显示节点，用于放大显示图像细节。

**特点**：
- 接收两个输入（原图和增强图）
- 在屏幕上显示左右对比+放大细节
- 支持自动FPS检测

**关键代码**（已实现）：

```python
# python/nndeploy/codec/detail_zoom_compare.py
class DetailZoomCompare(nndeploy.dag.Node):
    def __init__(self, name, inputs=None, outputs=None):
        super().set_key("nndeploy.codec.DetailZoomCompare")
        self.zoom_factor_ = 4      # 放大4倍
        self.roi_x_ = 0.3          # ROI中心X
        self.roi_y_ = 0.3          # ROI中心Y
        self.auto_fps_ = True      # 自动FPS
        
    def run(self):
        frame_original = self.get_input(0).get(self)
        frame_enhanced = self.get_input(1).get(self)
        
        # 创建对比显示
        display_frame = self.create_comparison_view(
            frame_original, frame_enhanced
        )
        
        # 显示并控制播放速度
        cv2.imshow(self.window_name_, display_frame)
        cv2.waitKey(self.wait_key_delay_)
        
        return nndeploy.base.Status.ok()
```

**在Android中使用**：

虽然 `DetailZoomCompare` 使用了 `cv2.imshow()`（桌面端显示），在Android端会自动跳过显示逻辑，但数据处理部分仍然有效。如需在Android端显示，需要：

1. **方案A**：修改节点，添加Android检测
```python
def run(self):
    # ... 数据处理 ...
    
    # 只在桌面端显示
    if not self.is_android():
        cv2.imshow(self.window_name_, display_frame)
        cv2.waitKey(self.wait_key_delay_)
    
    # 输出处理后的帧供Android使用
    self.get_output(0).set(display_frame)
    return nndeploy.base.Status.ok()
```

2. **方案B**：创建Android专用版本
```python
# python/nndeploy/codec/detail_zoom_compare_android.py
class DetailZoomCompareAndroid(nndeploy.dag.Node):
    """Android版本：只处理数据，不显示"""
    def run(self):
        frame_original = self.get_input(0).get(self)
        frame_enhanced = self.get_input(1).get(self)
        
        # 创建对比视图但不显示
        display_frame = self.create_comparison_view(
            frame_original, frame_enhanced
        )
        
        # 直接输出供Android端使用
        self.get_output(0).set(display_frame)
        return nndeploy.base.Status.ok()
```

### 案例2：视频超分工作流

完整工作流示例：`resources/workflow/RealESRGAN细节放大对比.json`

```json
{
  "name_": "RealESRGAN_DetailZoom",
  "nodes_": [
    {
      "key_": "nndeploy::codec::OpenCvVideoDecode",
      "name_": "VideoDecode_1",
      "path_": "resources/videos/face.mp4"
    },
    {
      "key_": "nndeploy.super_resolution.RealESRGAN",
      "name_": "RealESRGAN_2",
      "inputs_": [{"name_": "VideoDecode_1@output_0"}],
      "model_path_": "resources/models/RealESRGAN_x2plus.pth",
      "scale_": 2,
      "tile_": 256
    },
    {
      "key_": "nndeploy.codec.DetailZoomCompare",
      "name_": "Compare_3",
      "inputs_": [
        {"name_": "VideoDecode_1@output_0"},
        {"name_": "RealESRGAN_2@output_0"}
      ],
      "zoom_factor_": 6,
      "auto_fps_": true
    }
  ]
}
```

**在Android端使用此工作流**：

```kotlin
// 在Algorithm.kt中定义
AIAlgorithm(
    id = "realesrgan_detail_compare",
    name = "Real-ESRGAN Detail Compare",
    description = "视频超分+细节对比",
    workflowAsset = "resources/workflow/RealESRGAN细节放大对比.json",
    inputType = listOf(InOutType.VIDEO),
    outputType = listOf(InOutType.VIDEO),
    parameters = mapOf(
        "input_node" to mapOf("VideoDecode_1" to "path_"),
        "output_node" to mapOf("Compare_3" to "output_0")  // 如果有输出
    )
)
```

---

## 常见问题

### Q1: Android端无法找到Python节点？

**错误信息**：
```
E/nndeploy: Failed to createNode, node_key: nndeploy.custom.MyCustomNode
```

**解决方案**：

1. **检查节点是否已注册**
```python
# 在 __init__.py 中确保导入
from .my_node import my_custom_node_creator
```

2. **检查key是否一致**
```python
# 节点定义
super().set_key("nndeploy.custom.MyCustomNode")

# 注册
nndeploy.dag.register_node("nndeploy.custom.MyCustomNode", creator)

# JSON中使用
"key_": "nndeploy.custom.MyCustomNode"
```

3. **检查Python代码是否拷贝到assets**
```bash
# 确保这个文件存在
app/android/app/src/main/assets/python/nndeploy/custom/my_node.py
```

### Q2: 节点参数无法正确传递？

**问题**：JSON中配置的参数在节点中没有生效

**解决方案**：

检查 `deserialize()` 方法：

```python
def deserialize(self, target: str):
    json_obj = json.loads(target)
    
    # 使用 get() 提供默认值，避免KeyError
    self.my_param_ = json_obj.get("my_param_", default_value)
    
    # 必须调用父类方法
    return super().deserialize(target)
```

### Q3: cv2.imshow() 在Android上报错？

**错误信息**：
```
cv2.error: OpenCV(4.x) GUI is not available on this platform
```

**解决方案**：

添加平台检测：

```python
import platform

class MyNode(nndeploy.dag.Node):
    def __init__(self, name, inputs=None, outputs=None):
        super().__init__(name, inputs, outputs)
        self.is_android_ = platform.system() != "Windows" and \
                          platform.system() != "Linux"  # 简单检测
    
    def run(self):
        # 处理数据
        output_frame = self.process(input_frame)
        
        # 只在非Android平台显示
        if not self.is_android_:
            cv2.imshow(self.window_name_, output_frame)
            cv2.waitKey(1)
        
        # 总是输出数据
        self.get_output(0).set(output_frame)
        return nndeploy.base.Status.ok()
```

### Q4: 如何调试Android端的Python节点？

**方法1：使用日志**

```python
def run(self):
    print(f"[MyNode] 开始处理, 输入shape: {input_data.shape}")
    # 处理...
    print(f"[MyNode] 处理完成, 输出shape: {output_data.shape}")
    return nndeploy.base.Status.ok()
```

在Android端查看日志：
```bash
adb logcat | grep "MyNode"
```

**方法2：桌面端先测试**

在桌面端验证节点功能后再部署到Android：

```bash
# 桌面端测试
python -m nndeploy.server.app --port 8000
# 在Web界面测试工作流

# 确认无误后编译Android版本
```

### Q5: 模型文件路径问题？

**问题**：节点找不到模型文件

**原因**：Android端资源在外部存储，路径不同

**解决方案**：

在工作流JSON中使用相对路径：

```json
{
  "model_path_": "resources/models/my_model.pth"
}
```

Android端会自动替换为：

```
/storage/emulated/0/Android/data/com.nndeploy.app/files/resources/models/my_model.pth
```

代码实现（已在ImageInImageOut.kt中）：

```kotlin
val resolvedJson = rawJson.replace(
    "resources/", 
    "${extResDir.absolutePath}/"
)
```

### Q6: 多输入多输出节点如何定义？

**示例**：接收2个输入，输出3个结果

```python
class MultiIONode(nndeploy.dag.Node):
    def __init__(self, name, inputs=None, outputs=None):
        super().__init__(name, inputs, outputs)
        super().set_key("nndeploy.custom.MultiIONode")
        
        # 定义2个输入类型
        self.set_input_type(np.ndarray)  # 输入0
        self.set_input_type(np.ndarray)  # 输入1
        
        # 定义3个输出类型
        self.set_output_type(np.ndarray)  # 输出0
        self.set_output_type(dict)        # 输出1
        self.set_output_type(list)        # 输出2
    
    def run(self):
        # 获取2个输入
        input0 = self.get_input(0).get(self)
        input1 = self.get_input(1).get(self)
        
        # 处理...
        result0, result1, result2 = self.process(input0, input1)
        
        # 设置3个输出
        self.get_output(0).set(result0)
        self.get_output(1).set(result1)
        self.get_output(2).set(result2)
        
        return nndeploy.base.Status.ok()
```

JSON配置：

```json
{
  "key_": "nndeploy.custom.MultiIONode",
  "name_": "MultiIO_1",
  "inputs_": [
    {"name_": "Node1@output_0", "type_": "Mat"},
    {"name_": "Node2@output_0", "type_": "Mat"}
  ],
  "outputs_": [
    {"name_": "output_0", "type_": "Mat"},
    {"name_": "output_1", "type_": "Param"},
    {"name_": "output_2", "type_": "Param"}
  ]
}
```

---

## 最佳实践

### 1. 跨平台兼容性

```python
class CrossPlatformNode(nndeploy.dag.Node):
    def __init__(self, name, inputs=None, outputs=None):
        super().__init__(name, inputs, outputs)
        
        # 检测运行环境
        import sys
        self.is_mobile_ = 'android' in sys.platform.lower()
        
    def run(self):
        if self.is_mobile_:
            return self.run_mobile()
        else:
            return self.run_desktop()
    
    def run_mobile(self):
        """Android/iOS专用逻辑"""
        pass
    
    def run_desktop(self):
        """桌面端专用逻辑"""
        pass
```

### 2. 资源管理

```python
class ResourceManagedNode(nndeploy.dag.Node):
    def init(self):
        """初始化时加载资源"""
        self.model = self.load_model()
        return nndeploy.base.Status.ok()
    
    def deinit(self):
        """清理资源"""
        if hasattr(self, 'model'):
            del self.model
        return super().deinit()
```

### 3. 错误处理

```python
def run(self):
    try:
        input_data = self.get_input(0).get(self)
        
        if input_data is None:
            print("[Error] 输入数据为空")
            return nndeploy.base.Status(
                nndeploy.base.StatusCode.kStatusCodeErrorInvalidParam
            )
        
        output_data = self.process(input_data)
        self.get_output(0).set(output_data)
        
        return nndeploy.base.Status.ok()
        
    except Exception as e:
        print(f"[Error] 节点执行失败: {e}")
        import traceback
        traceback.print_exc()
        return nndeploy.base.Status(
            nndeploy.base.StatusCode.kStatusCodeErrorInvalidParam
        )
```

### 4. 性能优化

```python
class OptimizedNode(nndeploy.dag.Node):
    def init(self):
        # 预计算可重用的数据
        self.precomputed_data = self.precompute()
        
        # 预分配缓冲区
        self.buffer = np.zeros((1920, 1080, 3), dtype=np.uint8)
        
        return nndeploy.base.Status.ok()
    
    def run(self):
        # 复用预分配的缓冲区
        np.copyto(self.buffer, input_data)
        # 处理...
        return nndeploy.base.Status.ok()
```

---

## 总结

通过本指南，你应该能够：

1. ✅ **理解**原生节点和Python自定义节点在Android中的运行机制
2. ✅ **开发**跨平台的Python自定义节点
3. ✅ **部署**节点到Android应用
4. ✅ **调试**和优化节点性能
5. ✅ **解决**常见问题

关键要点：
- Python节点通过C++桥接在Android上运行
- 节点注册是自动的，只需正确导入模块
- JSON工作流是跨平台的
- 注意平台差异（如GUI功能）

有问题？查看：
- [Python自定义节点开发手册](../quick_start/plugin_python.md)
- [C++自定义节点开发手册](../quick_start/plugin.md)
- [Android README](../../app/android/README.md)
