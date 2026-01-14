# Android 端视频脸部追踪超分技术方案

## 📋 项目概述

本方案基于 nndeploy 框架，在 Android 端实现视频中的人脸追踪并对人脸区域进行超分辨率处理，提升视频中人脸的清晰度。

### 核心需求

- **输入**：视频流（从文件或摄像头）
- **处理流程**：
  1. 视频解码为帧序列
  2. 每帧中检测人脸位置
  3. 对检测到的人脸进行追踪（保持ID一致性）
  4. 对追踪到的人脸区域进行超分辨率处理
  5. 将超分后的人脸区域融合回原图
  6. 编码输出视频
- **输出**：超分后的视频文件或实时预览

---

## 🏗️ 技术架构

### 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                    Android Application                       │
├─────────────────────────────────────────────────────────────┤
│  视频输入 → 解码 → 人脸检测 → 人脸追踪 → 超分处理 → 融合 → 编码  │
└─────────────────────────────────────────────────────────────┘
                             ↓
┌─────────────────────────────────────────────────────────────┐
│                   nndeploy Framework                         │
├──────────────────┬──────────────────┬──────────────────────┤
│   Graph/Node     │  Inference       │    Operators         │
│   DAG Executor   │  Backends        │    (Preprocess)      │
└─────────────────────────────────────────────────────────────┘
                             ↓
┌─────────────────────────────────────────────────────────────┐
│               Inference Backends                             │
├──────────────────┬──────────────────┬──────────────────────┤
│  ONNX Runtime    │      MNN         │       ncnn           │
│  (默认推荐)        │   (高性能)        │   (轻量级)            │
└─────────────────────────────────────────────────────────────┘
```

### 工作流设计

基于 nndeploy 的 DAG（有向无环图）架构，设计以下工作流：

```
[VideoInput] → [Decode] → [FaceDetect] → [FaceTrack] → [CropFace] 
                                                            ↓
[VideoOutput] ← [Encode] ← [Merge] ← [SuperResolution] ← [BatchFaces]
```

**节点说明**：

1. **VideoInput**: 视频输入节点（文件或摄像头）
2. **Decode**: OpenCV 视频解码节点
3. **FaceDetect**: YOLO 人脸检测节点
4. **FaceTrack**: FairMot 人脸追踪节点
5. **CropFace**: 人脸裁剪节点（自定义）
6. **BatchFaces**: 人脸批处理节点（优化性能）
7. **SuperResolution**: 超分辨率处理节点
8. **Merge**: 融合节点（将超分人脸贴回原图）
9. **Encode**: OpenCV 视频编码节点
10. **VideoOutput**: 视频输出节点

---

## 🧩 核心模块实现

### 1. 人脸检测模块

**技术选型**：YOLO 系列（推荐 YOLOv8-face 或 YOLOv11）

**优势**：
- nndeploy 已完整支持 YOLOv5/v6/v7/v8/v11
- 速度快，适合移动端实时处理
- Android 端已验证可用（见 `demo2_yolo`）

**实现方式**：

```cpp
// 方式1：使用现有的 YoloGraph
detect::YoloGraph* face_detect = new detect::YoloGraph("face_detect", inputs, outputs);
face_detect->setVersion(8);  // 使用 YOLOv8
face_detect->setInferenceType(base::kInferenceTypeOnnxRuntime);
face_detect->setNumClasses(1);  // 人脸单类别
face_detect->setModelHW(640, 640);
face_detect->setScoreThreshold(0.5);
face_detect->setNmsThreshold(0.45);
```

**模型准备**：
- 使用预训练的 YOLOv8-face 模型
- 导出为 ONNX 格式
- 使用 onnxsim 优化模型
- 参考尺寸：320x320 或 640x640（移动端推荐 320x320）

### 2. 人脸追踪模块

**技术选型**：基于 FairMot 的追踪算法

**优势**：
- nndeploy 已实现 FairMot 追踪（`plugin/source/nndeploy/track/fairmot/`）
- 支持多目标追踪，可维持人脸 ID 一致性
- 内置 JDE Tracker，基于外观特征和运动模型

**实现方式**：

```cpp
// 使用 FairMotGraph 进行追踪
track::FairMotGraph* face_tracker = new track::FairMotGraph("face_tracker", {input}, {output});

// 配置追踪参数
dag::NodeDesc pre_desc("preprocess", {"track_in"}, model_inputs);
dag::NodeDesc infer_desc("infer", model_inputs, model_outputs);
dag::NodeDesc post_desc("postprocess", model_outputs, {"track_out"});

