import numpy as np
import json
import cv2
import time

import nndeploy.base
import nndeploy.dag

class SideBySideCompare(nndeploy.dag.Node):
    """左右对比显示节点"""
    def __init__(self, name, inputs: list[nndeploy.dag.Edge] = None, outputs: list[nndeploy.dag.Edge] = None):
        super().__init__(name, inputs, outputs)
        super().set_key("nndeploy.codec.SideBySideCompare")
        super().set_desc("左右对比显示两个输入")
        self.set_input_type(np.ndarray)
        self.set_output_type(np.ndarray)
        
        self.window_name_ = "Side-by-Side Compare"
        self.left_title_ = "Original"
        self.right_title_ = "Enhanced"
        self.target_height_ = 720  # 统一缩放到这个高度，0表示不缩放
        self.show_fps_ = True
        self.fps_ = 30  # 播放帧率，默认30FPS
        self.auto_fps_ = True  # 是否自动检测视频 FPS
        self.wait_key_delay_ = 1
        self.original_size_ = False  # True=显示原始尺寸，False=缩放到target_height_
        
        self.frame_count = 0
        self.last_time = time.time()
        self.last_frame_time = time.time()
        self.window_created = False
        self.fps_detected_ = False
        
    def init(self):
        try:
            cv2.namedWindow(self.window_name_, cv2.WINDOW_NORMAL)
            self.window_created = True
            print(f"✓ SideBySideCompare 窗口已创建: {self.window_name_}")
            return nndeploy.base.Status.ok()
        except Exception as e:
            print(f"✗ SideBySideCompare 初始化失败: {e}")
            return nndeploy.base.Status(nndeploy.base.StatusCode.kStatusCodeErrorInvalidParam)
        
    def run(self):
        # 获取两个输入
        input_edge_0 = self.get_input(0)  # 原图
        input_edge_1 = self.get_input(1)  # 超分图
        
        frame_original = input_edge_0.get(self)
        frame_enhanced = input_edge_1.get(self)
        
        # 添加调试日志
        if not hasattr(self, 'first_run_logged'):
            print(f"[SideBySideCompare] 第一次 run() 调用")
            print(f"[SideBySideCompare] frame_original: {type(frame_original)}, shape: {frame_original.shape if frame_original is not None else 'None'}")
            print(f"[SideBySideCompare] frame_enhanced: {type(frame_enhanced)}, shape: {frame_enhanced.shape if frame_enhanced is not None else 'None'}")
            self.first_run_logged = True
        
        if frame_original is None or frame_enhanced is None:
            if not hasattr(self, 'null_warned'):
                print(f"[SideBySideCompare] 警告: 收到空帧 (original={frame_original is not None}, enhanced={frame_enhanced is not None})")
                self.null_warned = True
            return nndeploy.base.Status(nndeploy.base.StatusCode.kStatusCodeErrorInvalidParam)
        
        try:
            # 自动检测视频 FPS（首次运行）
            if self.auto_fps_ and not self.fps_detected_:
                try:
                    # 尝试从第一个输入节点获取 FPS
                    input_node = input_edge_0.producer_
                    if hasattr(input_node, 'cap_') and input_node.cap_ is not None:
                        detected_fps = input_node.cap_.get(cv2.CAP_PROP_FPS)
                        if detected_fps > 0:
                            self.fps_ = detected_fps
                            print(f"✓ SideBySideCompare 自动检测到视频 FPS: {self.fps_:.2f}")
                        self.fps_detected_ = True
                except Exception as e:
                    print(f"⚠ SideBySideCompare 无法自动检测 FPS，使用默认值: {self.fps_} ({e})")
                    self.fps_detected_ = True
            
            # 计算 FPS
            self.frame_count += 1
            current_time = time.time()
            elapsed = current_time - self.last_time
            
            if elapsed >= 1.0:
                fps = self.frame_count / elapsed
                self.frame_count = 0
                self.last_time = current_time
            else:
                fps = self.frame_count / max(elapsed, 0.001)
            
            # 统一缩放到目标高度
            h1, w1 = frame_original.shape[:2]
            h2, w2 = frame_enhanced.shape[:2]
            
            # 添加标题和信息
            title_height = 40
            
            if self.original_size_:
                # 显示原始尺寸，不缩放
                left_img = frame_original
                right_img = frame_enhanced
                
                # 创建标题画布
                left_canvas = np.zeros((h1 + title_height, w1, 3), dtype=np.uint8)
                right_canvas = np.zeros((h2 + title_height, w2, 3), dtype=np.uint8)
            else:
                # 缩放到统一高度
                scale1 = self.target_height_ / h1
                scale2 = self.target_height_ / h2
                new_w1 = int(w1 * scale1)
                new_w2 = int(w2 * scale2)
                left_img = cv2.resize(frame_original, (new_w1, self.target_height_))
                right_img = cv2.resize(frame_enhanced, (new_w2, self.target_height_))
                
                # 创建标题画布
                left_canvas = np.zeros((self.target_height_ + title_height, new_w1, 3), dtype=np.uint8)
                right_canvas = np.zeros((self.target_height_ + title_height, new_w2, 3), dtype=np.uint8)
            
            # 填充标题区域
            left_canvas[:title_height] = (40, 40, 40)
            right_canvas[:title_height] = (40, 40, 40)
            
            # 绘制标题
            cv2.putText(left_canvas, f"{self.left_title_} ({w1}x{h1})", 
                       (10, 28), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2)
            cv2.putText(right_canvas, f"{self.right_title_} ({w2}x{h2})", 
                       (10, 28), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 0), 2)
            
            # 放置图像
            left_canvas[title_height:, :] = left_img
            right_canvas[title_height:, :] = right_img
            
            # 拼接左右图像（使用最大高度对齐）
            max_height = max(left_canvas.shape[0], right_canvas.shape[0])
            if left_canvas.shape[0] < max_height:
                padding = np.zeros((max_height - left_canvas.shape[0], left_canvas.shape[1], 3), dtype=np.uint8)
                left_canvas = np.vstack([left_canvas, padding])
            if right_canvas.shape[0] < max_height:
                padding = np.zeros((max_height - right_canvas.shape[0], right_canvas.shape[1], 3), dtype=np.uint8)
                right_canvas = np.vstack([right_canvas, padding])
            
            combined = np.hstack([left_canvas, right_canvas])
            
            # 显示 FPS
            if self.show_fps_:
                cv2.putText(combined, f"FPS: {fps:.1f}", 
                           (combined.shape[1] - 150, 28),
                           cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 255), 2)
            
            # 显示倍率信息
            scale_info = f"Scale: {w2/w1:.1f}x"
            cv2.putText(combined, scale_info, 
                       (combined.shape[1]//2 - 50, 28),
                       cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 0, 255), 2)
            
            cv2.imshow(self.window_name_, combined)
            
            # 键盘控制
            key = cv2.waitKey(self.wait_key_delay_) & 0xFF
            if key == 27:  # ESC
                print("用户按下ESC，停止播放")
                return nndeploy.base.Status(nndeploy.base.StatusCode.kStatusCodeErrorInvalidParam)
            elif key == 32:  # Space
                print("暂停（按任意键继续）")
                cv2.waitKey(0)
            
            # 帧率控制 - 根据目标 FPS 延迟
            if self.fps_ > 0:
                target_delay = 1.0 / self.fps_
                elapsed_frame = time.time() - self.last_frame_time
                sleep_time = target_delay - elapsed_frame
                if sleep_time > 0:
                    time.sleep(sleep_time)
                self.last_frame_time = time.time()
            
            return nndeploy.base.Status.ok()
            
        except Exception as e:
            print(f"✗ SideBySideCompare 处理失败: {e}")
            import traceback
            traceback.print_exc()
            return nndeploy.base.Status(nndeploy.base.StatusCode.kStatusCodeErrorInvalidParam)
    
    def deinit(self):
        if self.window_created:
            cv2.destroyWindow(self.window_name_)
            print(f"✓ SideBySideCompare 窗口已关闭: {self.window_name_}")
        return nndeploy.base.Status.ok()
    
    def serialize(self):
        json_str = super().serialize()
        json_obj = json.loads(json_str)
        json_obj["window_name_"] = self.window_name_
        json_obj["left_title_"] = self.left_title_
        json_obj["right_title_"] = self.right_title_
        json_obj["target_height_"] = self.target_height_
        json_obj["show_fps_"] = self.show_fps_
        json_obj["fps_"] = self.fps_
        json_obj["auto_fps_"] = self.auto_fps_
        json_obj["wait_key_delay_"] = self.wait_key_delay_
        json_obj["original_size_"] = self.original_size_
        return json.dumps(json_obj)
        
    def deserialize(self, target: str):
        json_obj = json.loads(target)
        self.window_name_ = json_obj.get("window_name_", "Side-by-Side Compare")
        self.left_title_ = json_obj.get("left_title_", "Original")
        self.right_title_ = json_obj.get("right_title_", "Enhanced")
        self.target_height_ = json_obj.get("target_height_", 720)
        self.show_fps_ = json_obj.get("show_fps_", True)
        self.fps_ = json_obj.get("fps_", 30)
        self.auto_fps_ = json_obj.get("auto_fps_", True)
        self.wait_key_delay_ = json_obj.get("wait_key_delay_", 1)
        self.original_size_ = json_obj.get("original_size_", False)
        return super().deserialize(target)


class SideBySideCompareCreator(nndeploy.dag.NodeCreator):
    def __init__(self):
        super().__init__()
        
    def create_node(self, name: str, inputs: list[nndeploy.dag.Edge], outputs: list[nndeploy.dag.Edge]):
        self.node = SideBySideCompare(name, inputs, outputs)
        return self.node

side_by_side_compare_creator = SideBySideCompareCreator()
nndeploy.dag.register_node("nndeploy.codec.SideBySideCompare", side_by_side_compare_creator)
