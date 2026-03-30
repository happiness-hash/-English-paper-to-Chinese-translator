package com.example.papertranslator.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.work.*
import com.example.papertranslator.R
import com.example.papertranslator.api.ApiManager
import com.example.papertranslator.data.*
import com.example.papertranslator.utils.PdfUtils
import com.example.papertranslator.utils.TranslationWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperInputScreen(
    type: String,
    onBackClick: () -> Unit,
    onConfirmClick: (String, String) -> Unit,
    onReaderNavigate: (File) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val apiConfigRepository = remember(context) { ApiConfigRepository(context.dataStore) }
    val db = remember(context) { AppDatabase.getDatabase(context) }
    
    var paperContent by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isExtractingText by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }
    var useBackground by remember { mutableStateOf(false) }
    
    val isTranslate = type == "translate"
    val title = if (isTranslate) stringResource(R.string.translate) else stringResource(R.string.interpret)
    val gradientColors = if (isTranslate) 
        listOf(Color(0xFF11998e), Color(0xFF38ef7d)) 
    else 
        listOf(Color(0xFFeb3349), Color(0xFFf45c43))
    
    // 初始化通知通道
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "translation_channel",
                "论文翻译通知",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            if (isTranslate) {
                scope.launch {
                    val config = apiConfigRepository.apiConfigFlow.first()
                    
                    if (useBackground) {
                        // 后台翻译模式
                        val inputData = workDataOf(
                            "input_uri" to selectedUri.toString(),
                            "api_type" to config.apiType,
                            "api_key" to config.apiKey
                        )
                        val translationWork = OneTimeWorkRequestBuilder<TranslationWorker>()
                            .setInputData(inputData)
                            .build()
                        WorkManager.getInstance(context).enqueue(translationWork)
                        Toast.makeText(context, "翻译已在后台开始，完成后可在历史记录查看", Toast.LENGTH_LONG).show()
                    } else {
                        // 前台翻译模式
                        isLoading = true
                        try {
                            val apiManager = ApiManager(config)
                            val translatedFile = PdfUtils.translatePdfAdvanced(context, selectedUri, apiManager) { current, total ->
                                progressMessage = "正在翻译第 $current / $total 页..."
                            }
                            
                            db.translationDao().insertRecord(
                                TranslationRecord(
                                    fileName = selectedUri.lastPathSegment ?: "Paper",
                                    originalText = "PDF Content",
                                    translatedText = "Translated Content",
                                    filePath = translatedFile.absolutePath
                                )
                            )
                            onReaderNavigate(translatedFile)
                        } catch (e: Exception) {
                            Toast.makeText(context, "翻译失败: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            isLoading = false
                            progressMessage = ""
                        }
                    }
                }
            } else {
                // 解读模式
                scope.launch {
                    isLoading = true
                    isExtractingText = true
                    try {
                        val extractedText = PdfUtils.extractTextFromPdf(context, selectedUri)
                        paperContent = "正在解读论文..."
                        
                        val config = apiConfigRepository.apiConfigFlow.first()
                        val apiManager = ApiManager(config)
                        val interpretedResult = apiManager.interpretPaper(extractedText)
                        paperContent = interpretedResult
                    } catch (e: Exception) {
                        Toast.makeText(context, "解读失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        isLoading = false
                        isExtractingText = false
                    }
                }
            }
        }
    }
    
    // 背景渐变
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(gradientColors)
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { 
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "返回",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.smallTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isTranslate) {
                    // 翻译模式 UI
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(100.dp),
                        tint = Color.White.copy(alpha = 0.9f)
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        text = "上传 PDF 文档",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "智能翻译，保留原文格式",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // 后台模式选择
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.2f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = useBackground,
                                onCheckedChange = { useBackground = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color.White,
                                    uncheckedColor = Color.White.copy(alpha = 0.7f)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "后台翻译模式",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                                Text(
                                    text = "退出应用后仍可继续翻译",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                )
                            }
                        }
                    }
                    
                    if (isLoading && progressMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = progressMessage,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // 上传按钮
                    Button(
                        onClick = { filePickerLauncher.launch("application/pdf") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = !isLoading,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = gradientColors.first()
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "选择 PDF 文件",
                            color = gradientColors.first(),
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    // 解读模式 UI
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = Color.White.copy(alpha = 0.9f)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "论文智能解读",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "上传 PDF，自动总结核心观点",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White.copy(alpha = 0.8f)
                        ),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // 结果显示区域
                    if (paperContent.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White.copy(alpha = 0.95f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "解读结果",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = paperContent,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    
                    if (isLoading) {
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "正在解读论文，请稍候...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White
                            )
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 上传按钮
                    Button(
                        onClick = { filePickerLauncher.launch("application/pdf") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = !isLoading,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = gradientColors.first()
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "上传 PDF 文件",
                            color = gradientColors.first(),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