face_tracker->make(pre_desc, infer_desc, inference_type, post_desc);
face_tracker->setInferParam(device_type, model_type, is_path, model_value);
```

**关键数据结构**：

```cpp
// 追踪结果
struct Track {
  int id;           // 人脸ID（持久化）
  float score;      // 置信度
  cv::Vec4f ltrb;   // 边界框 (left, top, right, bottom)
};
```

### 3. 超分辨率模块

**技术选型**：轻量级超分模型

**推荐模型**：
1. **ESRGAN-light**（2x/4x）- 效果好，速度中等
2. **Real-ESRGAN**（2x/4x）- 效果优秀
3. **EDSR-mobile**（2x/4x）- 速度快

**优势**：
- nndeploy 已支持超分模块（`plugin/source/nndeploy/super_resolution/`）
- 输出为 `std::vector<cv::Mat>`，方便后续处理
- 支持批处理，可同时处理多张人脸

**实现方式**：

```cpp
// 使用 SuperResolutionGraph
super_resolution::SuperResolutionGraph* sr_graph = 
    new super_resolution::SuperResolutionGraph("sr_graph", {input}, {output});

// 配置超分参数
dag::NodeDesc pre_desc("preprocess", {"sr_in"}, model_inputs);
dag::NodeDesc infer_desc("infer", model_inputs, model_outputs);
dag::NodeDesc post_desc("postprocess", model_outputs, {"sr_out"});

sr_graph->make(pre_desc, infer_desc, inference_type, post_desc);
sr_graph->setInferenceType(base::kInferenceTypeOnnxRuntime);
sr_graph->setInferParam(device_type, model_type, is_path, model_value);
```

### 4. 自定义节点

需要实现以下自定义节点：

#### 4.1 人脸裁剪节点（CropFaceNode）

```cpp
class CropFaceNode : public dag::Node {
 public:
  CropFaceNode(const std::string& name) : dag::Node(name) {
    key_ = "nndeploy::custom::CropFaceNode";
    this->setInputTypeInfo<cv::Mat, track::MOTResult>();
    this->setOutputTypeInfo<std::vector<FaceCrop>>();
  }
  
  virtual base::Status run() {
    // 1. 获取原图和追踪结果
    cv::Mat* frame = inputs_[0]->getCvMat(this);
    track::MOTResult* tracks = inputs_[1]->get<track::MOTResult>();
    
    // 2. 为每个追踪到的人脸裁剪区域（含扩边）
    auto* face_crops = new std::vector<FaceCrop>();
    for (size_t i = 0; i < tracks->boxes.size(); ++i) {
      FaceCrop crop;
      crop.id = tracks->ids[i];
      crop.bbox = tracks->boxes[i];
      
      // 扩边 20%（确保包含完整人脸）
      int padding = 0.2 * std::max(bbox[2] - bbox[0], bbox[3] - bbox[1]);
      int x1 = std::max(0, bbox[0] - padding);
      int y1 = std::max(0, bbox[1] - padding);
      int x2 = std::min(frame->cols, bbox[2] + padding);
      int y2 = std::min(frame->rows, bbox[3] + padding);
      
      crop.face_roi = (*frame)(cv::Rect(x1, y1, x2-x1, y2-y1)).clone();
      face_crops->push_back(crop);
    }
    
    outputs_[0]->set(face_crops, false);
    return base::kStatusCodeOk;
  }
};

struct FaceCrop {
  int id;                    // 人脸ID
  std::array<int, 4> bbox;   // 原图中的位置
  cv::Mat face_roi;          // 裁剪的人脸图像
};
```

#### 4.2 人脸融合节点（MergeFaceNode）

```cpp
class MergeFaceNode : public dag::Node {
 public:
  MergeFaceNode(const std::string& name) : dag::Node(name) {
    key_ = "nndeploy::custom::MergeFaceNode";
    this->setInputTypeInfo<cv::Mat, std::vector<FaceSRResult>>();
    this->setOutputTypeInfo<cv::Mat>();
  }
  
  virtual base::Status run() {
    // 1. 获取原图和超分结果
    cv::Mat* frame = inputs_[0]->getCvMat(this);
    auto* sr_results = inputs_[1]->get<std::vector<FaceSRResult>>();
    
    // 2. 克隆原图
    cv::Mat output = frame->clone();
    
    // 3. 将超分后的人脸贴回原图
    for (const auto& sr : *sr_results) {
      // 获取目标区域
      cv::Rect roi(sr.bbox[0], sr.bbox[1], 
                   sr.bbox[2] - sr.bbox[0], 
                   sr.bbox[3] - sr.bbox[1]);
      
      // 调整超分图像大小以匹配ROI
      cv::Mat resized_face;
      cv::resize(sr.face_sr, resized_face, roi.size());
      
      // 使用泊松融合或alpha混合
      blendFace(output, resized_face, roi);
    }
    
    outputs_[0]->set(new cv::Mat(output), false);
    return base::kStatusCodeOk;
  }
  
