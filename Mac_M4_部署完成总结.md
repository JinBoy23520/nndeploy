# nndeploy Mac M4 部署完成总结

## 部署日期
2024年12月11日

## 系统环境
- **系统**: macOS 15.3.1 (Sequoia)
- **芯片**: Apple M4 (ARM64)
- **Python**: 3.14.2 (Homebrew)
- **CMake**: 4.2.0
- **编译器**: AppleClang 17.0.0

## 已安装组件

### 核心推理框架
1. **OpenCV 4.12.0** (via Homebrew)
   - 路径: `/opt/homebrew/Cellar/opencv/4.12.0_17`
   - 包含模块: core, imgproc, imgcodecs, videoio, highgui, video, dnn, calib3d, features2d, flann

2. **ONNXRuntime 1.18.0**
   - 路径: `/Users/jin/work/nndeploy/third_party/onnxruntime1.18.0`
   - 库: libonnxruntime.1.18.0.dylib
   - 架构: ARM64

3. **MNN 3.2.4** (自编译)
   - 路径: `/Users/jin/work/nndeploy/third_party/mnn3.2.4`
   - 库: libMNN.dylib, libMNN_Express.dylib
   - 源码: `/Users/jin/work/nndeploy/download/mnn`

4. **tokenizers-cpp** (Rust + C++)
   - 自动下载并编译
   - 支持: sentencepiece, safetensors

### 编译配置
- 配置文件: `cmake/config_opencv_ort_mnn_tokenizer.cmake`
- 构建目录: `/Users/jin/work/nndeploy/build`
- 推理后端: MNN + ONNXRuntime
- Python绑定: 已启用 (pybind11)

### 已启用功能
- ✅ 推理后端: MNN, ONNXRuntime, Default
- ✅ 设备支持: CPU, ARM (Neon)
- ✅ 插件: 
  - 分类 (Classification)
  - 检测 (Detect/YOLO)
  - 分割 (Segment/SegmentAnything/RMBG)
  - 跟踪 (Track/FairMOT)
  - 抠图 (Matting/PPMatting)
  - OCR
  - 超分辨率 (Super Resolution)
  - 预处理 (Preprocess)
  - 编解码 (Codec)
  - Tokenizer (C++)
- ✅ Python API
- ✅ DAG图执行引擎
- ✅ 线程池
- ✅ 时间分析器

### 已禁用功能
- ❌ LLM插件 (需要MNN LLM模块)
- ❌ Stable Diffusion (依赖LLM)
- ❌ CUDA/ROCm/SYCL (非macOS)
- ❌ TensorRT/OpenVINO (未配置)

## 编译产物

### 共享库 (14个)
```
libnndeploy_framework.dylib
libnndeploy_plugin_basic.dylib
libnndeploy_plugin_preprocess.dylib
libnndeploy_plugin_infer.dylib
libnndeploy_plugin_codec.dylib
libnndeploy_plugin_tokenizer.dylib
libnndeploy_plugin_classification.dylib
libnndeploy_plugin_qwen.dylib
libnndeploy_plugin_detect.dylib
libnndeploy_plugin_segment.dylib
libnndeploy_plugin_super_resolution.dylib
libnndeploy_plugin_track.dylib
libnndeploy_plugin_matting.dylib
libnndeploy_plugin_ocr.dylib
```

### Python扩展
```
python/nndeploy/_nndeploy_internal.cpython-314-darwin.so
```

### Demo程序
```
nndeploy_demo_base
nndeploy_demo_classification
nndeploy_demo_codec
nndeploy_demo_dag
nndeploy_demo_detect
nndeploy_demo_device
nndeploy_demo_infer
nndeploy_demo_inference
nndeploy_demo_interpret
nndeploy_demo_ir
nndeploy_demo_matting
nndeploy_demo_net
nndeploy_demo_ocr
nndeploy_demo_op
nndeploy_demo_optimizer
nndeploy_demo_preprocess
nndeploy_demo_run_json
nndeploy_demo_segment
nndeploy_demo_segment_anything
nndeploy_demo_super_resolution
nndeploy_demo_tensor_pool
nndeploy_demo_tensor_pool_multi_net
nndeploy_demo_thread_pool
nndeploy_demo_tokenizer_cpp
nndeploy_demo_track
```

## Python包安装

### 安装命令
```bash
cd /Users/jin/work/nndeploy
python3 -m pip install --break-system-packages --ignore-installed numpy -e python/
```

### 已安装依赖
```
nndeploy==3.0.8
opencv-python>=4.10.0
numpy>=2.2.6
cython
pillow
modelscope
multiprocess
requests>=2.31.0
fastapi>=0.104.0
uvicorn>=0.24.0
websockets>=11.0
python-multipart>=0.0.6
pydantic>=2.0.0
chardet>=5.2.0
```

### 验证测试
```bash
python3 -c "import nndeploy; print('nndeploy version:', nndeploy.__version__)"
# 输出: nndeploy version: nndeploy 3.0.8
```

## RKNN 支持说明

### 可用性确认
nndeploy **完整支持RKNN推理引擎**，包括：

1. **RKNN Toolkit 1.x**
   - 配置选项: `ENABLE_NNDEPLOY_INFERENCE_RKNN_TOOLKIT_1`
   - 实现路径: `framework/source/nndeploy/inference/rknn`
   
2. **RKNN Toolkit 2.x** (推荐)
   - 配置选项: `ENABLE_NNDEPLOY_INFERENCE_RKNN_TOOLKIT_2`
   - 实现路径: `framework/source/nndeploy/inference/rknn2`

