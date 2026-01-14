# Gemma3 Plugin 编译完成总结

## 编译时间
2024年12月23日 15:57

## 编译成果

###成功编译的库文件
- **libnndeploy_plugin_gemma.so** (815KB)
  - 位置: `/Users/jin/work/nndeploy-1/build_android_arm64/libnndeploy_plugin_gemma.so`
  - 已复制到: `app/android/app/src/main/jniLibs/arm64-v8a/libnndeploy_plugin_gemma.so`
  - 平台: Android ARM64 (arm64-v8a)
  - 编译器: Clang (Android NDK)

## 插件功能

### Gemma3 模型适配器
专为 Google Gemma3-270M 模型设计的插件，包含:

1. **配置解析** (`parseConfig`)
   - 支持从 JSON 文件读取 Gemma3 模型配置
   - 参数: vocab_size=256000, hidden_size=2048, num_layers=18
   - KV cache形状: [18, 2, 1, 0, 4, 256] (18层, 4个KV头)

2. **参数类**
   - `Gemma3PromptParam`: Gemma3 提示词模板参数
     - 默认模板: `<start_of_turn>user\n%s<end_of_turn>\n<start_of_turn>model\n`
   - `Gemma3EmbeddingParam`: 嵌入层参数
     - 支持嵌入权重加载
     - KV cache初始化配置

3. **DAG节点**
   - `Gemma3PromptNode`: 提示词格式化节点 (当前为存根实现)
   - `Gemma3EmbeddingNode`: 嵌入生成节点 (当前为存根实现)

## 代码结构

```
plugin/
├── include/nndeploy/gemma/
│   └── gemma.h                    # Gemma3 插件头文件
└── source/nndeploy/gemma/
    ├── gemma.cc                   # Gemma3 插件实现
    └── config.cmake               # CMake 构建配置
```

## 构建配置修改

### 1. plugin/config.cmake
添加了 Gemma3 插件构建规则:
```cmake
# # gemma3
if(ENABLE_NNDEPLOY_PLUGIN_GEMMA)
  add_definitions(-DENABLE_NNDEPLOY_PLUGIN_GEMMA)
  include(${PLUGIN_ROOT_PATH}/source/nndeploy/gemma/config.cmake)
endif()
```

### 2. plugin/source/nndeploy/force_link.cc
添加了强制链接符号:
```cpp
#ifdef ENABLE_NNDEPLOY_PLUGIN_GEMMA
#include "nndeploy/gemma/gemma.h"
NNDEPLOY_FORCE_LOAD_LIB_SYMBOL(nndeploy::gemma::Gemma3PromptParam);
#endif
```

### 3. build_android_arm64/config.cmake
启用了 Gemma3 插件:
```cmake
## Gemma3 Model
set(ENABLE_NNDEPLOY_PLUGIN_GEMMA ON) # Whether to enable Gemma3 model plugin, default is OFF
```

## 编译命令

```bash
cd /Users/jin/work/nndeploy-1/build_android_arm64

# 1. 清理旧缓存
rm -rf CMakeCache.txt CMakeFiles

# 2. 使用 Android 工具链重新配置
cmake .. \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-24 \
  -DCMAKE_BUILD_TYPE=Release

# 3. 编译 Gemma3 插件
make nndeploy_plugin_gemma -j8
```

## 依赖库

Gemma3 插件依赖以下组件:
- **nndeploy_framework**: 核心框架
- **nndeploy_plugin_preprocess**: 预处理插件 (可选)
- **nndeploy_plugin_infer**: 推理插件 (可选)
- **nndeploy_plugin_tokenizer**: 分词器插件 (可选)

## 当前实现状态

### ✅ 已完成
- [x] 插件项目结构创建
- [x] CMake 构建配置
- [x] 头文件定义 (gemma.h)
- [x] 基础实现 (gemma.cc)
- [x] 配置解析函数
- [x] 参数序列化/反序列化
- [x] 编译成功 (libnndeploy_plugin_gemma.so)
- [x] 部署到 Android 项目

### ⚠️ 存根实现
以下功能目前为存根实现 (编译通过但功能未实现):
- `Gemma3PromptNode::run()` - 仅输出日志
- `Gemma3EmbeddingNode::init()` - 仅输出日志
- `Gemma3EmbeddingNode::run()` - 仅输出日志

### 🔧 待完善
- [ ] 实现完整的 Prompt 节点逻辑
- [ ] 实现完整的 Embedding 节点逻辑
- [ ] 添加 KV cache 管理
- [ ] 添加位置编码 (RoPE)
- [ ] 添加注意力掩码生成
- [ ] 与 LlmInfer 节点集成
- [ ] 测试完整推理流程

## 下一步计划