 private:
  void blendFace(cv::Mat& dst, const cv::Mat& src, const cv::Rect& roi) {
    // 羽化边缘，实现平滑过渡
    int feather = 5;
    cv::Mat mask = cv::Mat::zeros(src.size(), CV_32F);
    cv::Rect inner(feather, feather, src.cols - 2*feather, src.rows - 2*feather);
    mask(inner) = 1.0;
    cv::GaussianBlur(mask, mask, cv::Size(feather*2+1, feather*2+1), 0);
    
    // 融合
    for (int y = 0; y < src.rows; ++y) {
      for (int x = 0; x < src.cols; ++x) {
        float alpha = mask.at<float>(y, x);
        cv::Vec3b src_pixel = src.at<cv::Vec3b>(y, x);
        cv::Vec3b dst_pixel = dst.at<cv::Vec3b>(roi.y + y, roi.x + x);
        dst.at<cv::Vec3b>(roi.y + y, roi.x + x) = 
            alpha * src_pixel + (1 - alpha) * dst_pixel;
      }
    }
  }
};

struct FaceSRResult {
  int id;                    // 人脸ID
  std::array<int, 4> bbox;   // 原图中的位置
  cv::Mat face_sr;           // 超分后的人脸
};
```

---

## 🔧 完整工作流实现

### Graph 构建

```cpp
class FaceTrackSuperResolutionGraph : public dag::Graph {
 public:
  FaceTrackSuperResolutionGraph(const std::string& name) : dag::Graph(name) {
    key_ = "FaceTrackSuperResolutionGraph";
    this->setInputTypeInfo<std::string>();  // 视频路径
    this->setOutputTypeInfo<std::string>();  // 输出视频路径
  }
  
  base::Status make() {
    // 1. 视频解码
    codec::Decode* decode = codec::createDecode(
        base::kCodecTypeOpenCV, base::kCodecFlagVideo, "decode", input_);
    this->addNode(decode);
    
    // 2. 人脸检测（使用YOLO）
    detect::YoloGraph* face_detect = new detect::YoloGraph("face_detect");
    face_detect->setVersion(8);
    face_detect->setNumClasses(1);  // 人脸
    face_detect->setInferenceType(base::kInferenceTypeOnnxRuntime);
    // ... 配置检测参数
    this->addNode(face_detect);
    
    // 3. 人脸追踪（使用FairMot）
    track::FairMotGraph* face_track = new track::FairMotGraph("face_track");
    // ... 配置追踪参数
    this->addNode(face_track);
    
    // 4. 人脸裁剪（自定义节点）
    CropFaceNode* crop_face = this->createNode<CropFaceNode>("crop_face");
    this->addNode(crop_face);
    
    // 5. 人脸超分（使用SuperResolutionGraph）
    super_resolution::SuperResolutionGraph* sr = 
        new super_resolution::SuperResolutionGraph("sr");
    // ... 配置超分参数
    this->addNode(sr);
    
    // 6. 人脸融合（自定义节点）
    MergeFaceNode* merge = this->createNode<MergeFaceNode>("merge");
    this->addNode(merge);
    
    // 7. 视频编码
    codec::Encode* encode = codec::createEncode(
        base::kCodecTypeOpenCV, base::kCodecFlagVideo, "encode", output_);
    this->addNode(encode);
    
    return base::kStatusCodeOk;
  }
  
  // 并行执行模式：流水线并行
  base::Status setParallelType() {
    this->setParallelType(base::kParallelTypePipeline);
    return base::kStatusCodeOk;
  }
};
```

### 优化策略

#### 1. 流水线并行

```cpp
// 设置流水线并行模式，提升吞吐量
graph->setParallelType(base::kParallelTypePipeline);
```

**效果**：根据 README 性能测试，流水线并行可提升 **13%-57%** 性能。

#### 2. 批处理优化

```cpp
// 批量处理多张人脸，提高GPU利用率
class BatchFaceNode : public dag::Node {
 public:
  virtual base::Status run() {
    // 累积多帧的人脸
    if (face_batch_.size() < batch_size_) {
      face_batch_.push_back(current_faces);
      return base::kStatusCodeOk;  // 等待更多人脸
    }
    
    // 批量推理
    auto results = batch_inference(face_batch_);
    
    // 清空批次
    face_batch_.clear();
    
    outputs_[0]->set(results, false);
    return base::kStatusCodeOk;
  }
  
