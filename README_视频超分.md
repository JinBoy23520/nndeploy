# 视频超分自定义节点 - 完整文档索引

本项目为 nndeploy 3.0.7 框架添加了 6 个自定义 Python 节点和 11 个工作流配置，实现了实时视频超分辨率处理和效果对比功能。

---

## 📚 文档导航

| 文档名称 | 用途 | 适用人群 |
|---------|------|---------|
| [跨平台部署指南.md](跨平台部署指南.md) | 节点介绍、Git提交策略、macOS部署 | ⭐⭐⭐⭐⭐ 所有人必读 |
| [提交代码指南.md](提交代码指南.md) | Git提交命令、常见错误解决 | Windows开发者 |
| [视频超分效果说明.md](视频超分效果说明.md) | 各方案效果对比、性能分析 | 使用者 |
| [OpenCV超分模型下载指南.md](OpenCV超分模型下载指南.md) | 模型下载地址和使用方法 | 模型使用者 |
| [自定义节点文件清单.md](自定义节点文件清单.md) | 所有文件路径和完整性检查 | 开发者 |
| [README_视频超分.md](README_视频超分.md) | 本文件，文档索引 | 入口文档 |

---

## 🚀 快速开始

### Windows 用户

```powershell
# 1. 提交代码到 Git (首次)
.\commit_custom_nodes.ps1

# 2. 启动服务
& venv311\Scripts\Activate.ps1
python app.py
```

### macOS 用户

```bash
# 1. 克隆仓库
git clone <your-repo-url>
cd nndeploy-1

# 2. 自动配置环境
bash setup_macos.sh

# 3. 启动服务
source venv311/bin/activate
python app.py
```

---

## 📦 项目结构

```
nndeploy-1/
├── python/nndeploy/
│   ├── super_resolution/          # 超分辨率节点
│   │   ├── realesrgan.py         # Real-ESRGAN (高质量)
│   │   ├── srresnet.py           # SRResNet (轻量)
│   │   └── opencv_superres.py    # OpenCV DNN (推荐)
│   └── codec/                     # 编解码节点
│       ├── video_player.py       # 视频播放
│       ├── side_by_side_compare.py    # 左右对比
│       └── detail_zoom_compare.py     # 局部放大
│
├── resources/workflow/            # 工作流配置
│   ├── EDSR超分对比.json         # ⭐⭐⭐⭐⭐ 推荐
│   ├── EDSR超分（局部细节对比）.json  # ⭐⭐⭐⭐⭐ 推荐
│   ├── EDSR 4倍超分（原始尺寸）.json  # ⭐⭐⭐⭐⭐ 推荐
│   └── ...                       # 其他工作流
│
├── 跨平台部署指南.md              # ⭐ 主文档
├── 提交代码指南.md                # Git 提交
├── 视频超分效果说明.md            # 效果对比
├── OpenCV超分模型下载指南.md      # 模型下载
├── 自定义节点文件清单.md          # 文件清单
├── commit_custom_nodes.ps1       # Windows 提交脚本
└── setup_macos.sh                # macOS 部署脚本
```

---

## 🎯 功能特点

### 1. 多种超分算法
- ✅ **Real-ESRGAN**: 最高质量，适合通用图像
- ✅ **GFPGAN**: 专业脸部修复
- ✅ **OpenCV EDSR**: 高质量，轻量级 (38MB)
- ✅ **OpenCV ESPCN**: 最快速度 (84KB)
- ✅ **OpenCV FSRCNN**: 速度质量平衡 (38KB)
- ✅ **Lanczos + 锐化**: 无需模型，立即可用

### 2. 多种对比方式
- ✅ **左右对比**: 原图 vs 超分图
- ✅ **原始尺寸**: 显示实际分辨率差异 (320x180 vs 1280x720)
- ✅ **局部放大**: 6倍放大细节对比，清晰看到像素级差异

### 3. 跨平台支持
- ✅ Windows 10/11
- ✅ macOS (Intel & Apple Silicon)
- ✅ Linux

---

## 🏆 推荐工作流

### 无需下载模型 (立即可用)
1. **OpenCV超分对比.json** - Lanczos + 锐化
   - 无需模型，立即运行
   - 效果: ⭐⭐⭐
   - 速度: 60+ FPS

### 轻量级模型 (< 40MB，推荐)
2. **EDSR超分对比.json** - EDSR 2x
   - 需要: EDSR_x2.pb (38MB)
   - 效果: ⭐⭐⭐⭐⭐
   - 速度: 10-20 FPS

3. **EDSR超分（局部细节对比）.json** - 局部6倍放大
   - 需要: EDSR_x2.pb (38MB)
   - 效果: ⭐⭐⭐⭐⭐ (最清晰)
   - 特点: 像素级对比

4. **EDSR 4倍超分（原始尺寸）.json** - 显示尺寸差异
   - 需要: EDSR_x4.pb (38MB)
   - 效果: ⭐⭐⭐⭐⭐ (最震撼)
   - 特点: 320x180 vs 1280x720 视觉冲击

### 重量级模型
5. **RealESRGAN视频超分.json** - 最高质量
   - 需要: RealESRGAN_x2plus.pth (17MB)
   - 效果: ⭐⭐⭐⭐⭐
   - 速度: 3-8 FPS

