package com.example.papertranslator.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.papertranslator.api.ApiManager
import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.listener.LocationTextExtractionStrategy
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData
import com.itextpdf.kernel.pdf.canvas.parser.EventType
import com.itextpdf.layout.Canvas
import com.itextpdf.layout.element.Paragraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "PdfUtils"

object PdfUtils {

    data class TextBlock(
        val id: Int,
        val text: String,
        val rect: Rectangle,
        val fontSize: Float,
        val pageIndex: Int
    )

    data class TranslationResult(
        val blockId: Int,
        val translatedText: String,
        val rect: Rectangle,
        val fontSize: Float
    )

    suspend fun translatePdfAdvanced(
        context: Context,
        uri: Uri,
        apiManager: ApiManager,
        mode: String = "replace",
        onProgress: (Int, Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
        val cacheFile = File(context.cacheDir, "input_temp_${System.currentTimeMillis()}.pdf")
        
        inputStream?.use { input ->
            FileOutputStream(cacheFile).use { output ->
                input.copyTo(output)
            }
        }

        val outputFile = File(context.getExternalFilesDir(null), "Paper_Translated_${System.currentTimeMillis()}.pdf")
        val pdfReader = PdfReader(cacheFile.absolutePath)
        val pdfWriter = PdfWriter(outputFile.absolutePath)
        val pdfDoc = PdfDocument(pdfReader, pdfWriter)

        val fontPath = "fonts/NotoSansSC-VariableFont_wght.ttf"
        val fontBytes = try {
            context.assets.open(fontPath).readBytes()
        } catch (e: Exception) {
            null
        }
        val chineseFont = if (fontBytes != null) {
            PdfFontFactory.createFont(fontBytes, PdfEncodings.IDENTITY_H, true)
        } else {
            null
        }

        // 步骤1: 滑窗分块 - 提取所有文本块并编号
        Log.d(TAG, "开始分块...")
        val allBlocks = mutableListOf<TextBlock>()
        var globalBlockId = 0
        
        for (pageIndex in 1..pdfDoc.numberOfPages) {
            val page = pdfDoc.getPage(pageIndex)
            val strategy = BetterLayoutStrategy()
            PdfTextExtractor.getTextFromPage(page, strategy)
            
            val pageBlocks = strategy.getParagraphBlocks()
            for (block in pageBlocks) {
                if (block.text.trim().length > 2) {
                    allBlocks.add(TextBlock(
                        id = globalBlockId++,
                        text = block.text,
                        rect = block.rect,
                        fontSize = block.fontSize,
                        pageIndex = pageIndex
                    ))
                }
            }
        }
        
        Log.d(TAG, "共提取 ${allBlocks.size} 个文本块")

        // 步骤2: 分治翻译 - 递归合并翻译
        Log.d(TAG, "开始分治翻译...")
        val translatedResults = divideAndConquerTranslate(allBlocks, apiManager)
        Log.d(TAG, "翻译完成，共 ${translatedResults.size} 个结果")

        // 步骤3: 写入译文到 PDF
        val translatedMap = translatedResults.associateBy { it.blockId }
        
        for (block in allBlocks) {
            val result = translatedMap[block.id] ?: continue
            val page = pdfDoc.getPage(block.pageIndex)
            
            try {
                val canvas = PdfCanvas(page)
                if (mode == "replace") {
                    canvas.saveState()
                    canvas.setFillColor(ColorConstants.WHITE)
                    canvas.rectangle(block.rect)
                    canvas.fill()
                    canvas.restoreState()

                    val layoutCanvas = Canvas(page, block.rect)
                    val p = Paragraph(result.translatedText)
                    if (chineseFont != null) p.setFont(chineseFont)
                    
                    layoutCanvas.add(
                        p.setFontSize(block.fontSize * 0.82f)
                            .setFixedPosition(block.rect.left, block.rect.bottom, block.rect.width)
                    )
                    layoutCanvas.close()
                } else {
                    val layoutCanvas = Canvas(page, block.rect)
                    val p = Paragraph("\n译: ${result.translatedText}")
                    if (chineseFont != null) p.setFont(chineseFont)
                    
                    layoutCanvas.add(
                        p.setFontSize(block.fontSize * 0.6f)
                            .setFontColor(ColorConstants.BLUE)
                            .setFixedPosition(block.rect.left, block.rect.bottom - (block.fontSize * 0.7f), block.rect.width)
                    )
                    layoutCanvas.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "写入块 ${block.id} 失败: ${e.message}")
            }
            
            onProgress(block.pageIndex, pdfDoc.numberOfPages)
        }
        
        pdfDoc.close()
        cacheFile.delete()
        outputFile
    }

    /**
     * 分治翻译算法
     * 
     * 思路:
     * 1. 块数量少时直接翻译
     * 2. 块数量多时分成两半，递归翻译
     * 3. 合并时使用滑动窗口保持上下文
     */
    private suspend fun divideAndConquerTranslate(
        blocks: List<TextBlock>,
        apiManager: ApiManager
    ): List<TranslationResult> = withContext(Dispatchers.IO) {
        if (blocks.isEmpty()) return@withContext emptyList()
        
        // 阈值：少于5个块直接翻译
        val threshold = 5
        
        if (blocks.size <= threshold) {
            // 直接翻译所有块，使用滑动窗口保持连贯
            return@withContext translateWithSlidingWindow(blocks, apiManager)
        }
        
        // 分治：分成两半
        val mid = blocks.size / 2
        val leftBlocks = blocks.subList(0, mid)
        val rightBlocks = blocks.subList(mid, blocks.size)
        
        // 递归翻译左右两部分
        val leftResults = divideAndConquerTranslate(leftBlocks, apiManager)
        val rightResults = divideAndConquerTranslate(rightBlocks, apiManager)
        
        // 合并结果
        leftResults + rightResults
    }

    /**
     * 滑动窗口翻译 - 保持上下文连贯性
     */
    private suspend fun translateWithSlidingWindow(
        blocks: List<TextBlock>,
        apiManager: ApiManager
    ): List<TranslationResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<TranslationResult>()
        var previousTranslation = ""
        
        for (block in blocks) {
            try {
                val translatedText = apiManager.translateWithSlidingWindow(
                    content = block.text,
                    previousTranslation = previousTranslation
                )
                
                results.add(TranslationResult(
                    blockId = block.id,
                    translatedText = translatedText,
                    rect = block.rect,
                    fontSize = block.fontSize
                ))
                
                previousTranslation = translatedText
            } catch (e: Exception) {
                Log.e(TAG, "翻译块 ${block.id} 失败: ${e.message}")
                // 失败时保留原文
                results.add(TranslationResult(
                    blockId = block.id,
                    translatedText = block.text,
                    rect = block.rect,
                    fontSize = block.fontSize
                ))
            }
        }
        
        results
    }

