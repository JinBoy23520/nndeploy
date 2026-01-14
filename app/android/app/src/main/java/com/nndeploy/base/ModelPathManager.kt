package com.nndeploy.base

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * 模型路径管理器
 * 支持从外部存储加载大模型文件，避免打包到 APK
 * 首次运行时自动从 assets 复制模型到外部存储
 */
object ModelPathManager {
    private const val PREFS_NAME = "model_paths"
    private const val KEY_MODEL_ROOT = "model_root_path"
    private const val KEY_MODEL_COPIED_PREFIX = "model_copied_"
    
    // 默认模型根目录（外部存储）
    private const val DEFAULT_MODEL_DIR = "nndeploy_models"
    
    /**
     * 获取源模型目录（用户手动放置的模型）
     */
    fun getSourceModelPath(context: Context): File {
        val sdcard = android.os.Environment.getExternalStorageDirectory()
        return File(sdcard, "nndeploy_models/gemma3_source")
    }
    
    /**
     * 获取 SharedPreferences
     */
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * 获取模型根目录路径
     * 优先级：用户设置 > 默认外部存储路径
     */
    fun getModelRootPath(context: Context): File {
        val prefs = getPrefs(context)
        val customPath = prefs.getString(KEY_MODEL_ROOT, null)
        
        return if (customPath != null) {
            File(customPath)
        } else {
            // 默认使用外部存储的公共目录（/sdcard/nndeploy_models）
            File(android.os.Environment.getExternalStorageDirectory(), DEFAULT_MODEL_DIR)
        }
    }
    
    /**
     * 检查模型是否已从 assets 复制过
     */
    private fun isModelCopied(context: Context, modelName: String): Boolean {
        return getPrefs(context).getBoolean("${KEY_MODEL_COPIED_PREFIX}${modelName}", false)
    }
    
    /**
     * 标记模型已复制
     */
    private fun markModelCopied(context: Context, modelName: String) {
        getPrefs(context).edit().putBoolean("${KEY_MODEL_COPIED_PREFIX}${modelName}", true).apply()
    }
    
