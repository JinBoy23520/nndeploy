#!/usr/bin/env python3
"""
在终端直接运行视频超分工作流（带窗口显示）
不通过 web 服务，适合 macOS 窗口显示

用法:
    python run_workflow_with_window.py <workflow_json_path> <input_video_path>
    
示例:
    python run_workflow_with_window.py "resources/workflow/GFPGAN脸部超分对比.json" "resources/videos/face.mp4"
"""

import sys
import os
import json

# 设置 Python 路径
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'python'))
os.environ['PYTHONPATH'] = os.path.join(os.path.dirname(__file__), 'python')

import nndeploy
from nndeploy.dag.graph_runner import GraphRunner

def load_workflow(workflow_path):
    """加载工作流 JSON"""
    with open(workflow_path, 'r', encoding='utf-8') as f:
        return json.load(f)

def update_input_path(workflow_json, input_path):
    """更新工作流中的输入路径"""
    # 查找视频输入节点并更新路径
    for node in workflow_json.get('nodes', []):
        if 'OpenCvVideoDecode' in node.get('name', ''):
            if 'param' in node:
                if isinstance(node['param'], dict):
                    node['param']['video_path_'] = input_path
                else:
                    try:
                        param = json.loads(node['param'])
                        param['video_path_'] = input_path
                        node['param'] = json.dumps(param, ensure_ascii=False)
                    except:
                        pass
    return workflow_json

def main():
    if len(sys.argv) < 2:
        print("用法: python run_workflow_with_window.py <workflow_json> [input_video]")
        print()
        print("示例:")
        print('  python run_workflow_with_window.py "resources/workflow/GFPGAN脸部超分对比.json" "resources/videos/face.mp4"')
        print('  python run_workflow_with_window.py "resources/workflow/SRResNet 4倍超分.json"')
        return 1
    
    workflow_path = sys.argv[1]
    input_video = sys.argv[2] if len(sys.argv) > 2 else None
    
    # 检查文件是否存在
    if not os.path.exists(workflow_path):
        print(f"✗ 工作流文件不存在: {workflow_path}")
        return 1
    
    print("=" * 60)
    print("nndeploy 视频超分工作流（终端模式）")
    print("=" * 60)
    print(f"工作流: {workflow_path}")
    if input_video:
        print(f"输入视频: {input_video}")
        if not os.path.exists(input_video):
            print(f"✗ 输入视频不存在: {input_video}")
            return 1
    print("-" * 60)
    
    try:
        # 加载工作流
        print("正在加载工作流...")
        workflow_json = load_workflow(workflow_path)
        
        # 更新输入路径
        if input_video:
            workflow_json = update_input_path(workflow_json, input_video)
        
        # 转换为 JSON 字符串（GraphRunner 需要字符串格式）
        workflow_json_str = json.dumps(workflow_json, ensure_ascii=False)
        
        # 创建并运行工作流
        print("正在初始化工作流...")
        runner = GraphRunner()
        
        print("正在运行工作流...")
        print("\n提示:")
        print("  - 窗口会自动打开显示处理结果")
        print("  - 按 ESC 键停止处理")
        print("  - 按空格键暂停/继续")
        print("-" * 60)
        print()
        
        # 运行工作流（添加 task_id 参数）
        import uuid
        task_id = str(uuid.uuid4())
        tp_map, results, status, msg = runner.run(workflow_json_str, "workflow_terminal", task_id)
        
        if status.ok():
            print("\n✅ 工作流执行完成！")
            return 0
        else:
            print(f"\n✗ 工作流执行失败: {msg}")
            return 1
            
    except KeyboardInterrupt:
        print("\n\n用户中断执行 (Ctrl+C)")
        return 130
    except Exception as e:
        print(f"\n✗ 执行出错: {e}")
        import traceback
        traceback.print_exc()
        return 1

if __name__ == "__main__":
    exit(main())
