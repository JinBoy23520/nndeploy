# nndeploy Windows 调试与构建问题汇总

本文档总结在 Windows 平台上为使 `nndeploy` 支持 MNN LLM 后端所做的尝试、已下载/使用的工具、关键命令、产生的调试产物以及下一步建议，便于复现和后续分析。

## 一、问题概述
- 现象：在 Python 环境中调用 `nndeploy.dag.Graph.init()` 时，进程立即出现 Windows 致命异常（访问冲突，退出码 `0xC0000005`）。
- 特点：C++ demo（`nndeploy_demo_llm.exe`）在相同机器上可正常运行；但 Python 扩展导入或执行 `Graph.init()` 会崩溃；即使在最小化场景（空 Graph.init）也可复现。

## 二、已尝试的排查与验证步骤（按时间/逻辑）
1. 编译与环境准备
   - 从源码构建 MNN（v3.2.4），启用 LLM：`-DMNN_BUILD_LLM=ON`，并安装到 `tool/script/third_party/mnn3.2.4`。
   - 修复 `tool/script/build_win.py` 中的格式问题，并使用脚本生成 Windows build（CMake + Visual Studio 17 2022）。
   - 构建 `nndeploy`，生成 `build/Release` 下的 DLL（如 `MNN.dll`、`nndeploy_framework.dll`、`nndeploy_plugin_llm.dll`、`onnxruntime.dll`、opencv dlls 等），并将这些 DLL 拷贝到 Python 包目录 `venv/Lib/site-packages/nndeploy`。

2. 最小化重现与隔离测试
   - 直接在项目根运行最小化脚本 `test_mnn_direct.py`：直接调用 Python 中的 `Graph` 并 `init()`，立即崩溃。
   - 编写并运行多份隔离测试：
     - `build/isolations/test_mnn_init.py`（MNN Graph 初始化最小化）
     - `build/isolations/test_onnx_template.py`（使用 ONNX 模板）
     - `build/isolations/test_mnn_init_cwd_isolation.py`（更改 CWD 测试动态库加载）
     - `build/isolations/test_empty_graph_init.py`（创建空 Graph 并 init）
   - 结论：所有隔离测试均在 `Graph.init()` 崩溃 —— 表明问题在 Python -> 本地扩展调用或本地库初始化。

3. 运行时加载顺序与路径测试
   - 将 `build\\Release` 临时加入 `PATH`，运行最小化 `Graph.init()`，仍崩溃（记录：`build/isolations/test_path_env_result.txt`）。
   - 在运行时调用 `os.add_dll_directory(r'D:\\jinwork\\nndeploy-1\\build\\Release')`，再 import 并 `Graph.init()`，仍崩溃（记录：`build/isolations/test_add_dll_result.txt`）。
   - 临时将 `venv/Lib/site-packages/nndeploy/nndeploy_plugin_*.dll` 移出（`build/isolations/moved_plugins`），测试导入，结果为 `ImportError: DLL load failed while importing _nndeploy_internal: 找不到指定的模块。`（说明 Python 扩展依赖某些 plugin/共享库，移除后导入失败并非缓解崩溃）。

4. 本地崩溃捕获（ProcDump）与字符串提取
   - 使用 ProcDump 捕获崩溃转储：
```powershell
procdump.exe -e -ma -x .\\build\\crash_dump .\\venv\\Scripts\\python.exe -- test_mnn_direct.py
```
   - 产物：`build\\crash_dump\\python.exe_251206_191843.dmp`。
   - 使用 Sysinternals 的 `strings.exe` 对 `.dmp` 做简单可读字符串抽取，用于初步检查（未解析出函数级别符号）。

5. 直接导入 extension 子模块测试
   - 尝试仅 `import nndeploy._nndeploy_internal.dag` 并构造 `Graph`，调用 `init()`，同样导致访问冲突（`build/isolations/test_direct_internal.txt`）。

6. 查找本地诊断工具
   - 在系统上未发现 `dumpbin.exe` 或 `cdb.exe`（调试工具/Visual Studio Debugging Tools 未安装）。
   - 已下载 `vs_BuildTools.exe`（Visual Studio Build Tools 安装器），准备安装 `Debugging Tools for Windows` 来获取 `cdb.exe`/`windbg`。

