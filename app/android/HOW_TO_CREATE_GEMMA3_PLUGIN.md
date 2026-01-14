# 如何为 Gemma3 创建专用插件

## 🎯 为什么需要 Gemma3 插件

当前项目有：
- `libnndeploy_plugin_qwen.so` - Qwen 模型专用适配器
- `libnndeploy_plugin_llm.so` - 通用 LLM 基础设施

但 `gemma3demo.json` 错误地使用了 Qwen 适配器：
```json
"model_key": "Qwen",  // ← 这会导致崩溃！
```

## ✅ 方案 A：使用通用 ONNX Runtime（推荐）

**无需编译插件**，直接使用 `nndeploy::inference::OnnxRuntimeInference` 节点。

我已经修改了 `gemma3_simple.json`，改用：
- `OnnxRuntimeInference`（直接 ONNX 推理，无需模型适配器）
- `Sampler`（采样器，从 logits 生成 token）

**新架构**：
```
Prompt_1 → TokenizerEncode_2 → OnnxInfer_3 → Sampler_3b → TokenizerDecode_4 → LlmOut_5
```

**优势**：
- ✅ 不依赖模型特定适配器
- ✅ 直接用 ONNX Runtime
- ✅ 无需重新编译 C++
- ✅ 更通用，支持任意 ONNX 模型

## 🔧 方案 B：编译 Gemma3 插件（完整方案）

如果方案 A 不满足需求，需要创建专用插件。

### 步骤 1：创建 Gemma3 模型适配器

参考 Qwen 插件的结构，创建 Gemma3 适配器：

**文件结构**：
```
plugin/source/nndeploy/model/gemma/
├── gemma.h
├── gemma.cc
├── gemma_config.h
└── gemma_op.cc
```

**gemma.h** 示例：
```cpp
#ifndef _NNDEPLOY_MODEL_GEMMA_GEMMA_H_
#define _NNDEPLOY_MODEL_GEMMA_GEMMA_H_

#include "nndeploy/model/llm/llm.h"

namespace nndeploy {
namespace model {

class Gemma3Config : public llm::LlmConfig {
 public:
  Gemma3Config() {
    vocab_size_ = 256000;
    hidden_size_ = 2048;
    intermediate_size_ = 16384;
    num_hidden_layers_ = 18;  // Gemma3-270M 的层数
    num_attention_heads_ = 8;
    num_key_value_heads_ = 4;
    head_dim_ = 256;
    max_position_embeddings_ = 8192;
    rms_norm_eps_ = 1e-6;
    rope_theta_ = 10000.0;
  }
};

class Gemma3 : public llm::Llm {
 public:
  Gemma3() : llm::Llm() {}
  virtual ~Gemma3() {}

  virtual base::Status init() override;
  virtual base::Status deinit() override;
  virtual base::Status run() override;

 protected:
  // Gemma3 特定的前向传播逻辑
  base::Status forward(device::Tensor* input_ids,
                       device::Tensor* attention_mask,
                       device::Tensor* position_ids,
                       std::vector<device::Tensor*>& past_key_values);
};

}  // namespace model
}  // namespace nndeploy

#endif
```

**gemma.cc** 核心实现：
```cpp
#include "nndeploy/model/gemma/gemma.h"

namespace nndeploy {
namespace model {

base::Status Gemma3::init() {
  // 1. 加载配置
  config_ = std::make_shared<Gemma3Config>();
  
  // 2. 初始化推理引擎
  inference_param_.model_value_ = model_path_;
  inference_ = inference::createInference(inference_param_);
  
  // 3. 初始化 KV Cache
  kv_cache_.resize(config_->num_hidden_layers_);
  for (int i = 0; i < config_->num_hidden_layers_; ++i) {
    // Gemma3 的 KV Cache 形状：[batch, num_kv_heads, seq_len, head_dim]
    auto shape = {1, 4, 0, 256};  // 初始 seq_len=0
    kv_cache_[i].key = device::Tensor(shape, base::dataTypeOf<float>());
    kv_cache_[i].value = device::Tensor(shape, base::dataTypeOf<float>());
  }
  
  return base::kStatusCodeOk;
}

base::Status Gemma3::run() {
  // 1. 准备输入
  auto input_ids = getInput(0);
  
  // 2. 构建 attention_mask 和 position_ids
  // Gemma3 特定的 mask 和位置编码逻辑
  // ...
  
  // 3. 前向传播
  auto status = forward(input_ids, attention_mask, position_ids, kv_cache_);
  
  // 4. 获取输出 logits
  auto logits = getOutput(0);
  
  return status;
}

// 注册 Gemma3 模型
NNDEPLOY_MODEL_REGISTER(Gemma3, "gemma3");

}  // namespace model
}  // namespace nndeploy
```

