import numpy as np
import json
import cv2
import os

import nndeploy.base
import nndeploy.dag

class OpenCVSuperRes(nndeploy.dag.Node):
    """使用 OpenCV DNN Super Resolution 模块"""
    def __init__(self, name, inputs: list[nndeploy.dag.Edge] = None, outputs: list[nndeploy.dag.Edge] = None):
        super().__init__(name, inputs, outputs)
        super().set_key("nndeploy.super_resolution.OpenCVSuperRes")
        super().set_desc("OpenCV DNN 超分辨率（EDSR/ESPCN/FSRCNN）")
        self.set_input_type(np.ndarray)
        self.set_output_type(np.ndarray)
        
        self.model_name_ = "EDSR"  # EDSR, ESPCN, FSRCNN, LapSRN
        self.scale_ = 2  # 2, 3, 4
        self.model_path_ = None  # 可选：自定义模型路径
        self.sharpen_ = True  # 是否添加锐化
        self.sharpen_amount_ = 0.5  # 锐化强度 0.0-1.0
        
        self.sr = None
        self.output_frame = None
        
    def init(self):
        try:
            # 尝试使用 OpenCV DNN Super Resolution
            if hasattr(cv2, 'dnn_superres'):
                self.sr = cv2.dnn_superres.DnnSuperResImpl_create()
                
                # 如果指定了模型路径，使用自定义模型
                if self.model_path_ and os.path.exists(self.model_path_):
                    self.sr.readModel(self.model_path_)
                    self.sr.setModel(self.model_name_.lower(), self.scale_)
                    print(f"✓ OpenCVSuperRes 加载自定义模型: {self.model_path_}")
                else:
                    # 尝试从默认位置加载预训练模型
                    model_dir = "resources/models/opencv_dnn_superres"
                    model_file = f"{self.model_name_}_x{self.scale_}.pb"
                    model_full_path = os.path.join(model_dir, model_file)
                    
                    if os.path.exists(model_full_path):
                        self.sr.readModel(model_full_path)
                        self.sr.setModel(self.model_name_.lower(), self.scale_)
                        print(f"✓ OpenCVSuperRes 加载模型: {model_file}")
                    else:
                        print(f"⚠ 未找到预训练模型: {model_full_path}")
                        print(f"  将使用 Lanczos + 锐化增强作为备用方案")
                        self.sr = None
            else:
                print("⚠ OpenCV 版本不支持 dnn_superres 模块")
                print("  将使用 Lanczos + 锐化增强作为备用方案")
                self.sr = None
            
            return nndeploy.base.Status.ok()
        except Exception as e:
            print(f"OpenCVSuperRes 初始化失败: {e}")
            print("  将使用 Lanczos + 锐化增强作为备用方案")
            self.sr = None
            return nndeploy.base.Status.ok()
        
    def _apply_sharpen(self, img, amount=0.5):
        """应用锐化滤波器"""
        # 使用 Unsharp Mask 锐化
        blurred = cv2.GaussianBlur(img, (0, 0), 3)
        sharpened = cv2.addWeighted(img, 1.0 + amount, blurred, -amount, 0)
        return sharpened
    
    def _lanczos_upscale(self, img, scale):
        """使用 Lanczos 插值放大"""
        h, w = img.shape[:2]
        new_h, new_w = int(h * scale), int(w * scale)
        upscaled = cv2.resize(img, (new_w, new_h), interpolation=cv2.INTER_LANCZOS4)
        return upscaled
    
    def run(self):
        input_edge = self.get_input(0)
        input_numpy = input_edge.get(self)
        
        if input_numpy is None or input_numpy.size == 0:
            return nndeploy.base.Status(nndeploy.base.StatusCode.kStatusCodeErrorInvalidParam)
        
        try:
            # 如果有 DNN 模型，使用模型推理
            if self.sr is not None:
                self.output_frame = self.sr.upsample(input_numpy)
                
                # 可选：添加额外锐化
                if self.sharpen_ and self.sharpen_amount_ > 0:
                    self.output_frame = self._apply_sharpen(self.output_frame, self.sharpen_amount_)
            else:
                # 备用方案：Lanczos + 锐化
                self.output_frame = self._lanczos_upscale(input_numpy, self.scale_)
                
                if self.sharpen_:
                    self.output_frame = self._apply_sharpen(self.output_frame, self.sharpen_amount_)
            
            # 确保输出数据连续
            if not self.output_frame.flags['C_CONTIGUOUS']:
                self.output_frame = np.ascontiguousarray(self.output_frame)
            
            self.get_output(0).set(self.output_frame)
            return nndeploy.base.Status.ok()
            
        except Exception as e:
            print(f"✗ OpenCVSuperRes 处理失败: {e}")
            # 最终备用：双三次插值
            try:
                h, w = input_numpy.shape[:2]
                new_h, new_w = int(h * self.scale_), int(w * self.scale_)
                self.output_frame = cv2.resize(input_numpy, (new_w, new_h), 
                                              interpolation=cv2.INTER_CUBIC)
                
                if not self.output_frame.flags['C_CONTIGUOUS']:
                    self.output_frame = np.ascontiguousarray(self.output_frame)
                
                self.get_output(0).set(self.output_frame)
                return nndeploy.base.Status.ok()
            except Exception as e2:
                return nndeploy.base.Status(nndeploy.base.StatusCode.kStatusCodeErrorInvalidParam)
    
    def serialize(self):
        json_str = super().serialize()
        json_obj = json.loads(json_str)
        json_obj["model_name_"] = self.model_name_
        json_obj["scale_"] = self.scale_
        json_obj["model_path_"] = self.model_path_
        json_obj["sharpen_"] = self.sharpen_
        json_obj["sharpen_amount_"] = self.sharpen_amount_
        return json.dumps(json_obj)
    
    def deserialize(self, target: str):
        json_obj = json.loads(target)
        self.model_name_ = json_obj.get("model_name_", "EDSR")
        self.scale_ = json_obj.get("scale_", 2)
        self.model_path_ = json_obj.get("model_path_", None)
        self.sharpen_ = json_obj.get("sharpen_", True)
        self.sharpen_amount_ = json_obj.get("sharpen_amount_", 0.5)
        return super().deserialize(target)


class OpenCVSuperResCreator(nndeploy.dag.NodeCreator):
    def __init__(self):
        super().__init__()
        
    def create_node(self, name: str, inputs: list[nndeploy.dag.Edge], outputs: list[nndeploy.dag.Edge]):
        self.node = OpenCVSuperRes(name, inputs, outputs)
        return self.node

opencv_superres_creator = OpenCVSuperResCreator()
nndeploy.dag.register_node("nndeploy.super_resolution.OpenCVSuperRes", opencv_superres_creator)
