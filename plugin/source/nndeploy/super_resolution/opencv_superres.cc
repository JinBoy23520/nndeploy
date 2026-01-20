#include "nndeploy/super_resolution/opencv_superres.h"

#include "nndeploy/base/common.h"
#include "nndeploy/base/glic_stl_include.h"
#include "nndeploy/base/log.h"
#include "nndeploy/base/macro.h"
#include "nndeploy/base/object.h"
#include "nndeploy/base/opencv_include.h"
#include "nndeploy/base/status.h"
#include "nndeploy/dag/edge.h"
#include "nndeploy/dag/node.h"

namespace nndeploy {
namespace super_resolution {

cv::Mat OpenCVSuperRes::applySharpen(const cv::Mat &img, float amount) {
  cv::Mat blurred, sharpened;
  
  // 使用高斯模糊
  cv::GaussianBlur(img, blurred, cv::Size(0, 0), 3);
  
  // Unsharp Mask 锐化
  cv::addWeighted(img, 1.0 + amount, blurred, -amount, 0, sharpened);
  
  return sharpened;
}

cv::Mat OpenCVSuperRes::lanczosUpscale(const cv::Mat &img, int scale) {
  cv::Mat upscaled;
  
  int new_width = img.cols * scale;
  int new_height = img.rows * scale;
  
  // 使用 Lanczos 插值进行高质量缩放
  cv::resize(img, upscaled, cv::Size(new_width, new_height), 
             0, 0, cv::INTER_LANCZOS4);
  
  return upscaled;
}

base::Status OpenCVSuperRes::run() {
  // 获取输入
  cv::Mat *input = inputs_[0]->getCvMat(this);
  if (input == nullptr || input->empty()) {
    NNDEPLOY_LOGE("OpenCVSuperRes: Invalid input image\n");
    return base::kStatusCodeErrorInvalidParam;
  }
  
  // 获取参数
  OpenCVSuperResParam *param = dynamic_cast<OpenCVSuperResParam *>(param_.get());
  if (param == nullptr) {
    NNDEPLOY_LOGE("OpenCVSuperRes: Invalid parameters\n");
    return base::kStatusCodeErrorInvalidParam;
  }
  
  try {
    // 1. Lanczos 插值放大
    cv::Mat output = lanczosUpscale(*input, param->scale_);
    
    // 2. 可选：应用锐化增强
    if (param->sharpen_ && param->sharpen_amount_ > 0.0f) {
      output = applySharpen(output, param->sharpen_amount_);
    }
    
    // 确保输出是连续的内存
    if (!output.isContinuous()) {
      output = output.clone();
    }
    
    // 设置输出
    outputs_[0]->set(new cv::Mat(output), false);
    
    NNDEPLOY_LOGD("OpenCVSuperRes: %dx%d -> %dx%d (scale=%d)\n",
                  input->cols, input->rows, 
                  output.cols, output.rows, 
                  param->scale_);
    
    return base::kStatusCodeOk;
    
  } catch (const cv::Exception &e) {
    NNDEPLOY_LOGE("OpenCVSuperRes: OpenCV exception: %s\n", e.what());
    return base::kStatusCodeErrorInvalidParam;
  } catch (const std::exception &e) {
    NNDEPLOY_LOGE("OpenCVSuperRes: Exception: %s\n", e.what());
    return base::kStatusCodeErrorInvalidParam;
  }
}

// 注册节点
REGISTER_NODE("nndeploy::super_resolution::OpenCVSuperRes", OpenCVSuperRes);

}  // namespace super_resolution
}  // namespace nndeploy
