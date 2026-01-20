package com.nndeploy.app

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.nndeploy.ai.ImageInImageOut
import com.nndeploy.ai.ProcessResult
import com.nndeploy.ai.PromptInPromptOut
import com.nndeploy.ai.PromptInPromptOut.PromptProcessResult
import com.nndeploy.base.*
import kotlinx.coroutines.launch
import android.widget.Toast
import android.util.Log
import androidx.compose.runtime.rememberCoroutineScope
import android.content.Context
import android.content.Intent
import java.io.File
import com.nndeploy.ai.AIAlgorithm
import com.nndeploy.ai.InOutType
import com.nndeploy.ai.AlgorithmFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.os.Build

import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import java.util.Date
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween


/**
 * AI Page ViewModel
 */
class AIViewModel : ViewModel() {
    var selectedAlgorithm by mutableStateOf<AIAlgorithm?>(null)
    var inputUri by mutableStateOf<Uri?>(null)
    var outputUri by mutableStateOf<Uri?>(null)
    var isProcessing by mutableStateOf(false)
    
    // Available AI algorithms list (mutable so we can add imported workflows)
    val availableAlgorithms = mutableStateListOf<AIAlgorithm>().apply {
        addAll(AlgorithmFactory.createDefaultAlgorithms())
    }

    /**
     * Import a workflow JSON from given Uri into app external resources and register as an algorithm
     */
    fun importWorkflow(context: Context, uri: Uri, displayName: String? = null) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return
            val raw = inputStream.bufferedReader().use { it.readText() }

            // ensure external resources ready and write workflow to resources/workflow/<filename>
            val resDir = FileUtils.ensureExternalResourcesReady(context)
            val workflowDir = File(resDir, "workflow").apply { if (!exists()) mkdirs() }

            val name = displayName ?: (uri.lastPathSegment ?: "imported_workflow_${System.currentTimeMillis()}.json")
            val fileName = if (name.endsWith(".json")) name else "$name.json"
            val outFile = File(workflowDir, fileName)
            outFile.writeText(raw)

            // parse JSON to extract workflow name and detect OpenCv decode/encode node names
            var workflowName = fileName.substringBeforeLast('.')
            var inputNode: String? = null
            var outputNode: String? = null
            try {
                val obj = org.json.JSONObject(raw)
                if (obj.has("name_")) workflowName = obj.getString("name_")
                if (obj.has("node_repository_")) {
                    val arr = obj.getJSONArray("node_repository_")
                    for (i in 0 until arr.length()) {
                        val n = arr.getJSONObject(i)
                        val key = n.optString("key_")
                        val nm = n.optString("name_")
                        if (key.contains("OpenCvImageDecode")) inputNode = nm
                        if (key.contains("OpenCvImageEncode")) outputNode = nm
                    }
                }
            } catch (e: Exception) {
                // ignore parse errors
            }

            // build AIAlgorithm entry
            val algId = workflowName.replace("\\s+".toRegex(), "_").lowercase()
            val params = mutableMapOf<String, Any>()
            if (inputNode != null) params["input_node"] = mapOf(inputNode to "path_")
            if (outputNode != null) params["output_node"] = mapOf(outputNode to "path_")

            val alg = AIAlgorithm(
                id = algId,
                name = displayName ?: workflowName,
                description = "Imported workflow: $workflowName",
                icon = Icons.Default.ImportExport,
                inputType = listOf(com.nndeploy.ai.InOutType.IMAGE),
                outputType = listOf(com.nndeploy.ai.InOutType.IMAGE),
                category = com.nndeploy.ai.AlgorithmCategory.COMPUTER_VISION.displayName,
                workflowAsset = "external:${outFile.absolutePath}",
                tags = listOf("imported"),
                parameters = params,
                processFunction = "processImageInImageOut"
            )

            // add to list on main thread
            availableAlgorithms.add(0, alg)
        } catch (e: Exception) {
            Log.e("AIViewModel", "importWorkflow failed: ${e.message}")
        }
    }
}

/**
 * AI Algorithm Home Page
 */
