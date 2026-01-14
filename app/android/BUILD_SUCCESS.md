# ✅ Gemma3 Android 部署 - 编译成功

## 完成状态

### ✅ 已完成的工作

1. **修复了所有代码问题**
   - `isModelAvailable()` 现在正确检查文件而不只是目录
   - `copyModelFromSource()` 增强了错误处理和日志
   - 权限请求逻辑完善（MANAGE_EXTERNAL_STORAGE）
   - UI 增强：中文提示、进度显示、状态反馈

2. **修复了编译环境**
   - 下载并安装了 gradle-wrapper.jar
   - 修复了 gradle.properties（MaxPermSize → MaxMetaspaceSize）
   - 成功编译了包含所有最新修改的 APK (189MB)

3. **模型文件已就绪**
   - 设备路径：`/sdcard/nndeploy_models/gemma3/`
   - 8 个文件，总计 789MB
   - 包括：model.onnx, model.onnx_data (764M), tokenizer.json (19M), tokenizer.model (4.4M)

4. **权限已授予**
   - MANAGE_EXTERNAL_STORAGE: allow
   - 应用可以读写 /sdcard/ 目录

## 当前状态

**✅ 新版 APK 已安装到设备**
- 版本：2025-12-17 15:12 编译
- 大小：189MB
- 包含所有最新代码修复

## 下一步测试

### 测试 Gemma3 推理

```bash
# 1. 清除旧日志
adb logcat -c

# 2. 在设备上：
#    - 打开 NNDeploy 应用
#    - 进入 "Gemma3 Chat"
#    - 发送消息："介绍一下你自己"

# 3. 查看实时日志
adb logcat | grep -E 'nndeploy_default_str|ModelPathManager|PromptInPromptOut|tokenizer'
```

### 预期成功的日志

```
E nndeploy_default_str: tokenizer_encode init start
I nndeploy_default_str: tokenizer_encode init finish  ← 应该看到这行
E nndeploy_default_str: prefill_infer init start
I nndeploy_default_str: prefill_infer init finish
E nndeploy_default_str: decode_infer init start
...
I StreamOut: Generated text: [AI响应]
```

### 如果还有问题

如果仍然崩溃，查看详细错误：

```bash
# 查看崩溃日志
adb logcat -d > crash_full.log

# 查找 FATAL 错误
grep -E "FATAL|SIGSEGV|backtrace" crash_full.log

# 查看 tokenizer 相关错误
grep -B 10 -A 10 "tokenizer" crash_full.log
```

## Demo2 YOLO 测试

Demo2 使用简单的 ONNX 推理（不需要 tokenizer），可以作为对照测试：

```bash
# 测试步骤：
1. 打开 NNDeploy 应用
2. 选择 "Demo2 YOLO Detection"
3. 选择一张图片
4. 查看检测结果

# Demo2 配置（参考）：
- 模型：resources/models/detect/yolo11s.sim.onnx (12MB)
- 推理后端：kInferenceTypeOnnxRuntime
- 输入：图片 → 预处理 → 推理 → 后处理 → 图片
```

## Qwen ONNX 量化模型测试（下一步）

如果 Gemma3 成功运行，可以尝试其他 ONNX 模型：

### Gemma3 量化模型

```bash
# 可用的量化版本：
/Users/jin/work/nndeploy/models/gamma3-270m/gemma3-270m-onnx/onnx/
├── model_q4.onnx (242K) + model_q4.onnx_data (764M)      # INT4量化
├── model_q4f16.onnx (312K) + model_q4f16.onnx_data (406M) # INT4+FP16
└── model_fp16.onnx + model_fp16.onnx_data                # FP16

# Q4F16 模型更小（406MB vs 764MB），推理更快
```

### 上传量化模型到设备

