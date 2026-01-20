#include "nndeploy/codec/side_by_side_compare.h"

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
namespace codec {

// ========== SideBySideCompare ==========

cv::Mat SideBySideCompare::resizeForDisplay(const cv::Mat &img, 
                                             int max_width, 
                                             int max_height) {
  if (img.cols <= max_width && img.rows <= max_height) {
    return img.clone();
  }
  
  double scale_width = static_cast<double>(max_width) / img.cols;
  double scale_height = static_cast<double>(max_height) / img.rows;
  double scale = std::min(scale_width, scale_height);
  
  int new_width = static_cast<int>(img.cols * scale);
  int new_height = static_cast<int>(img.rows * scale);
  
  cv::Mat resized;
  cv::resize(img, resized, cv::Size(new_width, new_height), 0, 0, cv::INTER_LINEAR);
  
  return resized;
}

void SideBySideCompare::addLabel(cv::Mat &img, 
                                  const std::string &label, 
                                  int position) {
  int font_face = cv::FONT_HERSHEY_SIMPLEX;
  double font_scale = 0.8;
  int thickness = 2;
  int baseline = 0;
  
  cv::Size text_size = cv::getTextSize(label, font_face, font_scale, 
                                        thickness, &baseline);
  
  int margin = 10;
  int x = (position == 0) ? margin : (img.cols - text_size.width - margin);
  int y = margin + text_size.height;
  
  // 绘制背景矩形
  cv::rectangle(img, 
                cv::Point(x - 5, y - text_size.height - 5),
                cv::Point(x + text_size.width + 5, y + 5),
                cv::Scalar(0, 0, 0), 
                cv::FILLED);
  
  // 绘制文本
  cv::putText(img, label, cv::Point(x, y), 
              font_face, font_scale, 
              cv::Scalar(255, 255, 255), thickness);
}

cv::Mat SideBySideCompare::concatenateHorizontal(const cv::Mat &left, 
                                                  const cv::Mat &right) {
  // 确保两张图像高度相同
  int target_height = std::max(left.rows, right.rows);
  
  cv::Mat left_resized = left;
  cv::Mat right_resized = right;
  
  if (left.rows != target_height) {
    cv::resize(left, left_resized, 
               cv::Size(left.cols * target_height / left.rows, target_height));
  }
  
  if (right.rows != target_height) {
    cv::resize(right, right_resized, 
               cv::Size(right.cols * target_height / right.rows, target_height));
  }
  
  // 水平拼接
  cv::Mat result;
  cv::hconcat(left_resized, right_resized, result);
  
  return result;
}

base::Status SideBySideCompare::run() {
  // 获取参数
  SideBySideCompareParam *param = 
      dynamic_cast<SideBySideCompareParam *>(param_.get());
  if (param == nullptr) {
    NNDEPLOY_LOGE("SideBySideCompare: Invalid parameters\n");
    return base::kStatusCodeErrorInvalidParam;
  }
  
  // 获取输入图像
  if (inputs_.size() < 2) {
    NNDEPLOY_LOGE("SideBySideCompare: Requires 2 inputs (original and super-res)\n");
    return base::kStatusCodeErrorInvalidParam;
  }
  
  cv::Mat *original = inputs_[0]->getCvMat(this);
  cv::Mat *super_res = inputs_[1]->getCvMat(this);
  
  if (original == nullptr || original->empty()) {
    NNDEPLOY_LOGE("SideBySideCompare: Original image is empty\n");
    return base::kStatusCodeErrorInvalidParam;
  }
  
  if (super_res == nullptr || super_res->empty()) {
    NNDEPLOY_LOGE("SideBySideCompare: Super-resolution image is empty\n");
    return base::kStatusCodeErrorInvalidParam;
  }
  
  try {
    // 复制图像以便添加标签
    cv::Mat left = original->clone();
    cv::Mat right = super_res->clone();
    
    // 自动调整大小
    if (param->auto_resize_) {
      int max_single_width = param->max_display_width_ / 2;
      left = resizeForDisplay(left, max_single_width, param->max_display_height_);
      right = resizeForDisplay(right, max_single_width, param->max_display_height_);
    }
    
    // 添加标签
    if (param->show_labels_) {
      addLabel(left, param->left_label_, 0);
      addLabel(right, param->right_label_, 0);
    }
    
    // 水平拼接
    cv::Mat result = concatenateHorizontal(left, right);
    
    // 保存输出（如果需要）
    if (param->save_output_ && !param->output_path_.empty()) {
      cv::imwrite(param->output_path_, result);
      NNDEPLOY_LOGI("SideBySideCompare: Saved to %s\n", param->output_path_.c_str());
    }
    
    // 设置输出
    if (!outputs_.empty()) {
      outputs_[0]->set(new cv::Mat(result), false);
    }
    
    NNDEPLOY_LOGD("SideBySideCompare: %dx%d + %dx%d -> %dx%d\n",
                  left.cols, left.rows, right.cols, right.rows,
                  result.cols, result.rows);
    
    return base::kStatusCodeOk;
    
  } catch (const cv::Exception &e) {
    NNDEPLOY_LOGE("SideBySideCompare: OpenCV exception: %s\n", e.what());
    return base::kStatusCodeErrorInvalidParam;
  } catch (const std::exception &e) {
    NNDEPLOY_LOGE("SideBySideCompare: Exception: %s\n", e.what());
    return base::kStatusCodeErrorInvalidParam;
  }
}

// ========== VideoSideBySideCompare ==========

base::Status VideoSideBySideCompare::init() {
  SideBySideCompareParam *param = 
      dynamic_cast<SideBySideCompareParam *>(param_.get());
  if (param == nullptr) {
    NNDEPLOY_LOGE("VideoSideBySideCompare: Invalid parameters\n");
    return base::kStatusCodeErrorInvalidParam;
  }
  
  frame_count_ = 0;
  
  // 如果需要保存视频，初始化 VideoWriter
  if (param->save_output_ && !param->output_path_.empty()) {
    // VideoWriter 将在第一帧时初始化（需要知道尺寸）
    NNDEPLOY_LOGI("VideoSideBySideCompare: Will save output to %s\n", 
                  param->output_path_.c_str());
  }
  
  return base::kStatusCodeOk;
}

base::Status VideoSideBySideCompare::run() {
  SideBySideCompareParam *param = 
      dynamic_cast<SideBySideCompareParam *>(param_.get());
  if (param == nullptr) {
    return base::kStatusCodeErrorInvalidParam;
  }
  
  // 获取输入
  if (inputs_.size() < 2) {
    NNDEPLOY_LOGE("VideoSideBySideCompare: Requires 2 inputs\n");
    return base::kStatusCodeErrorInvalidParam;
  }
  
  cv::Mat *original = inputs_[0]->getCvMat(this);
  cv::Mat *super_res = inputs_[1]->getCvMat(this);
  
  if (original == nullptr || original->empty() || 
      super_res == nullptr || super_res->empty()) {
    return base::kStatusCodeErrorInvalidParam;
  }
  
  try {
    // 创建左右对比图像
    cv::Mat left = original->clone();
    cv::Mat right = super_res->clone();
    
    // 调整大小
    if (param->auto_resize_) {
      int max_single_width = param->max_display_width_ / 2;
      cv::resize(left, left, 
                 cv::Size(max_single_width, 
                         left.rows * max_single_width / left.cols));
      cv::resize(right, right, 
                 cv::Size(max_single_width, 
                         right.rows * max_single_width / right.cols));
    }
    
    // 添加标签
    if (param->show_labels_) {
      int font_face = cv::FONT_HERSHEY_SIMPLEX;
      double font_scale = 0.7;
      int thickness = 2;
      
      cv::putText(left, param->left_label_, cv::Point(10, 30), 
                  font_face, font_scale, cv::Scalar(255, 255, 255), thickness);
      cv::putText(right, param->right_label_, cv::Point(10, 30), 
                  font_face, font_scale, cv::Scalar(255, 255, 255), thickness);
    }
    
    // 拼接
    cv::Mat result;
    cv::hconcat(left, right, result);
    
    // 初始化 VideoWriter（第一帧）
    if (param->save_output_ && !param->output_path_.empty() && 
        !video_writer_.isOpened()) {
      int fourcc = cv::VideoWriter::fourcc('m', 'p', '4', 'v');
      video_writer_.open(param->output_path_, fourcc, fps_, 
                         cv::Size(result.cols, result.rows));
      
      if (!video_writer_.isOpened()) {
        NNDEPLOY_LOGW("VideoSideBySideCompare: Failed to open VideoWriter\n");
      } else {
        NNDEPLOY_LOGI("VideoSideBySideCompare: VideoWriter opened: %dx%d @ %.1f fps\n",
                      result.cols, result.rows, fps_);
      }
    }
    
    // 写入视频帧
    if (video_writer_.isOpened()) {
      video_writer_.write(result);
    }
    
    // 输出
    if (!outputs_.empty()) {
      outputs_[0]->set(new cv::Mat(result), false);
    }
    
    frame_count_++;
    
    if (frame_count_ % 30 == 0) {
      NNDEPLOY_LOGD("VideoSideBySideCompare: Processed %d frames\n", frame_count_);
    }
    
    return base::kStatusCodeOk;
    
  } catch (const std::exception &e) {
    NNDEPLOY_LOGE("VideoSideBySideCompare: Exception: %s\n", e.what());
    return base::kStatusCodeErrorInvalidParam;
  }
}

base::Status VideoSideBySideCompare::deinit() {
  if (video_writer_.isOpened()) {
    video_writer_.release();
    NNDEPLOY_LOGI("VideoSideBySideCompare: Released VideoWriter, total frames: %d\n", 
                  frame_count_);
  }
  
  return base::kStatusCodeOk;
}

// 注册节点
REGISTER_NODE("nndeploy::codec::SideBySideCompare", SideBySideCompare);
REGISTER_NODE("nndeploy::codec::VideoSideBySideCompare", VideoSideBySideCompare);

}  // namespace codec
}  // namespace nndeploy
