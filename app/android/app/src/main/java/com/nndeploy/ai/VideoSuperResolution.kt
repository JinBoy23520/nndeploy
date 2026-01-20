package com.nndeploy.ai

import android.content.Context
import android.net.Uri
import android.util.Log
import com.nndeploy.dag.GraphRunner
import com.nndeploy.base.FileUtils
import com.nndeploy.base.VideoUtils
import java.io.File

/**
 * Video Super-Resolution Processor with Side-by-Side Comparison
 * 视频超分处理器，支持左右对比效果展示
 */
object VideoSuperResolution {
    
    /**
     * 处理视频超分并生成左右对比输    "output_path_": ""出
     * 
     * @param context Android Context
     * @param inputVideoUri 输入视频 URI（可以是assets或外部文件）
     * @param alg 超分算法配置
     * @return VideoProcessResult 包含处理结果的URI和状态
     */
    suspend fun processVideoSuperResolution(
        context: Context, 
        inputVideoUri: Uri, 
        alg: AIAlgorithm
    ): VideoProcessResult {
        return try {
            Log.i("VideoSR", "Starting video super-resolution for ${alg.name}")
            
            // 1. 确保外部资源目录就绪
            val extResDir = FileUtils.ensureExternalResourcesReady(context)
            val extRoot = FileUtils.getExternalRoot(context)
            val extWorkflowDir = File(extResDir, "workflow").apply { mkdirs() }
            val extOutputDir = File(extRoot, "output").apply { mkdirs() }
            
            Log.d("VideoSR", "External resources dir: ${extResDir.absolutePath}")
            Log.d("VideoSR", "Output dir: ${extOutputDir.absolutePath}")
            
            // 检查资源目录
            if (!extResDir.exists()) {
                throw RuntimeException("External resources directory not found: ${extResDir.absolutePath}")
            }
            
            // 检查模型文件（如果是 RealESRGAN）
            if (alg.id.contains("realesrgan")) {
                val modelFile = File(extResDir, "models/RealESRGAN_x2plus.onnx")
                Log.d("VideoSR", "Checking model file: ${modelFile.absolutePath}")
                if (!modelFile.exists()) {
                    throw RuntimeException("Model file not found: ${modelFile.absolutePath}. Please ensure resources are copied.")
                }
                Log.d("VideoSR", "Model file found, size: ${modelFile.length()} bytes")
            }
            
            // 2. 处理输入视频
            val inputVideoFile = when {
                inputVideoUri.scheme == "file" -> {
                    File(inputVideoUri.path!!)
                }
                inputVideoUri.scheme == "content" -> {
                    // 从 content:// URI 复制到临时文件
                    val tempFile = File(context.cacheDir, "input_video_${System.currentTimeMillis()}.mp4")
                    context.contentResolver.openInputStream(inputVideoUri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    tempFile
                }
                inputVideoUri.toString().startsWith("asset://") -> {
                    // 从 assets 复制
                    val assetPath = inputVideoUri.toString().removePrefix("asset://")
                    val tempFile = File(context.cacheDir, "input_video_${System.currentTimeMillis()}.mp4")
                    context.assets.open(assetPath).use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    tempFile
                }
                else -> {
                    throw IllegalArgumentException("Unsupported URI scheme: ${inputVideoUri.scheme}")
                }
            }
            
            Log.d("VideoSR", "Input video: ${inputVideoFile.absolutePath} (${inputVideoFile.length() / 1024 / 1024}MB)")
            
            // 3. 读取并解析工作流配置
            val workflowAsset = alg.workflowAsset
            val rawJson = if (workflowAsset.startsWith("/") || workflowAsset.startsWith("file:")) {
                File(workflowAsset).readText()
            } else {
                context.assets.open(workflowAsset).bufferedReader().use { it.readText() }
            }
            
            // 先移除 asset:// 前缀，避免后续路径替换时产生冲突
            // 将 asset://resources/... 替换为 resources/...
            var resolvedJson = rawJson.replace("asset://resources/", "resources/")
            
            // 替换资源路径为绝对路径（但不影响 video_url_ 中的路径，稍后单独处理）
            val absoluteResPath = "${extResDir.absolutePath}/".replace("\\", "/")
            resolvedJson = resolvedJson.replace("resources/", absoluteResPath)
            
            // 4. 保存解析后的工作流到外部存储
            val workflowFile = File(extWorkflowDir, "${alg.id}_video_sr.json").apply {
                writeText(resolvedJson)
            }
            
            Log.d("VideoSR", "Workflow file: ${workflowFile.absolutePath}")
            Log.d("VideoSR", "Workflow file exists: ${workflowFile.exists()}")
            Log.d("VideoSR", "Workflow file size: ${workflowFile.length()} bytes")
            
            // 检查输入视频是否可访问
            if (!inputVideoFile.exists()) {
                throw RuntimeException("Input video file not found: ${inputVideoFile.absolutePath}")
            }
            if (inputVideoFile.length() == 0L) {
                throw RuntimeException("Input video file is empty: ${inputVideoFile.absolutePath}")
            }
            
            // 5. 准备输出文件路径
            val outputFileName = "sr_output_${System.currentTimeMillis()}.mp4"
            val outputFile = File(extOutputDir, outputFileName)
            
            // 6. 配置工作流参数
            val inputParams = alg.parameters["input_node"] as? Map<String, String>
            val outputParams = alg.parameters["output_node"] as? Map<String, String>
            
            // 创建参数映射
            val paramMap = mutableMapOf<String, String>()
            
            // 设置输入视频路径
            inputParams?.forEach { (nodeName, paramName) ->
                paramMap["$nodeName.$paramName"] = inputVideoFile.absolutePath
            }
            
            // 设置输出视频路径
            outputParams?.forEach { (nodeName, paramName) ->
                paramMap["$nodeName.$paramName"] = outputFile.absolutePath
            }
            
            Log.d("VideoSR", "Parameter map: $paramMap")
            
            // 7. 执行工作流
            Log.i("VideoSR", "Starting GraphRunner...")
            Log.d("VideoSR", "Input video: ${inputVideoFile.absolutePath}")
            Log.d("VideoSR", "Output video: ${outputFile.absolutePath}")
            val startTime = System.currentTimeMillis()
            
            // 读取工作流 JSON 并替换路径
            var workflowJson = workflowFile.readText()
            
            // 替换输入视频路径 - 使用 Unix 风格路径分隔符
            val inputPath = inputVideoFile.absolutePath.replace("\\", "/")
            val outputPath = outputFile.absolutePath.replace("\\", "/")
            
            // 替换 video_url_ 中的默认视频路径为实际输入视频路径
            // 注意：这里的路径已经是绝对路径了（被上面的 resources/ 替换处理过）
            val defaultVideoPath = "${absoluteResPath}videos/face.mp4"
            workflowJson = workflowJson.replace(
                "\"video_url_\": [\"$defaultVideoPath\"]",
                "\"video_url_\": [\"$inputPath\"]"
            )
            
            // 替换节点中的 path_ 参数
            workflowJson = workflowJson.replace(
                "\"path_\": \"$defaultVideoPath\"",
                "\"path_\": \"$inputPath\""
            )
            
            // 替换输出路径
            workflowJson = workflowJson.replace(
                "\"output_path_\": \"\"",
                "\"output_path_\": \"$outputPath\""
            )
            
            Log.d("VideoSR", "After path replacement:")
            Log.d("VideoSR", "  Default video path: $defaultVideoPath")
            Log.d("VideoSR", "  Input path: $inputPath")
            Log.d("VideoSR", "  Output path: $outputPath")
            Log.d("VideoSR", "Workflow JSON length: ${workflowJson.length} chars")
            
            // 保存修改后的工作流用于调试
            val debugWorkflowFile = File(extWorkflowDir, "${alg.id}_debug.json")
            debugWorkflowFile.writeText(workflowJson)
            Log.d("VideoSR", "Debug workflow saved to: ${debugWorkflowFile.absolutePath}")
            
            // 使用 GraphRunner 实例执行
            val graphRunner = GraphRunner()
            graphRunner.setJsonFile(true)  // 使用文件路径而不是 JSON 字符串
            graphRunner.setTimeProfile(true)
            graphRunner.setDebug(true)
            
            // 运行工作流（使用修改后的JSON文件）
            val success = graphRunner.run(debugWorkflowFile.absolutePath, "video_sr_workflow", "task_${System.currentTimeMillis()}")
            
            // 获取错误信息
            val errorMsg = graphRunner.getLastError()
            graphRunner.close()
            
            val duration = System.currentTimeMillis() - startTime
            Log.i("VideoSR", "GraphRunner completed in ${duration}ms, success=$success")
            
            if (!success) {
                val detailedError = errorMsg ?: "Unknown error"
                Log.e("VideoSR", "GraphRunner failed: $detailedError")
                Log.e("VideoSR", "Debug workflow file: ${debugWorkflowFile.absolutePath}")
                throw RuntimeException("Video super-resolution processing failed: $detailedError")
            }
            
            // 8. 检查输出文件
            Log.d("VideoSR", "Checking output file: ${outputFile.absolutePath}")
            Log.d("VideoSR", "Output file exists: ${outputFile.exists()}")
            if (outputFile.exists()) {
                Log.d("VideoSR", "Output file size: ${outputFile.length()} bytes")
            }
            
            if (!outputFile.exists() || outputFile.length() == 0L) {
                // 列出输出目录中的文件
                val outputDir = outputFile.parentFile
                if (outputDir?.exists() == true) {
                    val files = outputDir.listFiles()
                    Log.d("VideoSR", "Files in output directory: ${files?.joinToString { it.name }}")
                }
                throw RuntimeException("Output video file not generated or empty: ${outputFile.absolutePath}")
            }
            
            Log.i("VideoSR", "Output video: ${outputFile.absolutePath} (${outputFile.length() / 1024 / 1024}MB)")
            
            // 9. 返回结果
            VideoProcessResult(
                success = true,
                message = "Video super-resolution completed in ${duration / 1000.0}s",
                outputUri = Uri.fromFile(outputFile),
                processingTimeMs = duration
            )
            
        } catch (e: Exception) {
            Log.e("VideoSR", "Video super-resolution failed", e)
            VideoProcessResult(
                success = false,
                message = "Error: ${e.message}",
                outputUri = null,
                processingTimeMs = 0
            )
        }
    }
    
    /**
     * 使用预置的测试视频进行超分处理
     * 
     * @param context Android Context
     * @param alg 超分算法配置
     * @return VideoProcessResult
     */
    suspend fun processWithDefaultVideo(
        context: Context,
        alg: AIAlgorithm
    ): VideoProcessResult {
        // 使用 assets 中的默认测试视频
        val defaultVideoUri = Uri.parse("asset://resources/videos/720pface.mp4")
        return processVideoSuperResolution(context, defaultVideoUri, alg)
    }
}

/**
 * 视频处理结果数据类
 */
data class VideoProcessResult(
    val success: Boolean,
    val message: String,
    val outputUri: Uri?,
    val processingTimeMs: Long
)
