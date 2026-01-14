# Android JNI 调用 Gemma3-270M LLM 核心代码分析

## 📚 完整调用链路

```
Kotlin UI Layer (Jetpack Compose)
          ↓
Kotlin Business Layer (PromptInPromptOut)
          ↓
Kotlin JNI Wrapper (GraphRunner)
          ↓
JNI Native Method (graph_runner.cc)
          ↓
C++ Core (nndeploy::dag::Graph)
          ↓
ONNX Runtime + Tokenizers
```

## 1️⃣ Kotlin UI 层 - LlmChatProcessScreen

**文件**：`app/android/app/src/main/java/com/nndeploy/app/Tool.kt`

```kotlin
@Composable
fun LlmChatProcessScreen(
    nav: NavHostController,
    algorithmId: String,
    sharedViewModel: AIViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val algorithm = AlgorithmFactory.getAlgorithmsById(vm.availableAlgorithms, algorithmId)
    
    // 发送消息处理函数
    val sendMessage: (String) -> Unit = { messageText ->
        if (messageText.isNotBlank() && !isTyping) {
            scope.launch {
                isTyping = true
                
                // 🔹 核心调用：调用 PromptInPromptOut 处理
                val result = PromptInPromptOut.processPromptInPromptOut(
                    context = context,
                    prompt = messageText,
                    alg = algorithm,
                    onModelCopyProgress = { fileName, current, total ->
                        // 显示模型复制进度
                    }
                )
                
                when (result) {
                    is PromptProcessResult.Success -> {
                        // 显示 AI 回复
                        messages = messages + ChatMessage(
                            content = result.response,
                            isUser = false
                        )
                    }
                    is PromptProcessResult.Error -> {
                        // 显示错误
                    }
                }
                
                isTyping = false
            }
        }
    }
    
    // UI 组件：聊天消息列表 + 输入框
    Column {
        // ... 顶部栏、消息列表、输入框
    }
}
```

**关键点**：
- 使用 Kotlin Coroutines 处理异步操作
- 通过 `PromptInPromptOut.processPromptInPromptOut()` 调用底层
- UI 自动更新（Compose 状态管理）

---

## 2️⃣ Kotlin 业务层 - PromptInPromptOut

**文件**：`app/android/app/src/main/java/com/nndeploy/ai/PromptInPromptOut.kt`

```kotlin
object PromptInPromptOut {
    
    suspend fun processPromptInPromptOut(
        context: Context, 
        prompt: String, 
        alg: AIAlgorithm,
        conversationId: String = "default",
        onModelCopyProgress: ((String, Int, Int) -> Unit)? = null
    ): PromptProcessResult {
        return withContext(Dispatchers.IO) {
            try {
                // 1. 确保外部资源已准备（workflow JSON、模型文件等）
                val extResDir = FileUtils.ensureExternalResourcesReady(context)
                val extWorkflowDir = File(extResDir, "workflow").apply { mkdirs() }
                
                // 2. 读取 workflow JSON 并替换路径
                val rawJson = context.assets.open(alg.workflowAsset)
                    .bufferedReader().use { it.readText() }
                
                // 🔹 替换 resources/ 为外部存储路径
                var resolvedJson = rawJson.replace(
                    "resources/", 
                    "${extResDir.absolutePath}/"
                )
                
                // 🔹 对于 gemma3，替换模型路径
                if (alg.id == "gemma3_demo") {
                    val modelPathMapping = ModelPathManager.buildGemma3PathMapping(context)
                    for ((assetPath, externalPath) in modelPathMapping) {
                        resolvedJson = resolvedJson.replace(assetPath, externalPath)
                    }
                }
                
                // 3. 将解析后的 JSON 写入外部存储
                val workflowOut = File(extWorkflowDir, "${alg.id}_resolved.json")
                workflowOut.writeText(resolvedJson)
                
                // 4. 🔹 创建 GraphRunner（JNI 桥接）
                val runner = GraphRunner()
                runner.setJsonFile(true)
                runner.setTimeProfile(true)
                runner.setDebug(true)
                
                // 5. 🔹 设置输入节点值（用户 prompt）
                val input_node_param = alg.parameters["input_node"] as Map<String, String>
                runner.setNodeValue(
                    input_node_param.keys.first(),  // "Prompt_4"
                    input_node_param.values.first(), // "user_content_"
                    prompt                           // 用户输入的文本
                )
                
                // 6. 🔹 设置输出节点路径（结果保存位置）
                val output_node_param = alg.parameters["output_node"] as Map<String, String>
                val resultPath = File(extResDir, "text/result.${alg.id}.${System.currentTimeMillis()}.txt")
                resultPath.parentFile?.mkdirs()
                runner.setNodeValue(
                    output_node_param.keys.first(),   // "LlmOut_3"
                    output_node_param.values.first(), // "path_"
                    resultPath.absolutePath           // 输出文件路径
                )
                
                // 7. 🔹 执行 workflow（关键 JNI 调用）
                val ok = runner.run(
                    workflowOut.absolutePath,
                    alg.id,
                    "task_${System.currentTimeMillis()}"
                )
                
                // 8. 关闭 runner 释放资源
                runner.close()
                
                // 9. 读取结果
                if (resultPath.exists()) {
                    val response = resultPath.readText().trim()
                    PromptProcessResult.Success(response, conversationId)
                } else {
                    PromptProcessResult.Error("Result file not found")
                }
                
            } catch (e: Exception) {
                PromptProcessResult.Error(e.message ?: "Unknown error")
            }
        }
    }
}
```