## 三、已下载/使用的工具与用途
- `vs_BuildTools.exe`：Visual Studio Build Tools 安装器（用于可选安装 Debugging Tools）。
- `Procdump.exe`（Sysinternals）：用于在 Python 进程崩溃时生成完整内存转储（命令见上）。产物位于 `build/crash_dump`。
- `strings.exe`（Sysinternals）：对 `.dmp` 做可读字符串抽取以供初步检查。
- CMake / Visual Studio (MSVC) / Python venv / Rust（tokenizer-cpp）：用于构建依赖与扩展。

## 四、重要产物与路径
- 构建产物目录：`D:\\jinwork\\nndeploy-1\\build\\Release`（包含 `MNN.dll`, `nndeploy_framework.dll`, `nndeploy_plugin_llm.dll`, `onnxruntime.dll`, `opencv_*.dll` 等）。
- Python 包目录（运行时使用）：`D:\\jinwork\\nndeploy-1\\venv\\Lib\\site-packages\\nndeploy`（包含 `_nndeploy_internal.cp310-win_amd64.pyd`、若干 `.dll`）。
- 崩溃转储：`D:\\jinwork\\nndeploy-1\\build\\crash_dump\\python.exe_251206_191843.dmp`。
- 隔离测试输出（示例）：
  - `build\\isolations\\test_path_env_result.txt`
  - `build\\isolations\\test_add_dll_result.txt`
  - `build\\isolations\\test_plugins_moved.txt`
  - `build\\isolations\\test_direct_internal.txt`

## 五、如何使用已下载的工具进行进一步分析（步骤与命令）

1) 安装 Debugging Tools（在 Windows 上使 `cdb.exe` 可用）
   - 通过 `vs_BuildTools.exe` 打开安装器并在“单个组件（Individual components）”中选择 `Debugging Tools for Windows`，或在工作负载中选择包含 Debugging Tools 的项。
   - 验证 `cdb.exe`：
```powershell
where.exe cdb.exe
Get-Command cdb.exe
```

2) 使用 `cdb` 对 `.dmp` 做符号化分析（自动化命令）
   - 我们将使用 Microsoft 符号服务器并把符号缓存到本地 `C:\\\symbols`：
```powershell
cdb -z "D:\\jinwork\\nndeploy-1\\build\\crash_dump\\python.exe_251206_191843.dmp" -c ".symfix; .sympath SRV*c:\\\symbols*https://msdl.microsoft.com/download/symbols; .reload; !analyze -v; k; lmv; .ecxr; q"
```
   - 输出将包含：自动分析建议、符号化的线程堆栈（若有对应 PDB，可直接看到函数名与源文件）、已加载模块列表与路径、异常上下文寄存器等。

3) 若 `cdb` 显示崩溃发生在某个模块（例如 `MNN.dll` 或 `_nndeploy_internal.pyd`）且存在偏移地址，但无符号
   - 需要：获取该模块的 PDB 或用带符号的 Debug 构建重新编译相关组件（建议先重建 Python 扩展和 MNN 插件时启用 PDB 导出）。

## 六、可能的根因方向（基于现有证据）
- pybind11 / Python 本地扩展边界的 ABI 或调用约定问题。
- 运行时 CRT（MSVC runtime）不一致或 DLL 冲突（多个模块使用不同的 CRT 版本或运行时标志 `/MD` vs `/MT` 等）。
- DLL 加载顺序或初始化（静态构造函数）导致的未定义行为。
- 特定 plugin 或第三方库（MNN / ONNXRuntime / OpenCV 等）在 Python 进程中初始化时触发的异常。

## 七、推荐的下一步（优先级）
1. 立即（高优先级）：安装 `Debugging Tools for Windows`（`cdb.exe`），并运行上文的 `cdb` 分析命令，获取符号化堆栈。——这是定位问题最直接且高效的步骤。
2. 若需要更明确符号：使用带 PDB 的 Debug/RelWithDebInfo 构建 `nndeploy` 与 MNN，并重试生成崩溃转储以获得完整符号化堆栈。
3. 并行可做：在完全干净的 Python venv 或另一台机器上复现（排除本地环境/系统级依赖问题）。

## 八、我可以为你自动化/代劳的事情
- 当你安装好 `cdb.exe` 并告诉我后，我会自动运行 `cdb` 命令（上节所列）并把完整输出与符号化堆栈解析报告贴回给你。
- 我也可以帮你修改 CMake 配置以生成 PDB（Debug/RelWithDebInfo），并脚本化重新构建 + 收集新的 `.dmp`。

---
文件生成时间：2025-12-06
维护人：由协助工程师自动生成（请视为工作笔记并可补充/修订）。
