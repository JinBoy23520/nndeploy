#!/usr/bin/env python3
"""
将 RealESRGAN PyTorch 模型转换为 ONNX 格式
用于 C++ 原生实现的 Android 部署
"""

import argparse
import os
import sys
import torch
import torch.onnx


def convert_realesrgan_to_onnx(model_path, output_path, scale=2, opset_version=11):
    """
    转换 RealESRGAN 模型到 ONNX 格式
    
    Args:
        model_path: 输入的 .pth 模型路径
        output_path: 输出的 .onnx 模型路径
        scale: 放大倍数 (2 or 4)
        opset_version: ONNX opset 版本
    """
    try:
        from basicsr.archs.rrdbnet_arch import RRDBNet
        from realesrgan import RealESRGANer
    except ImportError:
        print("错误: 需要安装 realesrgan 和 basicsr")
        print("运行: pip install realesrgan basicsr")
        sys.exit(1)
    
    print(f"=== RealESRGAN PyTorch → ONNX 转换工具 ===")
    print(f"输入模型: {model_path}")
    print(f"输出模型: {output_path}")
    print(f"放大倍数: {scale}x")
    print(f"ONNX Opset: {opset_version}")
    print()
    
    # 检查输入文件
    if not os.path.exists(model_path):
        print(f"错误: 模型文件不存在: {model_path}")
        sys.exit(1)
    
    # 创建输出目录
    output_dir = os.path.dirname(output_path)
    if output_dir and not os.path.exists(output_dir):
        os.makedirs(output_dir)
    
    # 创建模型
    print("1. 创建 RRDBNet 模型架构...")
    if scale == 2:
        model = RRDBNet(num_in_ch=3, num_out_ch=3, num_feat=64, 
                       num_block=23, num_grow_ch=32, scale=2)
    elif scale == 4:
        model = RRDBNet(num_in_ch=3, num_out_ch=3, num_feat=64, 
                       num_block=23, num_grow_ch=32, scale=4)
    else:
        print(f"错误: 不支持的放大倍数: {scale}")
        sys.exit(1)
    
    # 加载权重
    print("2. 加载 PyTorch 权重...")
    try:
        loadnet = torch.load(model_path, map_location=torch.device('cpu'))
        if 'params_ema' in loadnet:
            keyname = 'params_ema'
        else:
            keyname = 'params'
        model.load_state_dict(loadnet[keyname], strict=True)
    except Exception as e:
        print(f"错误: 加载模型权重失败: {e}")
        sys.exit(1)
    
    # 设置为评估模式
    model.eval()
    model.cpu()
    
    # 创建示例输入 (NCHW format: batch, channels, height, width)
    # 使用 256x256 作为示例，实际推理时可以使用任意尺寸（动态shape）
    print("3. 创建示例输入张量...")
    dummy_input = torch.randn(1, 3, 256, 256)
    
    # 测试前向传播
    print("4. 测试模型前向传播...")
    with torch.no_grad():
        dummy_output = model(dummy_input)
        print(f"   输入 shape: {dummy_input.shape}")
        print(f"   输出 shape: {dummy_output.shape}")
    
    # 导出为 ONNX
    print("5. 导出为 ONNX 格式...")
    try:
        torch.onnx.export(
            model,
            dummy_input,
            output_path,
            export_params=True,
            opset_version=opset_version,
            do_constant_folding=True,
            input_names=['input'],
            output_names=['output'],
            dynamic_axes={
                'input': {0: 'batch', 2: 'height', 3: 'width'},
                'output': {0: 'batch', 2: 'height', 3: 'width'}
            }
        )
        print(f"✓ 成功导出到: {output_path}")
    except Exception as e:
        print(f"错误: ONNX 导出失败: {e}")
        sys.exit(1)
    
    # 验证 ONNX 模型
    print("6. 验证 ONNX 模型...")
    try:
        import onnx
        onnx_model = onnx.load(output_path)
        onnx.checker.check_model(onnx_model)
        print("✓ ONNX 模型验证通过")
    except ImportError:
        print("⚠ 警告: 未安装 onnx，跳过验证")
    except Exception as e:
        print(f"⚠ 警告: ONNX 验证失败: {e}")
    
    # 测试 ONNX Runtime 推理
    print("7. 测试 ONNX Runtime 推理...")
    try:
        import onnxruntime as ort
        import numpy as np
        
        # 创建推理会话
        sess = ort.InferenceSession(output_path)
        
        # 准备输入
        input_name = sess.get_inputs()[0].name
        output_name = sess.get_outputs()[0].name
        input_data = dummy_input.numpy()
        
        # 运行推理
        outputs = sess.run([output_name], {input_name: input_data})
        
        # 对比结果
        pytorch_output = dummy_output.numpy()
        onnx_output = outputs[0]
        
        diff = np.abs(pytorch_output - onnx_output).max()
        print(f"   PyTorch vs ONNX 最大差异: {diff:.6f}")
        
        if diff < 1e-3:
            print("✓ ONNX Runtime 推理测试通过")
        else:
            print(f"⚠ 警告: 输出差异较大: {diff}")
    except ImportError:
        print("⚠ 警告: 未安装 onnxruntime，跳过推理测试")
    except Exception as e:
        print(f"⚠ 警告: ONNX Runtime 测试失败: {e}")
    
    # 显示模型信息
    print("\n=== 模型信息 ===")
    print(f"输入: [batch, 3, height, width] (float32)")
    print(f"输出: [batch, 3, height*{scale}, width*{scale}] (float32)")
    print(f"动态维度: batch, height, width")
    
    file_size = os.path.getsize(output_path) / (1024 * 1024)
    print(f"文件大小: {file_size:.1f} MB")
    
    print("\n✓ 转换完成！")
    print(f"\n下一步:")
    print(f"1. 将 ONNX 模型复制到 Android assets:")
    print(f"   cp {output_path} app/android/app/src/main/assets/resources/models/")
    print(f"2. 重新编译 nndeploy Android 库")
    print(f"3. 在 Android 应用中测试超分功能")


def main():
    parser = argparse.ArgumentParser(
        description="将 RealESRGAN PyTorch 模型转换为 ONNX 格式"
    )
    parser.add_argument(
        "--model",
        "-m",
        type=str,
        required=True,
        help="输入的 PyTorch 模型路径 (.pth)"
    )
    parser.add_argument(
        "--output",
        "-o",
        type=str,
        required=True,
        help="输出的 ONNX 模型路径 (.onnx)"
    )
    parser.add_argument(
        "--scale",
        "-s",
        type=int,
        default=2,
        choices=[2, 4],
        help="放大倍数 (默认: 2)"
    )
    parser.add_argument(
        "--opset",
        type=int,
        default=11,
        help="ONNX opset 版本 (默认: 11)"
    )
    
    args = parser.parse_args()
    
    convert_realesrgan_to_onnx(
        model_path=args.model,
        output_path=args.output,
        scale=args.scale,
        opset_version=args.opset
    )


if __name__ == "__main__":
    main()
