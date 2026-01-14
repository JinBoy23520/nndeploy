# SRResNet-lite 视频超分工作流
# 使用轻量级 SRResNet 模型进行快速视频超分处理

import cv2
import numpy as np
import torch
import torch.nn as nn
import argparse
import time
import os

class ResidualBlock(nn.Module):
    """SRResNet 的残差块"""
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
    """上采样块 (PixelShuffle)"""
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
    """
    轻量级 SRResNet 模型
    参数量约为标准 SRResNet 的 1/3
    """
    def __init__(self, scale_factor=2, num_channels=3, num_features=32, num_blocks=8):
        super(SRResNetLite, self).__init__()
        self.scale_factor = scale_factor
        
        # 初始卷积层
        self.conv_input = nn.Conv2d(num_channels, num_features, kernel_size=9, padding=4)
        self.prelu_input = nn.PReLU()
        
        # 残差块
        self.residual_blocks = nn.Sequential(
            *[ResidualBlock(num_features) for _ in range(num_blocks)]
        )
        
        # 中间卷积层
        self.conv_mid = nn.Conv2d(num_features, num_features, kernel_size=3, padding=1)
        self.bn_mid = nn.BatchNorm2d(num_features)
        
        # 上采样层
        upsample_blocks = []
        if scale_factor == 2:
            upsample_blocks.append(UpsampleBlock(num_features, 2))
        elif scale_factor == 4:
            upsample_blocks.append(UpsampleBlock(num_features, 2))
            upsample_blocks.append(UpsampleBlock(num_features, 2))
        self.upsample = nn.Sequential(*upsample_blocks)
        
        # 输出卷积层
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


