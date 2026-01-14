from typing import Any, List
import os
import numpy as np
import json
import cv2

import nndeploy.base
import nndeploy.device
import nndeploy.dag

class SRResNet(nndeploy.dag.Node):
    def __init__(self, name, inputs: list[nndeploy.dag.Edge] = None, outputs: list[nndeploy.dag.Edge] = None):
        super().__init__(name, inputs, outputs)
        super().set_key("nndeploy.super_resolution.SRResNet")
        super().set_desc("SRResNet-lite: 轻量级快速超分辨率")
        self.set_input_type(np.ndarray)
        self.set_output_type(np.ndarray)
        
        self.model_path_ = None
        self.scale_ = 2
        self.num_features_ = 32
        self.num_blocks_ = 8
        self.device_, _ = nndeploy.base.get_available_device()
        self.model = None
        self.output_frame = None
        self.use_bicubic_fallback_ = True
        
    def init(self):
        try:
            import torch
            import torch.nn as nn
            
            class ResidualBlock(nn.Module):
                def __init__(self, channels):
                    super(ResidualBlock, self).__init__()
                    self.conv1 = nn.Conv2d(channels, channels, kernel_size=3, padding=1)
                    self.bn1 = nn.BatchNorm2d(channels)
                    self.prelu = nn.PReLU()
                    self.conv2 = nn.Conv2d(channels, channels, kernel_size=3, padding=1)
                    self.bn2 = nn.BatchNorm2d(channels)
                    
                def forward(self, x):
                    residual = x
                    out = self.conv1(x)
                    out = self.bn1(out)
                    out = self.prelu(out)
                    out = self.conv2(out)
                    out = self.bn2(out)
                    out = out + residual
                    return out

            class UpsampleBlock(nn.Module):
                def __init__(self, in_channels, up_scale):
                    super(UpsampleBlock, self).__init__()
                    self.conv = nn.Conv2d(in_channels, in_channels * (up_scale ** 2), 
                                         kernel_size=3, padding=1)
                    self.pixel_shuffle = nn.PixelShuffle(up_scale)
                    self.prelu = nn.PReLU()
                    
                def forward(self, x):
                    x = self.conv(x)
                    x = self.pixel_shuffle(x)
                    x = self.prelu(x)
                    return x

            class SRResNetLite(nn.Module):
                def __init__(self, scale_factor=2, num_channels=3, num_features=32, num_blocks=8):
                    super(SRResNetLite, self).__init__()
                    self.scale_factor = scale_factor
                    
                    self.conv_input = nn.Conv2d(num_channels, num_features, kernel_size=9, padding=4)
                    self.prelu_input = nn.PReLU()
                    
                    self.residual_blocks = nn.Sequential(
                        *[ResidualBlock(num_features) for _ in range(num_blocks)]
                    )
                    
                    self.conv_mid = nn.Conv2d(num_features, num_features, kernel_size=3, padding=1)
                    self.bn_mid = nn.BatchNorm2d(num_features)
                    
                    upsample_blocks = []
                    if scale_factor == 2:
                        upsample_blocks.append(UpsampleBlock(num_features, 2))
                    elif scale_factor == 4:
                        upsample_blocks.append(UpsampleBlock(num_features, 2))
                        upsample_blocks.append(UpsampleBlock(num_features, 2))
                    self.upsample = nn.Sequential(*upsample_blocks)
                    
                    self.conv_output = nn.Conv2d(num_features, num_channels, kernel_size=9, padding=4)
                    
                def forward(self, x):
                    out = self.conv_input(x)
                    out = self.prelu_input(out)
                    
                    residual = out
                    out = self.residual_blocks(out)
                    out = self.conv_mid(out)
                    out = self.bn_mid(out)
                    out = out + residual
                    
                    out = self.upsample(out)
                    out = self.conv_output(out)
                    
                    return out
            
            device = torch.device(self.device_)
            self.model = SRResNetLite(
                scale_factor=self.scale_,
                num_channels=3,
                num_features=self.num_features_,
                num_blocks=self.num_blocks_
            ).to(device)
            
            if self.model_path_ and os.path.exists(self.model_path_):
                self.model.load_state_dict(torch.load(self.model_path_, map_location=device))
                print(f"✓ SRResNet 加载预训练模型: {self.model_path_}")
                self.use_bicubic_fallback_ = False
            else:
                if self.use_bicubic_fallback_:
                    print(f"⚠ SRResNet 未找到预训练模型，将使用双三次插值作为备用方案")
                    self.model = None
                else:
                    print(f"⚠ SRResNet 使用随机初始化 (features={self.num_features_}, blocks={self.num_blocks_})")
            
            if self.model is not None:
                self.model.eval()
            self.torch = torch
            
            return nndeploy.base.Status.ok()
        except Exception as e:
            print(f"SRResNet 初始化失败: {e}")
            return nndeploy.base.Status(nndeploy.base.StatusCode.kStatusCodeErrorInvalidParam)
        
    def run(self):
        input_edge = self.get_input(0)
        input_numpy = input_edge.get(self)
        
        if input_numpy is None or input_numpy.size == 0:
            return nndeploy.base.Status(nndeploy.base.StatusCode.kStatusCodeErrorInvalidParam)
        
        try:
            if self.use_bicubic_fallback_ and self.model is None:
                h, w = input_numpy.shape[:2]
                new_h, new_w = h * self.scale_, w * self.scale_
                self.output_frame = cv2.resize(input_numpy, (new_w, new_h), 
                                              interpolation=cv2.INTER_CUBIC)
                
                if not self.output_frame.flags['C_CONTIGUOUS']:
                    self.output_frame = np.ascontiguousarray(self.output_frame)
                
                self.get_output(0).set(self.output_frame)
                return nndeploy.base.Status.ok()
            
            img = cv2.cvtColor(input_numpy, cv2.COLOR_BGR2RGB)
            img = img.astype(np.float32) / 255.0
            img_tensor = self.torch.from_numpy(img).permute(2, 0, 1).unsqueeze(0)
            img_tensor = img_tensor.to(self.torch.device(self.device_))
            
            with self.torch.no_grad():
                output_tensor = self.model(img_tensor)
            
            self.output_frame = output_tensor.squeeze(0).permute(1, 2, 0).cpu().numpy()
            self.output_frame = np.clip(self.output_frame * 255.0, 0, 255).astype(np.uint8)
            self.output_frame = cv2.cvtColor(self.output_frame, cv2.COLOR_RGB2BGR)
            
            if not self.output_frame.flags['C_CONTIGUOUS']:
                self.output_frame = np.ascontiguousarray(self.output_frame)
            
            self.get_output(0).set(self.output_frame)
            return nndeploy.base.Status.ok()
        except Exception as e:
            try:
                h, w = input_numpy.shape[:2]
                new_h, new_w = h * self.scale_, w * self.scale_
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
        json_obj["model_path_"] = self.model_path_
        json_obj["scale_"] = self.scale_
        json_obj["num_features_"] = self.num_features_
        json_obj["num_blocks_"] = self.num_blocks_
        json_obj["use_bicubic_fallback_"] = self.use_bicubic_fallback_
        return json.dumps(json_obj)
    
    def deserialize(self, target: str):
        json_obj = json.loads(target)
        self.model_path_ = json_obj.get("model_path_", None)
        self.scale_ = json_obj.get("scale_", 2)
        self.num_features_ = json_obj.get("num_features_", 32)
        self.num_blocks_ = json_obj.get("num_blocks_", 8)
        self.use_bicubic_fallback_ = json_obj.get("use_bicubic_fallback_", True)
        return super().deserialize(target)
    
    
class SRResNetCreator(nndeploy.dag.NodeCreator):
    def __init__(self):
        super().__init__()
        
    def create_node(self, name: str, inputs: list[nndeploy.dag.Edge], outputs: list[nndeploy.dag.Edge]):
        self.node = SRResNet(name, inputs, outputs)
        return self.node
    
srresnet_node_creator = SRResNetCreator()
nndeploy.dag.register_node("nndeploy.super_resolution.SRResNet", srresnet_node_creator)
