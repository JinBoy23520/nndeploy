#include "nndeploy/tokenizer/tokenizer_mnn/tokenizer_mnn.h"
#include "sentencepiece_processor.h"
#include "tokenizers_cpp.h"

namespace nndeploy {
namespace tokenizer {

// @ZhaodeWang:
// 继承TokenizerEncode类，其输入输出必须是TokenizerText和TokenizerIds
TokenizerEncodeMnn::TokenizerEncodeMnn(const std::string& name)
    : TokenizerEncode(name) {
  key_ = "nndeploy::tokenizer::TokenizerEncodeMnn";
  desc_ =
      "A tokenizer encode node that  to "
      "encode text into token IDs. Supports HuggingFace and BPE tokenizers. "
      "Can encode single strings or batches of text. Provides vocabulary "
      "lookup and token-to-ID conversion.";
  param_ = std::make_shared<TokenizerPraram>();
  // this->setInputTypeInfo<TokenizerText>();
  // this->setOutputTypeInfo<TokenizerIds>();
}
TokenizerEncodeMnn::TokenizerEncodeMnn(const std::string& name,
                                       std::vector<dag::Edge*> inputs,
                                       std::vector<dag::Edge*> outputs)
    : TokenizerEncode(name, inputs, outputs) {
  key_ = "nndeploy::tokenizer::TokenizerEncodeMnn";
  desc_ =
      "A tokenizer encode node that  to "
      "encode text into token IDs. Supports HuggingFace and BPE tokenizers. "
      "Can encode single strings or batches of text. Provides vocabulary "
      "lookup and token-to-ID conversion.";
  param_ = std::make_shared<TokenizerPraram>();
  // this->setInputTypeInfo<TokenizerText>();
  // this->setOutputTypeInfo<TokenizerIds>();
}

// TokenizerEncodeMnn 实现
TokenizerEncodeMnn::~TokenizerEncodeMnn() {
  if (sp_processor_) {
    delete static_cast<tokenizers::Tokenizer*>(sp_processor_);
    sp_processor_ = nullptr;
  }
}

base::Status TokenizerEncodeMnn::init() {
  TokenizerPraram* tokenizer_param = (TokenizerPraram*)(param_.get());
  
  try {
    std::string model_path = tokenizer_param->json_blob_;
    
    // Convert relative path to absolute path on Android
    if (model_path.find("resources/") == 0) {
      model_path = "/storage/emulated/0/Android/data/com.nndeploy.app/files/" + model_path;
      NNDEPLOY_LOGI("Converted to absolute path: %s\n", model_path.c_str());
    }
    
    NNDEPLOY_LOGI("Loading tokenizer from: %s\n", model_path.c_str());
    
    // Read file into memory
    FILE* f = fopen(model_path.c_str(), "rb");
    if (!f) {
      NNDEPLOY_LOGE("Cannot open file: %s\n", model_path.c_str());
      return base::kStatusCodeErrorInvalidValue;
    }
    
    fseek(f, 0, SEEK_END);
    long fsize = ftell(f);
    fseek(f, 0, SEEK_SET);
    
    std::string model_blob;
    model_blob.resize(fsize);
    size_t read_bytes = fread(&model_blob[0], 1, fsize, f);
    fclose(f);
    
    if (read_bytes != static_cast<size_t>(fsize)) {
      NNDEPLOY_LOGE("Failed to read complete file: %zu/%ld bytes\n", read_bytes, fsize);
      return base::kStatusCodeErrorInvalidValue;
    }
    
    NNDEPLOY_LOGI("Read %zu bytes, creating tokenizer from blob\n", read_bytes);
    
    // Use tokenizers-cpp instead of direct SentencePiece (avoids C++ ABI conflicts)
    auto tokenizer = tokenizers::Tokenizer::FromBlobSentencePiece(model_blob);
    if (!tokenizer) {
      NNDEPLOY_LOGE("Failed to create tokenizer from blob\n");
      return base::kStatusCodeErrorInvalidValue;
    }
    
    sp_processor_ = tokenizer.release();
    NNDEPLOY_LOGI("Tokenizer loaded successfully, vocab size: %zu\n", 
                  static_cast<tokenizers::Tokenizer*>(sp_processor_)->GetVocabSize());
    return base::kStatusCodeOk;
    
  } catch (const std::exception& e) {
    NNDEPLOY_LOGE("Exception during tokenizer init: %s\n", e.what());
    if (sp_processor_) {
      delete static_cast<tokenizers::Tokenizer*>(sp_processor_);
      sp_processor_ = nullptr;
    }
    return base::kStatusCodeErrorInvalidValue;
  } catch (...) {
    NNDEPLOY_LOGE("Unknown exception during tokenizer init\n");
    if (sp_processor_) {
      delete static_cast<tokenizers::Tokenizer*>(sp_processor_);
      sp_processor_ = nullptr;
    }
    return base::kStatusCodeErrorInvalidValue;
  }
}

base::Status TokenizerEncodeMnn::deinit() {
  if (sp_processor_) {
    delete static_cast<tokenizers::Tokenizer*>(sp_processor_);
    sp_processor_ = nullptr;
  }
  return base::kStatusCodeOk;
}

base::Status TokenizerEncodeMnn::run() {
  NNDEPLOY_LOGE("TokenizerEncodeMnn::run() started\n");
  
  auto* tokenizer = static_cast<tokenizers::Tokenizer*>(sp_processor_);
  if (!tokenizer) {
    NNDEPLOY_LOGE("Tokenizer not initialized\n");
    return base::kStatusCodeErrorNullParam;
  }
  
  // Get input text
  NNDEPLOY_LOGE("Getting input from edge\n");
  TokenizerText* text_param = (TokenizerText*)(inputs_[0]->getParam(this));
  if (!text_param) {
    NNDEPLOY_LOGE("Input text_param is null\n");
    return base::kStatusCodeErrorInvalidValue;
  }
  
  NNDEPLOY_LOGE("Input has %zu texts\n", text_param->texts_.size());
  if (text_param->texts_.size() > 0) {
    NNDEPLOY_LOGE("First text: %s\n", text_param->texts_[0].c_str());
  }
  
  // Encode to IDs
  std::vector<std::vector<int32_t>> ids = encodeBatch(text_param->texts_);
  
  NNDEPLOY_LOGE("Encoded to %zu sequences\n", ids.size());
  if (ids.size() > 0 && ids[0].size() > 0) {
    NNDEPLOY_LOGE("First sequence has %zu tokens, first token: %d\n", 
                  ids[0].size(), ids[0][0]);
  }
  
  // Check output type - if Tensor, convert to tensor format
  auto output_type_info = outputs_[0]->getTypeInfo();
  if (output_type_info && output_type_info->getTypeName() == "nndeploy::device::Tensor") {
    NNDEPLOY_LOGE("Output type is Tensor, converting TokenizerIds to Tensor\n");
    
    // Create tensor with shape [batch, seq_len]
    device::Device *device = device::getDefaultHostDevice();
    if (!device) {
      NNDEPLOY_LOGE("Failed to get default device\n");
      return base::kStatusCodeErrorInvalidParam;
    }
    
    if (ids.empty() || ids[0].empty()) {
      NNDEPLOY_LOGE("Empty token ids\n");
      return base::kStatusCodeErrorInvalidValue;
    }
    
    int32_t batch_size = ids.size();
    int32_t seq_len = ids[0].size();
    
    device::TensorDesc desc(base::dataTypeOf<int32_t>(), base::kDataFormatNC,
                            {batch_size, seq_len});
    device::Tensor *output = outputs_[0]->create(device, desc);
    
    int32_t *data = (int32_t *)output->getData();
    for (int i = 0; i < batch_size; i++) {
      for (int j = 0; j < ids[i].size(); j++) {
        data[i * seq_len + j] = ids[i][j];
      }
    }
    
    outputs_[0]->notifyWritten(output);
    NNDEPLOY_LOGE("Converted to Tensor: [%d, %d]\n", batch_size, seq_len);
  } else {
    // Output as TokenizerIds
    TokenizerIds* ids_param = new TokenizerIds();
    ids_param->ids_ = ids;
    outputs_[0]->set(ids_param, false);
    NNDEPLOY_LOGE("Output as TokenizerIds\n");
  }
  
  NNDEPLOY_LOGE("TokenizerEncodeMnn::run() completed\n");
  return base::kStatusCodeOk;
}

std::vector<int32_t> TokenizerEncodeMnn::encode(const std::string& text) {
  auto* tokenizer = static_cast<tokenizers::Tokenizer*>(sp_processor_);
  if (!tokenizer) {
    NNDEPLOY_LOGE("Tokenizer not initialized\n");
    return std::vector<int32_t>();
  }
  
  return tokenizer->Encode(text);
}

std::vector<std::vector<int32_t>> TokenizerEncodeMnn::encodeBatch(
    const std::vector<std::string>& texts) {
  auto* tokenizer = static_cast<tokenizers::Tokenizer*>(sp_processor_);
  if (!tokenizer) {
    return std::vector<std::vector<int32_t>>();
  }
  
  return tokenizer->EncodeBatch(texts);
}

size_t TokenizerEncodeMnn::getVocabSize() {
  // TODO: 返回词汇表大小
  return 0;
}

int32_t TokenizerEncodeMnn::tokenToId(const std::string& token) {
  // TODO: 将token转换为ID
  return -1;
}

base::Status TokenizerEncodeMnn::serialize(
    rapidjson::Value& json, rapidjson::Document::AllocatorType& allocator) {
  base::Status status = dag::Node::serialize(json, allocator);
  if (status != base::kStatusCodeOk) {
    NNDEPLOY_LOGE("TokenizerDecode::serialize failed\n");
    return status;
  }
  return base::kStatusCodeOk;
}

base::Status TokenizerEncodeMnn::deserialize(rapidjson::Value& json) {
  base::Status status = dag::Node::deserialize(json);
  if (status != base::kStatusCodeOk) {
    NNDEPLOY_LOGE("TokenizerDecode::deserialize failed\n");
    return status;
  }
  return base::kStatusCodeOk;
}

// TokenizerDecodeMnn 实现

TokenizerDecodeMnn::TokenizerDecodeMnn(const std::string& name)
    : TokenizerDecode(name) {
  key_ = "nndeploy::tokenizer::TokenizerDecodeMnn";
  desc_ =
      "A tokenizer decode node that  to "
      "decode token IDs into text. Supports HuggingFace and BPE "
      "tokenizers. "
      "Can decode single token IDs or batches of token IDs. Provides "
      "token-to-"
      "text conversion.";
  param_ = std::make_shared<TokenizerPraram>();
  // this->setInputTypeInfo<TokenizerIds>();
  // this->setOutputTypeInfo<TokenizerText>();
}
TokenizerDecodeMnn::TokenizerDecodeMnn(const std::string& name,
                                       std::vector<dag::Edge*> inputs,
                                       std::vector<dag::Edge*> outputs)
    : TokenizerDecode(name, inputs, outputs) {
  key_ = "nndeploy::tokenizer::TokenizerDecodeMnn";
  desc_ =
      "A tokenizer decode node that  to "
      "decode token IDs into text. Supports HuggingFace and BPE "
      "tokenizers. "
      "Can decode single token IDs or batches of token IDs. Provides "
      "token-to-"
      "text conversion.";
  param_ = std::make_shared<TokenizerPraram>();
  // this->setInputTypeInfo<TokenizerIds>();
  // this->setOutputTypeInfo<TokenizerText>();
}
TokenizerDecodeMnn::~TokenizerDecodeMnn() {
  if (sp_processor_) {
    delete static_cast<tokenizers::Tokenizer*>(sp_processor_);
    sp_processor_ = nullptr;
  }
}

base::Status TokenizerDecodeMnn::init() {
  TokenizerPraram* tokenizer_param = (TokenizerPraram*)(param_.get());
  
  try {
    std::string model_path = tokenizer_param->json_blob_;
    if (model_path.find("resources/") == 0) {
      model_path = "/storage/emulated/0/Android/data/com.nndeploy.app/files/" + model_path;
    }
    
    FILE* f = fopen(model_path.c_str(), "rb");
    if (!f) {
      NNDEPLOY_LOGE("Cannot open file for decode: %s\n", model_path.c_str());
      return base::kStatusCodeErrorInvalidValue;
    }
    
    fseek(f, 0, SEEK_END);
    long fsize = ftell(f);
    fseek(f, 0, SEEK_SET);
    
    std::string model_blob;
    model_blob.resize(fsize);
    fread(&model_blob[0], 1, fsize, f);
    fclose(f);
    
    auto tokenizer = tokenizers::Tokenizer::FromBlobSentencePiece(model_blob);
    if (!tokenizer) {
      NNDEPLOY_LOGE("Failed to create decode tokenizer\n");
      return base::kStatusCodeErrorInvalidValue;
    }
    
    sp_processor_ = tokenizer.release();
    return base::kStatusCodeOk;
  } catch (...) {
    if (sp_processor_) {
      delete static_cast<tokenizers::Tokenizer*>(sp_processor_);
      sp_processor_ = nullptr;
    }
    return base::kStatusCodeErrorInvalidValue;
  }
}

base::Status TokenizerDecodeMnn::deinit() {
  if (sp_processor_) {
    delete static_cast<tokenizers::Tokenizer*>(sp_processor_);
    sp_processor_ = nullptr;
  }
  return base::kStatusCodeOk;
}

base::Status TokenizerDecodeMnn::run() {
  auto* tokenizer = static_cast<tokenizers::Tokenizer*>(sp_processor_);
  if (!tokenizer) {
    NNDEPLOY_LOGE("Tokenizer not initialized\n");
    return base::kStatusCodeErrorNullParam;
  }
  
  // Get input IDs
  TokenizerIds* ids_param = (TokenizerIds*)(inputs_[0]->getParam(this));
  
  // Decode to text
  TokenizerText* text_param = new TokenizerText();
  text_param->texts_ = decodeBatch(ids_param->ids_);
  
  // Set output
  outputs_[0]->set(text_param, false);
  
  return base::kStatusCodeOk;
}

std::string TokenizerDecodeMnn::decode(const std::vector<int32_t>& ids) {
  auto* tokenizer = static_cast<tokenizers::Tokenizer*>(sp_processor_);
  if (!tokenizer) {
    NNDEPLOY_LOGE("Tokenizer not initialized\n");
    return std::string();
  }
  
  return tokenizer->Decode(ids);
}

std::vector<std::string> TokenizerDecodeMnn::decodeBatch(
    const std::vector<std::vector<int32_t>>& ids) {
  std::vector<std::string> result;
  result.reserve(ids.size());
  for (const auto& id_seq : ids) {
    result.push_back(decode(id_seq));
  }
  return result;
}

size_t TokenizerDecodeMnn::getVocabSize() {
  // TODO: 返回词汇表大小
  return 0;
}

std::string TokenizerDecodeMnn::idToToken(int32_t token_id) {
  // TODO: 将ID转换为token
  return std::string();
}

base::Status TokenizerDecodeMnn::serialize(
    rapidjson::Value& json, rapidjson::Document::AllocatorType& allocator) {
  base::Status status = dag::Node::serialize(json, allocator);
  if (status != base::kStatusCodeOk) {
    NNDEPLOY_LOGE("TokenizerDecode::serialize failed\n");
    return status;
  }
  return base::kStatusCodeOk;
}
base::Status TokenizerDecodeMnn::deserialize(rapidjson::Value& json) {
  base::Status status = dag::Node::deserialize(json);
  if (status != base::kStatusCodeOk) {
    NNDEPLOY_LOGE("TokenizerDecode::deserialize failed\n");
    return status;
  }
  return base::kStatusCodeOk;
}

REGISTER_NODE("nndeploy::tokenizer::TokenizerEncodeMnn", TokenizerEncodeMnn);
REGISTER_NODE("nndeploy::tokenizer::TokenizerDecodeMnn", TokenizerDecodeMnn);

}  // namespace tokenizer
}  // namespace nndeploy
