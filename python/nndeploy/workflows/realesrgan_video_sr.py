# Real-ESRGAN 视频超分工作流
# 使用 Real-ESRGAN 替代 GFPGAN 进行视频超分处理

import cv2
import numpy as np
from basicsr.archs.rrdbnet_arch import RRDBNet
from realesrgan import RealESRGANer
import argparse
import time
import os

class RealESRGANVideoSR:
    def __init__(self, model_path='RealESRGAN_x2plus.pth', scale=2, device='cpu'):
        """
        初始化 Real-ESRGAN 模型
        
        Args:
            model_path: 模型文件路径
            scale: 超分倍数 (2 或 4)
            device: 'cpu' 或 'cuda'
        """
        self.scale = scale
        self.device = device
        
        # 根据缩放倍数选择模型架构
        if scale == 2:
            model = RRDBNet(num_in_ch=3, num_out_ch=3, num_feat=64, 
                           num_block=23, num_grow_ch=32, scale=2)
        else:  # scale == 4
            model = RRDBNet(num_in_ch=3, num_out_ch=3, num_feat=64, 
                           num_block=23, num_grow_ch=32, scale=4)
        
        # 初始化 Real-ESRGAN
        self.upsampler = RealESRGANer(
            scale=scale,
            model_path=model_path,
            model=model,
            tile=0,  # 0 表示不使用 tile 模式
            tile_pad=10,
            pre_pad=0,
            half=False if device == 'cpu' else True,  # CPU 不支持半精度
            device=device
        )
        
        print(f"Real-ESRGAN 模型已加载: scale={scale}, device={device}")
    
    def process_frame(self, frame):
        """
        处理单帧图像
        
        Args:
            frame: BGR 格式的图像 (numpy array)
            
        Returns:
            超分后的图像
        """
        try:
            # Real-ESRGAN 期望 BGR 输入
            output, _ = self.upsampler.enhance(frame, outscale=self.scale)
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
        
        try:
            while True:
                ret, frame = cap.read()
                if not ret:
                    break
                
                frame_count += 1
                
                # 跳帧处理
                if frame_count % skip_frames == 0:
                    # 处理当前帧
                    result = self.process_frame(frame)
                    last_result = result
                    processed_count += 1
                    
                    # 计算性能指标
                    elapsed = time.time() - start_time
                    current_fps = processed_count / elapsed if elapsed > 0 else 0
                    
                    # 在图像上添加信息
                    info_text = f"Frame: {frame_count}/{total_frames} | FPS: {current_fps:.1f}"
                    cv2.putText(result, info_text, (10, 30),
                               cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 0), 2)
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
                    
                    cv2.imshow('Real-ESRGAN Video SR', display_frame)
                    if cv2.waitKey(1) & 0xFF == ord('q'):
                        print("用户中断处理")
                        break
                
                # 显示进度
                if frame_count % 30 == 0:
                    progress = frame_count / total_frames * 100
                    print(f"进度: {progress:.1f}% | 处理帧数: {processed_count} | FPS: {current_fps:.1f}")
        
        finally:
            # 清理资源
            cap.release()
            if writer:
                writer.release()
            if display:
                cv2.destroyAllWindows()
            
            # 打印统计信息
            elapsed = time.time() - start_time
            print(f"\n处理完成!")
            print(f"总帧数: {frame_count}")
            print(f"处理帧数: {processed_count}")
            print(f"总耗时: {elapsed:.2f} 秒")
            print(f"平均 FPS: {processed_count/elapsed:.2f}")
            if output_path:
                print(f"输出文件: {output_path}")


def main():
    parser = argparse.ArgumentParser(description='Real-ESRGAN 视频超分处理')
    parser.add_argument('--input', type=str, required=True, help='输入视频路径')
    parser.add_argument('--output', type=str, default=None, help='输出视频路径')
    parser.add_argument('--model', type=str, 
                       default='resources/models/RealESRGAN_x2plus.pth',
                       help='模型文件路径')
    parser.add_argument('--scale', type=int, default=2, choices=[2, 4],
                       help='超分倍数 (2 或 4)')
    parser.add_argument('--device', type=str, default='cpu', choices=['cpu', 'cuda'],
                       help='计算设备')
    parser.add_argument('--skip', type=int, default=3,
                       help='跳帧间隔 (1=不跳帧, 3=每3帧处理1帧)')
    parser.add_argument('--no-display', action='store_true',
                       help='不显示实时结果')
    
    args = parser.parse_args()
    
    # 检查输入文件
    if not os.path.exists(args.input):
        print(f"错误: 输入文件不存在: {args.input}")
        return
    
    # 检查模型文件
    if not os.path.exists(args.model):
        print(f"警告: 模型文件不存在: {args.model}")
        print("请先下载 Real-ESRGAN 模型:")
        print("  x2: wget https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.1/RealESRGAN_x2plus.pth")
        print("  x4: wget https://github.com/xinntao/Real-ESRGAN/releases/download/v0.1.0/RealESRGAN_x4plus.pth")
        return
    
    # 创建处理器
    processor = RealESRGANVideoSR(
        model_path=args.model,
        scale=args.scale,
        device=args.device
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
