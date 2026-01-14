import numpy as np
import json
import cv2
import time

import nndeploy.base
import nndeploy.dag

class VideoPlayer(nndeploy.dag.Node):
    """持续显示视频流的播放节点"""
    def __init__(self, name, inputs: list[nndeploy.dag.Edge] = None, outputs: list[nndeploy.dag.Edge] = None):
        super().__init__(name, inputs, outputs)
        super().set_key("nndeploy.codec.VideoPlayer")
        super().set_desc("持续显示视频流，支持FPS控制")
        self.set_input_type(np.ndarray)
        self.set_output_type(np.ndarray)
        
        self.window_name_ = "Video Player"
        self.fps_ = 30  # 默认 FPS，可被自动检测覆盖
        self.auto_fps_ = True  # 是否自动检测视频 FPS
        self.show_fps_ = True
        self.wait_key_delay_ = 1
        
        self.frame_count = 0
        self.last_time = time.time()
        self.window_created = False
        self.fps_detected_ = False  # FPS 是否已检测
        
    def init(self):
        try:
            cv2.namedWindow(self.window_name_, cv2.WINDOW_NORMAL)
            cv2.resizeWindow(self.window_name_, 960, 540)
            self.window_created = True
            print(f"✓ VideoPlayer 窗口已创建: {self.window_name_}")
            return nndeploy.base.Status.ok()
        except Exception as e:
            print(f"✗ VideoPlayer 初始化失败: {e}")
            return nndeploy.base.Status(nndeploy.base.StatusCode.kStatusCodeErrorInvalidParam)
        
    def run(self):
        input_edge = self.get_input(0)
        input_frame = input_edge.get(self)
        
        # 添加调试日志
        if not hasattr(self, 'first_run_logged'):
            print(f"[VideoPlayer] 第一次 run() 调用")
            print(f"[VideoPlayer] input_frame: {type(input_frame)}, shape: {input_frame.shape if input_frame is not None else 'None'}")
            self.first_run_logged = True
        
        if input_frame is None or input_frame.size == 0:
            if not hasattr(self, 'null_warned'):
                print(f"[VideoPlayer] 警告: 收到空帧")
                self.null_warned = True
            return nndeploy.base.Status(nndeploy.base.StatusCode.kStatusCodeErrorInvalidParam)
        
        try:
            # 自动检测视频 FPS（首次运行）
            if self.auto_fps_ and not self.fps_detected_:
                try:
                    # 尝试从 OpenCvVideoDecode 节点获取 FPS
                    input_node = input_edge.producer_
                    if hasattr(input_node, 'cap_') and input_node.cap_ is not None:
                        detected_fps = input_node.cap_.get(cv2.CAP_PROP_FPS)
                        if detected_fps > 0:
                            self.fps_ = detected_fps
                            print(f"✓ 自动检测到视频 FPS: {self.fps_:.2f}")
                        self.fps_detected_ = True
                except Exception as e:
                    print(f"⚠ 无法自动检测 FPS，使用默认值: {self.fps_} ({e})")
                    self.fps_detected_ = True
            
            self.frame_count += 1
            current_time = time.time()
            elapsed = current_time - self.last_time
            
            if elapsed >= 1.0:
                fps = self.frame_count / elapsed
                self.frame_count = 0
                self.last_time = current_time
                
                if self.show_fps_:
                    display_frame = input_frame.copy()
                    cv2.putText(display_frame, f"FPS: {fps:.1f}", (10, 30),
                               cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 0), 2)
                else:
                    display_frame = input_frame
            else:
                if self.show_fps_:
                    display_frame = input_frame.copy()
                    fps = self.frame_count / max(elapsed, 0.001)
                    cv2.putText(display_frame, f"FPS: {fps:.1f}", (10, 30),
                               cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 0), 2)
                else:
                    display_frame = input_frame
            
            cv2.imshow(self.window_name_, display_frame)
            
            key = cv2.waitKey(self.wait_key_delay_) & 0xFF
            if key == 27:
                print("用户按下ESC，停止播放")
                return nndeploy.base.Status(nndeploy.base.StatusCode.kStatusCodeErrorInvalidParam)
            elif key == 32:
                print("暂停（按任意键继续）")
                cv2.waitKey(0)
            
            if self.fps_ > 0:
                target_delay = 1.0 / self.fps_
                sleep_time = target_delay - (time.time() - current_time)
                if sleep_time > 0:
                    time.sleep(sleep_time)
            
            return nndeploy.base.Status.ok()
            
        except Exception as e:
            print(f"✗ VideoPlayer 播放失败: {e}")
            import traceback
            traceback.print_exc()
            return nndeploy.base.Status(nndeploy.base.StatusCode.kStatusCodeErrorInvalidParam)
    
    def deinit(self):
        if self.window_created:
            cv2.destroyWindow(self.window_name_)
            print(f"✓ VideoPlayer 窗口已关闭: {self.window_name_}")
        return nndeploy.base.Status.ok()
    
    def serialize(self):
        json_str = super().serialize()
        json_obj = json.loads(json_str)
        json_obj["window_name_"] = self.window_name_
        json_obj["fps_"] = self.fps_
        json_obj["auto_fps_"] = self.auto_fps_
        json_obj["show_fps_"] = self.show_fps_
        json_obj["wait_key_delay_"] = self.wait_key_delay_
        return json.dumps(json_obj)
    
    def deserialize(self, target: str):
        json_obj = json.loads(target)
        self.window_name_ = json_obj.get("window_name_", "Video Player")
        self.auto_fps_ = json_obj.get("auto_fps_", True)
        self.fps_ = json_obj.get("fps_", 30)
        self.show_fps_ = json_obj.get("show_fps_", True)
        self.wait_key_delay_ = json_obj.get("wait_key_delay_", 1)
        return super().deserialize(target)


class VideoPlayerCreator(nndeploy.dag.NodeCreator):
    def __init__(self):
        super().__init__()
        
    def create_node(self, name: str, inputs: list[nndeploy.dag.Edge], outputs: list[nndeploy.dag.Edge]):
        self.node = VideoPlayer(name, inputs, outputs)
        return self.node

video_player_creator = VideoPlayerCreator()
nndeploy.dag.register_node("nndeploy.codec.VideoPlayer", video_player_creator)
