try:
    from nndeploy.super_resolution.realesrgan import RealESRGAN
except ImportError as e:
    import warnings
    warnings.warn(f"无法导入 RealESRGAN: {e}. 请安装依赖: pip install realesrgan basicsr")
except Exception as e:
    import warnings
    warnings.warn(f"RealESRGAN 导入失败: {e}")

try:
    from nndeploy.super_resolution.srresnet import SRResNet
except ImportError as e:
    import warnings
    warnings.warn(f"无法导入 SRResNet: {e}. 请安装依赖: pip install torch torchvision")
except Exception as e:
    import warnings
    warnings.warn(f"SRResNet 导入失败: {e}")

try:
    from nndeploy.super_resolution.opencv_superres import OpenCVSuperRes
except ImportError as e:
    import warnings
    warnings.warn(f"无法导入 OpenCVSuperRes: {e}")
except Exception as e:
    import warnings
    warnings.warn(f"OpenCVSuperRes 导入失败: {e}")