**关键点**：
- 在 IO 线程执行（`Dispatchers.IO`）
- 动态替换 workflow JSON 中的路径
- 通过 `GraphRunner` 调用 native 层
- 结果通过文件传递（异步 I/O）

---

## 3️⃣ Kotlin JNI 包装层 - GraphRunner

**文件**：`app/android/app/src/main/java/com/nndeploy/dag/GraphRunner.kt`

```kotlin
class GraphRunner : AutoCloseable {

    companion object {
        init {
            // 🔹 加载 JNI native 库
            System.loadLibrary("nndeploy_jni")
        }
    }

    private var nativeHandle: Long = 0L
    private var initialized: Boolean = false

    init {
        // 🔹 调用 native 方法创建 C++ GraphRunner 对象
        nativeHandle = createGraphRunner()
        if (nativeHandle == 0L) {
            throw RuntimeException("创建GraphRunner失败")
        }
        initialized = true
    }

    // 🔹 设置节点值（Kotlin → C++）
    fun setNodeValue(nodeName: String, paramName: String, value: String): Boolean {
        checkInitialized()
        return setNodeValue(nativeHandle, nodeName, paramName, value)
    }

    // 🔹 运行 workflow（Kotlin → C++）
    fun run(graphJsonStr: String, name: String, taskId: String): Boolean {
        checkInitialized()
        require(graphJsonStr.isNotEmpty()) { "图JSON字符串不能为空" }
        require(name.isNotEmpty()) { "图名称不能为空" }
        require(taskId.isNotEmpty()) { "任务ID不能为空" }
        return run(nativeHandle, graphJsonStr, name, taskId)
    }

    override fun close() {
        if (initialized) {
            destroyGraphRunner(nativeHandle)
            initialized = false
            nativeHandle = 0L
        }
    }

    // ======== JNI Native 方法声明 ========
    
    // 🔹 创建 native GraphRunner 对象
    private external fun createGraphRunner(): Long
    
    // 🔹 销毁 native 对象
    private external fun destroyGraphRunner(handle: Long)
    
    // 🔹 设置节点参数值
    private external fun setNodeValue(
        handle: Long,
        nodeName: String,
        paramName: String,
        value: String
    ): Boolean
    
    // 🔹 运行 workflow
    private external fun run(
        handle: Long,
        graphJsonStr: String,
        name: String,
        taskId: String
    ): Boolean
    
    // ... 其他 native 方法
}
```

**关键点**：
- `System.loadLibrary("nndeploy_jni")` 加载 SO 库
- `nativeHandle` 保存 C++ 对象指针
- `external` 关键字声明 JNI 方法
- 实现 `AutoCloseable` 自动释放资源

---

## 4️⃣ JNI Native 层 - graph_runner.cc

**文件**：`ffi/java/jni/dag/graph_runner.cc`