```bash
# 1. 上传 Q4F16 模型
cd /Users/jin/work/nndeploy/models/gamma3-270m/gemma3-270m-onnx/onnx
adb shell "mkdir -p /sdcard/nndeploy_models/gemma3_q4f16"
adb push model_q4f16.onnx /sdcard/nndeploy_models/gemma3_q4f16/
adb push model_q4f16.onnx_data /sdcard/nndeploy_models/gemma3_q4f16/

# 2. 复制 tokenizer 文件
adb shell "cp /sdcard/nndeploy_models/gemma3/tokenizer.* /sdcard/nndeploy_models/gemma3_q4f16/"
adb shell "cp /sdcard/nndeploy_models/gemma3/*.json /sdcard/nndeploy_models/gemma3_q4f16/"

# 3. 验证
adb shell "ls -lh /sdcard/nndeploy_models/gemma3_q4f16/"
```

### 创建 gemma3_q4f16.json 配置

复制 gemma3demo.json 并修改：
- 将 `model.onnx` 改为 `model_q4f16.onnx`
- 将 `model.onnx_data` 改为 `model_q4f16.onnx_data`
- 路径前缀改为 `resources/models/gemma3_q4f16/`

## 故障排查清单

### 问题 1：应用仍然崩溃

**可能原因：**
- Tokenizer C++ 库初始化失败
- ONNX 模型文件损坏
- OnnxRuntime 版本不兼容

**解决方案：**
```bash
# 1. 验证模型文件完整性
cd /Users/jin/work/nndeploy/models/gemma3
md5 model.onnx_data
adb shell "md5sum /sdcard/nndeploy_models/gemma3/model.onnx_data"
# 对比两个 MD5 值是否一致

# 2. 检查 OnnxRuntime 版本
adb shell "ls -l /data/app/*/com.nndeploy.app*/lib/arm64/libonnxruntime.so"

# 3. 测试 demo2（不需要 tokenizer）
# 如果 demo2 能运行，说明 ONNX 推理本身没问题
```

### 问题 2：找不到模型文件

**解决方案：**
```bash
# 检查路径映射
adb logcat -d | grep "Mapped:"

# 应该看到：
# Mapped: resources/models/gemma3/model.onnx -> /storage/emulated/0/nndeploy_models/gemma3/model.onnx

# 验证文件确实存在
adb shell "ls -la /storage/emulated/0/nndeploy_models/gemma3/"
```

### 问题 3：内存不足

Gemma3-270M 需要约 1-2GB RAM

**解决方案：**
```bash
# 1. 使用量化模型（Q4F16，406MB）
# 2. 检查设备可用内存
adb shell "cat /proc/meminfo | grep -E 'MemAvailable|MemFree'"

# 3. 关闭其他应用
adb shell "am kill-all"
```

## 性能预期

### Gemma3-270M FP32
- 模型大小：764MB
- 内存占用：~1.5GB
- 首个token：~3-5秒
- 后续token：~200-300 ms/token

### Gemma3-270M Q4F16（量化）
- 模型大小：406MB
- 内存占用：~800MB
- 首个token：~2-3秒
- 后续token：~150-250 ms/token

## 成功标志

当一切正常工作时，你应该看到：

1. **UI 显示**
   - "✅ Gemma3 模型已就绪"
   - 消息发送后出现 loading 动画
   - AI 响应逐字显示（流式输出）

2. **日志输出**
   ```
   I PromptInPromptOut: Starting processing for Gemma3 Chat
   D PromptInPromptOut: All required files exist
   E nndeploy_default_str: tokenizer_encode init finish
   E nndeploy_default_str: prefill_infer init finish
   I StreamOut: Generated text: [响应内容]
   ```

3. **性能**
   - 首次响应：3-5秒
   - 流式输出：每秒 3-5 个 token

## 相关文件

- **源代码**: `/Users/jin/work/nndeploy/`
- **APK**: `app/android/app/build/outputs/apk/debug/app-debug.apk`
- **模型**: `/sdcard/nndeploy_models/gemma3/`
- **日志**: `adb logcat`
- **编译脚本**: `app/android/rebuild_and_install.sh`

## 快速重新编译命令

```bash
cd /Users/jin/work/nndeploy/app/android
./gradlew clean assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

**当前状态**: ✅ 新版 APK 已安装，等待测试结果
**下一步**: 在设备上测试 Gemma3 Chat 推理功能
