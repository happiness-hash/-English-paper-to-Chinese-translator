package com.example.papertranslator.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.papertranslator.data.ApiConfigRepository
import com.example.papertranslator.data.dataStore
import java.io.File

/**
 * 论文阅读器：支持左右滑动翻页阅读
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PaperReaderScreen(
    pdfFile: File,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val apiConfigRepository = ApiConfigRepository(context.dataStore)
    val apiConfig by apiConfigRepository.apiConfigFlow.collectAsState(initial = ApiConfigRepository.ApiConfig("", "", ""))
    
    // 注意：在真正的生产环境中，PDF 渲染需要将 PDF 页面转换为 Bitmap 或使用专门的 PDF View
    // 此处为演示滑动功能，我们假设有一个页面列表（可以是生成的图片列表）
    // 为了让代码能运行，我们模拟 5 页。
    val pageCount = 5 
    val pagerState = rememberPagerState(pageCount = { pageCount })

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景
        if (apiConfig.wallpaperUri.isNotEmpty()) {
            AsyncImage(
                model = Uri.parse(apiConfig.wallpaperUri),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.2f
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("阅读论文 (第 ${pagerState.currentPage + 1} 页)") },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
            }
        ) { padding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) { pageIndex ->
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("PDF 内容渲染区域", style = MaterialTheme.typography.headlineSmall)
                            Text("Page ${pageIndex + 1}", style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(20.dp))
                            Text("在此处左右滑动切换页面", color = MaterialTheme.colorScheme.primary)
                            
                            // 这里实际应用时应使用 PdfRenderer 将 PDF 页面渲染为 Bitmap 并显示在这里
                        }
                    }
                }
            }
        }
    }
}
