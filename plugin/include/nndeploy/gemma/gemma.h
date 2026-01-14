#ifndef _NNDEPLOY_GEMMA_GEMMA_H_
#define _NNDEPLOY_GEMMA_GEMMA_H_

#include "nndeploy/base/common.h"
#include "nndeploy/base/glic_stl_include.h"
#include "nndeploy/base/log.h"
#include "nndeploy/base/macro.h"
#include "nndeploy/base/param.h"
#include "nndeploy/base/status.h"
#include "nndeploy/dag/node.h"
#include "nndeploy/device/tensor.h"

namespace nndeploy {
namespace gemma {

/**
 * @brief Gemma3 Model Configuration
 * 
 * Gemma3-270M Architecture:
 * - vocab_size: 256000
 * - hidden_size: 2048
 * - intermediate_size: 16384
 * - num_hidden_layers: 18
 * - num_attention_heads: 8
 * - num_key_value_heads: 4
 * - head_dim: 256
 * - max_position_embeddings: 8192
 */
struct NNDEPLOY_CC_API Gemma3Config {
  int vocab_size_ = 256000;
  int hidden_size_ = 2048;
  int intermediate_size_ = 16384;
  int num_hidden_layers_ = 18;
  int num_attention_heads_ = 8;
  int num_key_value_heads_ = 4;
  int head_dim_ = 256;
  int max_position_embeddings_ = 8192;
  int max_seq_len_ = 2048;
  float rms_norm_eps_ = 1e-6f;
  float rope_theta_ = 10000.0f;
  
  std::string model_path_;
  std::string tokenizer_json_;
  std::string embedding_file_;
  
  std::vector<int32_t> kv_init_shape_;  // [num_layers, 2, batch, seq_len, num_kv_heads, head_dim]
};

/**
 * @brief Parse Gemma3 configuration from JSON file
 */
extern NNDEPLOY_CC_API Gemma3Config parseConfig(const std::string& file_path);

/**
 * @brief Gemma3 Prompt Parameter
 * 
 * Supports Gemma3 prompt template:
 * <start_of_turn>user
 * {user_content}<end_of_turn>
 * <start_of_turn>model
 */
class NNDEPLOY_CC_API Gemma3PromptParam : public base::Param {
 public:
  std::string prompt_template_ = 
      "<start_of_turn>user\n%s<end_of_turn>\n<start_of_turn>model\n";
  std::string user_content_;
  
 public:
  base::Status serialize(rapidjson::Value& json,
                         rapidjson::Document::AllocatorType& allocator) override;
  base::Status deserialize(rapidjson::Value& json) override;
};

/**
 * @brief Gemma3 Embedding Parameter for Prefill
 */
class NNDEPLOY_CC_API Gemma3EmbeddingParam : public base::Param {
 public:
  int hidden_size_ = 2048;
  int num_hidden_layers_ = 18;
  int all_seq_len_ = 0;
  int gen_seq_len_ = 0;
  std::string embedding_file_;
  
  std::vector<int32_t> kv_init_shape_;
  base::DataType data_type_ = base::dataTypeOf<float>();
  base::DataFormat data_format_ = base::DataFormat::kDataFormatS1D;
  
 public:
  base::Status serialize(rapidjson::Value& json,
                         rapidjson::Document::AllocatorType& allocator) override;
  base::Status deserialize(rapidjson::Value& json) override;
};

/**
 * @brief Gemma3 Prompt Node
 * 
 * Convert user input to Gemma3 prompt format
 */
class NNDEPLOY_CC_API Gemma3PromptNode : public dag::Node {
 public:
  Gemma3PromptNode(const std::string& name, std::vector<dag::Edge*>& inputs,
                   std::vector<dag::Edge*>& outputs);
  virtual ~Gemma3PromptNode() override;
  
  virtual base::Status run() override;
};

/**
 * @brief Gemma3 Embedding Node
 * 
 * Generate input embeddings for Gemma3 model
 */
class NNDEPLOY_CC_API Gemma3EmbeddingNode : public dag::Node {
 public:
  Gemma3EmbeddingNode(const std::string& name, std::vector<dag::Edge*>& inputs,
                      std::vector<dag::Edge*>& outputs);
  virtual ~Gemma3EmbeddingNode() override;
  
  virtual base::Status init() override;
  virtual base::Status deinit() override;
  virtual base::Status run() override;
  
 private:
  bool is_first_ = true;
  device::Tensor* embedding_weight_ = nullptr;
  std::vector<device::Tensor*> past_key_values_;
};

}  // namespace gemma
}  // namespace nndeploy

#endif  // _NNDEPLOY_GEMMA_GEMMA_H_