 private:
  int batch_size_ = 4;  // 批大小
  std::vector<std::vector<cv::Mat>> face_batch_;
};
```

#### 3. 内存优化

```cpp
// 使用内存池减少内存分配
device::MemoryPool* pool = device::getDefaultMemoryPool(device_type);
pool->setMaxSize(100 * 1024 * 1024);  // 100MB

// 零拷贝优化（在节点间共享数据）
outputs_[0]->set(data, true);  // 第二个参数true表示共享所有权
```

#### 4. 推理加速

**Android 端推荐推理后端**：

| 推理框架      | 适用场景              | 性能  | 包大小  |
|--------------|----------------------|------|---------|
| ONNX Runtime | 通用，默认推荐          | 中等  | ~20MB   |
| MNN          | 高性能，阿里巴巴出品     | 高    | ~5MB    |
| ncnn         | 轻量级，腾讯出品        | 高    | ~2MB    |

**推荐配置**：
- **开发阶段**：使用 ONNX Runtime（兼容性好，易调试）
- **生产部署**：使用 MNN 或 ncnn（性能优，体积小）

---

## 📱 Android 集成

### 1. 编译 Android 库

```bash
# 1. 配置环境变量
export ANDROID_NDK=/path/to/android-ndk-r25c
export ANDROID_SDK=/path/to/android-sdk

# 2. 编译
cd nndeploy
mkdir build_android_arm64 && cd build_android_arm64

cmake -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-21 \
    -DCMAKE_BUILD_TYPE=Release \
    -DENABLE_NNDEPLOY_OPENCV=ON \
    -DENABLE_NNDEPLOY_PLUGIN_DETECT=ON \
    -DENABLE_NNDEPLOY_PLUGIN_TRACK=ON \
    -DENABLE_NNDEPLOY_PLUGIN_SUPER_RESOLUTION=ON \
    ..

ninja
ninja install
```

### 2. 拷贝库到 Android 项目

```bash
# 拷贝 SO 库
python3 tool/script/android_install_so.py \
    /path/to/build/nndeploy_xxx_Android_aarch64_Release_Clang \
    /path/to/app/android/app/src/main/jniLibs/arm64-v8a

# 拷贝资源文件（模型、配置等）
python3 tool/script/android_install_resouces.py \
    -r /path/to/resources/ \
    -a /path/to/app/android/app/src/main/assets
```

**必需的 SO 库**：
- `libnndeploy_framework.so` - 框架核心
- `libnndeploy_inference_onnxruntime.so` - ONNX Runtime 后端
- `libnndeploy_plugin_detect.so` - 检测模块
- `libnndeploy_plugin_track.so` - 追踪模块（**2.3 MB**）
- `libnndeploy_plugin_super_resolution.so` - 超分模块（**692 KB**）
- `libc++_shared.so` - C++ 标准库
- `libonnxruntime.so` - ONNX Runtime 引擎

### 3. JNI 接口

```kotlin
// GraphRunner.kt
class FaceTrackSRRunner {
    external fun init(
        modelDir: String,
        yoloModelPath: String,
        trackModelPath: String,
        srModelPath: String
    ): Boolean
    
    external fun processVideo(
        inputPath: String,
        outputPath: String,
        progressCallback: (Int) -> Unit
    ): Boolean
    
    external fun release(): Boolean
    
