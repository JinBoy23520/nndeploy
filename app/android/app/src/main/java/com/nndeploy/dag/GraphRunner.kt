package com.nndeploy.dag

class GraphRunner : AutoCloseable {

    companion object {
        init {
            // Load dependencies first
            System.loadLibrary("c++_shared")
            System.loadLibrary("MNN")
            System.loadLibrary("MNN_Express")
            System.loadLibrary("opencv_java4")
            System.loadLibrary("onnxruntime")
            System.loadLibrary("llm")
            
            // Load nndeploy framework
            System.loadLibrary("nndeploy_framework")
            
            // Load plugins
            System.loadLibrary("nndeploy_plugin_basic")
            System.loadLibrary("nndeploy_plugin_infer")
            System.loadLibrary("nndeploy_plugin_preprocess")
            System.loadLibrary("nndeploy_plugin_codec")
            System.loadLibrary("nndeploy_plugin_tokenizer")
            System.loadLibrary("nndeploy_plugin_llm")
            System.loadLibrary("nndeploy_plugin_qwen")
            System.loadLibrary("nndeploy_plugin_gemma")
            System.loadLibrary("nndeploy_plugin_classification")
            System.loadLibrary("nndeploy_plugin_detect")
            System.loadLibrary("nndeploy_plugin_segment")
            System.loadLibrary("nndeploy_plugin_matting")
            System.loadLibrary("nndeploy_plugin_ocr")
            System.loadLibrary("nndeploy_plugin_super_resolution")
            System.loadLibrary("nndeploy_plugin_track")
            System.loadLibrary("nndeploy_plugin_template")
            
            // Load JNI wrapper
            System.loadLibrary("nndeploy_jni")
        }
    }

    private var nativeHandle: Long = 0L
    private var initialized: Boolean = false

    private var isJsonFile: Boolean = true
    private var isDump: Boolean = true
    private var isTimeProfile: Boolean = true
    private var isDebug: Boolean = false
    private var parallelType: Int = 0
    private var isLoopMaxFlag: Boolean = true

    init {
        nativeHandle = createGraphRunner()
        if (nativeHandle == 0L) {
            throw RuntimeException("创建GraphRunner失败")
        }
        initialized = true
    }

    fun run(graphJsonStr: String, name: String, taskId: String): Boolean {
        checkInitialized()
        require(graphJsonStr.isNotEmpty()) { "图JSON字符串不能为空" }
        require(name.isNotEmpty()) { "图名称不能为空" }
        require(taskId.isNotEmpty()) { "任务ID不能为空" }
        return run(nativeHandle, graphJsonStr, name, taskId)
    }

    fun setJsonFile(isJsonFile: Boolean) {
        checkInitialized()
        this.isJsonFile = isJsonFile
        setJsonFile(nativeHandle, isJsonFile)
    }

    fun setDump(isDump: Boolean) {
        checkInitialized()
        this.isDump = isDump
        setDump(nativeHandle, isDump)
    }

    fun setTimeProfile(isTimeProfile: Boolean) {
        checkInitialized()
        println("GraphRunner: 设置时间性能分析 - isTimeProfile: $isTimeProfile")
        this.isTimeProfile = isTimeProfile
        setTimeProfile(nativeHandle, isTimeProfile)
        println("GraphRunner: 时间性能分析设置完成")
    }

    fun setDebug(isDebug: Boolean) {
        checkInitialized()
        this.isDebug = isDebug
        setDebug(nativeHandle, isDebug)
    }

    fun setParallelType(parallelType: Int) {
        checkInitialized()
        this.parallelType = parallelType
        setParallelType(nativeHandle, parallelType)
    }

    fun setLoopMaxFlag(isLoopMaxFlag: Boolean) {
        checkInitialized()
        this.isLoopMaxFlag = isLoopMaxFlag
        setLoopMaxFlag(nativeHandle, isLoopMaxFlag)
    }

    fun setNodeValue(nodeName: String, key: String, value: String) {
        checkInitialized()
        require(nodeName.isNotEmpty()) { "节点名称不能为空" }
        require(key.isNotEmpty()) { "参数键不能为空" }
        setNodeValue(nativeHandle, nodeName, key, value)
    }

    private fun checkInitialized() {
        check(initialized && nativeHandle != 0L) { "GraphRunner已关闭" }
    }

    override fun close() {
        if (initialized && nativeHandle != 0L) {
            destroyGraphRunner(nativeHandle)
            nativeHandle = 0L
            initialized = false
        }
    }

    // native methods
    private external fun createGraphRunner(): Long
    private external fun destroyGraphRunner(handle: Long)
    private external fun run(
        handle: Long,
        graphJsonStr: String,
        name: String,
        taskId: String
    ): Boolean

    private external fun setJsonFile(handle: Long, isJsonFile: Boolean)
    private external fun setDump(handle: Long, isDump: Boolean)
    private external fun setTimeProfile(handle: Long, isTimeProfile: Boolean)
    private external fun setDebug(handle: Long, isDebug: Boolean)
    private external fun setParallelType(handle: Long, parallelType: Int)
    private external fun setLoopMaxFlag(handle: Long, isLoopMaxFlag: Boolean)
    private external fun setNodeValue(handle: Long, nodeName: String, key: String, value: String)
    private external fun getLastError(handle: Long): String?

    fun getLastError(): String? {
        checkInitialized()
        return try {
            getLastError(nativeHandle)
        } catch (t: Throwable) {
            null
        }
    }
}




