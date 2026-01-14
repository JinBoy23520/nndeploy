# Gemma3-270M Chat Demo 使用指南

## 📱 如何使用 Gemma3 Chat

### 方式 1：从 AI 列表启动

1. 打开 nndeploy Android 应用
2. 在底部导航栏点击 **"AI Tools"**
3. 在算法列表中找到并点击 **"Gemma3 Chat"** 卡片
4. 进入聊天界面

### 方式 2：通过搜索启动

1. 在 AI Tools 页面顶部搜索框输入 `gemma` 或 `chat`
2. 点击 **"Gemma3 Chat"** 卡片进入

## 🎯 聊天界面功能

### 顶部工具栏
- **← 返回按钮**：返回算法列表
- **📁 模型配置按钮**：配置模型文件路径（首次使用必需）
- **🔄 清空聊天**：清除所有历史消息

### 中间聊天区域
- **用户消息**：蓝色气泡，右对齐
- **AI 回复**：白色气泡，左对齐
- **滚动查看**：自动滚动到最新消息

### 底部输入区
- **快捷问题**：点击预设问题快速提问
  - 介绍一下你自己
  - 帮我写一首关于春天的诗
  - 解释什么是人工智能
  - 推荐一本好书
  - 讲一个有趣的故事
  - 给我一些学习建议
- **输入框**：手动输入自定义问题
- **发送按钮 (➤)**：发送消息

## 🚀 首次使用配置

### 步骤 1：准备模型文件

确保以下文件存在于 `/sdcard/nndeploy/models/gemma3/` 目录：

```
/sdcard/nndeploy/models/gemma3/
├── model.onnx              # 主模型文件 (~789 MB)
├── model.onnx_data         # 模型数据文件
├── tokenizer.json          # Tokenizer 配置
└── tokenizer.model         # Tokenizer 模型
```

### 步骤 2：使用模型复制功能（推荐）

如果模型文件在其他目录（如 `/sdcard/nndeploy_models/gemma3_source/`）：

1. 进入 Gemma3 Chat 界面
2. 点击顶部 **📁 按钮**
3. 在弹出的对话框中点击 **"从源目录复制"**
4. 等待复制完成（会显示进度）
5. 完成后即可开始聊天

### 步骤 3：开始对话

1. 点击快捷问题或输入自定义问题
2. 点击发送按钮 ➤
3. 等待 AI 生成回复（首次推理可能需要 10-30 秒）
4. 查看 AI 的回复
5. 继续对话（支持多轮上下文）

## 📊 核心实现架构

### JNI 调用流程

```
用户输入
    ↓
LlmChatProcessScreen (Compose UI)
    ↓
PromptInPromptOut.processPromptInPromptOut()
    ↓
GraphRunner.run()
    ↓
libnndeploy_jni.so
    ↓
C++ nndeploy::dag::Graph
    ↓
Gemma3 Workflow Pipeline:
  Prompt_4 (nndeploy::llm::Prompt)
      → 处理用户输入
  LlmInfer (nndeploy::llm::LlmInfer)
      → ONNX Runtime 推理
  LlmOut_3 (nndeploy::llm::LlmOut)
      → 输出文本
    ↓
结果返回 → UI 显示
```

### 关键文件位置

| 文件 | 路径 | 作用 |
|------|------|------|
| 算法配置 | `Algorithm.kt` | 定义 `gemma3_demo` 算法 |
| 聊天 UI | `Tool.kt` | `LlmChatProcessScreen()` 实现 |
| JNI 封装 | `PromptInPromptOut.kt` | 调用 native GraphRunner |
| Workflow | `assets/resources/workflow/gemma3demo.json` | LLM Pipeline 定义 |
| Native 桥接 | `GraphRunner.kt` | Kotlin ↔ C++ JNI |
| SO 库 | `jniLibs/arm64-v8a/` | libnndeploy_jni.so 等 |

### 配置参数（Algorithm.kt）

```kotlin
AIAlgorithm(
    id = "gemma3_demo",
    name = "Gemma3 Chat",
    description = "Gemma3-270M 智能对话...",
    icon = Icons.Default.QuestionAnswer,
    inputType = listOf(InOutType.PROMPT),
    outputType = listOf(InOutType.TEXT),
    category = AlgorithmCategory.NATURAL_LANGUAGE.displayName,
    workflowAsset = "resources/workflow/gemma3demo.json",
    parameters = mapOf(
        "input_node" to mapOf("Prompt_4" to "user_content_"),
        "output_node" to mapOf("LlmOut_3" to "path_")
    ),
    processFunction = "processPromptInPromptOut"
)
```

## 🔧 故障排查

### 问题 1：模型文件未找到

**错误信息**：
```
Gemma3 models not found.
Please click the 📁 button to copy models...
```

**解决方案**：
1. 确认源目录存在：`/sdcard/nndeploy_models/gemma3_source/`
2. 点击 📁 按钮并使用"从源目录复制"功能
3. 或手动复制文件到目标目录

### 问题 2：缺少部分模型文件

**错误信息**：
```
Missing model files: model.onnx_data, tokenizer.json
```

**解决方案**：
1. 检查源目录是否包含所有必需文件
2. 确认文件名正确（区分大小写）
3. 重新复制完整的模型文件集

### 问题 3：推理速度慢

**现象**：首次推理需要 30 秒以上

**原因与优化**：
- 模型加载（首次）：~5-10 秒
- Tokenizer 初始化：~2-3 秒
- 推理生成：取决于输入长度和设备性能

**优化建议**：
1. 使用高性能设备（如 Android TV 或旗舰手机）
2. 保持应用在前台（避免系统休眠）
3. 后续推理会复用已加载的模型，速度会快很多

### 问题 4：SO 库加载失败

**错误信息**：
```
java.lang.UnsatisfiedLinkError: dlopen failed
```

**解决方案**：
1. 检查 `jniLibs/arm64-v8a/` 目录包含所有 .so 文件：
   - libnndeploy_jni.so
   - libnndeploy_framework.so
   - libnndeploy_plugin_llm.so
   - libnndeploy_plugin_tokenizer.so
   - libnndeploy_plugin_qwen.so
2. 确认设备架构为 arm64-v8a
3. 检查 Logcat 中的详细错误信息

## 📝 示例对话

### 示例 1：自我介绍
```
👤 用户：介绍一下你自己

🤖 Gemma3：你好！我是 Gemma3，一个基于 270M 参数的智能对话模型。
我可以帮你回答问题、写作、翻译、编程等多种任务。我很高兴能为你服务！
```

### 示例 2：创作诗歌
```
👤 用户：帮我写一首关于春天的诗

🤖 Gemma3：春风拂面暖人心，
万物复苏绿意新。
花开遍地香满径，
鸟语啁啾唱早晨。
```

### 示例 3：知识问答
```
👤 用户：解释什么是人工智能

🤖 Gemma3：人工智能（AI）是计算机科学的一个分支，旨在创建能够
执行通常需要人类智能的任务的系统。这包括学习、推理、问题解决、
感知和语言理解等能力...
```

## 🎉 完成

恭喜！你现在已经成功运行 Gemma3-270M Chat Demo。

如有问题，请查看：
- Logcat 日志：`adb logcat | grep -E "nndeploy|Gemma3|LlmChat"`
- 模型文件权限：`adb shell ls -l /sdcard/nndeploy/models/gemma3/`
- 应用崩溃日志：Android Studio → Logcat → 筛选 "FATAL"