    companion object {
        init {
            System.loadLibrary("nndeploy_framework")
            System.loadLibrary("nndeploy_inference_onnxruntime")
            System.loadLibrary("nndeploy_plugin_detect")
            System.loadLibrary("nndeploy_plugin_track")
            System.loadLibrary("nndeploy_plugin_super_resolution")
            System.loadLibrary("face_track_sr_jni")  // 自定义JNI
        }
    }
}
```

```cpp
// face_track_sr_jni.cpp
extern "C" JNIEXPORT jboolean JNICALL
Java_com_nndeploy_FaceTrackSRRunner_init(
    JNIEnv* env, jobject thiz,
    jstring model_dir,
    jstring yolo_model,
    jstring track_model,
    jstring sr_model) {
    
    // 1. 创建工作流
    auto graph = std::make_shared<FaceTrackSuperResolutionGraph>("face_track_sr");
    
    // 2. 配置模型路径
    // ...
    
    // 3. 初始化
    base::Status status = graph->init();
    if (status != base::kStatusCodeOk) {
        return JNI_FALSE;
    }
    
    // 4. 保存到全局
    g_graph = graph;
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_nndeploy_FaceTrackSRRunner_processVideo(
    JNIEnv* env, jobject thiz,
    jstring input_path,
    jstring output_path,
    jobject callback) {
    
    // 1. 设置输入输出
    const char* input = env->GetStringUTFChars(input_path, nullptr);
    const char* output = env->GetStringUTFChars(output_path, nullptr);
    
    g_graph->setInputPath(input);
    g_graph->setOutputPath(output);
    
    // 2. 运行
    base::Status status = g_graph->run();
    
    // 3. 清理
    env->ReleaseStringUTFChars(input_path, input);
    env->ReleaseStringUTFChars(output_path, output);
    
    return status == base::kStatusCodeOk ? JNI_TRUE : JNI_FALSE;
}
```

### 4. Android UI

```kotlin
// MainActivity.kt
class MainActivity : ComponentActivity() {
    private val runner = FaceTrackSRRunner()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化
        val modelDir = "${filesDir.absolutePath}/models"
        runner.init(
            modelDir,
            "$modelDir/yolov8_face.onnx",
            "$modelDir/fairmot.onnx",
            "$modelDir/esrgan_4x.onnx"
        )
        
        setContent {
            FaceTrackSRScreen(runner)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        runner.release()
    }
}

@Composable
fun FaceTrackSRScreen(runner: FaceTrackSRRunner) {
    var inputVideo by remember { mutableStateOf("") }
    var outputVideo by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0) }
    var isProcessing by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // 选择输入视频
        Button(onClick = { /* 打开文件选择器 */ }) {
            Text("选择视频")
        }
        
        // 进度条
        if (isProcessing) {
            LinearProgressIndicator(
                progress = progress / 100f,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
            )
        }
        
        // 开始处理
        Button(
            onClick = {
                isProcessing = true
                CoroutineScope(Dispatchers.IO).launch {
                    val success = runner.processVideo(
                        inputVideo,
                        outputVideo
                    ) { p -> progress = p }
                    
                    withContext(Dispatchers.Main) {
                        isProcessing = false
                        if (success) {
                            // 显示成功
                        }
                    }
                }
            },
            enabled = !isProcessing && inputVideo.isNotEmpty()
        ) {
            Text("开始处理")
        }
        
        // 预览结果
        if (outputVideo.isNotEmpty() && !isProcessing) {
            VideoPlayer(videoPath = outputVideo)
        }
    }
}
```

---

## 📦 模型准备

### 1. 人脸检测模型

**选项 A: YOLOv8-face**

```bash
# 1. 下载预训练模型
wget https://github.com/derronqi/yolov8-face/releases/download/v1.0/yolov8n-face.pt

# 2. 导出 ONNX
python export.py --weights yolov8n-face.pt --img 320 --batch 1

# 3. 简化模型
onnxsim yolov8n-face.onnx yolov8n-face-sim.onnx

# 4. 拷贝到 assets
cp yolov8n-face-sim.onnx app/android/app/src/main/assets/models/
```

**选项 B: YOLOv5-face**

```bash
# 类似流程，使用 yolov5-face 仓库
git clone https://github.com/deepcam-cn/yolov5-face
# ... 导出和简化
```

### 2. 人脸追踪模型

```bash
# 使用 FairMot 官方模型或重训练
# 注意：FairMot 需要检测和 ReID 特征，确保模型输出包含：
# - bbox: [N, 6] (x, y, w, h, conf, class)
# - embedding: [N, 128] (ReID feature)

# 拷贝到 assets
cp fairmot.onnx app/android/app/src/main/assets/models/
```

### 3. 超分模型

**选项 A: Real-ESRGAN**

```bash
# 1. 克隆仓库
git clone https://github.com/xinntao/Real-ESRGAN

# 2. 下载预训练模型
wget https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.1/RealESRGAN_x4plus.pth

# 3. 导出 ONNX（修改 inference_realesrgan.py）
import torch
from basicsr.archs.rrdbnet_arch import RRDBNet

model = RRDBNet(num_in_ch=3, num_out_ch=3, num_feat=64, num_block=23, num_grow_ch=32)
model.load_state_dict(torch.load('RealESRGAN_x4plus.pth')['params_ema'])
model.eval()

dummy_input = torch.randn(1, 3, 64, 64)
torch.onnx.export(
    model, dummy_input, "realesrgan_4x.onnx",
    input_names=["input"],
    output_names=["output"],
    dynamic_axes={"input": {0: "batch", 2: "height", 3: "width"},
                  "output": {0: "batch", 2: "height", 3: "width"}}
)