6. **脸部超分修复.json** - 脸部专用
   - 需要: GFPGANv1.4.pth (348MB)
   - 效果: ⭐⭐⭐⭐⭐ (脸部最佳)
   - 速度: 5-10 FPS

---

## 📊 性能对比

| 方案 | 模型大小 | 效果 | 速度 (CPU) | 速度 (GPU) | 推荐度 |
|------|---------|------|-----------|-----------|--------|
| Lanczos+锐化 | 0 | ⭐⭐⭐ | 60+ FPS | 60+ FPS | ⭐⭐⭐ |
| ESPCN | 84KB | ⭐⭐⭐ | 40-60 FPS | 60+ FPS | ⭐⭐⭐ |
| FSRCNN | 38KB | ⭐⭐⭐⭐ | 30-50 FPS | 50+ FPS | ⭐⭐⭐⭐ |
| EDSR | 38MB | ⭐⭐⭐⭐⭐ | 10-20 FPS | 30+ FPS | ⭐⭐⭐⭐⭐ |
| Real-ESRGAN | 17MB | ⭐⭐⭐⭐⭐ | 3-8 FPS | 15+ FPS | ⭐⭐⭐⭐⭐ |
| GFPGAN | 348MB | ⭐⭐⭐⭐⭐ | 5-10 FPS | 20+ FPS | ⭐⭐⭐⭐⭐ |

---

## 🔧 常见问题

### Q1: 为什么效果不明显?
**A**: 使用以下工作流可以看到明显效果：
- `EDSR 4倍超分（原始尺寸）.json` - 显示实际尺寸差异
- `EDSR超分（局部细节对比）.json` - 6倍放大细节对比

### Q2: 播放速度太快或太慢?
**A**: VideoPlayer 节点自动检测视频帧率，如需手动调整:
```json
{
  "fps_": 30,  // 手动设置 FPS
  "auto_fps_": false  // 关闭自动检测
}
```

### Q3: macOS 上窗口不显示?
**A**: 安装支持 GUI 的 OpenCV:
```bash
pip uninstall opencv-python
pip install opencv-contrib-python
```

### Q4: 模型文件太大，如何下载?
**A**: 参考 [OpenCV超分模型下载指南.md](OpenCV超分模型下载指南.md)
- EDSR (推荐): 38MB
- ESPCN (最快): 84KB
- FSRCNN (平衡): 38KB

### Q5: 如何在 Mac 上运行?
**A**: 参考 [跨平台部署指南.md](跨平台部署指南.md) 第2-3章
```bash
bash setup_macos.sh  # 自动配置环境
```

---

## 📝 开发日志

### v1.0.0 (2026-01-09)
- ✅ 创建 RealESRGAN, SRResNet, OpenCVSuperRes 节点
- ✅ 创建 VideoPlayer, SideBySideCompare, DetailZoomCompare 节点
- ✅ 创建 11 个工作流配置
- ✅ 修复黑屏、窗口闪退、播放速度等问题
- ✅ 解决"效果不明显"问题（添加原始尺寸和局部放大对比）
- ✅ 完善跨平台部署文档
- ✅ 总计: 996 行代码，11 个工作流，5 个文档

---

## 🤝 贡献指南

### 添加新节点
1. 在 `python/nndeploy/super_resolution/` 或 `python/nndeploy/codec/` 创建 Python 文件
2. 继承 `nndeploy.dag.Node` 类
3. 实现 `init()`, `run()`, `serialize()`, `deserialize()` 方法
4. 在 `__init__.py` 中注册节点
5. 创建工作流 JSON 配置文件
6. 更新文档

### 提交代码
```bash
# 使用一键提交脚本 (Windows)
.\commit_custom_nodes.ps1

# 或手动提交
git add python/nndeploy/**/*.py
git add resources/workflow/*.json
git commit -m "feat: 添加新节点"
git push
```

---

## 📖 参考资源

### 官方文档
- [nndeploy GitHub](https://github.com/nndeploy/nndeploy)
- [Real-ESRGAN](https://github.com/xinntao/Real-ESRGAN)
- [GFPGAN](https://github.com/TencentARC/GFPGAN)
- [OpenCV DNN Super Resolution](https://docs.opencv.org/master/d5/d29/tutorial_dnn_superres_upscale_image_single.html)

### 模型下载
- [EDSR TensorFlow Models](https://github.com/Saafke/EDSR_Tensorflow)
- [ESPCN TensorFlow Models](https://github.com/fannymonori/TF-ESPCN)
- [FSRCNN TensorFlow Models](https://github.com/Saafke/FSRCNN_Tensorflow)

---

## 📞 支持

如有问题，请参考：
1. [跨平台部署指南.md](跨平台部署指南.md) - 常见问题章节
2. [提交代码指南.md](提交代码指南.md) - Git 常见错误
3. GitHub Issues (如果项目有公开仓库)

---

## 📄 许可证

遵循 nndeploy 项目的许可证。

---

## 🎉 致谢

感谢 nndeploy 团队提供优秀的推理部署框架！

---

**最后更新**: 2026年1月9日