### 步骤 2：更新 CMake 配置

**plugin/CMakeLists.txt**：
```cmake
# 添加 Gemma3 插件选项
option(ENABLE_NNDEPLOY_PLUGIN_GEMMA "Enable Gemma3 plugin" ON)

if(ENABLE_NNDEPLOY_PLUGIN_GEMMA)
  # 收集 Gemma3 源文件
  file(GLOB_RECURSE GEMMA_SOURCE
    ${ROOT_PATH}/plugin/source/nndeploy/model/gemma/*.cc
  )
  
  # 创建 Gemma3 插件库
  add_library(nndeploy_plugin_gemma SHARED ${GEMMA_SOURCE})
  
  # 链接依赖
  target_link_libraries(nndeploy_plugin_gemma
    nndeploy_framework
    nndeploy_plugin_llm
  )
  
  # 安装
  install(TARGETS nndeploy_plugin_gemma
    LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR}
  )
endif()
```

### 步骤 3：强制链接（避免符号被优化掉）

**plugin/source/nndeploy/force_link.cc**：
```cpp
#ifdef ENABLE_NNDEPLOY_PLUGIN_GEMMA
#include "nndeploy/model/gemma/gemma.h"
extern void forceGemmaLinking() {
  // 强制链接 Gemma3 模型注册
  nndeploy::model::Gemma3* dummy = nullptr;
  (void)dummy;
}
#endif
```

### 步骤 4：编译 Android 版本

```bash
cd /Users/jin/work/nndeploy-1
cd build_android_arm64

# 启用 Gemma3 插件
echo "set(ENABLE_NNDEPLOY_PLUGIN_GEMMA ON)" >> config.cmake

# 重新编译
cmake -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
      -DANDROID_ABI=arm64-v8a \
      -DANDROID_PLATFORM=android-24 \
      ..
      
make -j$(nproc)

# 生成的库：libnndeploy_plugin_gemma.so
```

### 步骤 5：复制到 Android 项目

```bash
cp build_android_arm64/libnndeploy_plugin_gemma.so \
   app/android/app/src/main/jniLibs/arm64-v8a/
```

### 步骤 6：更新 workflow 配置

修改 `gemma3demo.json`：
```json
{
  "key_": "nndeploy::llm::LlmInfer",
  "name_": "prefill_infer",
  "is_composite_node_": true,
  "is_prefill": true,
  "model_key": "gemma3",  // ← 改为 gemma3！
  "infer_key": "DefaultLlmInfer",
  "param_": {
    "inference_type_": "kInferenceTypeOnnxRuntime",
    "layer_nums_": 18,
    "kv_init_shape_": [18, 2, 1, 0, 4, 256],  // Gemma3 的 KV shape
    "max_seq_len_": 8192
  }
}
```

## 📊 两种方案对比

| 特性 | 方案 A (通用 ONNX) | 方案 B (专用插件) |
|------|-------------------|-------------------|
| 开发难度 | ⭐ 简单 | ⭐⭐⭐⭐⭐ 复杂 |
| 编译时间 | 0（无需编译） | ~10 分钟 |
| 灵活性 | 高（支持任意模型） | 中（仅 Gemma3） |
| 性能 | 良好 | 最优 |
| 维护成本 | 低 | 高 |
| 推荐场景 | **日常使用** | 生产环境 |

## 🎯 推荐方案

**先尝试方案 A**（我已修改了 `gemma3_simple.json`）：

```bash
cd /Users/jin/work/nndeploy-1/app/android
./test_gemma3_simple.sh
```

如果方案 A 失败，再考虑方案 B。

## 🔍 验证新 workflow

查看日志应该看到：
```
Prompt_1 init start.
Prompt_1 init finish.           ✓
TokenizerEncode_2 init start.
TokenizerEncode_2 init finish.  ✓
OnnxInfer_3 init start.         ← 直接 ONNX 推理
OnnxInfer_3 init finish.        ✓
Sampler_3b init start.
Sampler_3b init finish.         ✓
TokenizerDecode_4 init start.
TokenizerDecode_4 init finish.  ✓
LlmOut_5 init start.
LlmOut_5 init finish.           ✓
```

**不应该出现**：
- ❌ `Prefill_1 init start`（已移除嵌套子图）
- ❌ `model_key: Qwen`（不再使用 Qwen 适配器）
- ❌ `pthread_mutex_lock` 错误

---

现在请测试修改后的 `gemma3_simple`，如果仍然崩溃，贴出新日志我继续分析。