class SRResNetVideoSR:
    def __init__(self, model_path=None, scale=2, device='cpu', 
                 num_features=32, num_blocks=8):
        """
        初始化 SRResNet-lite 模型
        
        Args:
            model_path: 预训练模型路径 (None 表示使用随机初始化)
            scale: 超分倍数 (2 或 4)
            device: 'cpu' 或 'cuda'
            num_features: 特征通道数 (默认32, 标准版为64)
            num_blocks: 残差块数量 (默认8, 标准版为16)
        """
        self.scale = scale
        self.device = torch.device(device)
        
        # 创建模型
        self.model = SRResNetLite(
            scale_factor=scale,
            num_channels=3,
            num_features=num_features,
            num_blocks=num_blocks
        ).to(self.device)
        
        # 加载预训练权重
        if model_path and os.path.exists(model_path):
            print(f"加载预训练模型: {model_path}")
            self.model.load_state_dict(torch.load(model_path, map_location=self.device))
        else:
            print("使用随机初始化的模型 (未加载预训练权重)")
        
        self.model.eval()
        
        # 计算模型参数量
        total_params = sum(p.numel() for p in self.model.parameters())
        print(f"SRResNet-lite 模型参数量: {total_params/1e6:.2f}M")
        print(f"配置: scale={scale}, features={num_features}, blocks={num_blocks}, device={device}")
    
    def preprocess(self, frame):
        """预处理图像"""
        # BGR -> RGB -> Tensor
        img = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        img = img.astype(np.float32) / 255.0
        img = torch.from_numpy(img).permute(2, 0, 1).unsqueeze(0)
        return img.to(self.device)
    
    def postprocess(self, tensor):
        """后处理张量"""
        img = tensor.squeeze(0).permute(1, 2, 0).cpu().numpy()
        img = np.clip(img * 255.0, 0, 255).astype(np.uint8)
        img = cv2.cvtColor(img, cv2.COLOR_RGB2BGR)
        return img
    
    def process_frame(self, frame):
        """
        处理单帧图像
        
        Args:
            frame: BGR 格式的图像 (numpy array)
            
        Returns:
            超分后的图像
        """
        try:
            with torch.no_grad():
                # 预处理
                input_tensor = self.preprocess(frame)
                
                # 推理
                output_tensor = self.model(input_tensor)
                
                # 后处理
                output = self.postprocess(output_tensor)
                
                return output
        except Exception as e:
            print(f"处理帧时出错: {e}")
            return frame
    
    def process_video(self, input_path, output_path=None, display=True, skip_frames=1):
        """
        处理视频文件
        
        Args:
            input_path: 输入视频路径
            output_path: 输出视频路径 (None 表示不保存)
            display: 是否显示处理结果
            skip_frames: 跳帧间隔 (1=不跳帧, 2=每2帧处理1帧)
        """
        cap = cv2.VideoCapture(input_path)
        
        if not cap.isOpened():
            print(f"无法打开视频文件: {input_path}")
            return
        
        # 获取视频属性
        fps = cap.get(cv2.CAP_PROP_FPS)
        width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        
        print(f"输入视频: {width}x{height}, {fps} FPS, {total_frames} 帧")
        print(f"输出分辨率: {width*self.scale}x{height*self.scale}")
        print(f"跳帧间隔: {skip_frames}")
        
        # 创建视频写入器
        writer = None
        if output_path:
            fourcc = cv2.VideoWriter_fourcc(*'mp4v')
            writer = cv2.VideoWriter(
                output_path, fourcc, fps,
                (width * self.scale, height * self.scale)
            )
        
        frame_count = 0
        processed_count = 0
        start_time = time.time()
        last_result = None
        
        # 推理时间统计
        inference_times = []
        
        try:
            while True:
                ret, frame = cap.read()
                if not ret:
                    break
                
                frame_count += 1
                
                # 跳帧处理
                if frame_count % skip_frames == 0:
                    # 处理当前帧
                    infer_start = time.time()
                    result = self.process_frame(frame)
                    infer_time = (time.time() - infer_start) * 1000  # ms
                    inference_times.append(infer_time)
                    
                    last_result = result
                    processed_count += 1
                    
                    # 计算性能指标
                    elapsed = time.time() - start_time
                    current_fps = processed_count / elapsed if elapsed > 0 else 0
                    avg_infer_time = np.mean(inference_times[-30:])  # 最近30帧平均
                    
                    # 在图像上添加信息
                    info_text = f"Frame: {frame_count}/{total_frames} | FPS: {current_fps:.1f} | Infer: {avg_infer_time:.0f}ms"
                    cv2.putText(result, info_text, (10, 30),
                               cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 255, 0), 2)
                else:
                    # 使用上一帧结果
                    result = last_result if last_result is not None else frame
                
                # 保存到视频
                if writer:
                    writer.write(result)
                
                # 显示结果
                if display:
                    # 缩放显示以适应屏幕
                    display_width = 1280
                    display_height = int(result.shape[0] * display_width / result.shape[1])
                    display_frame = cv2.resize(result, (display_width, display_height))
                    
                    cv2.imshow('SRResNet-lite Video SR', display_frame)
                    if cv2.waitKey(1) & 0xFF == ord('q'):
                        print("用户中断处理")
                        break
                
                # 显示进度
                if frame_count % 30 == 0:
                    progress = frame_count / total_frames * 100
                    print(f"进度: {progress:.1f}% | 处理: {processed_count} | FPS: {current_fps:.1f} | Infer: {avg_infer_time:.0f}ms")
        
        finally:
            # 清理资源
            cap.release()
            if writer:
                writer.release()
            if display:
                cv2.destroyAllWindows()
            
            # 打印统计信息
            elapsed = time.time() - start_time
            avg_infer = np.mean(inference_times) if inference_times else 0
            print(f"\n处理完成!")
            print(f"总帧数: {frame_count}")
            print(f"处理帧数: {processed_count}")
            print(f"总耗时: {elapsed:.2f} 秒")
            print(f"平均 FPS: {processed_count/elapsed:.2f}")
            print(f"平均推理时间: {avg_infer:.2f} ms")
            if output_path:
                print(f"输出文件: {output_path}")


def main():
    parser = argparse.ArgumentParser(description='SRResNet-lite 视频超分处理')
    parser.add_argument('--input', type=str, required=True, help='输入视频路径')
    parser.add_argument('--output', type=str, default=None, help='输出视频路径')
    parser.add_argument('--model', type=str, default=None,
                       help='预训练模型路径 (可选)')
    parser.add_argument('--scale', type=int, default=2, choices=[2, 4],
                       help='超分倍数 (2 或 4)')
    parser.add_argument('--device', type=str, default='cpu', choices=['cpu', 'cuda'],
                       help='计算设备')
    parser.add_argument('--features', type=int, default=32,
                       help='特征通道数 (32=lite, 64=standard)')
    parser.add_argument('--blocks', type=int, default=8,
                       help='残差块数量 (8=lite, 16=standard)')
    parser.add_argument('--skip', type=int, default=2,
                       help='跳帧间隔 (1=不跳帧, 2=每2帧处理1帧)')
    parser.add_argument('--no-display', action='store_true',
                       help='不显示实时结果')
    
    args = parser.parse_args()
    
    # 检查输入文件
    if not os.path.exists(args.input):
        print(f"错误: 输入文件不存在: {args.input}")
        return
    
    # 创建处理器
    processor = SRResNetVideoSR(
        model_path=args.model,
        scale=args.scale,
        device=args.device,
        num_features=args.features,
        num_blocks=args.blocks
    )
    
    # 处理视频
    processor.process_video(
        input_path=args.input,
        output_path=args.output,
        display=not args.no_display,
        skip_frames=args.skip
    )


if __name__ == '__main__':
    main()
