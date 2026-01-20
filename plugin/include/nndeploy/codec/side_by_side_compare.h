#ifndef _NNDEPLOY_CODEC_SIDE_BY_SIDE_COMPARE_H_
#define _NNDEPLOY_CODEC_SIDE_BY_SIDE_COMPARE_H_

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
namespace codec {

/**
 * @brief 左右对比显示参数
 */
class NNDEPLOY_CC_API SideBySideCompareParam : public base::Param {
 public:
  std::string window_name_ = "Side-by-Side Comparison";  ///< 窗口名称
  bool show_labels_ = true;                                ///< 是否显示标签
  std::string left_label_ = "Original";                   ///< 左侧标签
  std::string right_label_ = "Super-Resolution";          ///< 右侧标签
  bool auto_resize_ = true;                               ///< 自动调整显示大小
  int max_display_width_ = 1920;                          ///< 最大显示宽度
  int max_display_height_ = 1080;                         ///< 最大显示高度
  bool save_output_ = false;                              ///< 是否保存输出
  std::string output_path_;                               ///< 输出路径
  
  SideBySideCompareParam() : base::Param() {}
  virtual ~SideBySideCompareParam() {}
  
  PARAM_COPY(SideBySideCompareParam)
  PARAM_COPY_TO(SideBySideCompareParam)
};

/**
 * @brief 左右对比显示节点
 * @details 将两个输入图像左右拼接显示，用于超分效果对比
 * 
 * 输入:
 *   - input[0]: 原始图像 (cv::Mat)
 *   - input[1]: 超分图像 (cv::Mat)
 * 输出:
 *   - output[0]: 左右拼接图像 (cv::Mat)
 */
class NNDEPLOY_CC_API SideBySideCompare : public dag::Node {
 public:
  SideBySideCompare(const std::string &name) : dag::Node(name) {
    key_ = "nndeploy::codec::SideBySideCompare";
    this->setInputTypeInfo<cv::Mat>();
    this->setOutputTypeInfo<cv::Mat>();
    param_ = std::make_shared<SideBySideCompareParam>();
  }
  
  SideBySideCompare(const std::string &name,
                    std::vector<dag::Edge *> inputs,
                    std::vector<dag::Edge *> outputs)
      : dag::Node(name, inputs, outputs) {
    key_ = "nndeploy::codec::SideBySideCompare";
    this->setInputTypeInfo<cv::Mat>();
    this->setOutputTypeInfo<cv::Mat>();
    param_ = std::make_shared<SideBySideCompareParam>();
  }
  
  virtual ~SideBySideCompare() {}

  virtual base::Status run();

 private:
  /**
   * @brief 调整图像大小以适应显示
   * @param img 输入图像
   * @param max_width 最大宽度
   * @param max_height 最大高度
   * @return 调整后的图像
   */
  cv::Mat resizeForDisplay(const cv::Mat &img, int max_width, int max_height);
  
  /**
   * @brief 在图像上添加文本标签
   * @param img 输入图像
   * @param label 标签文本
   * @param position 标签位置 (0=左上, 1=右上)
   */
  void addLabel(cv::Mat &img, const std::string &label, int position);
  
  /**
   * @brief 水平拼接两张图像
   * @param left 左侧图像
   * @param right 右侧图像
   * @return 拼接后的图像
   */
  cv::Mat concatenateHorizontal(const cv::Mat &left, const cv::Mat &right);
};

/**
 * @brief 视频左右对比节点
 * @details 用于视频流的左右对比显示，支持实时处理
 */
class NNDEPLOY_CC_API VideoSideBySideCompare : public dag::Node {
 public:
  VideoSideBySideCompare(const std::string &name) : dag::Node(name) {
    key_ = "nndeploy::codec::VideoSideBySideCompare";
    this->setInputTypeInfo<cv::Mat>();
    this->setOutputTypeInfo<cv::Mat>();
    param_ = std::make_shared<SideBySideCompareParam>();
  }
  
  VideoSideBySideCompare(const std::string &name,
                         std::vector<dag::Edge *> inputs,
                         std::vector<dag::Edge *> outputs)
      : dag::Node(name, inputs, outputs) {
    key_ = "nndeploy::codec::VideoSideBySideCompare";
    this->setInputTypeInfo<cv::Mat>();
    this->setOutputTypeInfo<cv::Mat>();
    param_ = std::make_shared<SideBySideCompareParam>();
  }
  
  virtual ~VideoSideBySideCompare() {}

  virtual base::Status init();
  virtual base::Status run();
  virtual base::Status deinit();

 private:
  cv::VideoWriter video_writer_;
  int frame_count_ = 0;
  double fps_ = 30.0;
};

}  // namespace codec
}  // namespace nndeploy

#endif  // _NNDEPLOY_CODEC_SIDE_BY_SIDE_COMPARE_H_
