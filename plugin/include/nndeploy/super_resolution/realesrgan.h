#ifndef _NNDEPLOY_SUPER_RESOLUTION_REALESRGAN_H_
#define _NNDEPLOY_SUPER_RESOLUTION_REALESRGAN_H_

#include "nndeploy/base/common.h"
#include "nndeploy/base/glic_stl_include.h"
#include "nndeploy/base/log.h"
#include "nndeploy/base/macro.h"
#include "nndeploy/base/object.h"
#include "nndeploy/base/opencv_include.h"
#include "nndeploy/base/param.h"
#include "nndeploy/base/status.h"
#include "nndeploy/dag/edge.h"
#include "nndeploy/dag/graph.h"
#include "nndeploy/dag/node.h"
#include "nndeploy/device/tensor.h"
#include "nndeploy/infer/infer.h"
#include "nndeploy/preprocess/cvt_norm_trans.h"

namespace nndeploy {
namespace super_resolution {

/**
 * @brief RealESRGAN 参数
 */
class NNDEPLOY_CC_API RealESRGANParam : public base::Param {
 public:
  std::string model_path_;             ///< 模型路径 (.onnx)
  int scale_ = 2;                      ///< 放大倍数 (2 or 4)
  int tile_size_ = 0;                  ///< tile模式大小，0表示全图处理
  base::InferenceType inference_type_ = base::kInferenceTypeOnnxRuntime;
  
  RealESRGANParam() : base::Param() {}
  virtual ~RealESRGANParam() {}
  
  PARAM_COPY(RealESRGANParam)
  PARAM_COPY_TO(RealESRGANParam)
};

/**
 * @brief RealESRGAN 预处理节点
 * @details 将 BGR 图像转换为 RGB float32 tensor (NCHW)
 */
class NNDEPLOY_CC_API RealESRGANPreProcess : public dag::Node {
 public:
  RealESRGANPreProcess(const std::string &name) : dag::Node(name) {
    key_ = "nndeploy::super_resolution::RealESRGANPreProcess";
    this->setInputTypeInfo<cv::Mat>();
    this->setOutputTypeInfo<device::Tensor>();
  }
  
  RealESRGANPreProcess(const std::string &name,
                       std::vector<dag::Edge *> inputs,
                       std::vector<dag::Edge *> outputs)
      : dag::Node(name, inputs, outputs) {
    key_ = "nndeploy::super_resolution::RealESRGANPreProcess";
    this->setInputTypeInfo<cv::Mat>();
    this->setOutputTypeInfo<device::Tensor>();
  }
  
  virtual ~RealESRGANPreProcess() {}
  virtual base::Status run();
};

/**
 * @brief RealESRGAN 后处理节点
 * @details 将 float32 tensor (NCHW) 转换为 BGR cv::Mat
 */
class NNDEPLOY_CC_API RealESRGANPostProcess : public dag::Node {
 public:
  RealESRGANPostProcess(const std::string &name) : dag::Node(name) {
    key_ = "nndeploy::super_resolution::RealESRGANPostProcess";
    this->setInputTypeInfo<device::Tensor>();
    this->setOutputTypeInfo<cv::Mat>();
  }
  
  RealESRGANPostProcess(const std::string &name,
                        std::vector<dag::Edge *> inputs,
                        std::vector<dag::Edge *> outputs)
      : dag::Node(name, inputs, outputs) {
    key_ = "nndeploy::super_resolution::RealESRGANPostProcess";
    this->setInputTypeInfo<device::Tensor>();
    this->setOutputTypeInfo<cv::Mat>();
  }
  
  virtual ~RealESRGANPostProcess() {}
  virtual base::Status run();
};

/**
 * @brief RealESRGAN 超分辨率图结构
 * @details 包含预处理、推理、后处理三个节点
 */
class NNDEPLOY_CC_API RealESRGAN : public dag::Graph {
 public:
  RealESRGAN(const std::string &name) : dag::Graph(name) {
    key_ = "nndeploy::super_resolution::RealESRGAN";
    this->setInputTypeInfo<cv::Mat>();
    this->setOutputTypeInfo<cv::Mat>();
    param_ = std::make_shared<RealESRGANParam>();
  }
  
  RealESRGAN(const std::string &name,
             std::vector<dag::Edge *> inputs,
             std::vector<dag::Edge *> outputs)
      : dag::Graph(name, inputs, outputs) {
    key_ = "nndeploy::super_resolution::RealESRGAN";
    this->setInputTypeInfo<cv::Mat>();
    this->setOutputTypeInfo<cv::Mat>();
    param_ = std::make_shared<RealESRGANParam>();
  }
  
  virtual ~RealESRGAN() {}
  
  virtual base::Status init();
  
 private:
  RealESRGANPreProcess *pre_ = nullptr;   ///< 预处理节点
  infer::Infer *infer_ = nullptr;         ///< 推理节点
  RealESRGANPostProcess *post_ = nullptr; ///< 后处理节点
};

}  // namespace super_resolution
}  // namespace nndeploy

#endif  // _NNDEPLOY_SUPER_RESOLUTION_REALESRGAN_H_
