# nndeploy 快速开始指南

## 🚀 5分钟快速部署

### 环境要求
- Windows 10/11
- Visual Studio 2022
- Python 3.11
- CMake 3.20+

### 快速部署步骤

#### 1. 环境准备 (2分钟)
```bash
# 下载并安装Python 3.11
# 创建虚拟环境
python311\python.exe -m venv venv311
venv311\Scripts\activate

# 安装依赖
pip install -r requirements.txt
```

#### 2. 构建项目 (3分钟)
```bash
# 创建构建目录
mkdir build
cd build

# 配置并构建
cmake .. -DCMAKE_BUILD_TYPE=Release -DENABLE_NNDEPLOY_PYTHON=ON -DPython_ROOT_DIR="..\venv311" -DPYBIND11_PYTHON_VERSION="3.11"
cmake --build . --config Release --parallel

# 安装Python扩展
copy python\Release\_nndeploy_internal.cp311-win_amd64.pyd ..\python\nndeploy\_nndeploy_internal.pyd
```

#### 3. 验证部署 (30秒)
```bash
# 测试Python绑定
python -c "import nndeploy; graph = nndeploy.dag.Graph('test'); graph.init(); print('✅ 部署成功!')"

# 启动服务器
python ..\app.py --port 8000
```

### 🎯 验证成功标志

- ✅ Python导入无错误
- ✅ Graph创建和初始化成功
- ✅ 服务器在指定端口启动
- ✅ 无访问冲突或DLL加载错误

### 🆘 常见问题快速解决

| 问题 | 快速解决 |
|------|----------|
| Python版本不匹配 | 使用Python 3.11 venv |
| CMake找不到Python | 设置Python_ROOT_DIR |
| DLL加载失败 | 检查编译器版本一致性 |
| Graph属性错误 | 使用`nndeploy.dag.Graph` |

### 📋 完整部署检查清单

- [ ] Python 3.11 虚拟环境创建
- [ ] 所有依赖包安装完成
- [ ] CMake配置成功（无错误）
- [ ] 项目构建完成（无错误）
- [ ] Python扩展正确复制
- [ ] 基本功能测试通过
- [ ] 服务器成功启动

### 🔧 一键部署脚本

创建 `deploy.ps1` 脚本：

```powershell
# deploy.ps1 - 一键部署脚本
Write-Host "🚀 开始部署 nndeploy..."

# 激活环境
& "venv311\Scripts\activate"

# 构建项目
if (!(Test-Path "build")) { mkdir build }
cd build
cmake .. -DCMAKE_BUILD_TYPE=Release -DENABLE_NNDEPLOY_PYTHON=ON -DPython_ROOT_DIR="..\venv311" -DPYBIND11_PYTHON_VERSION="3.11"
cmake --build . --config Release --parallel

# 复制扩展
copy python\Release\_nndeploy_internal.cp311-win_amd64.pyd ..\python\nndeploy\_nndeploy_internal.pyd

# 测试
cd ..
python -c "import nndeploy; graph = nndeploy.dag.Graph('test'); graph.init(); print('✅ 部署成功!')"

Write-Host "🎉 部署完成!"
```

运行一键部署：
```bash
.\deploy.ps1
```

### 📊 部署状态监控

```bash
# 检查服务器状态
curl http://localhost:8000/health

# 查看系统资源使用
Get-Process | Where-Object {$_.ProcessName -like "*python*"}

# 检查端口占用
netstat -ano | findstr :8000
```

### 🎉 部署成功！

恭喜！nndeploy 已成功部署在您的系统上。

- 🌐 Web界面: http://localhost:8000
- 📚 完整文档: `DEPLOYMENT_GUIDE.md`
- 🆘 需要帮助: 查看故障排除部分

享受使用 nndeploy 进行AI推理部署！ 🤖</content>
<parameter name="filePath">d:\jinwork\nndeploy-1\QUICK_START.md