# 4. 简化
onnxsim realesrgan_4x.onnx realesrgan_4x-sim.onnx

# 5. 拷贝到 assets
cp realesrgan_4x-sim.onnx app/android/app/src/main/assets/models/
```

**选项 B: EDSR-mobile（推荐移动端）**

```bash
# 更轻量，适合实时处理
# 参考：https://github.com/sanghyun-son/EDSR-PyTorch
```

---

## 🎯 工作流 JSON 配置

基于 nndeploy 的可视化工作流，可以先在桌面端搭建和调试，然后导出 JSON 配置到 Android。

```json
{
  "name": "FaceTrackSuperResolution",
  "nodes": [
    {
      "name": "decode",
      "type": "nndeploy::codec::OpenCvVideoDecode",
      "inputs": ["video_path"],
      "outputs": ["frame"],
      "params": {}
    },
    {
      "name": "face_detect",
      "type": "nndeploy::detect::YoloGraph",
      "inputs": ["frame"],
      "outputs": ["detections"],
      "params": {
        "version": 8,
        "num_classes": 1,
        "score_threshold": 0.5,
        "nms_threshold": 0.45,
        "model_h": 320,
        "model_w": 320,
        "inference_type": "kInferenceTypeOnnxRuntime",
        "model_path": "models/yolov8n-face-sim.onnx"
      }
    },
    {
      "name": "face_track",
      "type": "nndeploy::track::FairMotGraph",
      "inputs": ["detections"],
      "outputs": ["tracks"],
      "params": {
        "conf_thresh": 0.5,
        "tracked_thresh": 0.4,
        "min_box_area": 100,
        "model_path": "models/fairmot.onnx"
      }
    },
    {
      "name": "crop_face",
      "type": "nndeploy::custom::CropFaceNode",
      "inputs": ["frame", "tracks"],
      "outputs": ["face_crops"],
      "params": {
        "padding_ratio": 0.2
      }
    },
    {
      "name": "super_resolution",
      "type": "nndeploy::super_resolution::SuperResolutionGraph",
      "inputs": ["face_crops"],
      "outputs": ["sr_faces"],
      "params": {
        "model_path": "models/realesrgan_4x-sim.onnx",
        "scale": 4
      }
    },
    {
      "name": "merge_face",
      "type": "nndeploy::custom::MergeFaceNode",
      "inputs": ["frame", "sr_faces"],
      "outputs": ["output_frame"],
      "params": {
        "feather": 5
      }
    },
    {
      "name": "encode",
      "type": "nndeploy::codec::OpenCvVideoEncode",
      "inputs": ["output_frame"],
      "outputs": ["output_path"],
      "params": {
        "fps": 30,
        "codec": "mp4v"
      }
    }
  ],
  "edges": [
    {"from": "decode.frame", "to": "face_detect.frame"},
    {"from": "face_detect.detections", "to": "face_track.detections"},
    {"from": "decode.frame", "to": "crop_face.frame"},
    {"from": "face_track.tracks", "to": "crop_face.tracks"},
    {"from": "crop_face.face_crops", "to": "super_resolution.face_crops"},
    {"from": "decode.frame", "to": "merge_face.frame"},
    {"from": "super_resolution.sr_faces", "to": "merge_face.sr_faces"},
    {"from": "merge_face.output_frame", "to": "encode.output_frame"}
  ],
  "parallel_type": "kParallelTypePipeline"
}
```

**使用方式**：

```kotlin
// 在 Android 中加载 JSON 配置
val jsonPath = "workflow/face_track_sr.json"
val graph = Graph("")
graph.removeInOutNode()
graph.loadFile(assetManager.open(jsonPath))
graph.init()

// 设置输入
val input = graph.getInput(0)
input.set(videoPath)

// 运行
graph.run()

// 获取输出
val output = graph.getOutput(0)
val resultPath = output.getGraphOutput<String>()

// 清理
graph.deinit()
```

---

## 🚀 性能优化建议

### 1. 模型量化

```python
# ONNX 模型量化（INT8）
import onnxruntime as ort
from onnxruntime.quantization import quantize_dynamic, QuantType

model_fp32 = 'yolov8n-face-sim.onnx'
model_quant = 'yolov8n-face-sim-int8.onnx'