    /**
     * 并行翻译版本 - 适用于大量块
     * 使用分块并行翻译，最后合并
     */
    suspend fun translatePdfParallel(
        context: Context,
        uri: Uri,
        apiManager: ApiManager,
        onProgress: (Int, Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
        val cacheFile = File(context.cacheDir, "input_temp_${System.currentTimeMillis()}.pdf")
        
        inputStream?.use { input ->
            FileOutputStream(cacheFile).use { output ->
                input.copyTo(output)
            }
        }

        val outputFile = File(context.getExternalFilesDir(null), "Paper_Translated_${System.currentTimeMillis()}.pdf")
        val pdfReader = PdfReader(cacheFile.absolutePath)
        val pdfWriter = PdfWriter(outputFile.absolutePath)
        val pdfDoc = PdfDocument(pdfReader, pdfWriter)

        val fontPath = "fonts/NotoSansSC-VariableFont_wght.ttf"
        val fontBytes = try {
            context.assets.open(fontPath).readBytes()
        } catch (e: Exception) {
            null
        }
        val chineseFont = if (fontBytes != null) {
            PdfFontFactory.createFont(fontBytes, PdfEncodings.IDENTITY_H, true)
        } else {
            null
        }

        // 提取所有块
        val allBlocks = mutableListOf<TextBlock>()
        var globalBlockId = 0
        
        for (pageIndex in 1..pdfDoc.numberOfPages) {
            val page = pdfDoc.getPage(pageIndex)
            val strategy = BetterLayoutStrategy()
            PdfTextExtractor.getTextFromPage(page, strategy)
            
            val pageBlocks = strategy.getParagraphBlocks()
            for (block in pageBlocks) {
                if (block.text.trim().length > 2) {
                    allBlocks.add(TextBlock(
                        id = globalBlockId++,
                        text = block.text,
                        rect = block.rect,
                        fontSize = block.fontSize,
                        pageIndex = pageIndex
                    ))
                }
            }
        }

        // 分块并行翻译 (每块50个)
        val chunkSize = 50
        val allResults = ConcurrentHashMap<Int, TranslationResult>()
        
        val chunks = allBlocks.chunked(chunkSize)
        var processedChunks = 0
        
        for (chunk in chunks) {
            val chunkResults = translateWithSlidingWindow(chunk, apiManager)
            chunkResults.forEach { allResults[it.blockId] = it }
            
            processedChunks++
            onProgress(processedChunks, chunks.size)
        }

        // 写入 PDF
        for (block in allBlocks) {
            val result = allResults[block.id] ?: continue
            val page = pdfDoc.getPage(block.pageIndex)
            
            try {
                val canvas = PdfCanvas(page)
                canvas.saveState()
                canvas.setFillColor(ColorConstants.WHITE)
                canvas.rectangle(block.rect)
                canvas.fill()
                canvas.restoreState()

                val layoutCanvas = Canvas(page, block.rect)
                val p = Paragraph(result.translatedText)
                if (chineseFont != null) p.setFont(chineseFont)
                
                layoutCanvas.add(
                    p.setFontSize(block.fontSize * 0.82f)
                        .setFixedPosition(block.rect.left, block.rect.bottom, block.rect.width)
                )
                layoutCanvas.close()
            } catch (e: Exception) {
                Log.e(TAG, "写入块 ${block.id} 失败: ${e.message}")
            }
        }
        
        pdfDoc.close()
        cacheFile.delete()
        outputFile
    }

    class BetterLayoutStrategy : LocationTextExtractionStrategy() {
        private val chunks = mutableListOf<TextChunk>()

        override fun eventOccurred(data: IEventData?, type: EventType?) {
            if (type == EventType.RENDER_TEXT) {
                val renderInfo = data as TextRenderInfo
                val baseline = renderInfo.baseline
                val rect = Rectangle(
                    baseline.startPoint.get(0),
                    baseline.startPoint.get(1),
                    renderInfo.ascentLine.endPoint.get(0) - baseline.startPoint.get(0),
                    renderInfo.ascentLine.endPoint.get(1) - baseline.startPoint.get(1) + 1.5f
                )
                if (rect.width > 0.5f && rect.height > 0.5f) {
                    chunks.add(TextChunk(renderInfo.text, rect, rect.height))
                }
            }
            super.eventOccurred(data, type)
        }

        fun getParagraphBlocks(): List<TextChunk> {
            if (chunks.isEmpty()) return emptyList()
            val sortedChunks = chunks.sortedWith(
                compareByDescending<TextChunk> { it.rect.bottom }
                    .thenBy { it.rect.left }
            )
            val blocks = mutableListOf<TextChunk>()
            var currentBlock = sortedChunks[0]
            
            for (i in 1 until sortedChunks.size) {
                val next = sortedChunks[i]
                val sameRow = kotlin.math.abs(next.rect.bottom - currentBlock.rect.bottom) < 4f
                val closeEnough = (next.rect.left - currentBlock.rect.right) < 18f
                
                if (sameRow && closeEnough) {
                    val mergedRect = Rectangle(
                        currentBlock.rect.left,
                        kotlin.math.min(currentBlock.rect.bottom, next.rect.bottom),
                        next.rect.right - currentBlock.rect.left,
                        kotlin.math.max(currentBlock.rect.height, next.rect.height)
                    )
                    currentBlock = TextChunk(currentBlock.text + next.text, mergedRect, currentBlock.fontSize)
                } else {
                    blocks.add(currentBlock)
                    currentBlock = next
                }
            }
            blocks.add(currentBlock)
            return blocks
        }
    }

    data class TextChunk(val text: String, val rect: Rectangle, val fontSize: Float)

    suspend fun extractTextFromPdf(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val stringBuilder = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val pdfDoc = PdfDocument(PdfReader(stream))
            for (i in 1..pdfDoc.numberOfPages) {
                stringBuilder.append(PdfTextExtractor.getTextFromPage(pdfDoc.getPage(i))).append("\n")
            }
            pdfDoc.close()
        }
        stringBuilder.toString()
    }