### RK3588 Android TV 部署指南
如需在Android TV RK3588上部署，请参考：
1. 配置文件: `cmake/config_android.cmake`
2. 安装RKNN SDK: [RKNN官方仓库](https://github.com/rockchip-linux/rknn-toolkit2)
3. 启用配置:
   ```cmake
   set(ENABLE_NNDEPLOY_INFERENCE_RKNN_TOOLKIT_2 "path/to/rknn-toolkit2")
   ```
4. 交叉编译Android ARM64目标

## 部署步骤记录

### 1. 系统依赖安装
```bash
# 更新Homebrew
brew update

# 安装OpenCV (最大依赖项，~150MB + 依赖)
brew install opencv

# 安装Rust/Cargo (tokenizers-cpp需要)
brew install rust
```

### 2. 下载推理框架
```bash
# ONNXRuntime (自动下载ARM64预编译包)
python3 tool/script/install_onnxruntime.py

# MNN (需要手动编译)
python3 tool/script/install_mnn.py  # 下载源码
cd download/mnn && mkdir build && cd build
cmake -DCMAKE_BUILD_TYPE=Release \
      -DMNN_BUILD_SHARED_LIBS=ON \
      -DMNN_BUILD_CONVERTER=OFF \
      -DMNN_BUILD_TRAIN=OFF \
      -DCMAKE_INSTALL_PREFIX=/Users/jin/work/nndeploy/third_party/mnn3.2.4 \
      ..
make -j10
make install
```

### 3. 创建符号链接
```bash
cd tool/script/third_party
ln -sf ../../../third_party/mnn3.2.4 mnn3.2.4
ln -sf ../../../third_party/onnxruntime1.18.0 onnxruntime1.18.0
ln -sf /opt/homebrew/Cellar/opencv/4.12.0_17 opencv4.10.0
```

### 4. CMake配置
```bash
cd build
rm -rf CMakeFiles CMakeCache.txt
cmake -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
      -DCARGO_EXECUTABLE=/opt/homebrew/bin/cargo \
      -DENABLE_NNDEPLOY_PLUGIN_LLM=OFF \
      -DENABLE_NNDEPLOY_PLUGIN_STABLE_DIFFUSION=OFF \
      -Wno-dev \
      -C ../cmake/config_opencv_ort_mnn_tokenizer.cmake \
      ..
```

### 5. 编译
```bash
make -j10
```

### 6. Python集成
```bash
# 复制库文件
cp build/python/_nndeploy_internal.cpython-314-darwin.so python/nndeploy/
cp build/libnndeploy*.dylib python/nndeploy/

# 安装Python包
python3 -m pip install --break-system-packages --ignore-installed numpy -e python/
```

## 遇到的问题及解决方案

### 问题1: CMake 4.2.0版本过新
**现象**: tokenizers-cpp编译失败，CMAKE_MINIMUM_REQUIRED错误  
**解决**: 添加 `-DCMAKE_POLICY_VERSION_MINIMUM=3.5 -Wno-dev` 参数

### 问题2: Cargo未找到
**现象**: tokenizers-cpp需要Rust编译器  
**解决**: 
```bash
brew install rust
cmake -DCARGO_EXECUTABLE=/opt/homebrew/bin/cargo ...
```

### 问题3: OpenCV下载慢
**现象**: GitHub源码下载速度只有几KB/s  
**解决**: 使用Homebrew安装预编译包
```bash
brew install opencv  # 约10分钟完成
```

### 问题4: MNN库缺失
**现象**: 预编译包未包含ARM Mac动态库  
**解决**: 手动编译MNN 3.2.4源码

### 问题5: MNN LLM模块缺失
**现象**: `MNN/llm/llm.hpp` 文件不存在  
**解决**: 禁用LLM和Stable Diffusion插件
```cmake
set(ENABLE_NNDEPLOY_PLUGIN_LLM OFF)
set(ENABLE_NNDEPLOY_PLUGIN_STABLE_DIFFUSION OFF)
```

### 问题6: Python导入dylib错误
**现象**: `Library not loaded: @loader_path/libnndeploy_plugin_basic.dylib`  
**解决**: 将所有dylib复制到python/nndeploy目录

### 问题7: Homebrew numpy冲突
**现象**: `Cannot uninstall numpy 2.3.5`  
**解决**: 使用 `--ignore-installed numpy` 参数

## 性能参数

### 编译时间
- tokenizers-cpp: ~3分20秒
- MNN 3.2.4: ~6分钟
- nndeploy完整编译: ~5分钟
- **总计**: 约15分钟

### 磁盘占用
- OpenCV (Homebrew): ~360MB (包含所有依赖)
- MNN 3.2.4: ~5MB (dylib) + ~200MB (源码)
- ONNXRuntime 1.18.0: ~30MB
- nndeploy编译产物: ~50MB
- Python包: ~80MB (包含依赖)

## 下一步建议

1. **运行Demo程序**
   ```bash
   cd build
   ./nndeploy_demo_classification --help
   ```

2. **测试推理**
   - 准备ONNX或MNN模型
   - 使用demo程序测试分类/检测功能
   - 参考 `demo/` 目录示例代码

3. **Android TV RK3588部署**
   - 如需RKNN NPU加速，请联系获取交叉编译指导
   - 配置Android NDK环境
   - 使用 `cmake/config_android.cmake`

4. **Python API开发**
   ```python
   import nndeploy
   # 参考 python/examples/ 目录
   ```

## 技术支持

- GitHub: https://github.com/nndeploy/nndeploy
- 文档: 查看项目README和docs目录
- RKNN支持: 框架代码已包含，需单独配置SDK

---

**部署状态**: ✅ 成功完成  
**测试状态**: ✅ Python导入正常  
**RKNN支持**: ✅ 代码已集成，需SDK配置