quantize_dynamic(
    model_fp32,
    model_quant,
    weight_type=QuantType.QUInt8
)
```

**效果**：
- 模型大小减少 **70-75%**
- 推理速度提升 **2-3x**
- 精度损失 < **2%**

### 2. 输入尺寸优化

| 分辨率  | 检测精度 | 速度    | 推荐场景       |
|--------|---------|---------|---------------|
| 320x320| 中      | 快       | 实时预览       |
| 640x640| 高      | 中       | 离线处理       |
| 1280x1280| 极高   | 慢       | 高质量要求     |

**推荐策略**：
- **实时模式**：320x320（检测） + 2x（超分）
- **高质量模式**：640x640（检测） + 4x（超分）

### 3. 帧率控制

```cpp
// 仅对关键帧进行追踪和超分
class KeyFrameSelector : public dag::Node {
 public:
  virtual base::Status run() {
    frame_count_++;
    
    // 每 3 帧处理一次
    if (frame_count_ % 3 != 0) {
      outputs_[0]->set(nullptr, true);  // 跳过
      return base::kStatusCodeOk;
    }
    
    // 处理关键帧
    outputs_[0]->set(inputs_[0]->get<cv::Mat>(), true);
    return base::kStatusCodeOk;
  }
  
 private:
  int frame_count_ = 0;
};
```

### 4. GPU 加速

```cpp
// 使用 GPU 推理
graph->setInferParam(
    base::kDeviceTypeCodeCuda,  // 或 base::kDeviceTypeCodeOpencl
    model_type, is_path, model_value
);
```

**注意**：Android 端 GPU 支持需要：
- **ONNX Runtime GPU**：需要额外编译（较复杂）
- **ncnn Vulkan**：推荐，易于集成
- **MNN Metal**：iOS 专用

---

## 📊 性能评估

### 预期性能（基于类似场景）

**测试环境**：
- 设备：骁龙 888 / 旗舰手机
- 视频：1080p @ 30fps
- 人脸数：1-3 个

| 配置               | 检测耗时 | 追踪耗时 | 超分耗时 | 总耗时  | FPS    |
|-------------------|---------|---------|---------|--------|--------|
| 320+2x (实时)      | 15ms    | 8ms     | 50ms    | ~73ms  | **13** |
| 640+4x (高质量)    | 45ms    | 12ms    | 200ms   | ~257ms | **3.8**|
| 320+2x+流水线并行  | 15ms    | 8ms     | 50ms    | ~40ms  | **25** |

**优化后（流水线并行）**：
- 实时模式可达 **20-25 FPS**
- 高质量模式可达 **5-8 FPS**

### 内存占用

| 组件       | 内存占用   |
|-----------|-----------|
| YOLO检测   | ~30MB     |
| FairMot追踪| ~50MB     |
| 超分模型   | ~40MB     |
| 运行时缓存 | ~100MB    |
| **总计**   | **~220MB**|

---

## 🛠️ 开发步骤

### 第一阶段：桌面端原型（1-2 天）

1. **搭建基础工作流**
   - 使用 nndeploy 可视化界面
   - 测试 YOLO 检测 + 超分流程
   - 验证效果

2. **实现自定义节点**
   - 开发 `CropFaceNode`
   - 开发 `MergeFaceNode`
   - 单元测试

3. **集成追踪模块**
   - 配置 FairMot
   - 验证 ID 持久性

4. **导出 JSON 配置**
   - 保存工作流
   - 测试加载

### 第二阶段：Android 移植（2-3 天）

1. **编译 Android 库**
   - 配置 CMake
   - 编译 arm64 版本
   - 验证 SO 库

2. **开发 JNI 接口**
   - 封装 C++ 逻辑
   - 实现进度回调
   - 错误处理

3. **开发 Android UI**
   - 视频选择
   - 进度显示
   - 结果预览

4. **模型集成**
   - 转换模型格式
   - 打包到 assets
   - 测试加载

### 第三阶段：优化与测试（2-3 天）

1. **性能优化**
   - 模型量化
   - 流水线并行
   - 内存优化

2. **功能测试**
   - 不同视频测试
   - 多人脸场景
   - 边界情况

3. **用户体验**
   - 进度提示
   - 错误提示
   - 性能监控

---

## 📝 注意事项

### 1. 模型兼容性

- **YOLO 人脸模型**：确保输出格式与 nndeploy 的 YoloPostProcess 兼容
  - YOLOv8: [batch, 84+num_class, num_boxes] (需转置)
  - YOLOv5: [batch, num_boxes, 85+num_class]

### 2. 追踪稳定性

- **遮挡处理**：当人脸被遮挡时，追踪可能失败
  - 解决：使用外观特征（ReID）+ 运动模型
  - FairMot 已内置此功能

- **ID 切换**：快速运动可能导致 ID 错误分配
  - 解决：调整 `tracked_thresh` 参数
  - 增加时间窗口平滑

### 3. 超分效果

- **输入质量**：过度模糊的人脸超分效果有限
  - 建议：对 score < 0.3 的检测框跳过超分

- **分辨率匹配**：超分倍数需与人脸大小匹配
  - 小人脸（<64px）：4x
  - 中人脸（64-128px）：2x
  - 大人脸（>128px）：不需要超分

### 4. 内存管理

- **大视频处理**：长视频可能导致内存溢出
  - 解决：分段处理，每处理 N 帧清理一次缓存

- **批处理权衡**：batch_size 过大会增加延迟
  - 实时场景：batch_size = 1
  - 离线场景：batch_size = 4-8

---

## 🔗 参考资源

### nndeploy 相关

- [nndeploy GitHub](https://github.com/nndeploy/nndeploy)
- [编译文档](../../quick_start/build.md)
- [工作流文档](../../quick_start/workflow.md)
- [Android 示例](../../../app/android/README.md)

### 模型资源

- [YOLOv8-face](https://github.com/derronqi/yolov8-face)
- [FairMot](https://github.com/ifzhang/FairMOT)
- [Real-ESRGAN](https://github.com/xinntao/Real-ESRGAN)
- [EDSR](https://github.com/sanghyun-son/EDSR-PyTorch)

### 推理框架

- [ONNX Runtime](https://onnxruntime.ai/)
- [MNN](https://github.com/alibaba/MNN)
- [ncnn](https://github.com/Tencent/ncnn)

---

## 💡 扩展功能

### 1. 实时预览

```cpp
// 添加实时预览回调
class PreviewCallback : public dag::Node {
 public:
  virtual base::Status run() {
    cv::Mat* frame = inputs_[0]->getCvMat(this);
    
    // 调用 JNI 回调显示预览
    if (preview_callback_) {
      preview_callback_(frame);
    }
    
    outputs_[0]->set(frame, true);
    return base::kStatusCodeOk;
  }
  
