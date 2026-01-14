from typing import Any, List
import os
import numpy as np
import json

import nndeploy.base
import nndeploy.device
import nndeploy.dag

class RealESRGAN(nndeploy.dag.Node):
    def __init__(self, name, inputs: list[nndeploy.dag.Edge] = None, outputs: list[nndeploy.dag.Edge] = None):
        super().__init__(name, inputs, outputs)
        super().set_key("nndeploy.super_resolution.RealESRGAN")
        super().set_desc("Real-ESRGAN: 通用图像超分辨率")
        self.set_input_type(np.ndarray)
        self.set_output_type(np.ndarray)
        
        self.model_path_ = "resources/models/RealESRGAN_x2plus.pth"
        self.scale_ = 2  # 超分倍数: 2 或 4
        self.tile_ = 256  # tile模式, 0表示不使用
        self.device_ = None  # 延迟到 init() 时检测
        self.upsampler = None
        self.output_frame = None  # 保存输出帧
        self.frame_count = 0  # 帧计数器
        self.use_fast_fallback_ = True  # CPU模式下使用快速fallback
        self.using_fallback = False  # 是否正在使用fallback
        
    def init(self):
        # 检测 GPU
        if self.device_ is None:
            try:
                import torch
                if torch.cuda.is_available():
                    self.device_ = 'cuda'
                    gpu_name = torch.cuda.get_device_name(0)
                    print(f"[RealESRGAN] 检测到 GPU: {gpu_name}")
                else:
                    self.device_ = 'cpu'
                    print(f"[RealESRGAN] 未检测到 CUDA GPU，使用 CPU")
            except Exception as e:
                self.device_ = 'cpu'
                print(f"[RealESRGAN] GPU 检测失败 ({e})，使用 CPU")
        
        print(f"[RealESRGAN] 开始初始化...")
        print(f"[RealESRGAN] 模型路径: {self.model_path_}")
        print(f"[RealESRGAN] 缩放倍数: {self.scale_}x")
        print(f"[RealESRGAN] 设备: {self.device_}")
        print(f"[RealESRGAN] Tile模式: {self.tile_}")
        
        # 只有CPU模式且启用了快速fallback才使用bicubic
        if self.device_ == 'cpu' and self.use_fast_fallback_:
            print(f"[RealESRGAN] CPU模式 + 快速fallback启用，使用bicubic插值")
            self.using_fallback = True
            return nndeploy.base.Status.ok()
        
        try:
            from basicsr.archs.rrdbnet_arch import RRDBNet
            from realesrgan import RealESRGANer
            import torch
            
            # GPU 可用性最终检查
            if self.device_ == 'cuda' and not torch.cuda.is_available():
                print(f"[RealESRGAN] 警告: CUDA 不可用，切换到 CPU")
                self.device_ = 'cpu'
            
            # 根据缩放倍数选择模型架构
            if self.scale_ == 2:
                model = RRDBNet(num_in_ch=3, num_out_ch=3, num_feat=64, 
                               num_block=23, num_grow_ch=32, scale=2)
            else:  # scale == 4
                model = RRDBNet(num_in_ch=3, num_out_ch=3, num_feat=64, 
                               num_block=23, num_grow_ch=32, scale=4)
            
            # 初始化 Real-ESRGAN
            print(f"[RealESRGAN] 正在加载模型到 {self.device_.upper()}...")
            
            # GPU模式使用half精度加速
            use_half = (self.device_ == 'cuda')
            
            self.upsampler = RealESRGANer(
                scale=self.scale_,
                model_path=self.model_path_,
                model=model,
                tile=self.tile_ if self.tile_ > 0 else 0,
                tile_pad=10,
                pre_pad=0,
                half=use_half,
                device=self.device_
            )
            
            if self.device_ == 'cuda':
                print(f"[RealESRGAN] OK GPU加速已启用 (FP16)")
            else:
                print(f"[RealESRGAN] OK 初始化成功 (CPU模式)")
            
            return nndeploy.base.Status.ok()
        except Exception as e:
            print(f"[RealESRGAN] ERROR 初始化失败: {e}")
            print("请安装依赖: pip install realesrgan basicsr")
            import traceback
            traceback.print_exc()
            return nndeploy.base.Status.failed()
        
    def run(self):
        input_edge = self.get_input(0)
        input_numpy = input_edge.get(self)
        
        self.frame_count += 1
        
        if input_numpy is None or input_numpy.size == 0:
            print(f"[RealESRGAN] 帧#{self.frame_count} 收到空输入")
            return nndeploy.base.Status(nndeploy.base.StatusCode.kStatusCodeErrorInvalidParam)
        
        # 使用快速fallback（bicubic插值）
        if self.using_fallback:
            import cv2
            h, w = input_numpy.shape[:2]
            new_h, new_w = h * self.scale_, w * self.scale_
            self.output_frame = cv2.resize(input_numpy, (new_w, new_h), interpolation=cv2.INTER_CUBIC)
            
            if self.frame_count == 1:
                print(f"[RealESRGAN] 使用快速bicubic插值: {input_numpy.shape} -> {self.output_frame.shape}")
            
            if not self.output_frame.flags['C_CONTIGUOUS']:
                self.output_frame = np.ascontiguousarray(self.output_frame)
            
            self.get_output(0).set(self.output_frame)
            return nndeploy.base.Status.ok()
        
        # 原始Real-ESRGAN处理（GPU或CPU）
        if self.frame_count == 1:
            print(f"[RealESRGAN] 开始处理第1帧 (使用{self.device_.upper()})")
            print(f"[RealESRGAN]   输入shape: {input_numpy.shape}, dtype: {input_numpy.dtype}")
            print(f"[RealESRGAN]   输入范围: [{input_numpy.min()}, {input_numpy.max()}]")
            if self.tile_ > 0:
                print(f"[RealESRGAN]   使用tile模式: {self.tile_}x{self.tile_}")
        
        try:
            import time
            start_time = time.time()
            
            if self.frame_count == 1:
                print(f"[RealESRGAN] 帧#1 调用 enhance() 开始...")
            
            # Real-ESRGAN 期望 BGR 输入
            self.output_frame, _ = self.upsampler.enhance(input_numpy, outscale=self.scale_)
            
            elapsed = time.time() - start_time
            
            if self.frame_count == 1:
                print(f"[RealESRGAN] 帧#1 enhance() 完成, 耗时: {elapsed:.2f}s")
                print(f"[RealESRGAN]   输出shape: {self.output_frame.shape}, dtype: {self.output_frame.dtype}")
                print(f"[RealESRGAN]   输出范围: [{self.output_frame.min()}, {self.output_frame.max()}]")
            elif self.frame_count % 30 == 0:
                print(f"[RealESRGAN] 已处理 {self.frame_count} 帧, 当前帧耗时: {elapsed:.2f}s")
            
            # 确保输出数据连续
            if not self.output_frame.flags['C_CONTIGUOUS']:
                self.output_frame = np.ascontiguousarray(self.output_frame)
            
            self.get_output(0).set(self.output_frame)
            
            return nndeploy.base.Status.ok()
        except Exception as e:
            print(f"[RealESRGAN] 帧#{self.frame_count} 处理失败!!!")
            print(f"[RealESRGAN] 错误类型: {type(e).__name__}")
            print(f"[RealESRGAN] 错误信息: {e}")
            import traceback
            print(f"[RealESRGAN] 完整堆栈:")
            traceback.print_exc()
            
            # 失败时返回原图
            print(f"[RealESRGAN] 返回原图作为fallback")
            self.output_frame = input_numpy.copy()
            
            if not self.output_frame.flags['C_CONTIGUOUS']:
                self.output_frame = np.ascontiguousarray(self.output_frame)
            
            self.get_output(0).set(self.output_frame)
            return nndeploy.base.Status(nndeploy.base.StatusCode.kStatusCodeErrorInvalidParam)
        
        try:
            import time
            start_time = time.time()
            
            print(f"[RealESRGAN] 帧#{self.frame_count} 调用 enhance() 开始...")
            
            # Real-ESRGAN 期望 BGR 输入
            self.output_frame, _ = self.upsampler.enhance(input_numpy, outscale=self.scale_)
            
            elapsed = time.time() - start_time
            
            print(f"[RealESRGAN] 帧#{self.frame_count} enhance() 完成, 耗时: {elapsed:.2f}s")
            
            if self.frame_count == 1:
                print(f"[RealESRGAN]   输出shape: {self.output_frame.shape}, dtype: {self.output_frame.dtype}")
                print(f"[RealESRGAN]   输出范围: [{self.output_frame.min()}, {self.output_frame.max()}]")
            
            # 确保输出数据连续
            if not self.output_frame.flags['C_CONTIGUOUS']:
                print(f"[RealESRGAN] 帧#{self.frame_count} 转换为连续数组")
                self.output_frame = np.ascontiguousarray(self.output_frame)
            
            print(f"[RealESRGAN] 帧#{self.frame_count} 设置输出到edge")
            self.get_output(0).set(self.output_frame)
            print(f"[RealESRGAN] 帧#{self.frame_count} 处理完成")
            
            return nndeploy.base.Status.ok()
        except Exception as e:
            print(f"[RealESRGAN] 帧#{self.frame_count} 处理失败!!!")
            print(f"[RealESRGAN] 错误类型: {type(e).__name__}")
            print(f"[RealESRGAN] 错误信息: {e}")
            import traceback
            print(f"[RealESRGAN] 完整堆栈:")
            traceback.print_exc()
            
            # 失败时返回原图
            print(f"[RealESRGAN] 返回原图作为fallback")
            self.output_frame = input_numpy.copy()
            
            if not self.output_frame.flags['C_CONTIGUOUS']:
                self.output_frame = np.ascontiguousarray(self.output_frame)
            
            self.get_output(0).set(self.output_frame)
            return nndeploy.base.Status(nndeploy.base.StatusCode.kStatusCodeErrorInvalidParam)
    
    def serialize(self):
        self.add_required_param("model_path_")
        json_str = super().serialize()
        json_obj = json.loads(json_str)
        json_obj["model_path_"] = self.model_path_
        json_obj["scale_"] = self.scale_
        json_obj["tile_"] = self.tile_
        return json.dumps(json_obj)
    
    def deserialize(self, target: str):
        json_obj = json.loads(target)
        self.model_path_ = json_obj.get("model_path_", "resources/models/RealESRGAN_x2plus.pth")
        self.scale_ = json_obj.get("scale_", 2)
        self.tile_ = json_obj.get("tile_", 0)
        return super().deserialize(target)
    
    
class RealESRGANCreator(nndeploy.dag.NodeCreator):
    def __init__(self):
        super().__init__()
        
    def create_node(self, name: str, inputs: list[nndeploy.dag.Edge], outputs: list[nndeploy.dag.Edge]):
        self.node = RealESRGAN(name, inputs, outputs)
        return self.node
    
realesrgan_node_creator = RealESRGANCreator()
nndeploy.dag.register_node("nndeploy.super_resolution.RealESRGAN", realesrgan_node_creator)
