#!/bin/bash

# nndeploy Android TV 重新编译并部署脚本
# 用途: 修改 C++ 代码后快速重新编译并更新 Android 项目

set -e  # 遇到错误立即退出

echo "=========================================="
echo "   nndeploy Android 重新编译脚本"
echo "=========================================="
echo ""

# 进入构建目录
cd /Users/jin/work/nndeploy/build_android_arm64

echo "✓ 清理旧的构建产物..."
/opt/homebrew/bin/ninja clean

echo ""
echo "✓ 开始编译 (使用8个并行任务)..."
/opt/homebrew/bin/ninja -j8

if [ $? -eq 0 ]; then
    echo ""
    echo "✓ 编译成功！"
    echo ""
    echo "✓ 复制库文件到 Android 项目..."
    
    # 复制所有 nndeploy 库
    cp -f libnndeploy_*.so \
        /Users/jin/work/nndeploy/app/android/app/src/main/jniLibs/arm64-v8a/
    
    echo ""
    echo "=========================================="
    echo "   部署完成！"
    echo "=========================================="
    echo ""
    echo "下一步操作:"
    echo "1. 在 Android Studio 中打开项目"
    echo "2. Build → Clean Project"
    echo "3. Build → Rebuild Project"
    echo "4. Run → Run 'app'"
    echo ""
    echo "或者在命令行运行:"
    echo "  cd /Users/jin/work/nndeploy/app/android"
    echo "  ./gradlew clean assembleDebug"
    echo ""
else
    echo ""
    echo "✗ 编译失败！请检查错误信息。"
    exit 1
fi
