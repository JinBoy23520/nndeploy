// /home/always/github/public/nndeploy/app/android/app/src/main/java/com/nndeploy/ai/ImageInImageOut.kt
package com.nndeploy.ai

import android.content.Context
import android.net.Uri
import android.util.Log
import com.nndeploy.dag.GraphRunner
import com.nndeploy.base.FileUtils
import com.nndeploy.base.ImageUtils
import com.nndeploy.base.VideoUtils
import java.io.File

/**
 * AI Algorithm Processor - supports image, video, camera input
 */
object ImageInImageOut {
    
    /**
     * Segmentation algorithm processing
     */
    suspend fun processImageInImageOut(context: Context, inputUri: Uri, alg: AIAlgorithm): ProcessResult {
        return try {
            Log.w("ImageInImageOut", "Starting processing for ${alg.name}")
            
            // 1) Ensure external resources are ready
            val extResDir = FileUtils.ensureExternalResourcesReady(context)
            val extRoot = FileUtils.getExternalRoot(context)
            val extWorkflowDir = File(extResDir, "workflow").apply { mkdirs() }
            
            // Print three variables
            Log.d("ImageInImageOut", "extResDir: ${extResDir.absolutePath}")
            Log.d("ImageInImageOut", "extRoot: ${extRoot.absolutePath}")
            Log.d("ImageInImageOut", "extWorkflowDir: ${extWorkflowDir.absolutePath}")

            val workflowAsset = alg.workflowAsset
            
            // 3) Preprocess input data
            val (processedInputFile, processedInputUri) = ImageUtils.preprocessImage(context, inputUri)
            
            // 4) Read workflow from assets and replace relative paths with external absolute paths
            val rawJson = try {
                if (workflowAsset.startsWith("/") || workflowAsset.startsWith("file:") || workflowAsset.startsWith("external:")) {
                    // support absolute path or external: scheme (external:/absolute/path)
                    val path = if (workflowAsset.startsWith("external:")) workflowAsset.removePrefix("external:") else workflowAsset
                    File(path).readText()
                } else {
                    context.assets.open(workflowAsset).bufferedReader().use { it.readText() }
                }
            } catch (e: Exception) {
                Log.w("ImageInImageOut", "Failed to read workflowAsset from assets, trying as absolute path: ${e.message}")
                // fallback: try as absolute path
                val fallbackPath = workflowAsset.replace("resources/", "${extResDir.absolutePath}/")
                File(fallbackPath).readText()
            }

            // Parse JSON and replace resources/ prefix with absolute path
            val resolvedJson = try {
                Log.d("ImageInImageOut", "Raw JSON contains 'resources/models': ${rawJson.contains("resources/models")}")
                
                // Directly replace resource prefix with external absolute path
                val absolutePath = "${extResDir.absolutePath}/".replace("\\", "/")
                Log.d("ImageInImageOut", "About to replace 'resources/' with '$absolutePath'")
                val result = rawJson.replace("resources/", absolutePath)
                Log.d("ImageInImageOut", "After replace contains absolute path: ${result.contains(absolutePath + "models")}")
                result
            } catch (e: Exception) {
                Log.w("ImageInImageOut", "JSON parse/replace failed: ${e.message}")
                rawJson.replace("resources/", "${extResDir.absolutePath}/".replace("\\", "/"))
            }

            // Print resolved JSON content
            Log.d("ImageInImageOut", "External resources dir: ${extResDir.absolutePath}")
            Log.d("ImageInImageOut", "Resolved JSON length: ${resolvedJson.length} chars")
            if (resolvedJson.contains("resources/models")) {
                Log.w("ImageInImageOut", "WARNING: Still contains 'resources/models', path replacement may have failed!")
            }
            
            // 5) Write to external private directory to get real file path
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
            runner.setNodeValue(input_node_param.keys.first(), input_node_param.values.first(), processedInputFile.absolutePath)
            val resultPath = File(extResDir, "images/result.${alg.id}.jpg")
            
            // 确保结果文件的父目录存在
            resultPath.parentFile?.let { parentDir ->
                if (!parentDir.exists()) {
                    val created = parentDir.mkdirs()
                    Log.d("ImageInImageOut", "Created result directory: $created - ${parentDir.absolutePath}")
                }
            }

            runner.setNodeValue(output_node_param.keys.first(), output_node_param.values.first(), resultPath.absolutePath)
            
            val ok = runner.run(workflowOut.absolutePath, alg.id, "task_${System.currentTimeMillis()}")
            if (!ok) {
                val nativeErr = try { runner.getLastError() } catch (t: Throwable) { null }
                runner.close()
                val msg = if (!nativeErr.isNullOrEmpty()) {
                    "Native run failed: $nativeErr"
                } else {
                    "Native run failed"
                }
                Log.e("ImageInImageOut", msg)
                return ProcessResult.Error(msg)
            }
            runner.close()

            Log.d("ImageInImageOut", "resultPath: ${resultPath.absolutePath}")
            if (resultPath.exists()) {
                Log.d("ImageInImageOut", "resultPath exists")
                // Return a content Uri via FileProvider so other apps/components can access it
                val contentUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    resultPath
                )
                ProcessResult.Success(contentUri)
            } else {
                Log.d("ImageInImageOut", "resultPath not exists")
                // 尝试获取 native 层的错误信息（如果有）以便诊断
                val nativeErr = try { runner.getLastError() } catch (t: Throwable) { null }
                val msg = if (!nativeErr.isNullOrEmpty()) {
                    "Result file not found: ${resultPath.absolutePath}. Native error: $nativeErr"
                } else {
                    "Result file not found: ${resultPath.absolutePath}"
                }
                Log.e("ImageInImageOut", msg)
                ProcessResult.Error(msg)
            }
            
        } catch (e: Exception) {
            Log.e("ImageInImageOut", "Segmentation processing failed", e)
            ProcessResult.Error("Processing failed: ${e.message}")
        }
    }  
}

/**
 * Processing result wrapper
 */
sealed class ProcessResult {
    data class Success(val resultUri: Uri) : ProcessResult()
    data class Error(val message: String) : ProcessResult()
}