### 方案 A: 使用存根插件测试
可以先使用当前存根实现配合 `gemma3_simple.json` 测试:
- gemma3_simple.json 使用直接 ONNX Runtime 推理
- 不依赖 Gemma3 模型适配器
- 可以验证基础推理流程

### 方案 B: 完善插件实现
参考 `plugin/source/nndeploy/qwen/qwen.cc` 完整实现:
1. 实现 `Gemma3PromptNode`
   - 格式化用户输入为 Gemma3 模板
   - 输出到 DAG Buffer
   
2. 实现 `Gemma3EmbeddingNode`
   - 加载嵌入权重
   - Token ID → Embedding 转换
   - 初始化 KV cache
   - 生成位置 ID 和注意力掩码

3. 创建 `Gemma3LlmInfer` 节点
   - 继承 `llm::LlmInfer`
   - 实现 Gemma3 特定的推理逻辑
   - 管理 Prefill 和 Decode 阶段

## 测试建议

### 快速测试 (使用 gemma3_simple.json)
```bash
cd app/android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat | grep -E "(Gemma3|gemma3_simple|init finish)"
```

### 完整测试 (使用 gemma3demo.json)
需要先完善插件实现，然后:
1. 修改 `gemma3demo.json` 中的 `model_key` 为 `"gemma3"`
2. 重新编译 Android APK
3. 在设备上测试完整 Prefill/Decode 流程

## 遇到的问题与解决

### 问题 1: CMake 缓存路径不匹配
**错误**: `The current CMakeCache.txt directory is different than the directory where CMakeCache.txt was created`
**解决**: 删除 CMakeCache.txt 和 CMakeFiles 目录，重新配置

### 问题 2: OpenCV 库后缀错误
**错误**: `No rule to make target libopencv_java4.dylib`
**原因**: CMake 检测到主机为 macOS，使用了 .dylib 后缀而非 Android 的 .so
**解决**: 使用 Android 工具链正确配置: `-DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake`

### 问题 3: 头文件缺失
**错误**: `'nndeploy/base/json.h' file not found`
**原因**: 参考了错误的包含头文件
**解决**: 使用正确的头文件包含顺序 (参考 qwen.cc)

### 问题 4: API 不匹配
**错误**: `no member named 'Buffer' in namespace 'nndeploy::dag'`
**原因**: 自定义实现与实际 API 不符
**解决**: 简化为存根实现，先编译通过

## 编译日志摘要

```
[  2%] Built target tokenizers_c
[ 22%] Built target sentencepiece-static
[ 24%] Built target tokenizers_cpp
[ 90%] Built target nndeploy_framework
[ 90%] Built target nndeploy_plugin_infer
[ 92%] Built target nndeploy_plugin_tokenizer
[ 98%] Built target nndeploy_plugin_preprocess
[100%] Building CXX object CMakeFiles/nndeploy_plugin_gemma.dir/plugin/source/nndeploy/gemma/gemma.cc.o
[100%] Linking CXX shared library libnndeploy_plugin_gemma.so
[100%] Built target nndeploy_plugin_gemma
```

## Android 项目集成

### JNI 库列表
```
app/src/main/jniLibs/arm64-v8a/
├── libnndeploy_framework.so
├── libnndeploy_jni.so
├── libnndeploy_plugin_codec.so
├── libnndeploy_plugin_gemma.so       # ← 新增
├── libnndeploy_plugin_infer.so
├── libnndeploy_plugin_llm.so
├── libnndeploy_plugin_preprocess.so
├── libnndeploy_plugin_qwen.so
├── libnndeploy_plugin_tokenizer.so
├── libopencv_java4.so
├── libsentencepiece.so
└── libtokenizers_cpp.so
```

### Gradle 配置
插件会通过 JNI 自动加载，无需修改 Gradle 配置。

## 总结

✅ **编译成功**: Gemma3 插件已成功编译并部署到 Android 项目

⚠️ **功能状态**: 当前为存根实现，可编译但功能未完整实现

📝 **推荐方案**: 
- **短期**: 使用 gemma3_simple.json (直接 ONNX Runtime)
- **长期**: 参考 Qwen 插件完善 Gemma3 实现

🔧 **下一步**: 
1. 测试 gemma3_simple.json 工作流
2. 根据测试结果决定是否需要完善插件实现
3. 如需完整实现，参考 qwen.cc 的 1154 行代码逐步完善

## 参考文档
- Qwen 插件实现: `plugin/source/nndeploy/qwen/qwen.cc`
- DAG 节点定义: `framework/include/nndeploy/dag/node.h`
- 设备张量 API: `framework/include/nndeploy/device/tensor.h`
- Android 构建脚本: `build_android_arm64/config.cmake`