    /**
     * 从 assets 复制模型到外部存储
     * @param modelName 模型名称，如 "gemma3"
     * @param assetPath assets 中的路径，如 "resources/models/gemma3"
     * @param progressCallback 复制进度回调 (当前文件名, 当前索引, 总文件数)
     * @return 是否复制成功
     */
    fun copyModelFromAssets(
        context: Context,
        modelName: String,
        assetPath: String,
        progressCallback: ((String, Int, Int) -> Unit)? = null
    ): Boolean {
        try {
            val targetDir = getModelPath(context, modelName)
            
            // 如果已经复制过且目录存在，跳过
            if (isModelCopied(context, modelName) && targetDir.exists()) {
                Log.i("ModelPathManager", "Model $modelName already copied, skipping")
                return true
            }
            
            // 创建目标目录
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            
            // 列出 assets 中的文件
            val assetManager = context.assets
            val files = assetManager.list(assetPath) ?: emptyArray()
            
            if (files.isEmpty()) {
                Log.e("ModelPathManager", "No files found in assets path: $assetPath")
                return false
            }
            
            Log.i("ModelPathManager", "Copying ${files.size} files from $assetPath to ${targetDir.absolutePath}")
            
            // 复制每个文件
            files.forEachIndexed { index, fileName ->
                progressCallback?.invoke(fileName, index + 1, files.size)
                
                val assetFilePath = "$assetPath/$fileName"
                val targetFile = File(targetDir, fileName)
                
                Log.d("ModelPathManager", "Copying $fileName (${index + 1}/${files.size})")
                
                assetManager.open(assetFilePath).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output, bufferSize = 8192)
                    }
                }
            }
            
            // 标记已复制
            markModelCopied(context, modelName)
            Log.i("ModelPathManager", "Successfully copied model $modelName")
            return true
            
        } catch (e: Exception) {
            Log.e("ModelPathManager", "Failed to copy model from assets", e)
            return false
        }
    }
    
    /**
     * 从源目录复制模型到工作目录
     * @param modelName 模型名称，如 "gemma3"
     * @param progressCallback 复制进度回调 (当前文件名, 当前索引, 总文件数)
     * @return 是否复制成功
     */
    fun copyModelFromSource(
        context: Context,
        modelName: String,
        progressCallback: ((String, Int, Int) -> Unit)? = null
    ): Boolean {
        try {
            val sourceDir = getSourceModelPath(context)
            val targetDir = getModelPath(context, modelName)
            
            Log.i("ModelPathManager", "Copying from ${sourceDir.absolutePath} to ${targetDir.absolutePath}")
            
            if (!sourceDir.exists()) {
                Log.e("ModelPathManager", "Source directory does not exist: ${sourceDir.absolutePath}")
                return false
            }
            
            if (!sourceDir.isDirectory) {
                Log.e("ModelPathManager", "Source path is not a directory: ${sourceDir.absolutePath}")
                return false
            }
            
            // 创建目标目录
            if (!targetDir.exists()) {
                val created = targetDir.mkdirs()
                Log.i("ModelPathManager", "Created target directory: $created")
            }
            
            // 列出源目录文件
            val files = sourceDir.listFiles()
            Log.i("ModelPathManager", "Source directory files: ${files?.size ?: 0}")
            
            if (files == null) {
                Log.e("ModelPathManager", "Failed to list files in source directory (permission issue?)")
                return false
            }
            
            val modelFiles = files.filter { 
                val name = it.name
                name.startsWith("model.") || name.startsWith("tokenizer.") || name.endsWith(".json")
            }
            
            if (modelFiles.isEmpty()) {
                Log.e("ModelPathManager", "No model files found in source directory")
                Log.e("ModelPathManager", "All files: ${files.joinToString { it.name }}")
                return false
            }
            
            Log.i("ModelPathManager", "Copying ${modelFiles.size} files from source to ${targetDir.absolutePath}")
            
            // 复制每个文件
            var successCount = 0
            modelFiles.forEachIndexed { index, sourceFile ->
                try {
                    progressCallback?.invoke(sourceFile.name, index + 1, modelFiles.size)
                    
                    val targetFile = File(targetDir, sourceFile.name)
                    Log.d("ModelPathManager", "Copying ${sourceFile.name} (${sourceFile.length()} bytes) - ${index + 1}/${modelFiles.size}")
                    
                    sourceFile.copyTo(targetFile, overwrite = true)
                    successCount++
                    Log.d("ModelPathManager", "  ✓ Copied successfully")
                } catch (e: Exception) {
                    Log.e("ModelPathManager", "Failed to copy ${sourceFile.name}", e)
                }
            }
            
            if (successCount == modelFiles.size) {
                // 标记已复制
                markModelCopied(context, modelName)
                Log.i("ModelPathManager", "Successfully copied all $successCount files for model $modelName")
                return true
            } else {
                Log.e("ModelPathManager", "Only copied $successCount of ${modelFiles.size} files")
                return false
            }
            
        } catch (e: Exception) {
            Log.e("ModelPathManager", "Failed to copy model from source", e)
            return false
        }
    }
    
    /**
     * 设置自定义模型根目录
     */
    fun setModelRootPath(context: Context, path: String): Boolean {
        val file = File(path)
        if (!file.exists() || !file.isDirectory) {
            Log.e("ModelPathManager", "Invalid path: $path")
            return false
        }
        
        getPrefs(context).edit().putString(KEY_MODEL_ROOT, path).apply()
        Log.i("ModelPathManager", "Model root path set to: $path")
        return true
    }
    
    /**
     * 重置为默认路径
     */
    fun resetToDefaultPath(context: Context) {
        getPrefs(context).edit().remove(KEY_MODEL_ROOT).apply()
        Log.i("ModelPathManager", "Model root path reset to default")
    }
    
    /**
     * 获取特定模型的路径
     * @param modelName 模型名称，如 "gemma3"
     */
    fun getModelPath(context: Context, modelName: String): File {
        return File(getModelRootPath(context), modelName)
    }
    
    /**
     * 检查模型是否存在
     */
    /**
     * 检查模型是否可用（目录存在且包含必要的模型文件）
     */
    fun isModelAvailable(context: Context, modelName: String): Boolean {
        val modelDir = getModelPath(context, modelName)
        if (!modelDir.exists() || !modelDir.isDirectory) {
            return false
        }
        
        // 检查是否有文件（至少要有一些模型文件）
        val files = modelDir.listFiles()
        if (files == null || files.isEmpty()) {
            return false
        }
        
        // 对于 gemma3，检查关键文件
        if (modelName == "gemma3") {
            val requiredFiles = listOf("model.onnx", "model.onnx_data", "tokenizer.json", "tokenizer.model")
            val hasAllFiles = requiredFiles.all { fileName ->
                File(modelDir, fileName).exists()
            }
            return hasAllFiles
        }
        
        // 其他模型只要目录非空即可
        return true
    }
    
    /**
     * 获取模型目录下的特定文件
     */
    fun getModelFile(context: Context, modelName: String, fileName: String): File? {
        val modelDir = getModelPath(context, modelName)
        val file = File(modelDir, fileName)
        return if (file.exists()) file else null
    }
    
    /**
     * 列出可用的模型
     */
    fun listAvailableModels(context: Context): List<String> {
        val rootDir = getModelRootPath(context)
        if (!rootDir.exists() || !rootDir.isDirectory) {
            return emptyList()
        }
        
        return rootDir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?: emptyList()
    }
    
    /**
     * 为 Gemma3 模型构建完整路径映射
     * 返回格式：resources/models/gemma3/model.onnx -> /storage/.../nndeploy_models/gemma3/model.onnx
     */
    fun buildGemma3PathMapping(context: Context): Map<String, String> {
        val modelDir = getModelPath(context, "gemma3")
        
        return mapOf(
            "resources/models/gemma3/model.onnx" to File(modelDir, "model.onnx").absolutePath,
            "resources/models/gemma3/model_q4.onnx_data" to File(modelDir, "model_q4.onnx_data").absolutePath,
            "resources/models/gemma3/tokenizer.json" to File(modelDir, "tokenizer.json").absolutePath,
            "resources/models/gemma3/tokenizer.model" to File(modelDir, "tokenizer.model").absolutePath
        )
    }
    
    /**
     * 创建默认模型目录结构
     */
    fun initializeDefaultModelDir(context: Context): File {
        val modelRoot = getModelRootPath(context)
        if (!modelRoot.exists()) {
            modelRoot.mkdirs()
            Log.i("ModelPathManager", "Created model root directory: ${modelRoot.absolutePath}")
        }
        return modelRoot
    }
    
    /**
     * 获取模型配置信息（用于UI显示）
     */
    fun getModelConfigInfo(context: Context, modelName: String): String {
        val modelDir = getModelPath(context, modelName)
        if (!modelDir.exists()) {
            return "Model not found: $modelName"
        }
        
        val files = modelDir.listFiles() ?: return "Empty directory"
        val totalSize = files.sumOf { it.length() }
        val sizeInMB = totalSize / (1024 * 1024)
        
        return """
            Model: $modelName
            Path: ${modelDir.absolutePath}
            Files: ${files.size}
            Total Size: ${sizeInMB}MB
        """.trimIndent()
    }
}