@Composable
fun AIScreen(nav: NavHostController, sharedViewModel: AIViewModel = viewModel()) {
    val vm: AIViewModel = sharedViewModel
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // Title bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "nndeploy Algorithm Center",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E3A8A),
                    modifier = Modifier.weight(1f)
                )

                // Import workflow button
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                    onResult = { uri ->
                        if (uri != null) {
                            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            // call import
                            scope.launch {
                                vm.importWorkflow(context, uri)
                                Toast.makeText(context, "Workflow imported", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )

                IconButton(onClick = { launcher.launch(arrayOf("application/json", "text/*")) }) {
                    Icon(imageVector = Icons.Default.ImportExport, contentDescription = "Import")
                }
            }
        }
        
        // Algorithm list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Group by category
            val groupedAlgorithms = vm.availableAlgorithms.groupBy { it.category }
            
            groupedAlgorithms.forEach { (category, algorithms) ->
                item {
                    Text(
                        text = category,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF374151),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                items(algorithms) { algorithm ->
                    AIAlgorithmCard(
                        algorithm = algorithm,
                        onClick = {
                            vm.selectedAlgorithm = algorithm
                            // 视频超分对比算法特殊处理
                            if (algorithm.id.contains("video_sr") && algorithm.id.contains("compare")) {
                                nav.navigate("video_sr_screen")
                            } else {
                                nav.navigate("ai_process/${algorithm.id}")
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * AI Algorithm Card
 */
@Composable
fun AIAlgorithmCard(
    algorithm: AIAlgorithm,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Algorithm icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE8EEF9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = algorithm.icon,
                    contentDescription = null,
                    tint = Color(0xFF1E3A8A),
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Algorithm information
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = algorithm.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = algorithm.description,
                    fontSize = 14.sp,
                    color = Color(0xFF6B7280),
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Supported input type tags
                Row {
                    algorithm.inputType.forEach { type ->
                        InputTypeChip(type)
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
            }
            
            // Arrow icon
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = Color(0xFF9CA3AF),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Input Type Chip
 */
@Composable
fun InputTypeChip(inputType: InOutType) {
    val (text, color) = when (inputType) {
        InOutType.IMAGE -> "Image" to Color(0xFF10B981)
        InOutType.VIDEO -> "Video" to Color(0xFFF59E0B)
        InOutType.CAMERA -> "Camera" to Color(0xFFEF4444)
        InOutType.AUDIO -> "Audio" to Color(0xFFFF6B6B)
        InOutType.TEXT -> "Text" to Color(0xFF4ECDC4)
        InOutType.PROMPT -> "Prompt" to Color(0xFF8B5CF6)
        InOutType.ALL -> "All" to Color(0xFF06B6D4)
    }
    
    Box(
        modifier = Modifier
            .background(
                color = color.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * AI Algorithm Processing Page
 */
@Composable
fun CVProcessScreen(
    nav: NavHostController,
    algorithmId: String,
    sharedViewModel: AIViewModel = viewModel()
) {
    val vm: AIViewModel = sharedViewModel
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Find the corresponding algorithm
    val algorithm = AlgorithmFactory.getAlgorithmsById(vm.availableAlgorithms, algorithmId)
    
    // Image picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        vm.inputUri = uri
    }
    
    // Video picker
    val videoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        vm.inputUri = uri
    }
    
    // Camera
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            // Handle photo taken successfully
            Log.w("CVProcessScreen", "Photo taken successfully")
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Text(
                text = algorithm?.name ?: "AI Processing",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E3A8A),
                modifier = Modifier.weight(1f)
            )
        }
        
        // Input area
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (vm.inputUri != null) {
                    AsyncImage(
                        model = vm.inputUri,
                        contentDescription = "Input content",
                        modifier = Modifier.fillMaxWidth(0.8f)
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = Color(0xFF9CA3AF),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Select input content",
                            fontSize = 16.sp,
                            color = Color(0xFF6B7280)
                        )
                    }
                }
            }
        }
        
        // Input selection buttons
        algorithm?.let { algo ->
            InputSelectionButtons(
                inputTypes = algo.inputType,
                onImageSelect = { imagePickerLauncher.launch("image/*") },
                onVideoSelect = { videoPickerLauncher.launch("video/*") },
                onCameraPhoto = { 
                    // Create temporary file for photo
                    val photoUri = CameraUtils.createPhotoUri(context)
                    vm.inputUri = photoUri
                    cameraLauncher.launch(photoUri)
                },
                onCameraVideo = {
                    // Video recording function implementation
                    Toast.makeText(context, "Video recording feature to be implemented", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Quick-use example image from assets/resources for demo2 workflow
        if (algorithm != null && (algorithm.id == "demo2_yolo" || algorithm.workflowAsset.contains("demo2"))) {
            Button(
                onClick = {
                    try {
                        val extRes = FileUtils.ensureExternalResourcesReady(context)
                        val example = File(extRes, "template/nndeploy-workflow/detect/zidane.jpg")
                        if (example.exists()) {
                            vm.inputUri = android.net.Uri.fromFile(example)
                            Toast.makeText(context, "Example image selected", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Example image not found: ${example.absolutePath}", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Failed to load example image: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Use Example Image (demo2)")
            }
        }
        
        // Process button
        Button(
            onClick = {
                vm.inputUri?.let { uri ->
                    scope.launch {
                        vm.isProcessing = true
                        // Check if algorithm exists
                        if (algorithm == null) {
                            Toast.makeText(context, "Algorithm $algorithmId does not exist", Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        try {
                            val result = ImageInImageOut.processImageInImageOut(context, uri, algorithm!!)
                            
                            when (result) {
                                is ProcessResult.Success -> {
                                    vm.outputUri = result.resultUri
                                    nav.navigate("ai_result")
                                }
                                is ProcessResult.Error -> {
                                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                }
                            }
                        } finally {
                            vm.isProcessing = false
                        }
                    }
                } ?: Toast.makeText(context, "Please select input content first", Toast.LENGTH_SHORT).show()
            },
            enabled = vm.inputUri != null && !vm.isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(56.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            if (vm.isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Processing...")
            } else {
                Text("Start Processing", fontSize = 16.sp)
            }
        }
    }
}

/**
 * Input Selection Button Group
 */
@Composable
fun InputSelectionButtons(
    inputTypes: List<InOutType>,
    onImageSelect: () -> Unit,
    onVideoSelect: () -> Unit,
    onCameraPhoto: () -> Unit,
    onCameraVideo: () -> Unit
) {
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        Text(
            text = "Select input method",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF374151),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Show corresponding buttons based on algorithm supported input types
            if (inputTypes.contains(InOutType.IMAGE)) {
                OutlinedButton(
                    onClick = onImageSelect,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Photo, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Gallery")
                }
                OutlinedButton(
                    onClick = onCameraPhoto,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Camera")
                }
            }
        }
    }
}

/**
 * AI Processing Result Page
 */
@Composable
fun CVResultScreen(nav: NavHostController, sharedViewModel: AIViewModel = viewModel()) {
    val vm: AIViewModel = sharedViewModel
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Text(
                text = "Processing Result",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E3A8A),
                modifier = Modifier.weight(1f)
            )
        }
        
        // Result display area
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (vm.outputUri != null) {
                    AsyncImage(
                        model = vm.outputUri,
                        contentDescription = "Processing result",
                        modifier = Modifier.fillMaxWidth(0.9f)
                    )
                } else {
                    Text(
                        text = "No processing result",
                        fontSize = 16.sp,
                        color = Color(0xFF6B7280)
                    )
                }
            }
        }
        
        // Action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    vm.outputUri?.let { uri ->
                        if (FileUtils.saveCopyToDownloads(context, uri)) {
                            Toast.makeText(context, "Saved successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Save")
            }
            
            Button(
                onClick = {
                    vm.outputUri?.let { uri ->
                        val shareIntent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            type = "image/*"
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share result"))
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Share")
            }
        }
        
        // Continue processing button
        Button(
            onClick = { nav.navigate("ai") },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(52.dp),
            shape = RoundedCornerShape(26.dp)
        ) {
            Text("Continue processing other algorithms", fontSize = 16.sp)
        }
    }
}

/**
 * Determine input media type based on URI
 */
private fun determineInputType(uri: Uri, context: Context): com.nndeploy.ai.InputMediaType {
    return try {
        val mimeType = context.contentResolver.getType(uri)
        when {
            mimeType?.startsWith("image/") == true -> com.nndeploy.ai.InputMediaType.IMAGE
            mimeType?.startsWith("video/") == true -> com.nndeploy.ai.InputMediaType.VIDEO
            else -> com.nndeploy.ai.InputMediaType.IMAGE // Default to image
        }
    } catch (e: Exception) {
        com.nndeploy.ai.InputMediaType.IMAGE // Default to image on exception
    }
}

/**
 * LLM Chat Processing Page
 */
@Composable
fun LlmChatProcessScreen(
    nav: NavHostController,
    algorithmId: String,
    sharedViewModel: AIViewModel = viewModel()
) {
    val vm: AIViewModel = sharedViewModel
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Find the corresponding algorithm
    val algorithm = AlgorithmFactory.getAlgorithmsById(vm.availableAlgorithms, algorithmId)
    
    // Chat message state
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isTyping by remember { mutableStateOf(false) }
    var showInputDialog by remember { mutableStateOf(false) }
    var showModelConfigDialog by remember { mutableStateOf(false) }
    var modelPathInput by remember { mutableStateOf("") }
    var isCopyingModel by remember { mutableStateOf(false) }
    var copyProgress by remember { mutableStateOf("") }
    var isSourceCopying by remember { mutableStateOf(false) }
    var sourceCopyStatus by remember { mutableStateOf("") }
    var sourceCopySuccess by remember { mutableStateOf<Boolean?>(null) }
    
    // Common questions list
    val commonQuestions = listOf(
        "介绍一下你自己",
        "帮我写一首关于春天的诗",
        "解释什么是人工智能",
        "推荐一本好书",
        "讲一个有趣的故事",
        "给我一些学习建议"
    )
    
    // Function to handle sending message
    val sendMessage: (String) -> Unit = { messageText ->
        if (messageText.isNotBlank() && !isTyping) {
            // 1. Save current input text
            val currentInput = messageText
            
            // 2. Show user message
            val userMessage = ChatMessage(
                content = currentInput,
                isUser = true,
                timestamp = System.currentTimeMillis()
            )
            messages = messages + userMessage
            
            // 3. Clear input field
            inputText = ""
            
            // 4. Launch coroutine for AI response
            scope.launch {
                isTyping = true
                isCopyingModel = false
                copyProgress = ""
                
                try {
                    if (algorithm == null) {
                        Toast.makeText(context, "Algorithm $algorithmId does not exist", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    
                    Log.d("LlmChatProcessScreen", "currentInput: $currentInput")
                    
                    // Call with progress callback for model copying
                    val result = PromptInPromptOut.processPromptInPromptOut(
                        context = context,
                        prompt = currentInput,
                        alg = algorithm,
                        onModelCopyProgress = { fileName, current, total ->
                            isCopyingModel = true
                            copyProgress = "Copying model: $fileName ($current/$total)"
                            Log.d("LlmChatProcessScreen", copyProgress)
                        }
                    )
                    
                    isCopyingModel = false
                    copyProgress = ""
                    
                    when (result) {
                        is PromptProcessResult.Success -> {
                            val aiMessage = ChatMessage(
                                content = result.response,
                                isUser = false,
                                timestamp = System.currentTimeMillis()
                            )
                            messages = messages + aiMessage
                        }
                        is PromptProcessResult.Error -> {
                            val errorMessage = ChatMessage(
                                content = "Sorry, an error occurred: ${result.message}",
                                isUser = false,
                                timestamp = System.currentTimeMillis(),
                                isError = true
                            )
                            messages = messages + errorMessage
                        }
                    }
                } catch (e: Exception) {
                    Log.e("LlmChatProcessScreen", "AI processing failed", e)
                    val errorMessage = ChatMessage(
                        content = "Sorry, an unknown error occurred: ${e.message}",
                        isUser = false,
                        timestamp = System.currentTimeMillis(),
                        isError = true
                    )
                    messages = messages + errorMessage
                } finally {
                    isTyping = false
                }
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Text(
                text = algorithm?.name ?: "AI Chat",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E3A8A),
                modifier = Modifier.weight(1f)
            )
            // Model config button (for external models like Gemma3)
            val isGemma3 = algorithmId == "gemma3_demo" || algorithmId == "gemma3_simple"
            if (isGemma3) {
                IconButton(onClick = { showModelConfigDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Configure model path",
                        tint = Color(0xFF1E3A8A)
                    )
                }
            }
            IconButton(
                onClick = { 
                    messages = listOf()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Clear chat"
                )
            }
        }
        
        // Chat message area
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Chat,
                                contentDescription = null,
                                tint = Color(0xFF9CA3AF),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Start conversation with AI",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF374151)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Choose a common question or enter your own",
                                fontSize = 14.sp,
                                color = Color(0xFF6B7280),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                
                // Common questions section
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "💡 Common Questions",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF374151),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                items(commonQuestions.chunked(2)) { rowQuestions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowQuestions.forEach { question ->
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { 
                                        if (!isTyping) {
                                            sendMessage(question)
                                        }
                                    }
                                    .focusable(),  // Enable TV remote focus
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFF0F9FF)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Text(
                                    text = question,
                                    fontSize = 13.sp,
                                    color = Color(0xFF1E3A8A),
                                    modifier = Modifier.padding(12.dp),
                                    lineHeight = 18.sp
                                )
                            }
                        }
                        // Add empty space if odd number of questions
                        if (rowQuestions.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                items(messages) { message ->
                    ChatMessageItem(message = message)
                }
            }
            
            // Typing indicator or model copying progress
            if (isTyping || isCopyingModel) {
                item {
                    if (isCopyingModel) {
                        // Model copying progress
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color(0xFFF59E0B),
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "📦 Preparing Model",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF92400E)
                                    )
                                    if (copyProgress.isNotEmpty()) {
                                        Text(
                                            text = copyProgress,
                                            fontSize = 12.sp,
                                            color = Color(0xFF92400E).copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        TypingIndicator()
                    }
                }
            }
        }
        
        // Input area
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Enter your question...") },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF10B981),
                        unfocusedBorderColor = Color(0xFFE5E7EB)
                    ),
                    maxLines = 4
                )
                
                IconButton(
                    onClick = {
                        sendMessage(inputText)
                    },
                    enabled = inputText.isNotBlank() && !isTyping,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (inputText.isNotBlank() && !isTyping) Color(0xFF10B981) else Color(0xFFE5E7EB),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (inputText.isNotBlank() && !isTyping) Color.White else Color(0xFF9CA3AF)
                    )
                }
                
                // Add dialog button for TV remote control
                IconButton(
                    onClick = { showInputDialog = true },
                    enabled = !isTyping,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (!isTyping) Color(0xFF3B82F6) else Color(0xFFE5E7EB),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Open input dialog",
                        tint = if (!isTyping) Color.White else Color(0xFF9CA3AF)
                    )
                }
            }
        }
    }
    
    // Input Dialog for TV remote control
    if (showInputDialog) {
        var dialogInput by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { showInputDialog = false },
            title = {
                Text(
                    text = "Enter Your Question",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0xFF1E3A8A)
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = dialogInput,
                        onValueChange = { dialogInput = it },
                        placeholder = { Text("Type your question here...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF10B981),
                            unfocusedBorderColor = Color(0xFFE5E7EB)
                        ),
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (dialogInput.isNotBlank()) {
                            sendMessage(dialogInput)
                            showInputDialog = false
                            dialogInput = ""
                        }
                    },
                    enabled = dialogInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981)
                    )
                ) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInputDialog = false }) {
                    Text("Cancel", color = Color(0xFF6B7280))
                }
            }
        )
    }
    
    // Model Configuration Dialog
    if (showModelConfigDialog) {
        val modelRoot = ModelPathManager.getModelRootPath(context).absolutePath
        if (modelPathInput.isEmpty()) {
            modelPathInput = modelRoot
        }
        
        AlertDialog(
            onDismissRequest = { 
                showModelConfigDialog = false
                sourceCopyStatus = ""
                sourceCopySuccess = null
            },
            title = {
                Text(
                    text = "Model Storage Configuration",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF1E3A8A)
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Model files are stored in external storage and auto-copied on first use",
                        fontSize = 13.sp,
                        color = Color(0xFF6B7280),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    // Current model path display
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Storage Location:",
                                fontSize = 12.sp,
                                color = Color(0xFF6B7280),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = modelRoot,
                                fontSize = 11.sp,
                                color = Color(0xFF374151),
                                lineHeight = 14.sp
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Model status check
                    val gemma3Available = ModelPathManager.isModelAvailable(context, "gemma3")
                    val sourceDir = ModelPathManager.getSourceModelPath(context)
                    val sourceAvailable = sourceDir.exists() && sourceDir.isDirectory
                    
                    // Debug logging
                    LaunchedEffect(Unit) {
                        Log.d("ModelConfigDialog", "Gemma3 available: $gemma3Available")
                        Log.d("ModelConfigDialog", "Source dir: ${sourceDir.absolutePath}")
                        Log.d("ModelConfigDialog", "Source available: $sourceAvailable")
                        if (sourceAvailable) {
                            val files = sourceDir.listFiles()
                            Log.d("ModelConfigDialog", "Source files count: ${files?.size}")
                            files?.forEach { Log.d("ModelConfigDialog", "  - ${it.name}") }
                        }
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (gemma3Available) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (gemma3Available) Color(0xFF10B981) else Color(0xFFF59E0B),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (gemma3Available) "✅ Gemma3 模型已就绪" else "⚠️ Gemma3 模型未找到",
                                fontSize = 13.sp,
                                color = if (gemma3Available) Color(0xFF10B981) else Color(0xFFF59E0B),
                                fontWeight = FontWeight.Bold
                            )
                            if (!gemma3Available && sourceAvailable) {
                                Text(
                                    text = "检测到源目录，可以复制模型",
                                    fontSize = 11.sp,
                                    color = Color(0xFF6B7280)
                                )
                            } else if (!gemma3Available && !sourceAvailable) {
                                Text(
                                    text = "源目录不存在: ${sourceDir.absolutePath}",
                                    fontSize = 10.sp,
                                    color = Color(0xFFEF4444)
                                )
                            }
                        }
                    }
                    
                    // Copy from source button (if source exists but target doesn't)
                    if (!gemma3Available && sourceAvailable) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Button(
                            onClick = {
                                // 检查存储权限
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    if (!android.os.Environment.isExternalStorageManager()) {
                                        // 需要授权
                                        Toast.makeText(
                                            context, 
                                            "需要存储权限，正在打开设置页面...", 
                                            Toast.LENGTH_LONG
                                        ).show()
                                        
                                        try {
                                            val intent = android.content.Intent(
                                                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                                android.net.Uri.parse("package:${context.packageName}")
                                            )
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            val intent = android.content.Intent(
                                                android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                                            )
                                            context.startActivity(intent)
                                        }
                                        return@Button
                                    }
                                }
                                
                                isSourceCopying = true
                                sourceCopyStatus = ""
                                sourceCopySuccess = null
                                
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        try {
                                            val success = ModelPathManager.copyModelFromSource(
                                                context,
                                                "gemma3"
                                            ) { fileName, current, total ->
                                                sourceCopyStatus = "正在复制: $fileName ($current/$total)"
                                            }
                                            
                                            withContext(Dispatchers.Main) {
                                                isSourceCopying = false
                                                sourceCopySuccess = success
                                                if (success) {
                                                    sourceCopyStatus = "✅ 复制完成！"
                                                    Toast.makeText(context, "✅ 模型复制成功！现在可以开始对话了。", Toast.LENGTH_LONG).show()
                                                } else {
                                                    sourceCopyStatus = "❌ 复制失败，请查看日志"
                                                    Toast.makeText(context, "❌ 复制失败，请检查存储权限", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                isSourceCopying = false
                                                sourceCopySuccess = false
                                                sourceCopyStatus = "❌ 错误: ${e.message}"
                                                Toast.makeText(context, "❌ 复制错误: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = !isSourceCopying,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            if (isSourceCopying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Copying...", fontSize = 13.sp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("📦 Copy Models from Source", fontSize = 13.sp)
                            }
                        }
                        
                        // Show copy progress
                        if (sourceCopyStatus.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = when (sourceCopySuccess) {
                                        true -> Color(0xFFDCFCE7)  // Green
                                        false -> Color(0xFFFEE2E2)  // Red
                                        null -> Color(0xFFFEF3C7)   // Yellow (copying)
                                    }
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = sourceCopyStatus,
                                    fontSize = 12.sp,
                                    color = when (sourceCopySuccess) {
                                        true -> Color(0xFF166534)
                                        false -> Color(0xFF991B1B)
                                        null -> Color(0xFF92400E)
                                    },
                                    modifier = Modifier.padding(12.dp),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    
                    if (!gemma3Available) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "✨ Auto-Copy on First Use\n\nModel files will be automatically copied from app assets to external storage when you send your first message.\n\nRequired: ~800MB free space",
                                fontSize = 11.sp,
                                color = Color(0xFF166534),
                                modifier = Modifier.padding(12.dp),
                                lineHeight = 16.sp
                            )
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "✅ Model Ready\n\nModel files are available and ready for inference.",
                                fontSize = 11.sp,
                                color = Color(0xFF166534),
                                modifier = Modifier.padding(12.dp),
                                lineHeight = 16.sp
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Path input field
                    OutlinedTextField(
                        value = modelPathInput,
                        onValueChange = { modelPathInput = it },
                        label = { Text("Custom Path (optional)", fontSize = 12.sp) },
                        placeholder = { Text("e.g., /sdcard/models", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )
                }
            },
            confirmButton = {
                if (modelPathInput != modelRoot) {
                    Button(
                        onClick = {
                            if (ModelPathManager.setModelRootPath(context, modelPathInput)) {
                                Toast.makeText(context, "Path updated successfully", Toast.LENGTH_SHORT).show()
                                showModelConfigDialog = false
                            } else {
                                Toast.makeText(context, "Invalid path", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text("Update Path", fontSize = 13.sp)
                    }
                }
            },
            dismissButton = {
                Row {
                    if (modelPathInput != modelRoot) {
                        TextButton(
                            onClick = {
                                ModelPathManager.resetToDefaultPath(context)
                                modelPathInput = ModelPathManager.getModelRootPath(context).absolutePath
                                Toast.makeText(context, "Reset to default", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Reset", fontSize = 13.sp, color = Color(0xFFF59E0B))
                        }
                    }
                    TextButton(onClick = { showModelConfigDialog = false }) {
                        Text("Close", fontSize = 13.sp, color = Color(0xFF6B7280))
                    }
                }
            }
        )
    }
}

/**
 * Chat Message Data Class
 */
data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val timestamp: Long,
    val isError: Boolean = false
)

/**
 * Chat Message Item Component
 */
@Composable
fun ChatMessageItem(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (message.isUser) {
            Spacer(modifier = Modifier.width(48.dp))
        }
        
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    message.isError -> Color(0xFFFEE2E2)
                    message.isUser -> Color(0xFF10B981)
                    else -> Color.White
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = message.content,
                    fontSize = 14.sp,
                    color = when {
                        message.isError -> Color(0xFFDC2626)
                        message.isUser -> Color.White
                        else -> Color(0xFF374151)
                    },
                    lineHeight = 20.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                    fontSize = 10.sp,
                    color = when {
                        message.isError -> Color(0xFFDC2626).copy(alpha = 0.7f)
                        message.isUser -> Color.White.copy(alpha = 0.7f)
                        else -> Color(0xFF9CA3AF)
                    }
                )
            }
        }
        
        if (!message.isUser) {
            Spacer(modifier = Modifier.width(48.dp))
        }
    }
}

/**
 * Typing Indicator
 */
@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 100.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 4.dp,
                bottomEnd = 16.dp
            ),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    val animatedAlpha by animateFloatAsState(
                        targetValue = if ((System.currentTimeMillis() / 500) % 3 == index.toLong()) 1f else 0.3f,
                        animationSpec = tween(500),
                        label = "typing_dot_$index"
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                Color(0xFF9CA3AF).copy(alpha = animatedAlpha),
                                CircleShape
                            )
                    )
                    
                    if (index < 2) {
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.width(48.dp))
    }
}