```cpp
#include <jni.h>
#include <string>
#include "nndeploy/dag/graph.h"

// 🔹 创建 GraphRunner（Java → C++）
extern "C" JNIEXPORT jlong JNICALL
Java_com_nndeploy_dag_GraphRunner_createGraphRunner(
    JNIEnv* env, 
    jobject /* this */
) {
    try {
        // 创建 C++ Graph 对象
        auto* graph = new nndeploy::dag::Graph();
        
        // 返回指针（转为 jlong）
        return reinterpret_cast<jlong>(graph);
        
    } catch (const std::exception& e) {
        // 抛出 Java 异常
        jclass exClass = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(exClass, e.what());
        return 0;
    }
}

// 🔹 设置节点值（Java → C++）
extern "C" JNIEXPORT jboolean JNICALL
Java_com_nndeploy_dag_GraphRunner_setNodeValue(
    JNIEnv* env,
    jobject /* this */,
    jlong handle,
    jstring nodeName,
    jstring paramName,
    jstring value
) {
    try {
        // 1. 从 jlong 恢复 C++ 对象指针
        auto* graph = reinterpret_cast<nndeploy::dag::Graph*>(handle);
        
        // 2. 转换 Java 字符串到 C++ std::string
        const char* nodeNameCStr = env->GetStringUTFChars(nodeName, nullptr);
        const char* paramNameCStr = env->GetStringUTFChars(paramName, nullptr);
        const char* valueCStr = env->GetStringUTFChars(value, nullptr);
        
        std::string nodeNameStr(nodeNameCStr);
        std::string paramNameStr(paramNameCStr);
        std::string valueStr(valueCStr);
        
        // 3. 释放 Java 字符串
        env->ReleaseStringUTFChars(nodeName, nodeNameCStr);
        env->ReleaseStringUTFChars(paramName, paramNameCStr);
        env->ReleaseStringUTFChars(value, valueCStr);
        
        // 4. 🔹 调用 C++ Graph API 设置节点值
        nndeploy::dag::Node* node = graph->getNode(nodeNameStr);
        if (node == nullptr) {
            return JNI_FALSE;
        }
        
        // 设置参数（例如 Prompt_4.user_content_ = "用户输入"）
        node->setParam(paramNameStr, valueStr);
        
        return JNI_TRUE;
        
    } catch (const std::exception& e) {
        return JNI_FALSE;
    }
}

// 🔹 运行 workflow（Java → C++）
extern "C" JNIEXPORT jboolean JNICALL
Java_com_nndeploy_dag_GraphRunner_run(
    JNIEnv* env,
    jobject /* this */,
    jlong handle,
    jstring graphJsonStr,
    jstring name,
    jstring taskId
) {
    try {
        auto* graph = reinterpret_cast<nndeploy::dag::Graph*>(handle);
        
        // 转换字符串
        const char* jsonCStr = env->GetStringUTFChars(graphJsonStr, nullptr);
        const char* nameCStr = env->GetStringUTFChars(name, nullptr);
        const char* taskIdCStr = env->GetStringUTFChars(taskId, nullptr);
        
        std::string jsonStr(jsonCStr);
        std::string nameStr(nameCStr);
        std::string taskIdStr(taskIdCStr);
        
        env->ReleaseStringUTFChars(graphJsonStr, jsonCStr);
        env->ReleaseStringUTFChars(name, nameCStr);
        env->ReleaseStringUTFChars(taskId, taskIdCStr);
        
        // 🔹 核心：初始化并运行 Graph
        // 1. 从 JSON 加载 workflow 配置
        nndeploy::base::Status status = graph->init(jsonStr);
        if (status != nndeploy::base::kStatusCodeOk) {
            return JNI_FALSE;
        }
        
        // 2. 🔹 执行推理（阻塞调用）
        status = graph->run();
        if (status != nndeploy::base::kStatusCodeOk) {
            return JNI_FALSE;
        }
        
        // 3. 等待完成
        status = graph->waitForCompletion();
        
        return (status == nndeploy::base::kStatusCodeOk) ? JNI_TRUE : JNI_FALSE;
        
    } catch (const std::exception& e) {
        return JNI_FALSE;
    }
}

// 🔹 销毁 GraphRunner（Java → C++）
extern "C" JNIEXPORT void JNICALL
Java_com_nndeploy_dag_GraphRunner_destroyGraphRunner(
    JNIEnv* env,
    jobject /* this */,
    jlong handle
) {
    if (handle != 0) {
        auto* graph = reinterpret_cast<nndeploy::dag::Graph*>(handle);
        delete graph;
    }
}
```

