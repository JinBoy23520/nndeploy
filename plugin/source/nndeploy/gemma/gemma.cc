#include "nndeploy/gemma/gemma.h"

#include <fstream>
#include <sstream>

#include "nndeploy/base/common.h"
#include "nndeploy/base/file.h"
#include "nndeploy/base/glic_stl_include.h"
#include "nndeploy/base/log.h"
#include "nndeploy/base/macro.h"
#include "nndeploy/base/object.h"
#include "nndeploy/base/status.h"
#include "nndeploy/base/string.h"
#include "nndeploy/dag/edge.h"
#include "nndeploy/dag/node.h"
#include "nndeploy/device/buffer.h"
#include "nndeploy/device/device.h"
#include "nndeploy/device/tensor.h"

namespace nndeploy {
namespace gemma {

/**
 * @brief Parse Gemma3 configuration from JSON file
 */
Gemma3Config parseConfig(const std::string& file_path) {
  Gemma3Config config;
  
  std::ifstream ifs(file_path);
  if (!ifs.is_open()) {
    NNDEPLOY_LOGE("Failed to open config file: %s\n", file_path.c_str());
    return config;
  }
  
  std::stringstream buffer;
  buffer << ifs.rdbuf();
  std::string json_str = buffer.str();
  ifs.close();
  
  rapidjson::Document doc;
  doc.Parse(json_str.c_str());
  
  if (doc.HasParseError()) {
    NNDEPLOY_LOGE("Failed to parse JSON config\n");
    return config;
  }
  
  // Parse model architecture parameters
  if (doc.HasMember("vocab_size") && doc["vocab_size"].IsInt()) {
    config.vocab_size_ = doc["vocab_size"].GetInt();
  }
  if (doc.HasMember("hidden_size") && doc["hidden_size"].IsInt()) {
    config.hidden_size_ = doc["hidden_size"].GetInt();
  }
  if (doc.HasMember("intermediate_size") && doc["intermediate_size"].IsInt()) {
    config.intermediate_size_ = doc["intermediate_size"].GetInt();
  }
  if (doc.HasMember("num_hidden_layers") && doc["num_hidden_layers"].IsInt()) {
    config.num_hidden_layers_ = doc["num_hidden_layers"].GetInt();
  }
  if (doc.HasMember("num_attention_heads") && doc["num_attention_heads"].IsInt()) {
    config.num_attention_heads_ = doc["num_attention_heads"].GetInt();
  }
  if (doc.HasMember("num_key_value_heads") && doc["num_key_value_heads"].IsInt()) {
    config.num_key_value_heads_ = doc["num_key_value_heads"].GetInt();
  }
  if (doc.HasMember("head_dim") && doc["head_dim"].IsInt()) {
    config.head_dim_ = doc["head_dim"].GetInt();
  }
  if (doc.HasMember("max_position_embeddings") && doc["max_position_embeddings"].IsInt()) {
    config.max_position_embeddings_ = doc["max_position_embeddings"].GetInt();
  }
  if (doc.HasMember("max_seq_len") && doc["max_seq_len"].IsInt()) {
    config.max_seq_len_ = doc["max_seq_len"].GetInt();
  }
  if (doc.HasMember("rms_norm_eps") && doc["rms_norm_eps"].IsNumber()) {
    config.rms_norm_eps_ = doc["rms_norm_eps"].GetFloat();
  }
  if (doc.HasMember("rope_theta") && doc["rope_theta"].IsNumber()) {
    config.rope_theta_ = doc["rope_theta"].GetFloat();
  }
  
  // Parse file paths
  if (doc.HasMember("model_path") && doc["model_path"].IsString()) {
    config.model_path_ = doc["model_path"].GetString();
  }
  if (doc.HasMember("tokenizer_json") && doc["tokenizer_json"].IsString()) {
    config.tokenizer_json_ = doc["tokenizer_json"].GetString();
  }
  if (doc.HasMember("embedding_file") && doc["embedding_file"].IsString()) {
    config.embedding_file_ = doc["embedding_file"].GetString();
  }
  
  // Parse KV cache init shape
  if (doc.HasMember("kv_init_shape") && doc["kv_init_shape"].IsArray()) {
    const auto& array = doc["kv_init_shape"].GetArray();
    config.kv_init_shape_.clear();
    for (const auto& val : array) {
      if (val.IsInt()) {
        config.kv_init_shape_.push_back(val.GetInt());
      }
    }
  } else {
    // Default KV cache shape: [num_layers, 2, batch=1, seq_len=0, num_kv_heads, head_dim]
    config.kv_init_shape_ = {
        config.num_hidden_layers_,  // 18 layers
        2,                          // key + value
        1,                          // batch size
        0,                          // sequence length (dynamic)
        config.num_key_value_heads_, // 4 KV heads
        config.head_dim_            // 256 head dimension
    };
  }
  
  return config;
}

/**
 * @brief Gemma3PromptParam serialize
 */
base::Status Gemma3PromptParam::serialize(
    rapidjson::Value& json, rapidjson::Document::AllocatorType& allocator) {
  base::Status status = base::kStatusCodeOk;
  
  rapidjson::Value prompt_template_value;
  prompt_template_value.SetString(prompt_template_.c_str(), 
                                  prompt_template_.length(), allocator);
  json.AddMember("prompt_template_", prompt_template_value, allocator);
  
  rapidjson::Value user_content_value;
  user_content_value.SetString(user_content_.c_str(), 
                               user_content_.length(), allocator);
  json.AddMember("user_content_", user_content_value, allocator);
  
  return status;
}

/**
 * @brief Gemma3PromptParam deserialize
 */
base::Status Gemma3PromptParam::deserialize(rapidjson::Value& json) {
  base::Status status = base::kStatusCodeOk;
  
  if (json.HasMember("prompt_template_") && json["prompt_template_"].IsString()) {
    prompt_template_ = json["prompt_template_"].GetString();
  }
  
  if (json.HasMember("user_content_") && json["user_content_"].IsString()) {
    user_content_ = json["user_content_"].GetString();
  }
  
  return status;
}

/**
 * @brief Gemma3EmbeddingParam serialize
 */
base::Status Gemma3EmbeddingParam::serialize(
    rapidjson::Value& json, rapidjson::Document::AllocatorType& allocator) {
  base::Status status = base::kStatusCodeOk;
  
  json.AddMember("hidden_size_", hidden_size_, allocator);
  json.AddMember("num_hidden_layers_", num_hidden_layers_, allocator);
  json.AddMember("all_seq_len_", all_seq_len_, allocator);
  json.AddMember("gen_seq_len_", gen_seq_len_, allocator);
  
  rapidjson::Value embedding_file_value;
  embedding_file_value.SetString(embedding_file_.c_str(), 
                                 embedding_file_.length(), allocator);
  json.AddMember("embedding_file_", embedding_file_value, allocator);
  
  rapidjson::Value kv_init_shape_array(rapidjson::kArrayType);
  for (auto val : kv_init_shape_) {
    kv_init_shape_array.PushBack(val, allocator);
  }
  json.AddMember("kv_init_shape_", kv_init_shape_array, allocator);
  
  return status;
}

/**
 * @brief Gemma3EmbeddingParam deserialize
 */
base::Status Gemma3EmbeddingParam::deserialize(rapidjson::Value& json) {
  base::Status status = base::kStatusCodeOk;
  
  if (json.HasMember("hidden_size_") && json["hidden_size_"].IsInt()) {
    hidden_size_ = json["hidden_size_"].GetInt();
  }
  if (json.HasMember("num_hidden_layers_") && json["num_hidden_layers_"].IsInt()) {
    num_hidden_layers_ = json["num_hidden_layers_"].GetInt();
  }
  if (json.HasMember("all_seq_len_") && json["all_seq_len_"].IsInt()) {
    all_seq_len_ = json["all_seq_len_"].GetInt();
  }
  if (json.HasMember("gen_seq_len_") && json["gen_seq_len_"].IsInt()) {
    gen_seq_len_ = json["gen_seq_len_"].GetInt();
  }
  if (json.HasMember("embedding_file_") && json["embedding_file_"].IsString()) {
    embedding_file_ = json["embedding_file_"].GetString();
  }
  
  if (json.HasMember("kv_init_shape_") && json["kv_init_shape_"].IsArray()) {
    const auto& array = json["kv_init_shape_"].GetArray();
    kv_init_shape_.clear();
    for (const auto& val : array) {
      if (val.IsInt()) {
        kv_init_shape_.push_back(val.GetInt());
      }
    }
  }
  
  return status;
}

/**
 * @brief Gemma3PromptNode constructor
 */
Gemma3PromptNode::Gemma3PromptNode(const std::string& name,
                                   std::vector<dag::Edge*>& inputs,
                                   std::vector<dag::Edge*>& outputs)
    : Node(name, inputs, outputs) {
  param_ = std::make_shared<Gemma3PromptParam>();
}

Gemma3PromptNode::~Gemma3PromptNode() {}

/**
 * @brief Gemma3PromptNode run - minimal stub implementation
 */
base::Status Gemma3PromptNode::run() {
  NNDEPLOY_LOGI("Gemma3PromptNode::run() - stub implementation\n");
  return base::kStatusCodeOk;
}

/**
 * @brief Gemma3EmbeddingNode constructor
 */
Gemma3EmbeddingNode::Gemma3EmbeddingNode(const std::string& name,
                                         std::vector<dag::Edge*>& inputs,
                                         std::vector<dag::Edge*>& outputs)
    : Node(name, inputs, outputs) {
  param_ = std::make_shared<Gemma3EmbeddingParam>();
}

Gemma3EmbeddingNode::~Gemma3EmbeddingNode() {}

/**
 * @brief Gemma3EmbeddingNode init - minimal stub implementation
 */
base::Status Gemma3EmbeddingNode::init() {
  NNDEPLOY_LOGI("Gemma3EmbeddingNode::init() - stub implementation\n");
  return base::kStatusCodeOk;
}

/**
 * @brief Gemma3EmbeddingNode deinit
 */
base::Status Gemma3EmbeddingNode::deinit() {
  if (embedding_weight_) {
    delete embedding_weight_;
    embedding_weight_ = nullptr;
  }
  
  for (auto tensor : past_key_values_) {
    if (tensor) {
      delete tensor;
    }
  }
  past_key_values_.clear();
  
  return base::kStatusCodeOk;
}

/**
 * @brief Gemma3EmbeddingNode run - minimal stub implementation
 */
base::Status Gemma3EmbeddingNode::run() {
  NNDEPLOY_LOGI("Gemma3EmbeddingNode::run() - stub implementation\n");
  return base::kStatusCodeOk;
}

}  // namespace gemma
}  // namespace nndeploy
