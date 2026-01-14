import numpy as np
import json
import cv2
import time

import nndeploy.base
import nndeploy.dag

class DetailZoomCompare(nndeploy.dag.Node):
    """局部放大细节对比节点"""
    def __init__(self, name, inputs: list[nndeploy.dag.Edge] = None, outputs: list[nndeploy.dag.Edge] = None):
        super().__init__(name, inputs, outputs)
        super().set_key("nndeploy.codec.DetailZoomCompare")
        super().set_desc("局部区域放大对比细节")
        self.set_input_type(np.ndarray)
        self.set_output_type(np.ndarray)
        
        self.window_name_ = "Detail Zoom Compare"
        self.zoom_factor_ = 4  # 放大倍数
        self.roi_x_ = 0.3  # ROI中心X坐标（0-1）
        self.roi_y_ = 0.3  # ROI中心Y坐标（0-1）
        self.roi_size_ = 200  # ROI尺寸（像素）
        self.show_fps_ = True
        self.fps_ = 30  # 播放帧率
        self.auto_fps_ = True  # 自动检测FPS
        self.wait_key_delay_ = 1
        
        self.frame_count = 0
        self.last_time = time.time()
        self.last_frame_time = time.time()
        self.window_created = False
        self.fps_detected_ = False
        
    def init(self):
        try:
            cv2.namedWindow(self.window_name_, cv2.WINDOW_NORMAL)
            self.window_created = True
            print(f"✓ DetailZoomCompare 窗口已创建: {self.window_name_}")
            return nndeploy.base.Status.ok()
        except Exception as e:
            print(f"✗ DetailZoomCompare 初始化失败: {e}")
            return nndeploy.base.Status(nndeploy.base.StatusCode.kStatusCodeErrorInvalidParam)
        
    def run(self):
        input_edge_0 = self.get_input(0)
        input_edge_1 = self.get_input(1)
        
        frame_original = input_edge_0.get(self)
        frame_enhanced = input_edge_1.get(self)
        
        # 添加调试日志
        if not hasattr(self, 'first_run_logged'):
            print(f"[DetailZoomCompare] 第一次 run() 调用")
            print(f"[DetailZoomCompare] frame_original: {type(frame_original)}, shape: {frame_original.shape if frame_original is not None else 'None'}")
            print(f"[DetailZoomCompare] frame_enhanced: {type(frame_enhanced)}, shape: {frame_enhanced.shape if frame_enhanced is not None else 'None'}")
            self.first_run_logged = True
        
        if frame_original is None or frame_enhanced is None:
            if not hasattr(self, 'null_warned'):
                print(f"[DetailZoomCompare] 警告: 收到空帧 (original={frame_original is not None}, enhanced={frame_enhanced is not None})")
                self.null_warned = True
            return nndeploy.base.Status(nndeploy.base.StatusCode.kStatusCodeErrorInvalidParam)
        
        try:
            # 自动检测视频 FPS（首次运行）
            if self.auto_fps_ and not self.fps_detected_:
                try:
                    input_node = input_edge_0.producer_
                    if hasattr(input_node, 'cap_') and input_node.cap_ is not None:
                        detected_fps = input_node.cap_.get(cv2.CAP_PROP_FPS)
                        if detected_fps > 0:
                            self.fps_ = detected_fps
                            print(f"✓ DetailZoomCompare 自动检测到视频 FPS: {self.fps_:.2f}")
                        self.fps_detected_ = True
                except Exception as e:
                    print(f"⚠ DetailZoomCompare 无法自动检测 FPS，使用默认值: {self.fps_} ({e})")
                    self.fps_detected_ = True
            
            self.frame_count += 1
            current_time = time.time()
            elapsed = current_time - self.last_time
            
            if elapsed >= 1.0:
                fps = self.frame_count / elapsed
                self.frame_count = 0
                self.last_time = current_time
            else:
                fps = self.frame_count / max(elapsed, 0.001)
            
            h1, w1 = frame_original.shape[:2]
            h2, w2 = frame_enhanced.shape[:2]
            
            # 计算ROI区域（原图）
            center_x = int(w1 * self.roi_x_)
            center_y = int(h1 * self.roi_y_)
            half_size = self.roi_size_ // 2
            
            x1 = max(0, center_x - half_size)
            y1 = max(0, center_y - half_size)
            x2 = min(w1, center_x + half_size)
            y2 = min(h1, center_y + half_size)
            
            # 提取原图ROI并放大
            roi_original = frame_original[y1:y2, x1:x2]
            roi_h, roi_w = roi_original.shape[:2]
            zoomed_original = cv2.resize(roi_original, 
                                        (roi_w * self.zoom_factor_, roi_h * self.zoom_factor_),
                                        interpolation=cv2.INTER_NEAREST)
            
            # 计算超分图对应区域（考虑scale）
            scale = w2 / w1
            x1_sr = int(x1 * scale)
            y1_sr = int(y1 * scale)
            x2_sr = int(x2 * scale)
            y2_sr = int(y2 * scale)
            
            # 提取超分图ROI并放大
            roi_enhanced = frame_enhanced[y1_sr:y2_sr, x1_sr:x2_sr]
            roi_sr_h, roi_sr_w = roi_enhanced.shape[:2]
            zoomed_enhanced = cv2.resize(roi_enhanced,
                                        (roi_w * self.zoom_factor_, roi_h * self.zoom_factor_),
                                        interpolation=cv2.INTER_NEAREST)
            
            # 创建完整显示图
            display_h = max(h1, h2, zoomed_original.shape[0])
            
            # 左侧：原图+ROI框
            left_img = frame_original.copy()
            cv2.rectangle(left_img, (x1, y1), (x2, y2), (0, 255, 0), 2)
            cv2.putText(left_img, "ROI", (x1, y1-10), 
                       cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 0), 2)
            
            # 中间：原图ROI放大
            middle_canvas = np.zeros((display_h, zoomed_original.shape[1], 3), dtype=np.uint8)
            middle_canvas[:zoomed_original.shape[0], :] = zoomed_original
            cv2.putText(middle_canvas, f"Original x{self.zoom_factor_}", (10, 30),
                       cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2)
            
            # 右侧：超分ROI放大
            right_canvas = np.zeros((display_h, zoomed_enhanced.shape[1], 3), dtype=np.uint8)
            right_canvas[:zoomed_enhanced.shape[0], :] = zoomed_enhanced
            cv2.putText(right_canvas, f"Enhanced x{self.zoom_factor_}", (10, 30),
                       cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 0), 2)
            
            # 调整左图高度
            if left_img.shape[0] != display_h:
                left_canvas = np.zeros((display_h, left_img.shape[1], 3), dtype=np.uint8)
                left_canvas[:left_img.shape[0], :] = left_img
                left_img = left_canvas
            
            # 拼接三部分
            combined = np.hstack([left_img, middle_canvas, right_canvas])
            
            # 添加FPS和信息
            info_text = f"FPS: {fps:.1f} | Scale: {scale:.1f}x | Zoom: {self.zoom_factor_}x"
            cv2.putText(combined, info_text, (10, combined.shape[0] - 20),
                       cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 255), 2)
            
            cv2.imshow(self.window_name_, combined)
            
            key = cv2.waitKey(self.wait_key_delay_) & 0xFF
            if key == 27:
                print("用户按下ESC，停止播放")
                return nndeploy.base.Status(nndeploy.base.StatusCode.kStatusCodeErrorInvalidParam)
            elif key == 32:
                print("暂停（按任意键继续）")
                cv2.waitKey(0)
            
            # 帧率控制
            if self.fps_ > 0:
                target_delay = 1.0 / self.fps_
                elapsed_frame = time.time() - self.last_frame_time
                sleep_time = target_delay - elapsed_frame
                if sleep_time > 0:
                    time.sleep(sleep_time)
                self.last_frame_time = time.time()
            
            return nndeploy.base.Status.ok()
            
        except Exception as e:
            print(f"✗ DetailZoomCompare 处理失败: {e}")
            import traceback
            traceback.print_exc()
            return nndeploy.base.Status(nndeploy.base.StatusCode.kStatusCodeErrorInvalidParam)
    
    def deinit(self):
        if self.window_created:
            cv2.destroyWindow(self.window_name_)
            print(f"✓ DetailZoomCompare 窗口已关闭: {self.window_name_}")
        return nndeploy.base.Status.ok()
    
    def serialize(self):
        json_str = super().serialize()
        json_obj = json.loads(json_str)
        json_obj["window_name_"] = self.window_name_
        json_obj["zoom_factor_"] = self.zoom_factor_
        json_obj["roi_x_"] = self.roi_x_
        json_obj["roi_y_"] = self.roi_y_
        json_obj["roi_size_"] = self.roi_size_
        json_obj["show_fps_"] = self.show_fps_
        json_obj["fps_"] = self.fps_
        json_obj["auto_fps_"] = self.auto_fps_
        json_obj["wait_key_delay_"] = self.wait_key_delay_
        return json.dumps(json_obj)
    
    def deserialize(self, target: str):
        json_obj = json.loads(target)
        self.window_name_ = json_obj.get("window_name_", "Detail Zoom Compare")
        self.zoom_factor_ = json_obj.get("zoom_factor_", 4)
        self.roi_x_ = json_obj.get("roi_x_", 0.3)
        self.roi_y_ = json_obj.get("roi_y_", 0.3)
        self.roi_size_ = json_obj.get("roi_size_", 200)
        self.show_fps_ = json_obj.get("show_fps_", True)
        self.fps_ = json_obj.get("fps_", 30)
        self.auto_fps_ = json_obj.get("auto_fps_", True)
        self.wait_key_delay_ = json_obj.get("wait_key_delay_", 1)
        return super().deserialize(target)


class DetailZoomCompareCreator(nndeploy.dag.NodeCreator):
    def __init__(self):
        super().__init__()
        
    def create_node(self, name: str, inputs: list[nndeploy.dag.Edge], outputs: list[nndeploy.dag.Edge]):
        self.node = DetailZoomCompare(name, inputs, outputs)
        return self.node

detail_zoom_compare_creator = DetailZoomCompareCreator()
nndeploy.dag.register_node("nndeploy.codec.DetailZoomCompare", detail_zoom_compare_creator)