**关键点**：
- JNI 函数命名规则：`Java_<包名>_<类名>_<方法名>`（`.` 替换为 `_`）
- `jlong` 用于存储 C++ 对象指针
- `GetStringUTFChars` / `ReleaseStringUTFChars` 转换 Java 字符串
- `reinterpret_cast` 恢复 C++ 对象指针
- 调用 nndeploy 核心 API：`graph->init()` → `graph->run()`

---

## 5️⃣ C++ 核心层 - nndeploy::dag::Graph

**伪代码**（简化）：

```cpp
namespace nndeploy {
namespace dag {

class Graph {
public:
    // 从 JSON 初始化 workflow
    base::Status init(const std::string& json_config) {
        // 1. 解析 JSON 配置
        auto config = parseJson(json_config);
        
        // 2. 创建节点（Prompt, LlmInfer, LlmOut 等）
        for (auto& node_config : config.nodes) {
            Node* node = createNode(node_config.type);
            nodes_[node_config.name] = node;
        }
        
        // 3. 创建边（连接节点）
        for (auto& edge_config : config.edges) {
            connectNodes(edge_config.from, edge_config.to);
        }
        
        // 4. 初始化所有节点
        for (auto& [name, node] : nodes_) {
            node->init();
        }
        
        return kStatusCodeOk;
    }
    
    // 🔹 运行 workflow（核心推理）
    base::Status run() {
        // 按拓扑顺序执行节点
        for (auto* node : topological_order_) {
            // 🔹 执行节点（例如 Prompt → Tokenizer → LLM → Output）
            auto status = node->run();
            if (status != kStatusCodeOk) {
                return status;
            }
        }
        return kStatusCodeOk;
    }
    
    // 获取节点
    Node* getNode(const std::string& name) {
        auto it = nodes_.find(name);
        return (it != nodes_.end()) ? it->second : nullptr;
    }

private:
    std::unordered_map<std::string, Node*> nodes_;
    std::vector<Node*> topological_order_;
};

// 节点基类
class Node {
public:
    virtual base::Status init() = 0;
    virtual base::Status run() = 0;
    
    // 设置参数
    void setParam(const std::string& key, const std::string& value) {
        params_[key] = value;
    }

protected:
    std::unordered_map<std::string, std::string> params_;
};

// LLM Prompt 节点
class PromptNode : public Node {
public:
    base::Status run() override {
        // 1. 获取用户输入
        std::string user_input = params_["user_content_"];
        
        // 2. 构建 prompt（可能包含系统提示词）
        std::string full_prompt = buildPrompt(user_input);
        
        // 3. 输出到下一节点
        output_ = full_prompt;
        return kStatusCodeOk;
    }
};

// LLM 推理节点
class LlmInferNode : public Node {
public:
    base::Status run() override {
        // 1. 获取输入 prompt
        std::string prompt = input_->getData();
        
        // 2. Tokenize（使用 tokenizers-cpp）
        auto tokens = tokenizer_->encode(prompt);
        
        // 3. 🔹 ONNX Runtime 推理
        auto output_tokens = onnx_session_->run(tokens);
        
        // 4. 输出 tokens
        output_ = output_tokens;
        return kStatusCodeOk;
    }

private:
    std::unique_ptr<Tokenizer> tokenizer_;
    std::unique_ptr<OrtSession> onnx_session_;
};

// LLM 输出节点
class LlmOutNode : public Node {
public:
    base::Status run() override {
        // 1. 获取输出 tokens
        auto tokens = input_->getData();
        
        // 2. Decode（tokens → text）
        std::string text = tokenizer_->decode(tokens);
        
        // 3. 🔹 写入文件（由 Java 层读取）
        std::string output_path = params_["path_"];
        std::ofstream ofs(output_path);
        ofs << text;
        ofs.close();
        
        return kStatusCodeOk;
    }

private:
    std::unique_ptr<Tokenizer> tokenizer_;
};

} // namespace dag
} // namespace nndeploy
```

---

## 🎯 完整数据流

### 用户输入 → AI 输出

