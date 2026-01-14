# Gemma3 Android 部署问题总结

## 当前状态

### ✅ 已完成
1. 模型文件已上传到设备：`/sdcard/nndeploy_models/gemma3_source/` (827MB, 8文件)
2. 模型文件已手动复制到目标：`/sdcard/nndeploy_models/gemma3/` (789MB, 8文件)
3. 存储权限已授予：`MANAGE_EXTERNAL_STORAGE: allow`
4. 路径映射正常工作：
   - `resources/models/gemma3/model.onnx` → `/storage/emulated/0/nndeploy_models/gemma3/model.onnx`
   - tokenizer.json, tokenizer.model 路径映射成功

5. UI 增强完成：
   - 中文提示信息
   - 权限检查和自动跳转设置
   - 复制进度显示
   - 调试日志输出

6. 代码修复完成：
   - `isModelAvailable()` 现在检查文件而不只是目录
   - `copyModelFromSource()` 增强错误处理
   - 权限请求逻辑完善

### ❌ 当前问题

**应用在 tokenizer初始化时崩溃**

```
12-17 13:20:51.563  E nndeploy_default_str: tokenizer_encode init start.
12-17 13:20:51.587  I ActivityManager: Process com.nndeploy.app (pid 4821) has died: fg  TOP
12-17 13:20:51.590  I Zygote  : Process 4821 exited cleanly (1)
```

## 根本原因分析

### 问题 1：旧版本 APK

当前运行的 APK 是之前编译的版本，**不包含最新的代码修改**：
- 旧的 `libnndeploy_plugin_tokenizer.so`
- 旧的 JSON 配置处理逻辑
- 可能的 tokenizer 初始化 bug

### 问题 2：Tokenizer 配置

tokenizer_encode 节点初始化失败可能因为：
1. Tokenizer 找不到配置文件（虽然路径映射了）
2. Tokenizer C++ 库版本不匹配
3. OnnxRuntime 和 Tokenizer 的依赖冲突

### 问题 3：编译环境

无法直接编译 APK，因为：
- Gradle wrapper jar 文件丢失
- 需要在 Android Studio 中编译

## 解决方案

### 方案 1：在 Android Studio 中重新编译（推荐）

1. **打开项目**
   ```
   Android Studio → Open → /Users/jin/work/nndeploy/app/android
   ```

2. **清理并重新编译**
   ```
   Build → Clean Project
   Build → Rebuild Project
   Run → Run 'app'
   ```

3. **测试流程**
   - 打开 Gemma3 Chat
   - 模型文件已经在正确位置
   - 直接发送消息测试推理

### 方案 2：修复 Gradle Wrapper

```bash
cd /Users/jin/work/nndeploy/app/android

# 下载 gradle wrapper jar
curl -L https://services.gradle.org/distributions/gradle-8.4-bin.zip -o gradle.zip
unzip gradle.zip -d gradle_temp
mv gradle_temp/gradle-8.4 gradle_home

# 使用本地 gradle 编译
./gradle_home/bin/gradle assembleDebug

# 安装
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 方案 3：临时测试（跳过 tokenizer）

如果问题是 tokenizer 特定的，可以：
1. 使用其他已验证的 LLM 模型
2. 或者修改 gemma3demo.json 简化配置

## 调试建议

### 1. 查看详细崩溃日志

```bash
# 清除日志
adb logcat -c

# 启动应用并发送消息

# 查看完整日志
adb logcat > crash.log

# 查找 FATAL 或 SIGSEGV
grep -E "FATAL|SIGSEGV|backtrace" crash.log
```

### 2. 检查 tombstone

```bash
adb shell ls -lt /data/tombstones/ | head -5
adb pull /data/tombstones/tombstone_XX
```

### 3. 启用 native debug

在 Android Studio 中：
- Run → Edit Configurations
- Debugger → Debug type → Native
- 设置断点在 tokenizer 初始化代码

## 预期下一步

1. **重新编译 APK** - 包含所有最新修复
2. **测试推理** - 模型文件已就绪
3. **验证性能** - Gemma3-270M 应该能流畅运行

## 文件检查清单

### ✅ 设备上的文件（已验证）

```bash
/sdcard/nndeploy_models/gemma3/
├── config.json (1.5K)
├── gemma3_config.json (418B)
├── generation_config.json (172B)
├── model.onnx (242K)
├── model.onnx_data (764M) ✓ 关键
├── tokenizer.json (19M) ✓ 关键
├── tokenizer.model (4.4M) ✓ 关键
└── tokenizer_config.json (1.1M)
```

### ✅ APK 中的库文件

```
jniLibs/arm64-v8a/
├── libnndeploy_plugin_tokenizer.so (24M) ✓
├── libnndeploy_plugin_llm.so (14M) ✓
├── libonnxruntime.so (16M) ✓
├── libc++_shared.so (8.9M)
└── ... (其他插件)
```

### ✅ 配置文件

```
assets/resources/workflow/
└── gemma3demo.json (26KB) ✓
```

## 成功标志

当重新编译后成功运行时，日志应显示：

```
I ModelPathManager: Model gemma3 available: true
I PromptInPromptOut: All required files exist
D PromptInPromptOut: Mapped paths successfully
E nndeploy_default_str: tokenizer_encode init start
I nndeploy_default_str: tokenizer_encode init finish  ← 应该看到这行
E nndeploy_default_str: prefill_infer init start
...
I StreamOut: Generated text: [响应内容]
```

## 临时workaround

如果实在无法编译，可以手动测试底层功能：

```bash
# 测试文件读取权限
adb shell "cat /sdcard/nndeploy_models/gemma3/config.json | head -5"

# 测试模型文件完整性
adb shell "md5sum /sdcard/nndeploy_models/gemma3/model.onnx_data"

# 比对本地文件
md5 /Users/jin/work/nndeploy/models/gemma3/model.onnx_data
```

## 联系点

- 代码位置：`/Users/jin/work/nndeploy/`
- APK 输出：`app/android/app/build/outputs/apk/debug/`
- 日志文件：使用 `adb logcat` 实时查看
- 设备模型路径：`/sdcard/nndeploy_models/gemma3/`
