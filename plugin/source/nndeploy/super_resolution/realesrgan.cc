#include "nndeploy/super_resolution/realesrgan.h"

#include "nndeploy/base/common.h"
#include "nndeploy/base/glic_stl_include.h"
#include "nndeploy/base/log.h"
#include "nndeploy/base/macro.h"
#include "nndeploy/base/object.h"
#include "nndeploy/base/opencv_include.h"
#include "nndeploy/base/status.h"
#include "nndeploy/dag/edge.h"
#include "nndeploy/dag/graph.h"
#include "nndeploy/dag/node.h"
#include "nndeploy/device/buffer.h"
#include "nndeploy/device/device.h"
#include "nndeploy/device/tensor.h"
#include "nndeploy/infer/infer.h"

namespace nndeploy {
namespace super_resolution {

// ========== RealESRGAN 预处理 ==========
base::Status RealESRGANPreProcess::run() {
  // 获取输入图像
  cv::Mat *input = inputs_[0]->getCvMat(this);
  if (input == nullptr || input->empty()) {
    NNDEPLOY_LOGE("RealESRGANPreProcess: Invalid input image\n");
    return base::kStatusCodeErrorInvalidParam;
  }
  
  try {
    int h = input->rows;
    int w = input->cols;
    int c = input->channels();
    
    if (c != 3) {
      NNDEPLOY_LOGE("RealESRGANPreProcess: Only 3-channel images supported\n");
      return base::kStatusCodeErrorInvalidParam;
    }
    
    // 创建输出 tensor (NCHW format)
    device::TensorDesc desc;
    desc.data_type_ = base::dataTypeOf<float>();
    desc.data_format_ = base::kDataFormatNCHW;
    desc.shape_ = {1, 3, h, w};
    
    device::Tensor *output = new device::Tensor(desc);
    float *data = static_cast<float *>(output->getData());
    
    // BGR -> RGB 并归一化到 [0, 1]
    int hw = h * w;
    for (int i = 0; i < h; ++i) {
      for (int j = 0; j < w; ++j) {
        cv::Vec3b pixel = input->at<cv::Vec3b>(i, j);
        int idx = i * w + j;
        
        // RGB 通道，归一化到 [0, 1]
        data[0 * hw + idx] = pixel[2] / 255.0f;  // R
        data[1 * hw + idx] = pixel[1] / 255.0f;  // G
        data[2 * hw + idx] = pixel[0] / 255.0f;  // B
      }
    }
    
    outputs_[0]->set(output, false);
    
    NNDEPLOY_LOGD("RealESRGANPreProcess: %dx%dx%d -> tensor [1,3,%d,%d]\n",
                  h, w, c, h, w);
    
    return base::kStatusCodeOk;
    
  } catch (const std::exception &e) {
    NNDEPLOY_LOGE("RealESRGANPreProcess: Exception: %s\n", e.what());
    return base::kStatusCodeErrorInvalidParam;
  }
}

// ========== RealESRGAN 后处理 ==========
base::Status RealESRGANPostProcess::run() {
  // 获取输入 tensor
  device::Tensor *input = inputs_[0]->getTensor(this);
  if (input == nullptr) {
    NNDEPLOY_LOGE("RealESRGANPostProcess: Invalid input tensor\n");
    return base::kStatusCodeErrorInvalidParam;
  }
  
  try {
    auto shape = input->getShape();
    if (shape.size() != 4) {
      NNDEPLOY_LOGE("RealESRGANPostProcess: Invalid tensor shape\n");
      return base::kStatusCodeErrorInvalidParam;
    }
    
    int batch = shape[0];
    int channels = shape[1];
    int height = shape[2];
    int width = shape[3];
    
    if (batch != 1 || channels != 3) {
      NNDEPLOY_LOGE("RealESRGANPostProcess: Unsupported tensor format\n");
      return base::kStatusCodeErrorInvalidParam;
    }
    
    float *data = static_cast<float *>(input->getData());
    
    // 创建输出图像
    cv::Mat *output = new cv::Mat(height, width, CV_8UC3);
    
    // Tensor (NCHW) -> BGR cv::Mat，反归一化
    int hw = height * width;
    for (int i = 0; i < height; ++i) {
      for (int j = 0; j < width; ++j) {
        int idx = i * width + j;
        
        // 从 [0, 1] 反归一化到 [0, 255]，并 clip
        float r = std::max(0.0f, std::min(255.0f, data[0 * hw + idx] * 255.0f));
        float g = std::max(0.0f, std::min(255.0f, data[1 * hw + idx] * 255.0f));
        float b = std::max(0.0f, std::min(255.0f, data[2 * hw + idx] * 255.0f));
        
        // RGB -> BGR
        output->at<cv::Vec3b>(i, j) = cv::Vec3b(
          static_cast<uint8_t>(b),
          static_cast<uint8_t>(g),
          static_cast<uint8_t>(r)
        );
      }
    }
    
    outputs_[0]->set(output, false);
    
    NNDEPLOY_LOGD("RealESRGANPostProcess: tensor [1,3,%d,%d] -> %dx%dx3\n",
                  height, width, height, width);
    
    return base::kStatusCodeOk;
    
  } catch (const std::exception &e) {
    NNDEPLOY_LOGE("RealESRGANPostProcess: Exception: %s\n", e.what());
    return base::kStatusCodeErrorInvalidParam;
  }
}

// ========== RealESRGAN 图结构 ==========
base::Status RealESRGAN::init() {
  // 获取参数
  RealESRGANParam *param = dynamic_cast<RealESRGANParam *>(param_.get());
  if (param == nullptr) {
    NNDEPLOY_LOGE("RealESRGAN: Invalid parameters\n");
    return base::kStatusCodeErrorInvalidParam;
  }
  
  // 检查模型路径
  if (param->model_path_.empty()) {
    NNDEPLOY_LOGE("RealESRGAN: Model path is empty\n");
    return base::kStatusCodeErrorInvalidParam;
  }
  
  // 创建预处理节点
  pre_ = dynamic_cast<RealESRGANPreProcess *>(this->createNode<RealESRGANPreProcess>("preprocess"));
  if (pre_ == nullptr) {
    NNDEPLOY_LOGE("RealESRGAN: Failed to create preprocess node\n");
    return base::kStatusCodeErrorInvalidParam;
  }
  pre_->setGraph(this);
  
  // 创建推理节点
  infer_ = dynamic_cast<infer::Infer *>(this->createNode<infer::Infer>("infer"));
  if (infer_ == nullptr) {
    NNDEPLOY_LOGE("RealESRGAN: Failed to create inference node\n");
    return base::kStatusCodeErrorInvalidParam;
  }
  infer_->setGraph(this);
  infer_->setInferenceType(param->inference_type_);
  
  // 配置推理参数
  auto infer_param = dynamic_cast<inference::InferenceParam *>(infer_->getParam());
  if (infer_param == nullptr) {
    NNDEPLOY_LOGE("RealESRGAN: Failed to get inference param\n");
    return base::kStatusCodeErrorInvalidParam;
  }
  
  infer_param->device_type_ = base::kDeviceTypeCodeCpu;
  infer_param->model_type_ = base::kModelTypeOnnx;
  infer_param->is_path_ = true;
  infer_param->model_value_.push_back(param->model_path_);
  
  // 创建后处理节点
  post_ = dynamic_cast<RealESRGANPostProcess *>(this->createNode<RealESRGANPostProcess>("postprocess"));
  if (post_ == nullptr) {
    NNDEPLOY_LOGE("RealESRGAN: Failed to create postprocess node\n");
    return base::kStatusCodeErrorInvalidParam;
  }
  post_->setGraph(this);
  
  NNDEPLOY_LOGI("RealESRGAN: Initialized with model: %s (scale=%dx)\n",
                param->model_path_.c_str(), param->scale_);
  
  return base::kStatusCodeOk;
}

// 注册节点
REGISTER_NODE("nndeploy::super_resolution::RealESRGANPreProcess", 
              RealESRGANPreProcess);
REGISTER_NODE("nndeploy::super_resolution::RealESRGANPostProcess", 
              RealESRGANPostProcess);
REGISTER_NODE("nndeploy::super_resolution::RealESRGAN", 
              RealESRGAN);

}  // namespace super_resolution
}  // namespace nndeploy
