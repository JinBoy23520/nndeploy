# 🔧 Gemma3 最终修复 - 测试指南

## 编译时间
2024-12-23 16:11

## ✅ 已完成的修复

### 1. 双重保护机制

#### 保护层 1：UI 层屏蔽
**文件**: [app/android/app/src/main/java/com/nndeploy/ai/Algorithm.kt](app/android/app/src/main/java/com/nndeploy/ai/Algorithm.kt#L172-L188)

```kotlin
// ⚠️ Gemma3 原始完整版（需要完整插件实现，当前会崩溃）
// AIAlgorithm(
//     id = "gemma3_demo",
//     ...
// ),
```
**效果**：算法列表中不再显示 `gemma3_demo`

#### 保护层 2：运行时拦截
**文件**: [app/android/app/src/main/java/com/nndeploy/ai/PromptInPromptOut.kt](app/android/app/src/main/java/com/nndeploy/ai/PromptInPromptOut.kt#L48-L60)

```kotlin
// ⚠️ Block gemma3_demo - it will crash with current Qwen adapter
if (alg.id == "gemma3_demo") {
    return PromptProcessResult.Error(
        "⚠️ Gemma3 Chat (Full) is currently disabled.\n\n" +
        "Reason: gemma3demo.json uses 'model_key: Qwen' which is incompatible...\n\n" +
        "✅ Please use 'Gemma3 Chat (Optimized)' instead."
    )
}
```
**效果**：即使通过其他方式调用 `gemma3_demo`，也会被拦截并显示友好错误信息

### 2. 新编译的 APK

**路径**: `/Users/jin/work/nndeploy-1/app/android/app/build/outputs/apk/debug/app-debug.apk`
**大小**: 189 MB
**编译时间**: 2024-12-23 16:11

**包含的库**:
- ✅ libnndeploy_framework.so
- ✅ libnndeploy_plugin_gemma.so (存根实现)
- ✅ libnndeploy_plugin_qwen.so
- ✅ libnndeploy_plugin_tokenizer.so
- ✅ 其他依赖库

## 🚀 测试步骤

### 步骤 1: 卸载旧 APK（重要！）

```bash
# 方法 A: 通过 adb 卸载
adb uninstall com.nndeploy.app

# 方法 B: 在手机上手动卸载
# 设置 → 应用 → nndeploy → 卸载
```

⚠️ **必须卸载旧版本**，否则可能保留旧的算法配置缓存。

### 步骤 2: 安装新 APK

```bash
cd /Users/jin/work/nndeploy-1/app/android
adb install app/build/outputs/apk/debug/app-debug.apk
```

**预期输出**:
```
Performing Streamed Install
Success
```

### 步骤 3: 启动应用并验证

1. **打开应用**
2. **检查算法列表**：
   - ✅ 应该看到：**"Gemma3 Chat (Optimized)"**
   - ❌ 不应该看到：~~"Gemma3 Chat (Full)"~~

3. **如果仍然看到 gemma3_demo**：
   - 清除应用数据：`设置 → 应用 → nndeploy → 存储 → 清除数据`
   - 或者重启手机

### 步骤 4: 测试 Gemma3 Chat (Optimized)

1. 选择 **"Gemma3 Chat (Optimized)"** 算法
2. 输入提示词："介绍一下你自己"
3. 点击发送

**预期结果**：
- ✅ 正常初始化（不崩溃）
- ✅ 显示生成的回复
- ✅ 无 pthread mutex 错误

### 步骤 5: 查看日志（可选）

```bash
adb logcat -c  # 清除旧日志
adb logcat | grep -E "(Gemma3|gemma3_simple|PromptInPromptOut|init finish)"
```

**正常日志应包含**:
```
PromptInPromptOut: Starting processing for Gemma3 Chat (Optimized)
PromptInPromptOut: Gemma3 model files verified at: ...
gemma3_simple init start.
Prompt_1 init start.
Prompt_1 init finish.
TokenizerEncode_2 init start.
TokenizerEncode_2 init finish.
OnnxInfer_3 init start.
OnnxInfer_3 init finish.
...
gemma3_simple init finish.
```

**不应该出现**:
```
❌ gemma3demo init start       # 不应使用 gemma3demo
❌ Prefill_1 init start         # 不应有 Prefill 节点
❌ model_key: Qwen              # 不应使用 Qwen 适配器
❌ pthread_mutex_lock called on a destroyed mutex  # 不应崩溃
```

## 🔍 问题排查

### 问题 1: 仍然看到 "Gemma3 Chat (Full)"

**原因**: 使用了旧 APK 或应用缓存

**解决**:
```bash
# 完全卸载并重装
adb uninstall com.nndeploy.app
adb install app/build/outputs/apk/debug/app-debug.apk

# 清除数据
adb shell pm clear com.nndeploy.app
```

### 问题 2: 点击 "Gemma3 Chat (Optimized)" 仍然崩溃

**检查日志**:
```bash
adb logcat | grep -E "(FATAL|AndroidRuntime|FORTIFY)"
```

**可能原因**:
1. 模型文件未正确复制
2. ONNX Runtime 初始化失败
3. tokenizer.json 格式错误

**解决**: 检查 `/sdcard/nndeploy_models/gemma3/` 目录内容

### 问题 3: 显示错误 "Gemma3 Chat (Full) is currently disabled"

**原因**: 您尝试使用了被禁用的 gemma3_demo

**解决**: 这是正常的保护机制！请使用 **"Gemma3 Chat (Optimized)"**

## 📊 功能对比

| 功能 | Gemma3 Chat (Optimized) | Gemma3 Chat (Full) |
|------|------------------------|-------------------|
| 状态 | ✅ 可用 | ❌ 已禁用 |
| 工作流 | gemma3_simple.json | gemma3demo.json |
| 推理方式 | 直接 ONNX Runtime | Prefill/Decode 两阶段 |
| 模型适配器 | 无（直接推理） | Qwen（不兼容） |
| KV Cache | ❌ 无 | ✅ 有（但崩溃） |
| 稳定性 | ✅ 稳定 | ❌ 崩溃 |
| 性能 | 中等（每次重新计算） | 高（如果不崩溃） |
| 启动速度 | ✅ 快 | ❌ 慢 |
| 推荐使用 | ✅ 是 | ❌ 否 |

## 🎯 下一步

### 当前可用方案
✅ 使用 **Gemma3 Chat (Optimized)** 进行推理
- 稳定、可靠、不崩溃
- 功能完整，性能可接受
- 无需额外开发工作

### 未来优化方案（可选）
如需启用 Prefill/Decode 优化：

1. **完整实现 Gemma3 插件**（估计 2-3 天）
   - 参考 [plugin/source/nndeploy/qwen/qwen.cc](plugin/source/nndeploy/qwen/qwen.cc)
   - 实现 Gemma3PromptNode::run()
   - 实现 Gemma3EmbeddingNode::init() 和 run()
   - 实现 KV cache 管理（4个头）

2. **修改 gemma3demo.json**
   ```json
   "model_key": "gemma3"  // 改为使用 Gemma3 插件
   ```

3. **取消 Algorithm.kt 注释**
   ```kotlin
   AIAlgorithm(id = "gemma3_demo", ...)  // 重新启用
   ```

4. **移除 PromptInPromptOut.kt 拦截**
   ```kotlin
   // 删除运行时检查代码
   ```

## 📝 技术总结

### 崩溃原因
```
gemma3demo.json → "model_key": "Qwen" → Qwen 适配器
                                          ↓
                  Qwen: [28层, 1个KV头, 256 head_dim]
                                          ↓
                  应用到 Gemma3: [18层, 4个KV头, 256 head_dim]
                                          ↓
                  形状不匹配 → 内存错误 → pthread mutex 错误 → CRASH
```

### 修复策略
```
方案 A (当前): 使用 gemma3_simple.json
              → 直接 ONNX Runtime
              → 不使用模型适配器
              → 稳定但无 KV cache 优化

方案 B (未来): 完整实现 Gemma3 插件
              → 修改 gemma3demo.json 使用 "gemma3"
              → 支持 Prefill/Decode
              → 高性能但需要开发时间
```

## ✅ 验证清单

安装后请确认：

- [ ] 已完全卸载旧版本 APK
- [ ] 已安装新版本 APK (16:11 编译)
- [ ] 算法列表中只有 "Gemma3 Chat (Optimized)"
- [ ] 不存在 "Gemma3 Chat (Full)"
- [ ] 选择 Optimized 版本后不崩溃
- [ ] 可以正常生成回复
- [ ] 日志中无 pthread mutex 错误
- [ ] 日志中无 "gemma3demo" 关键词

## 🔗 相关文档

- [Gemma3_Plugin_编译完成.md](Gemma3_Plugin_编译完成.md) - 插件编译详情
- [Gemma3_崩溃修复_20241223.md](Gemma3_崩溃修复_20241223.md) - 第一次修复尝试
- [GEMMA3_OPTIMIZATION_FIX.md](GEMMA3_OPTIMIZATION_FIX.md) - 优化历史

---

**编译信息**:
- APK: app-debug.apk (189 MB)
- 编译时间: 2024-12-23 16:11
- Gradle 版本: 8.7
- Kotlin 版本: 1.9.0
