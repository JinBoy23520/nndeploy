import cv2
import numpy as np
from nndeploy import gan

# 初始化 GFPGAN 模型
gfpgan = gan.GFPGAN()
gfpgan.setModelPath("resources/models/face_swap/GFPGANv1.4.pth")
gfpgan.init()

# 打开摄像头
cap = cv2.VideoCapture(0)
if not cap.isOpened():
    print("无法打开摄像头")
    exit()

while True:
    ret, frame = cap.read()
    if not ret:
        break

    # 转换为 ndarray (假设模型需要)
    input_data = np.array(frame)

    # 运行超分
    output_data = gfpgan.run(input_data)

    # 显示结果
    cv2.imshow('Real-time Super Resolution', output_data)

    # 按 'q' 退出
    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

# 释放资源
cap.release()
cv2.destroyAllWindows()
gfpgan.deinit()