    suspend fun generatePdf(context: Context, text: String, fileName: String): File = withContext(Dispatchers.IO) {
        val file = File(context.getExternalFilesDir(null), fileName)
        val writer = PdfWriter(file)
        val pdfDoc = PdfDocument(writer)
        val document = com.itextpdf.layout.Document(pdfDoc)
        
        val fontPath = "fonts/NotoSansSC-VariableFont_wght.ttf"
        val fontBytes = try { context.assets.open(fontPath).readBytes() } catch (e: Exception) { null }
        val chineseFont = if (fontBytes != null) PdfFontFactory.createFont(fontBytes, PdfEncodings.IDENTITY_H, true) else null

        try {
            val paragraphs = text.split("\n")
            for (pText in paragraphs) {
                if (pText.isNotBlank()) {
                    val p = Paragraph(pText)
                    if (chineseFont != null) p.setFont(chineseFont)
                    document.add(p)
                }
            }
        } finally {
            document.close()
        }
        file
    }

    fun splitIntoChunks(text: String, maxChunkSize: Int = 1000): List<String> {
        val paragraphs = text.split(Regex("\n\\s*\n"))
        val chunks = mutableListOf<String>()
        var currentChunk = StringBuilder()

        for (para in paragraphs) {
            if (currentChunk.length + para.length > maxChunkSize && currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toString())
                currentChunk = StringBuilder()
            }
            currentChunk.append(para).append("\n\n")
        }
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString())
        }
        return chunks
    }
}