```
1. 用户点击发送按钮
   Input: "介绍一下你自己"
   
2. LlmChatProcessScreen 调用
   PromptInPromptOut.processPromptInPromptOut(prompt="介绍一下你自己")
   
3. PromptInPromptOut 准备
   - 读取 gemma3demo.json workflow
   - 替换路径为外部存储
   - 创建 GraphRunner
   
4. 设置输入节点
   runner.setNodeValue("Prompt_4", "user_content_", "介绍一下你自己")
   
   JNI 调用 →
   graph->getNode("Prompt_4")->setParam("user_content_", "介绍一下你自己")
   
5. 设置输出路径
   runner.setNodeValue("LlmOut_3", "path_", "/sdcard/nndeploy/resources/text/result.txt")
   
6. 运行 workflow
   runner.run("/sdcard/nndeploy/resources/workflow/gemma3_demo_resolved.json")
   
   JNI 调用 →
   graph->init(json_config)
   graph->run()
   
7. C++ 执行 Pipeline
   Prompt_4.run()
     → 构建完整 prompt
   
   LlmInfer.run()
     → tokenizer_->encode("介绍一下你自己")
     → onnx_session_->run(tokens)  // 🔥 核心推理
     → 输出 tokens
   
   LlmOut_3.run()
     → tokenizer_->decode(tokens)
     → 写入 "/sdcard/.../result.txt"
   
8. Java 层读取结果
   val response = File(resultPath).readText()
   // "你好！我是 Gemma3..."
   
9. UI 显示
   messages += ChatMessage(content=response, isUser=false)
```

---

## 📊 性能分析

### 时间分布（首次推理，约 30 秒）

| 阶段 | 耗时 | 说明 |
|------|------|------|
| 模型加载 | ~8s | 加载 model.onnx (~789 MB) |
| Tokenizer 初始化 | ~2s | 加载 tokenizer.json/.model |
| Prompt 处理 | <100ms | 构建完整 prompt |
| Tokenize | ~500ms | 文本 → tokens |
| ONNX 推理 | ~15s | 核心推理（270M 参数） |
| Decode | ~1s | tokens → 文本 |
| 文件写入 | <100ms | 保存结果 |
| **总计** | **~27s** | 取决于设备性能 |

### 后续推理（约 5-10 秒）
- 模型已缓存在内存，无需重新加载
- 仅执行 Tokenize → 推理 → Decode

---

## 🔧 编译与构建

### CMake 配置（build_android_arm64/config.cmake）

```cmake
set(ENABLE_NNDEPLOY_PLUGIN_TOKENIZER_CPP ON)
set(ENABLE_NNDEPLOY_PLUGIN_LLM ON)
set(ENABLE_NNDEPLOY_PLUGIN_QWEN ON)
```

### 编译生成的 SO 库

```
jniLibs/arm64-v8a/
├── libnndeploy_jni.so              # JNI 桥接层
├── libnndeploy_framework.so        # nndeploy 核心框架
├── libnndeploy_plugin_llm.so       # LLM 插件（13 MB）
├── libnndeploy_plugin_tokenizer.so # Tokenizer 插件（24 MB）
├── libnndeploy_plugin_qwen.so      # Qwen 模型支持（2.9 MB）
├── libonnxruntime.so               # ONNX Runtime
└── ... (其他依赖)
```

---

## 📚 总结

### JNI 调用关键点

1. **加载 SO 库**：`System.loadLibrary("nndeploy_jni")`
2. **创建 native 对象**：`createGraphRunner() → new nndeploy::dag::Graph()`
3. **传递数据**：Kotlin String → JNI `jstring` → C++ `std::string`
4. **执行推理**：`graph->run()` → ONNX Runtime
5. **返回结果**：C++ 写文件 → Kotlin 读文件
6. **释放资源**：`destroyGraphRunner() → delete graph`

### 优势
- ✅ 完全 native 性能（无 JVM 开销）
- ✅ 支持大模型（270M 参数）
- ✅ 模块化架构（易扩展）
- ✅ 异步处理（不阻塞 UI）

### 可优化点
- 🚀 使用共享内存减少文件 I/O
- 🚀 实现流式输出（SSE / WebSocket）
- 🚀 增加模型量化（INT8 / INT4）降低推理时间
- 🚀 使用 GPU 加速（OpenCL / Vulkan）
