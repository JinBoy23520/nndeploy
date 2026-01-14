#include "nndeploy/inference/onnxruntime/onnxruntime_inference.h"

#include "nndeploy/base/shape.h"
#include "nndeploy/inference/onnxruntime/onnxruntime_convert.h"
#include "nndeploy/inference/onnxruntime/onnxruntime_inference_param.h"

namespace nndeploy {
namespace inference {

TypeInferenceRegister<TypeInferenceCreator<OnnxRuntimeInference>>
    g_onnxruntime_inference_register(base::kInferenceTypeOnnxRuntime);

// OnnxRuntimeInference::OnnxRuntimeInference() : Inference() {}

OnnxRuntimeInference::OnnxRuntimeInference(base::InferenceType type)
    : Inference(type) {}

OnnxRuntimeInference::~OnnxRuntimeInference() {
  // NNDEPLOY_LOGI("OnnxRuntimeInference::~OnnxRuntimeInference()\n");
}

base::Status OnnxRuntimeInference::init() {
  base::Status status = base::kStatusCodeOk;

  is_share_context_ = true;
  if (!is_external_stream_ && stream_ == nullptr) {
    stream_ = device::createStream(inference_param_->device_type_);
  }

  OnnxRuntimeInferenceParam *onnxruntime_inference_param =
      dynamic_cast<OnnxRuntimeInferenceParam *>(inference_param_.get());
  std::string buffer;
  bool use_path_loading = false;
  std::string model_path;
  
  if (onnxruntime_inference_param->is_path_) {
    if (onnxruntime_inference_param->model_value_.size() > 0) {
      model_path = onnxruntime_inference_param->model_value_[0];
      NNDEPLOY_LOGE("Loading ONNX model from path: %s\n", model_path.c_str());
      
      // 如果有外部数据文件，使用路径加载（避免将 GB 级文件读入内存）
      if (onnxruntime_inference_param->external_model_data_.size() > 0) {
        use_path_loading = true;
        NNDEPLOY_LOGE("External data detected, using path-based loading\n");
        
        // 设置外部数据目录（ONNX Runtime 会自动加载同目录下的外部数据文件）
        size_t pos = model_path.find_last_of("/\\");
        if (pos != std::string::npos) {
          std::string model_dir = model_path.substr(0, pos);
#ifdef _WIN32
          std::wstring model_dir_w(model_dir.begin(), model_dir.end());
          session_options_.AddConfigEntry("session.load_model_format", "ORT");
          session_options_.AddConfigEntry("session.use_ort_model_bytes_directly", "1");
#else
          // Android/Linux: 确保外部数据路径可访问
          NNDEPLOY_LOGE("Model directory: %s\n", model_dir.c_str());
#endif
        }
      } else {
        // 没有外部数据，正常加载到内存
        buffer = base::openFile(model_path);
        NNDEPLOY_LOGE("Model buffer size: %zu bytes\n", buffer.size());
      }
    } else {
      NNDEPLOY_LOGE("model_value_ is empty!\n");
      return base::kStatusCodeErrorInvalidValue;
    }
  } else {
    buffer = onnxruntime_inference_param->model_value_[0];
  }

  OnnxRuntimeConvert::convertFromInferenceParam(*onnxruntime_inference_param,
                                                session_options_, stream_);
  try {
    if (use_path_loading) {
      // 直接从文件路径加载（ONNX Runtime 自动处理外部数据）
#ifdef _WIN32
      std::wstring model_path_w(model_path.begin(), model_path.end());
      session_ = {env_, model_path_w.c_str(), session_options_};
#else
      session_ = {env_, model_path.c_str(), session_options_};
#endif
      NNDEPLOY_LOGE("Session created from path successfully\n");
    } else {
      // 从内存缓冲区加载
      session_ = {env_, buffer.data(), buffer.size(), session_options_};
      NNDEPLOY_LOGE("Session created from buffer successfully\n");
    }
  } catch (const std::exception &e) {
    NNDEPLOY_LOGE("OnnxRuntime session creation failed: %s\n", e.what());
    return base::kStatusCodeErrorInferenceOnnxRuntime;
  }

  binding_ = std::make_shared<Ort::IoBinding>(session_);
  auto memory_info =
      Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
  Ort::Allocator allocator(session_, memory_info);
  device::Device *device = device::getDefaultHostDevice();

  auto n_inputs = session_.GetInputCount();
  for (int i = 0; i < n_inputs; ++i) {
    auto input_name = session_.GetInputNameAllocated(i, allocator).get();

    auto type_info = session_.GetInputTypeInfo(i);
    std::vector<int64_t> shape =
        type_info.GetTensorTypeAndShapeInfo().GetShape();
    ONNXTensorElementDataType data_type =
        type_info.GetTensorTypeAndShapeInfo().GetElementType();
    inputs_desc_.emplace_back(OrtValueInfo{input_name, shape, data_type});

    if (!isDynamic(shape)) {
      device::TensorDesc desc;
      if (onnxruntime_inference_param->max_shape_.find(input_name) !=
          onnxruntime_inference_param->max_shape_.end()) {
        desc.shape_ = OnnxRuntimeConvert::convertToShape(
            shape, onnxruntime_inference_param->max_shape_[input_name]);
      } else {
        desc.shape_ = OnnxRuntimeConvert::convertToShape(shape);
      }
      desc.data_type_ = OnnxRuntimeConvert::convertToDataType(data_type);
      desc.data_format_ = OnnxRuntimeConvert::getDataFormatByShape(desc.shape_);
      device::Tensor *max_input_tensor =
          new device::Tensor(device, desc, input_name);
      max_input_tensors_.insert({input_name, max_input_tensor});

      device::Buffer *max_input_buffer = max_input_tensor->getBuffer();
      device::Tensor *current_input_tensor =
          new device::Tensor(desc, max_input_buffer, input_name);
      input_tensors_.insert({input_name, current_input_tensor});
    } else {
      device::Tensor *max_input_tensor = new device::Tensor(input_name);
      max_input_tensors_.insert({input_name, max_input_tensor});

      device::Tensor *current_input_tensor = new device::Tensor(input_name);
      input_tensors_.insert({input_name, current_input_tensor});
    }
  }

  for (auto iter : input_tensors_) {
    batch_size_ = iter.second->getBatch();
  }

  auto n_outputs = session_.GetOutputCount();
  for (int i = 0; i < n_outputs; ++i) {
    auto output_name = session_.GetOutputNameAllocated(i, allocator).get();

    auto type_info = session_.GetOutputTypeInfo(i);
    std::vector<int64_t> shape =
        type_info.GetTensorTypeAndShapeInfo().GetShape();
    ONNXTensorElementDataType data_type =
        type_info.GetTensorTypeAndShapeInfo().GetElementType();
    outputs_desc_.emplace_back(OrtValueInfo{output_name, shape, data_type});

    if (!isDynamic(shape) && batch_size_ != -1) {
      device::TensorDesc desc;
      desc.shape_ = OnnxRuntimeConvert::convertToShape(shape);
      desc.shape_[0] = batch_size_;
      desc.data_type_ = OnnxRuntimeConvert::convertToDataType(data_type);
      desc.data_format_ = OnnxRuntimeConvert::getDataFormatByShape(desc.shape_);
      device::Tensor *max_output_tensor =
          new device::Tensor(device, desc, output_name);
      max_output_tensors_.insert({output_name, max_output_tensor});

      device::Buffer *max_output_buffer = max_output_tensor->getBuffer();
      device::Tensor *current_output_tensor =
          new device::Tensor(desc, max_output_buffer, output_name);
      output_tensors_.insert({output_name, current_output_tensor});
    } else {
      device::Tensor *max_output_tensor = new device::Tensor(output_name);
      max_output_tensors_.insert({output_name, max_output_tensor});

      device::Tensor *current_output_tensor = new device::Tensor(output_name);
      output_tensors_.insert({output_name, current_output_tensor});
    }
  }

  return status;
}

base::Status OnnxRuntimeInference::deinit() {
  base::Status status = base::kStatusCodeOk;
  // for (int i = 0; i < internal_outputs_.size(); ++i) {
  //   internal_outputs_[i].release();
  // }
  // internal_outputs_.clear();
  // for (int i = 0; i < internal_inputs_.size(); ++i) {
  //   internal_inputs_[i].release();
  // }
  // internal_inputs_.clear();
  for (auto iter : input_tensors_) {
    delete iter.second;
  }
  input_tensors_.clear();
  for (auto iter : max_input_tensors_) {
    delete iter.second;
  }
  max_input_tensors_.clear();
  for (auto iter : output_tensors_) {
    delete iter.second;
  }
  output_tensors_.clear();
  for (auto iter : max_output_tensors_) {
    delete iter.second;
  }
  max_output_tensors_.clear();

  // 4. 清理 IoBinding (新增)
  // binding_.release();
  binding_.reset();
  allocator_.release();
  memory_info_.release();

  // 5. 清理描述符向量
  inputs_desc_.clear();
  outputs_desc_.clear();

  // session_options_.release();
  // session_.release();
  // 只能这样写，写其他任何代码都会导致内存泄漏
  session_ = Ort::Session{nullptr};
  // env_.release();
  // env_ = Ort::Env{nullptr};

  // NNDEPLOY_LOGE("OnnxRuntimeInference::deinit end\n");

  return status;
}

base::Status OnnxRuntimeInference::reshape(base::ShapeMap &shape_map) {
  auto memory_info =
      Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
  Ort::Allocator allocator(session_, memory_info);
  device::Device *device = device::getDefaultHostDevice();

  base::ShapeMap current_shape;
  auto n_inputs = session_.GetInputCount();
  for (int i = 0; i < n_inputs; ++i) {
    auto input_name = session_.GetInputNameAllocated(i, allocator).get();
    auto type_info = session_.GetInputTypeInfo(i);
    auto src_shape = type_info.GetTensorTypeAndShapeInfo().GetShape();
    auto shape = OnnxRuntimeConvert::convertToShape(src_shape);
    current_shape.insert({input_name, shape});
  }
  for (auto iter : shape_map) {
    auto tmp = current_shape.find(iter.first);
    if (tmp != current_shape.end()) {
      auto &shape = current_shape[iter.first];
      if (base::shapeEqual(iter.second, shape)) {
        continue;
      } else {
        device::TensorDesc desc = input_tensors_[iter.first]->getDesc();
        desc.shape_ = iter.second;
        input_tensors_[iter.first]->justModify(desc);
      }
    } else {
      NNDEPLOY_LOGE("reshape failed, not found input tensor(%s)!\n",
                    iter.first.c_str());
      return base::kStatusCodeErrorInferenceOnnxRuntime;
    }
  }

  return base::kStatusCodeOk;
}

base::Status OnnxRuntimeInference::run() {
  base::Status status = base::kStatusCodeOk;
  // auto memory_info =
  //     Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
  Ort::MemoryInfo memory_info("Cpu", OrtDeviceAllocator, 0, OrtMemTypeDefault);
  Ort::Allocator allocator(session_, memory_info);
  device::Device *device = device::getDefaultHostDevice();
  try {
    auto n_inputs = session_.GetInputCount();

    for (int i = 0; i < n_inputs; ++i) {
      auto input_name = inputs_desc_[i].name.c_str();
      device::Tensor *input_tensor = nullptr;
      if (external_input_tensors_.find(input_name) !=
          external_input_tensors_.end()) {
        input_tensor = external_input_tensors_[input_name];
      } else {
        input_tensor = input_tensors_[input_name];
      }
      auto ort_value = OnnxRuntimeConvert::convertFromTensor(input_tensor);
      binding_->BindInput(input_name, ort_value);
    }

    for (size_t i = 0; i < outputs_desc_.size(); ++i) {
      binding_->BindOutput(outputs_desc_[i].name.c_str(), memory_info);
    }

    session_.Run({}, *binding_);

    std::vector<Ort::Value> output_values = binding_->GetOutputValues();
    for (size_t i = 0; i < output_values.size(); ++i) {
      auto output_name = outputs_desc_[i].name;
      device::Tensor *output_tensor = output_tensors_[output_name];
      OnnxRuntimeConvert::convertToTensor(output_values[i], output_name, device,
                                          output_tensor);
    }
  } catch (const std::exception &e) {
    NNDEPLOY_LOGE("%s.\n", e.what());
    status = base::kStatusCodeErrorInferenceOnnxRuntime;
  }
  return status;
}

device::Tensor *OnnxRuntimeInference::getOutputTensorAfterRun(
    const std::string &name, base::DeviceType device_type, bool is_copy,
    base::DataFormat data_format) {
  device::Tensor *external_output_tensor = nullptr;
  bool flag = is_copy || (!device::isHostDeviceType(device_type));
  device::Device *device = device::getDevice(device_type);
  if (output_tensors_.find(name) == output_tensors_.end()) {
    NNDEPLOY_LOGE("output_tensors_ not found name: %s\n", name.c_str());
    return nullptr;
  }
  device::Tensor *output_tensor = output_tensors_[name];
  device::TensorDesc desc = output_tensor->getDesc();
  if (flag) {
    external_output_tensor = new device::Tensor(device, desc, name);
    device->copy(output_tensor->getBuffer(),
                 external_output_tensor->getBuffer());
    // external_output_tensor->print();
    return external_output_tensor;
  } else {
    external_output_tensor =
        new device::Tensor(desc, output_tensor->getBuffer(), name);
    // external_output_tensor->print();
    return external_output_tensor;
  }

  // for (size_t i = 0; i < internal_outputs_.size(); ++i) {
  //   auto output_name = outputs_desc_[i].name;
  //   if (output_name != name) {
  //     continue;
  //   }
  //   device::Tensor *output_tensor = output_tensors_[output_name];
  //   OnnxRuntimeConvert::convertToTensor(internal_outputs_[i], output_name,
  //                                       device, output_tensor);
  //   device::TensorDesc desc = output_tensor->getDesc();
  //   if (flag) {
  //     external_output_tensor = new device::Tensor(device, desc, name);
  //     device->copy(output_tensor->getBuffer(),
  //                  external_output_tensor->getBuffer());
  //     external_output_tensor->print();
  //     return external_output_tensor;
  //   } else {
  //     external_output_tensor =
  //         new device::Tensor(desc, output_tensor->getBuffer(), name);
  //     external_output_tensor->print();
  //     return external_output_tensor;
  //   }
  // }
  // device::Device *device = device::getDevice(device_type);
  // device::TensorDesc desc;
  // desc.shape_ = {1, 1000};
  // desc.data_type_ = base::dataTypeOf<float>();
  // desc.data_format_ = base::kDataFormatNC;
  // external_output_tensor = new device::Tensor(device, desc, name);
  // external_output_tensor->print();
  // return external_output_tensor;
}

bool OnnxRuntimeInference::isDynamic(std::vector<int64_t> &shape) {
  int size = static_cast<int>(shape.size());
  for (int i = 1; i < size; ++i) {
    if (shape[i] < 0) {
      return true;
    }
  }
  return false;
}

}  // namespace inference
}  // namespace nndeploy