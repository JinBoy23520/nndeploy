# OpenCV DNN 超分辨率模型下载指南

## 立即可用方案（无需下载）

**工作流**: `OpenCV超分对比.json`

**算法**: Lanczos 插值 + Unsharp Mask 锐化

**效果**: ⭐⭐⭐（比双三次插值清晰很多）
- ✅ 边缘更锐利
- ✅ 细节更清晰
- ✅ CPU 实时运行（60+ FPS）
- ✅ 无需下载模型

---

## 进阶方案（下载轻量级模型）

OpenCV 提供了预训练的轻量级超分模型，比 Lanczos+锐化效果更好。

### 1. EDSR（推荐 - 效果最好）

**下载地址**:
```bash
# EDSR x2 模型（约 1.5MB）
https://github.com/Saafke/EDSR_Tensorflow/raw/master/models/EDSR_x2.pb

# EDSR x3 模型（约 1.5MB）
https://github.com/Saafke/EDSR_Tensorflow/raw/master/models/EDSR_x3.pb

# EDSR x4 模型（约 1.5MB）
https://github.com/Saafke/EDSR_Tensorflow/raw/master/models/EDSR_x4.pb
```

**保存路径**:
```
resources/models/opencv_dnn_superres/EDSR_x2.pb
resources/models/opencv_dnn_superres/EDSR_x3.pb
resources/models/opencv_dnn_superres/EDSR_x4.pb
```

**效果**: ⭐⭐⭐⭐（接近 AI 超分）

---

### 2. ESPCN（最快）

**下载地址**:
```bash
# ESPCN x2 模型（约 100KB）
https://github.com/fannymonori/TF-ESPCN/raw/master/export/ESPCN_x2.pb

# ESPCN x3 模型（约 100KB）
https://github.com/fannymonori/TF-ESPCN/raw/master/export/ESPCN_x3.pb

# ESPCN x4 模型（约 100KB）
https://github.com/fannymonori/TF-ESPCN/raw/master/export/ESPCN_x4.pb
```

**保存路径**:
```
resources/models/opencv_dnn_superres/ESPCN_x2.pb
resources/models/opencv_dnn_superres/ESPCN_x3.pb
resources/models/opencv_dnn_superres/ESPCN_x4.pb
```

**效果**: ⭐⭐⭐（速度极快，实时运行）

---

### 3. FSRCNN（平衡）

**下载地址**:
```bash
# FSRCNN x2 模型（约 40KB）
https://github.com/Saafke/FSRCNN_Tensorflow/raw/master/models/FSRCNN_x2.pb

# FSRCNN x3 模型（约 40KB）
https://github.com/Saafke/FSRCNN_Tensorflow/raw/master/models/FSRCNN_x3.pb

# FSRCNN x4 模型（约 40KB）
https://github.com/Saafke/FSRCNN_Tensorflow/raw/master/models/FSRCNN_x4.pb
```

**保存路径**:
```
resources/models/opencv_dnn_superres/FSRCNN_x2.pb
resources/models/opencv_dnn_superres/FSRCNN_x3.pb
resources/models/opencv_dnn_superres/FSRCNN_x4.pb
```

**效果**: ⭐⭐⭐（速度和效果平衡）

---

### 4. LapSRN（高质量）

**下载地址**:
```bash
# LapSRN x2 模型（约 1MB）
https://github.com/fannymonori/TF-LapSRN/raw/master/export/LapSRN_x2.pb

# LapSRN x4 模型（约 1MB）
https://github.com/fannymonori/TF-LapSRN/raw/master/export/LapSRN_x4.pb

# LapSRN x8 模型（约 1MB）
https://github.com/fannymonori/TF-LapSRN/raw/master/export/LapSRN_x8.pb
```

**保存路径**:
```
resources/models/opencv_dnn_superres/LapSRN_x2.pb
resources/models/opencv_dnn_superres/LapSRN_x4.pb
resources/models/opencv_dnn_superres/LapSRN_x8.pb
```

**效果**: ⭐⭐⭐⭐（支持 8 倍放大）

---

## 快速下载脚本（Windows PowerShell）

```powershell
# 创建模型目录
New-Item -ItemType Directory -Force -Path "resources/models/opencv_dnn_superres"

# 下载 EDSR x2（推荐）
Invoke-WebRequest -Uri "https://github.com/Saafke/EDSR_Tensorflow/raw/master/models/EDSR_x2.pb" -OutFile "resources/models/opencv_dnn_superres/EDSR_x2.pb"

# 下载 ESPCN x2（最快）
Invoke-WebRequest -Uri "https://github.com/fannymonori/TF-ESPCN/raw/master/export/ESPCN_x2.pb" -OutFile "resources/models/opencv_dnn_superres/ESPCN_x2.pb"

# 下载 FSRCNN x2（平衡）
Invoke-WebRequest -Uri "https://github.com/Saafke/FSRCNN_Tensorflow/raw/master/models/FSRCNN_x2.pb" -OutFile "resources/models/opencv_dnn_superres/FSRCNN_x2.pb"
```

---

## 使用方法

1. **无需下载，直接使用**：打开 `OpenCV超分对比.json` 工作流
   - 自动使用 Lanczos + 锐化算法
   - 比双三次插值清晰很多
   
2. **下载模型后**：
   - 打开 `OpenCV超分对比.json` 工作流
   - 修改 `OpenCVSuperRes_2` 节点参数：
     - `model_name_`: "EDSR" 或 "ESPCN" 或 "FSRCNN" 或 "LapSRN"
     - `scale_`: 2 或 3 或 4（与模型匹配）
   - 运行，自动加载对应模型

---

## 效果对比

| 方案 | 清晰度 | 速度 | 模型大小 | 需要下载 |
|------|--------|------|----------|----------|
| 双三次插值 | ⭐ | ⭐⭐⭐⭐⭐ | 0 | ❌ |
| Lanczos + 锐化 | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | 0 | ❌ |
| ESPCN | ⭐⭐⭐ | ⭐⭐⭐⭐ | 100KB | ✅ |
| FSRCNN | ⭐⭐⭐ | ⭐⭐⭐⭐ | 40KB | ✅ |
| EDSR | ⭐⭐⭐⭐ | ⭐⭐⭐ | 1.5MB | ✅ |
| LapSRN | ⭐⭐⭐⭐ | ⭐⭐⭐ | 1MB | ✅ |
| GFPGAN（脸部） | ⭐⭐⭐⭐⭐ | ⭐⭐ | 348MB | ✅ |
| Real-ESRGAN | ⭐⭐⭐⭐⭐ | ⭐ | 17MB | ✅ |

---

## 推荐配置

**日常使用（立即可用）**:
- 工作流: `OpenCV超分对比.json`
- 算法: Lanczos + 锐化（无需下载）
- 适合: 快速预览、实时处理

**追求质量（下载小模型）**:
- 下载: EDSR_x2.pb（1.5MB）
- 设置: model_name_="EDSR", scale_=2
- 适合: 高质量超分、细节丰富的场景

**极致性能（下载超小模型）**:
- 下载: FSRCNN_x2.pb（40KB）
- 设置: model_name_="FSRCNN", scale_=2
- 适合: 实时处理、低延迟需求

**脸部增强**:
- 工作流: `脸部超分修复.json`
- 算法: OpenCV 2x + GFPGAN
- 适合: 人脸视频、脸部细节修复
