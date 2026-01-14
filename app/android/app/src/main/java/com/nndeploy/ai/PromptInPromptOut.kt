package com.nndeploy.ai

import android.content.Context
import android.util.Log
import com.nndeploy.dag.GraphRunner
import com.nndeploy.base.FileUtils
import com.nndeploy.base.ModelPathManager
import java.io.File

/**
 * AI Algorithm Processor - Supports prompt input and text output
 */
object PromptInPromptOut {
    
    // Store conversation history Map, key is session ID, value is conversation history
    private val conversationHistory = mutableMapOf<String, MutableList<ConversationMessage>>()
    
    /**
     * Conversation message data class
     */
    data class ConversationMessage(
        val role: String, // "user" or "assistant"
        val content: String,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Processing result wrapper
     */
    sealed class PromptProcessResult {
        data class Success(val response: String, val conversationId: String) : PromptProcessResult()
        data class Error(val message: String) : PromptProcessResult()
    }
    
    /**
     * Intelligent conversation processing - Supports multi-turn dialogue
     */
    suspend fun processPromptInPromptOut(
        context: Context, 
        prompt: String, 
        alg: AIAlgorithm,
        conversationId: String = "default",
        onModelCopyProgress: ((String, Int, Int) -> Unit)? = null
    ): PromptProcessResult {
        return try {
            Log.w("PromptInPromptOut", "==========================================")
            Log.w("PromptInPromptOut", "Algorithm ID: ${alg.id}")
            Log.w("PromptInPromptOut", "Algorithm Name: ${alg.name}")
            Log.w("PromptInPromptOut", "==========================================")
            
            // ⚠️ Block gemma3_demo - it will crash with current Qwen adapter
            if (alg.id == "gemma3_demo") {
                Log.e("PromptInPromptOut", "🚫 BLOCKED: gemma3_demo is disabled!")
                return PromptProcessResult.Error(
                    "⚠️ Gemma3 Chat (Full) is currently disabled.\n\n" +
                    "Reason: gemma3demo.json uses 'model_key: Qwen' which is incompatible with Gemma3 architecture.\n" +
                    "This causes pthread mutex errors and crashes.\n\n" +
                    "✅ Please use 'Gemma3 Chat (Optimized)' instead.\n\n" +
                    "Technical details:\n" +
                    "- Qwen: 28 layers, 1 KV head\n" +
                    "- Gemma3: 18 layers, 4 KV heads\n" +
                    "→ Architecture mismatch → crash"
                )
            }
            
            Log.i("PromptInPromptOut", "✅ Algorithm check passed, continuing...")
            
            // 0) For external models (like gemma3), ensure model files are ready
            val isGemma3 = alg.id == "gemma3_demo" || alg.id == "gemma3_simple"
            if (isGemma3) {
                val modelDir = ModelPathManager.getModelPath(context, "gemma3")
                
                // Check if model directory exists - models should be copied manually via UI button
                if (!modelDir.exists() || modelDir.listFiles()?.isEmpty() != false) {
                    return PromptProcessResult.Error(
                        "Gemma3 models not found.\n\n" +
                        "Please click the 📁 button in the top bar to copy models from the source directory.\n\n" +
                        "Source: /sdcard/nndeploy_models/gemma3_source/\n" +
                        "Target: ${modelDir.absolutePath}"
                    )
                }
                
                // Verify required model files exist (tokenizer.model is optional for simplified version)
                val requiredFiles = if (alg.id == "gemma3_simple") {
                    listOf("model.onnx", "tokenizer.json")
                } else {
                    listOf("model.onnx", "model.onnx_data", "tokenizer.json", "tokenizer.model")
                }
                
                val missingFiles = requiredFiles.filter { !File(modelDir, it).exists() }
                
                if (missingFiles.isNotEmpty()) {
                    return PromptProcessResult.Error(
                        "Missing model files: ${missingFiles.joinToString(", ")}\n" +
                        "Please click the 📁 button to copy models from source.\n" +
                        "Path: ${modelDir.absolutePath}"
                    )
                }
                
                Log.i("PromptInPromptOut", "Gemma3 model files verified at: ${modelDir.absolutePath}")
            }
            
            // 1) Ensure external resources are ready
            val extResDir = FileUtils.ensureExternalResourcesReady(context)
            val extRoot = FileUtils.getExternalRoot(context)
            val extWorkflowDir = File(extResDir, "workflow").apply { mkdirs() }
            
            // Print three variables
            Log.d("PromptInPromptOut", "extResDir: ${extResDir.absolutePath}")
            Log.d("PromptInPromptOut", "extRoot: ${extRoot.absolutePath}")
            Log.d("PromptInPromptOut", "extWorkflowDir: ${extWorkflowDir.absolutePath}")

            val workflowAsset = alg.workflowAsset
            
            // 2) Get or create conversation history
            val history = conversationHistory.getOrPut(conversationId) { mutableListOf() }
            
            // 3) Build complete prompt including history
            // val fullPrompt = buildFullPrompt(prompt, history)
            // Log.d("PromptInPromptOut", "Full prompt with history: $fullPrompt")
            
            // 4) Read workflow from assets and replace paths
            val rawJson = context.assets.open(workflowAsset).bufferedReader().use { it.readText() }
            
            // 4.1) FIRST: Replace model paths with external model directory paths (for large models)
            // This must happen BEFORE generic resources/ replacement to avoid double-prefixing
            var resolvedJson = rawJson
            if (isGemma3) {
                val modelPathMapping = ModelPathManager.buildGemma3PathMapping(context)
                for ((assetPath, externalPath) in modelPathMapping) {
                    resolvedJson = resolvedJson.replace(assetPath, externalPath.replace("\\", "/"))
                    Log.d("PromptInPromptOut", "Mapped: $assetPath -> $externalPath")
                }
                Log.d("PromptInPromptOut", "Resolved JSON paths for ${alg.id}")
            }
            
            // 4.2) SECOND: Replace remaining resources/ paths with external storage paths
            resolvedJson = resolvedJson.replace("resources/", "${extResDir.absolutePath}/".replace("\\", "/"))
            
            // DEBUG: Log the actual model_value_ and external_model_data_ after all replacements
            val modelValuePattern = """"model_value_"\s*:\s*\[([^\]]+)]""".toRegex()
            modelValuePattern.find(resolvedJson)?.let { match ->
                Log.e("PromptInPromptOut", "Final model_value_ in JSON: ${match.value}")
            }
            
            val externalDataPattern = """"external_model_data_"\s*:\s*\[([^\]]+)]""".toRegex()
            externalDataPattern.find(resolvedJson)?.let { match ->
                Log.e("PromptInPromptOut", "Final external_model_data_ in JSON: ${match.value}")
            } ?: Log.e("PromptInPromptOut", "WARNING: No external_model_data_ found in JSON!")
            
            // 5) Write to external private directory, get real file path
            val workflowOut = File(extWorkflowDir, alg.id + "_resolved.json").apply {
                writeText(resolvedJson)
            }

            // 6) Run underlying system with file path
            val runner = GraphRunner()
            runner.setJsonFile(true)
            runner.setTimeProfile(true)
            runner.setDebug(true)
            
            val input_node_param = alg.parameters["input_node"] as Map<String, String>
            val output_node_param = alg.parameters["output_node"] as Map<String, String>
            
            // Set input prompt
            // Log.d("PromptInPromptOut", "prompt: $prompt")
            runner.setNodeValue(input_node_param.keys.first(), input_node_param.values.first(), prompt)
            
            // Set output path
            val resultPath = File(extResDir, "text/result.${alg.id}.${System.currentTimeMillis()}.txt")
            resultPath.parentFile?.mkdirs()
            runner.setNodeValue(output_node_param.keys.first(), output_node_param.values.first(), resultPath.absolutePath)
            
            val ok = runner.run(workflowOut.absolutePath, alg.id, "task_${System.currentTimeMillis()}")
            runner.close()
            
            // Log.d("PromptInPromptOut", "resultPath: ${resultPath.absolutePath}")
            if (resultPath.exists()) {
                Log.d("PromptInPromptOut", "resultPath exists")
                val response = resultPath.readText().trim()
                
                // 7) Update conversation history
                history.add(ConversationMessage("user", prompt))
                history.add(ConversationMessage("assistant", response))
                
                // 8) Limit history length to avoid excessive memory usage
                if (history.size > 20) { // Keep recent 10 rounds of conversation
                    history.removeAt(0)
                    history.removeAt(0)
                }
                
                PromptProcessResult.Success(response, conversationId)
            } else {
                Log.d("PromptInPromptOut", "resultPath not exists")
                PromptProcessResult.Error("Result file not found: ${resultPath.absolutePath}")
            }
            
        } catch (e: Exception) {
            Log.e("PromptInPromptOut", "Prompt processing failed", e)
            PromptProcessResult.Error("Processing failed: ${e.message}")
        }
    }
    
    /**
     * Build complete prompt including history
     */
    private fun buildFullPrompt(currentPrompt: String, history: List<ConversationMessage>): String {
        if (history.isEmpty()) {
            return currentPrompt
        }
        
        val contextBuilder = StringBuilder()
        contextBuilder.append("The following is conversation history:\n")
        
        // Add conversation history
        history.takeLast(10).forEach { message -> // Only take recent 5 rounds of conversation
            when (message.role) {
                "user" -> contextBuilder.append("User: ${message.content}\n")
                "assistant" -> contextBuilder.append("Assistant: ${message.content}\n")
            }
        }
        
        contextBuilder.append("\nCurrent user question: $currentPrompt")
        contextBuilder.append("\nPlease answer the current question based on conversation history:")
        
        return contextBuilder.toString()
    }
    
    /**
     * Clear conversation history for specified session
     */
    fun clearConversationHistory(conversationId: String = "default") {
        conversationHistory.remove(conversationId)
        Log.d("PromptInPromptOut", "Cleared conversation history for: $conversationId")
    }
    
    /**
     * Get conversation history for specified session
     */
    fun getConversationHistory(conversationId: String = "default"): List<ConversationMessage> {
        return conversationHistory[conversationId]?.toList() ?: emptyList()
    }
    
    /**
     * Get all conversation IDs
     */
    fun getAllConversationIds(): Set<String> {
        return conversationHistory.keys.toSet()
    }
}