  void setPreviewCallback(std::function<void(cv::Mat*)> callback) {
    preview_callback_ = callback;
  }
  
 private:
  std::function<void(cv::Mat*)> preview_callback_;
};
```

### 2. 美颜功能

```cpp
// 在超分后添加美颜处理
class BeautifyNode : public dag::Node {
 public:
  virtual base::Status run() {
    cv::Mat* face = inputs_[0]->getCvMat(this);
    
    // 磨皮
    cv::Mat smooth;
    cv::bilateralFilter(*face, smooth, 9, 75, 75);
    
    // 美白
    cv::Mat brightened;
    smooth.convertTo(brightened, -1, 1.0, 10);
    
    outputs_[0]->set(new cv::Mat(brightened), false);
    return base::kStatusCodeOk;
  }
};
```

### 3. 多种超分模式

```kotlin
enum class SRMode {
    FAST,      // 2x, 轻量模型
    BALANCED,  // 2x, 标准模型
    QUALITY    // 4x, 高质量模型
}

fun selectSRModel(mode: SRMode): String {
    return when(mode) {
        SRMode.FAST -> "models/edsr_2x.onnx"
        SRMode.BALANCED -> "models/realesrgan_2x.onnx"
        SRMode.QUALITY -> "models/realesrgan_4x.onnx"
    }
}
```

---

## 📞 技术支持

遇到问题可参考：

1. **nndeploy 文档**：[https://nndeploy-zh.readthedocs.io](https://nndeploy-zh.readthedocs.io)
2. **GitHub Issues**：[https://github.com/nndeploy/nndeploy/issues](https://github.com/nndeploy/nndeploy/issues)
3. **微信群**：参考 [docs/zh_cn/knowledge_shared/wechat.md](../../knowledge_shared/wechat.md)
4. **Discord**：[https://discord.gg/9rUwfAaMbr](https://discord.gg/9rUwfAaMbr)

---

## 📄 总结

本技术方案基于 nndeploy 框架，充分利用其：
- ✅ **可视化工作流**：快速搭建和调试
- ✅ **多端支持**：一次开发，Android/iOS 通用
- ✅ **高性能**：流水线并行、内存优化
- ✅ **已验证模块**：YOLO 检测、FairMot 追踪、超分辨率均已实现
- ✅ **易于扩展**：自定义节点开发简单

**预期开发周期**：7-10 天
- 桌面原型：2 天
- Android 移植：3 天
- 优化测试：3 天
- 集成调试：2 天

**核心优势**：
1. 无需从零开发，复用 nndeploy 已有能力
2. 工作流可视化，易于调试和迭代
3. 性能优秀，支持实时处理
4. 代码结构清晰，易于维护

祝开发顺利！🎉
