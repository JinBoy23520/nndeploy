#ifndef _NNDEPLOY_SUPER_RESOLUTION_OPENCV_SUPERRES_H_
#define _NNDEPLOY_SUPER_RESOLUTION_OPENCV_SUPERRES_H_

#include "nndeploy/base/common.h"
#include "nndeploy/base/glic_stl_include.h"
#include "nndeploy/base/log.h"
#include "nndeploy/base/macro.h"
#include "nndeploy/base/object.h"
#include "nndeploy/base/opencv_include.h"
#include "nndeploy/base/param.h"
#include "nndeploy/base/status.h"
#include "nndeploy/dag/edge.h"
#include "nndeploy/dag/node.h"

namespace nndeploy {
namespace super_resolution {

/**
 * @brief OpenCV SuperRes 参数
 */
class NNDEPLOY_CC_API OpenCVSuperResParam : public base::Param {
 public:
  int scale_ = 2;                      ///< 放大倍数 (2, 3, 4)
  bool sharpen_ = true;                ///< 是否应用锐化
  float sharpen_amount_ = 0.8f;        ///< 锐化强度 (0.0-1.0)
  
  OpenCVSuperResParam() : base::Param() {}
  virtual ~OpenCVSuperResParam() {}
  
  PARAM_COPY(OpenCVSuperResParam)
  PARAM_COPY_TO(OpenCVSuperResParam)
};

/**
 * @brief OpenCV 超分辨率节点（使用 Lanczos 插值 + 锐化）
 * @details 轻量级超分方案，无需深度学习模型，适合实时处理
 */
class NNDEPLOY_CC_API OpenCVSuperRes : public dag::Node {
 public:
  OpenCVSuperRes(const std::string &name) : dag::Node(name) {
    key_ = "nndeploy::super_resolution::OpenCVSuperRes";
    this->setInputTypeInfo<cv::Mat>();
    this->setOutputTypeInfo<cv::Mat>();
    param_ = std::make_shared<OpenCVSuperResParam>();
  }
  
  OpenCVSuperRes(const std::string &name,
                 std::vector<dag::Edge *> inputs,
                 std::vector<dag::Edge *> outputs)
      : dag::Node(name, inputs, outputs) {
    key_ = "nndeploy::super_resolution::OpenCVSuperRes";
    this->setInputTypeInfo<cv::Mat>();
    this->setOutputTypeInfo<cv::Mat>();
    param_ = std::make_shared<OpenCVSuperResParam>();
  }
  
  virtual ~OpenCVSuperRes() {}

  virtual base::Status run();

 private:
  /**
   * @brief 应用锐化滤波器
   * @param img 输入图像
   * @param amount 锐化强度
   * @return 锐化后的图像
   */
  cv::Mat applySharpen(const cv::Mat &img, float amount);
  
  /**
   * @brief 使用 Lanczos 插值放大图像
   * @param img 输入图像
   * @param scale 放大倍数
   * @return 放大后的图像
   */
  cv::Mat lanczosUpscale(const cv::Mat &img, int scale);
};

}  // namespace super_resolution
}  // namespace nndeploy

#endif  // _NNDEPLOY_SUPER_RESOLUTION_OPENCV_SUPERRES_H_
