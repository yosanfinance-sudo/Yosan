package com.example.features.import

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.example.data.local.entity.*
import com.example.features.rules.CategorizationEngine
import java.io.ByteArrayInputStream
import java.util.*
import java.util.zip.ZipInputStream

enum class SupportedBank(val displayName: String) {
    INDIAN_BANK("Indian Bank"),
    STATE_BANK("State Bank of India"),
    HDFC_BANK("HDFC Bank"),
    ICICI_BANK("ICICI Bank"),
    AXIS_BANK("Axis Bank"),
    KOTAK_BANK("Kotak Mahindra Bank"),
    YES_BANK("YES Bank"),
    INDUSIND_BANK("IndusInd Bank"),
    CANARA_BANK("Canara Bank"),
    BANK_OF_BARODA("Bank of Baroda"),
    UNION_BANK("Union Bank of India"),
    PNB("Punjab National Bank"),
    FEDERAL_BANK("Federal Bank"),
    UNKNOWN("Generic / Supported Bank")
}

data class AuditDiscrepancy(
    val rowIndex: Int,
    val field: String,
    val expected: String,
    val actual: String,
    val reason: String
)

data class StatementAuditReport(
    val status: String, // StatementAuditStatus.PASS, REVIEW_REQUIRED, INCOMPLETE
    val detectedBank: SupportedBank,
    val totalSourceRowsDetected: Int,
    val totalParsed: Int,
    val validCount: Int,
    val duplicateCount: Int,
    val openingBalance: Double?,
    val closingBalance: Double?,
    val calculatedClosingBalance: Double?,
    val totalDebits: Double,
    val totalCredits: Double,
    val balanceReconciliationPassed: Boolean?,
    val discrepancies: List<AuditDiscrepancy> = emptyList()
)

data class ParsedImportRow(
    val rawLine: String,
    val date: String,
    val description: String,
    val debitAmount: Double,
    val creditAmount: Double,
    val amount: Double,
    val balance: Double?,
    val referenceNumber: String,
    val suggestedCategory: String,
    val suggestedCategoryId: Long?,
    val suggestedType: TransactionType,
    val confidence: Float,
    val isDuplicate: Boolean = false,
    val hasDiscrepancy: Boolean = false
)

data class ImportPreviewResult(
    val totalRows: Int,
    val validRows: List<ParsedImportRow>,
    val duplicateCount: Int,
    val detectedColumns: List<String>,
    val fileName: String,
    val detectedBank: SupportedBank = SupportedBank.UNKNOWN,
    val auditReport: StatementAuditReport? = null
)

object StatementImportService {

    private fun columnLetterToIndex(letter: String): Int {
        var index = 0
        for (char in letter) {
            if (char in 'A'..'Z') {
                index = index * 26 + (char - 'A' + 1)
            }
        }
        return index - 1
    }

    fun parseStatementStream(
        context: Context,
        uri: Uri,
        fileName: String,
        rules: List<CategorizationRuleEntity>,
        existingTransactions: List<TransactionEntity>,
        bankOverride: SupportedBank? = null,
        password: String? = null
    ): ImportPreviewResult {
        val lowerName = fileName.lowercase()
        val textContent = if (lowerName.endsWith(".pdf")) {
            extractPdfTextFromUri(context, uri, password)
        } else {
            val bytes = try {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
            } catch (e: Exception) {
                android.util.Log.w("YosanDB", "Failed to read statement stream", e)
                ByteArray(0)
            }
            extractTextFromStatementBytes(context, bytes, fileName, password)
        }
        return parseStatementText(textContent, rules, existingTransactions, fileName, bankOverride)
    }

    private fun extractPdfTextFromUri(context: Context, uri: Uri, password: String? = null): String {
        try {
            if (!PDFBoxResourceLoader.isReady()) {
                PDFBoxResourceLoader.init(context.applicationContext)
            }
        } catch (_: Throwable) {}

        val tempFile = java.io.File(context.cacheDir, "temp_stream_import_" + System.currentTimeMillis() + ".pdf")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return ""

            val document = if (!password.isNullOrBlank()) {
                PDDocument.load(tempFile, password)
            } else {
                PDDocument.load(tempFile)
            }

            document.use { doc ->
                if (doc.isEncrypted) {
                    try { doc.setAllSecurityToBeRemoved(true) } catch (_: Throwable) {}
                }
                val stripper = PDFTextStripper()
                stripper.sortByPosition = true
                val sb = java.lang.StringBuilder()
                val totalPages = doc.numberOfPages
                for (page in 1..totalPages) {
                    stripper.startPage = page
                    stripper.endPage = page
                    sb.append(stripper.getText(doc)).append("\n")
                }
                return sb.toString()
            }
        } catch (e: Throwable) {
            val msg = e.message?.lowercase() ?: ""
            if (msg.contains("password") || msg.contains("encrypted") || e.javaClass.simpleName.contains("Password", ignoreCase = true)) {
                throw SecurityException("PDF_PASSWORD_REQUIRED")
            }
            throw e
        } finally {
            try { tempFile.delete() } catch (_: Throwable) {}
        }
    }

    fun parseStatementBytes(
        context: Context? = null,
        bytes: ByteArray,
        fileName: String,
        rules: List<CategorizationRuleEntity>,
        existingTransactions: List<TransactionEntity>,
        bankOverride: SupportedBank? = null
    ): ImportPreviewResult {
        val lowerName = fileName.lowercase()
        val isPdf = lowerName.endsWith(".pdf") || (bytes.size >= 4 && bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x44.toByte() && bytes[3] == 0x46.toByte())
        val isXlsx = lowerName.endsWith(".xlsx") || (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte())
        val isXls = lowerName.endsWith(".xls") || (bytes.size >= 4 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() && bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte())

        val textContent = extractTextFromStatementBytes(context, bytes, fileName)
        val resolvedName = when {
            isPdf && !lowerName.endsWith(".pdf") -> "$fileName.pdf"
            isXlsx && !lowerName.endsWith(".xlsx") -> "$fileName.xlsx"
            isXls && !lowerName.endsWith(".xls") -> "$fileName.xls"
            else -> fileName
        }
        return parseStatementText(textContent, rules, existingTransactions, resolvedName, bankOverride)
    }

    fun extractTextFromStatementBytes(
        context: Context? = null,
        bytes: ByteArray,
        fileName: String,
        password: String? = null
    ): String {
        val lowerName = fileName.lowercase()
        val isPdf = lowerName.endsWith(".pdf") || (bytes.size >= 4 && bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x44.toByte() && bytes[3] == 0x46.toByte())
        val isXlsx = lowerName.endsWith(".xlsx") || (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte())
        val isXls = lowerName.endsWith(".xls") || (bytes.size >= 4 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() && bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte())

        return when {
            isXlsx || isXls -> extractTextFromXlsxBytes(bytes)
            isPdf -> extractTextFromPdfBytes(context, bytes, password)
            else -> String(bytes, Charsets.UTF_8)
        }
    }

    private fun extractTextFromXlsxBytes(bytes: ByteArray): String {
        val sharedStrings = mutableListOf<String>()
        val sheetRows = mutableListOf<String>()
        val bytesMap = mutableMapOf<String, ByteArray>()

        val maxTotalBytes = 50 * 1024 * 1024 // 50MB decompression limit against zip bombs
        var totalBytesRead = 0
        var entryCount = 0
        val maxEntries = 200

        try {
            val zip = ZipInputStream(ByteArrayInputStream(bytes))
            var entry = zip.nextEntry
            while (entry != null) {
                entryCount++
                if (entryCount > maxEntries) {
                    break
                }
                // Prevent Zip Path Traversal (CWE-22)
                val safeName = entry.name.replace("\\", "/")
                if (safeName.contains("..")) {
                    entry = zip.nextEntry
                    continue
                }
                if (safeName == "xl/sharedStrings.xml" || safeName.startsWith("xl/worksheets/sheet")) {
                    val buffer = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(4096)
                    var read: Int
                    while (zip.read(chunk).also { read = it } != -1) {
                        totalBytesRead += read
                        if (totalBytesRead > maxTotalBytes) {
                            throw IllegalStateException("Decompression limit exceeded (possible zip bomb)")
                        }
                        buffer.write(chunk, 0, read)
                    }
                    bytesMap[safeName] = buffer.toByteArray()
                }
                entry = zip.nextEntry
            }
            zip.close()

            // 1. Extract shared strings
            bytesMap["xl/sharedStrings.xml"]?.let { xmlBytes ->
                val xml = String(xmlBytes, Charsets.UTF_8)
                val siRegex = Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL)
                val tRegex = Regex("<t[^>]*?>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
                siRegex.findAll(xml).forEach { siMatch ->
                    val siContent = siMatch.groupValues[1]
                    val tMatches = tRegex.findAll(siContent)
                    val itemText = tMatches.map { it.groupValues[1] }.joinToString("")
                    val cleanText = itemText
                        .replace("&amp;", "&")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&quot;", "\"")
                        .trim()
                    sharedStrings.add(sanitizeText(cleanText))
                }
            }

            // 2. Extract worksheets
            val sheetKeys = bytesMap.keys.filter { it.startsWith("xl/worksheets/sheet") }.sorted()
            for (sheetKey in sheetKeys) {
                val xmlBytes = bytesMap[sheetKey] ?: continue
                val xml = String(xmlBytes, Charsets.UTF_8)

                val rowRegex = Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
                val cellRegex = Regex("<c([^>]*?)/>|<c([^>]*?)>((?:(?!<c).)*?)</c>", RegexOption.DOT_MATCHES_ALL)
                val rAttrRegex = Regex("r=\"([A-Z]+)(\\d+)\"")
                val tAttrRegex = Regex("t=\"([^\"]+)\"")
                val vRegex = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL)

                rowRegex.findAll(xml).forEach { rowMatch ->
                    val rowXml = rowMatch.groupValues[1]
                    var maxColIdx = -1
                    val parsedCells = mutableListOf<Pair<Int, String>>()

                    cellRegex.findAll(rowXml).forEach { cellMatch ->
                        val g1 = cellMatch.groupValues[1]
                        val g2 = cellMatch.groupValues[2]
                        val g3 = cellMatch.groupValues[3]

                        val attrs = if (g1.isNotEmpty()) g1 else g2
                        val innerContent = g3

                        val rMatch = rAttrRegex.find(attrs)
                        if (rMatch != null) {
                            val colLetter = rMatch.groupValues[1]
                            val colIndex = columnLetterToIndex(colLetter)
                            if (colIndex > maxColIdx) {
                                maxColIdx = colIndex
                            }

                            val tMatch = tAttrRegex.find(attrs)
                            val cellType = tMatch?.groupValues[1] ?: ""

                            var cellText = ""
                            if (innerContent.isNotEmpty()) {
                                val vMatch = vRegex.find(innerContent)
                                val vVal = vMatch?.groupValues[1] ?: ""

                                cellText = when {
                                    cellType == "s" && vVal.isNotEmpty() -> {
                                        val idx = vVal.toIntOrNull()
                                        if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else vVal
                                    }
                                    vVal.isNotEmpty() -> vVal
                                    else -> ""
                                }
                            }
                            parsedCells.add(Pair(colIndex, sanitizeText(cellText)))
                        }
                    }

                    if (maxColIdx >= 0) {
                        val rowCells = Array(maxColIdx + 1) { "" }
                        var hasData = false
                        for ((colIndex, text) in parsedCells) {
                            rowCells[colIndex] = text
                            if (text.isNotEmpty()) {
                                hasData = true
                            }
                        }
                        if (hasData) {
                            sheetRows.add(rowCells.joinToString("\t"))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (com.example.BuildConfig.DEBUG) {
                android.util.Log.e("StatementImportService", "Failed to parse XLSX", e)
            }
        }

        return if (sheetRows.isNotEmpty()) sheetRows.joinToString("\n") else String(bytes, Charsets.UTF_8)
    }

    private fun decompressFlate(bytes: ByteArray): ByteArray? {
        try {
            val inflater = java.util.zip.Inflater(false)
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            inflater.setInput(bytes)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count <= 0) break
                out.write(buffer, 0, count)
            }
            inflater.end()
            val res = out.toByteArray()
            if (res.isNotEmpty()) return res
        } catch (_: Throwable) {}

        try {
            val inflaterNowrap = java.util.zip.Inflater(true)
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            inflaterNowrap.setInput(bytes)
            while (!inflaterNowrap.finished()) {
                val count = inflaterNowrap.inflate(buffer)
                if (count <= 0) break
                out.write(buffer, 0, count)
            }
            inflaterNowrap.end()
            val res = out.toByteArray()
            if (res.isNotEmpty()) return res
        } catch (_: Throwable) {}

        return null
    }

    private fun extractTextFromPdfBytes(context: Context?, bytes: ByteArray, password: String? = null): String {
        // 1. Try high-fidelity PDFBox extraction if available
        try {
            if (context != null) {
                try {
                    if (!PDFBoxResourceLoader.isReady()) {
                        PDFBoxResourceLoader.init(context.applicationContext)
                    }
                } catch (_: Throwable) {}
            }
            val document = if (!password.isNullOrBlank()) {
                PDDocument.load(ByteArrayInputStream(bytes), password)
            } else {
                PDDocument.load(ByteArrayInputStream(bytes))
            }
            document.use { doc ->
                if (doc.isEncrypted) {
                    try {
                        doc.setAllSecurityToBeRemoved(true)
                    } catch (_: Throwable) {}
                }
                val stripper = PDFTextStripper()
                stripper.sortByPosition = true
                stripper.startPage = 1
                stripper.endPage = doc.numberOfPages
                val text = stripper.getText(doc)
                if (text.isNotBlank()) {
                    return text
                }
            }
        } catch (e: Throwable) {
            val msg = e.message?.lowercase() ?: ""
            if (msg.contains("password") || msg.contains("encrypted") || e.javaClass.simpleName.contains("Password", ignoreCase = true)) {
                throw SecurityException("PDF_PASSWORD_REQUIRED")
            }
        }

        // 2. High-precision standalone CMap & Flate stream PDF extractor (Zero Android/JVM dependencies)
        val decompressedStreams = mutableListOf<Pair<Int, String>>()
        val streamIndices = mutableListOf<Int>()
        val endStreamIndices = mutableListOf<Int>()

        var i = 0
        while (i <= bytes.size - 9) {
            if (bytes[i] == 's'.code.toByte() && bytes[i+1] == 't'.code.toByte() && bytes[i+2] == 'r'.code.toByte() && bytes[i+3] == 'e'.code.toByte() && bytes[i+4] == 'a'.code.toByte() && bytes[i+5] == 'm'.code.toByte()) {
                streamIndices.add(i)
                i += 6
                continue
            }
            if (bytes[i] == 'e'.code.toByte() && bytes[i+1] == 'n'.code.toByte() && bytes[i+2] == 'd'.code.toByte() && bytes[i+3] == 's'.code.toByte() && bytes[i+4] == 't'.code.toByte() && bytes[i+5] == 'r'.code.toByte() && bytes[i+6] == 'e'.code.toByte() && bytes[i+7] == 'a'.code.toByte() && bytes[i+8] == 'm'.code.toByte()) {
                endStreamIndices.add(i)
                i += 9
                continue
            }
            i++
        }

        for ((idx, sStart) in streamIndices.withIndex()) {
            var contentStart = sStart + 6
            while (contentStart < bytes.size && (bytes[contentStart] == '\r'.code.toByte() || bytes[contentStart] == '\n'.code.toByte())) {
                contentStart++
            }
            val contentEnd = endStreamIndices.firstOrNull { it > contentStart } ?: continue
            var endPos = contentEnd
            while (endPos > contentStart && (bytes[endPos - 1] == '\r'.code.toByte() || bytes[endPos - 1] == '\n'.code.toByte())) {
                endPos--
            }
            if (endPos <= contentStart) continue
            val streamChunk = bytes.copyOfRange(contentStart, endPos)
            val decomp = decompressFlate(streamChunk)
            if (decomp != null && decomp.isNotEmpty()) {
                decompressedStreams.add(Pair(idx, String(decomp, Charsets.ISO_8859_1)))
            }
        }

        val cmap = mutableMapOf<Int, String>()
        for ((_, text) in decompressedStreams) {
            if (text.contains("begincmap") || text.contains("beginbfchar") || text.contains("beginbfrange")) {
                val bfcharRegex = Regex("<([0-9a-fA-F]+)>\\s*<([0-9a-fA-F]+)>")
                for (m in bfcharRegex.findAll(text)) {
                    val src = m.groupValues[1].toIntOrNull(16) ?: continue
                    val dst = m.groupValues[2].toIntOrNull(16) ?: continue
                    cmap[src] = dst.toChar().toString()
                }
                val bfrangeRegex = Regex("<([0-9a-fA-F]+)>\\s*<([0-9a-fA-F]+)>\\s*<([0-9a-fA-F]+)>")
                for (m in bfrangeRegex.findAll(text)) {
                    val sVal = m.groupValues[1].toIntOrNull(16) ?: continue
                    val eVal = m.groupValues[2].toIntOrNull(16) ?: continue
                    val dVal = m.groupValues[3].toIntOrNull(16) ?: continue
                    for (off in 0..(eVal - sVal)) {
                        cmap[sVal + off] = (dVal + off).toChar().toString()
                    }
                }
            }
        }

        fun decodeToken(token: String): String {
            return if (token.startsWith("<") && token.endsWith(">")) {
                val hex = token.substring(1, token.length - 1).replace("\\s+".toRegex(), "")
                val sb = StringBuilder()
                var j = 0
                while (j + 4 <= hex.length) {
                    val code = hex.substring(j, j + 4).toIntOrNull(16)
                    if (code != null) {
                        sb.append(cmap[code] ?: (if (code in 32..126) code.toChar().toString() else ""))
                    }
                    j += 4
                }
                sb.toString()
            } else if (token.startsWith("(") && token.endsWith(")")) {
                token.substring(1, token.length - 1)
                    .replace("\\(", "(")
                    .replace("\\)", ")")
                    .replace("\\n", " ")
                    .replace("\\r", "")
                    .replace("\\t", " ")
                    .replace("\\\\", "\\")
            } else {
                token
            }
        }

        data class PdfTextItem(val streamId: Int, val y: Float, val x: Float, val text: String)
        val items = mutableListOf<PdfTextItem>()

        val btRegex = Regex("BT(.*?)ET", RegexOption.DOT_MATCHES_ALL)
        val tdRegex = Regex("([0-9.-]+)\\s+([0-9.-]+)\\s+Td")
        val tmRegex = Regex("[0-9.-]+\\s+[0-9.-]+\\s+[0-9.-]+\\s+[0-9.-]+\\s+([0-9.-]+)\\s+([0-9.-]+)\\s+Tm")
        val tjRegex = Regex("(<[0-9a-fA-F\\s]+>|\\((?:[^()\\\\]|\\\\.)*\\))\\s*Tj")

        for ((sid, streamText) in decompressedStreams) {
            for (bt in btRegex.findAll(streamText)) {
                val block = bt.groupValues[1]
                val tdMatch = tdRegex.find(block)
                val tmMatch = if (tdMatch == null) tmRegex.find(block) else null
                val x = tdMatch?.groupValues?.get(1)?.toFloatOrNull() ?: (tmMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f)
                val y = tdMatch?.groupValues?.get(2)?.toFloatOrNull() ?: (tmMatch?.groupValues?.get(2)?.toFloatOrNull() ?: 0f)

                val blockTokens = mutableListOf<String>()
                for (m in tjRegex.findAll(block)) {
                    val decoded = decodeToken(m.groupValues[1])
                    if (decoded.isNotBlank()) {
                        blockTokens.add(decoded)
                    }
                }
                if (blockTokens.isNotEmpty()) {
                    items.add(PdfTextItem(sid, y, x, blockTokens.joinToString(" ")))
                }
            }
        }

        val sortedItems = items.sortedWith(compareBy({ it.streamId }, { -it.y }, { it.x }))
        val extractedLines = mutableListOf<String>()
        var currentSid: Int? = null
        var currentY: Float? = null
        val currentLineTokens = mutableListOf<String>()

        for (item in sortedItems) {
            if (currentSid != item.streamId || currentY == null || kotlin.math.abs(currentY - item.y) > 3.0f) {
                if (currentLineTokens.isNotEmpty()) {
                    extractedLines.add(currentLineTokens.joinToString(" "))
                    currentLineTokens.clear()
                }
                currentSid = item.streamId
                currentY = item.y
            }
            currentLineTokens.add(item.text)
        }
        if (currentLineTokens.isNotEmpty()) {
            extractedLines.add(currentLineTokens.joinToString(" "))
        }

        return if (extractedLines.isNotEmpty()) extractedLines.joinToString("\n") else String(bytes, Charsets.ISO_8859_1)
    }

    private fun sanitizeText(input: String): String {
        if (input.isBlank()) return ""
        val cleaned = input.replace("[^\\x20-\\x7E]".toRegex(), " ").trim()
        val collapsed = cleaned.replace("\\s+".toRegex(), " ")
        return if (collapsed.any { it.isLetterOrDigit() }) collapsed else ""
    }

    fun detectBank(content: String, fileName: String = ""): SupportedBank {
        val lowerContent = content.lowercase()
        val lowerName = fileName.lowercase()

        return when {
            lowerContent.contains("indian bank") || lowerContent.contains("indbank") ||
                    lowerContent.contains("idib0") || lowerContent.contains("corporate office: 254-260") ||
                    lowerName.contains("indian_bank") || lowerName.contains("indianbank") || lowerName.contains("indbank") -> {
                SupportedBank.INDIAN_BANK
            }
            lowerContent.contains("state bank") || lowerContent.contains("sbi") ||
                    lowerContent.contains("sbin0") || lowerName.contains("sbi") || lowerName.contains("state_bank") -> {
                SupportedBank.STATE_BANK
            }
            lowerContent.contains("hdfc") || lowerContent.contains("hdfc0") || lowerName.contains("hdfc") -> {
                SupportedBank.HDFC_BANK
            }
            lowerContent.contains("icici") || lowerContent.contains("icic0") || lowerName.contains("icici") -> {
                SupportedBank.ICICI_BANK
            }
            lowerContent.contains("axis bank") || lowerContent.contains("utib0") || lowerName.contains("axis") -> {
                SupportedBank.AXIS_BANK
            }
            lowerContent.contains("kotak") || lowerContent.contains("kmbl0") || lowerName.contains("kotak") -> {
                SupportedBank.KOTAK_BANK
            }
            lowerContent.contains("yes bank") || lowerContent.contains("yes touch") ||
                    lowerContent.contains("yesbank") || lowerContent.contains("yes first") ||
                    lowerContent.contains("smart salary") || lowerContent.contains("cin - l65190mh2003plc143249") ||
                    lowerName.contains("yes_bank") || lowerName.contains("yesbank") -> {
                SupportedBank.YES_BANK
            }
            lowerContent.contains("indusind") || lowerContent.contains("indb0") ||
                    lowerName.contains("indusind") -> {
                SupportedBank.INDUSIND_BANK
            }
            lowerContent.contains("canara") || lowerContent.contains("cnrb0") || lowerName.contains("canara") -> {
                SupportedBank.CANARA_BANK
            }
            lowerContent.contains("bank of baroda") || lowerContent.contains("bob") || lowerContent.contains("barb0") || lowerName.contains("baroda") -> {
                SupportedBank.BANK_OF_BARODA
            }
            lowerContent.contains("union bank") || lowerContent.contains("ubin0") || lowerName.contains("union") -> {
                SupportedBank.UNION_BANK
            }
            lowerContent.contains("punjab national") || lowerContent.contains("pnb") || lowerContent.contains("punb0") || lowerName.contains("pnb") -> {
                SupportedBank.PNB
            }
            lowerContent.contains("federal bank") || lowerContent.contains("fdrl0") || lowerName.contains("federal") -> {
                SupportedBank.FEDERAL_BANK
            }
            else -> SupportedBank.UNKNOWN
        }
    }

    fun auditParsedStatement(
        detectedBank: SupportedBank,
        sourceRowsCount: Int,
        parsedRows: List<ParsedImportRow>,
        openingBalance: Double?,
        closingBalance: Double?
    ): StatementAuditReport {
        val totalDebits = parsedRows.sumOf { it.debitAmount }
        val totalCredits = parsedRows.sumOf { it.creditAmount }
        val discrepancies = mutableListOf<AuditDiscrepancy>()

        // 1. Transaction count audit
        if (sourceRowsCount > 0 && parsedRows.size < sourceRowsCount / 2 && sourceRowsCount > 4) {
            discrepancies.add(
                AuditDiscrepancy(
                    rowIndex = 0,
                    field = "Transaction Count",
                    expected = "$sourceRowsCount raw rows",
                    actual = "${parsedRows.size} parsed",
                    reason = "Significant difference between detected raw statement lines and valid parsed rows"
                )
            )
        }

        // 2. Sequential running balance reconciliation
        var prevBalance: Double? = openingBalance
        for (i in parsedRows.indices) {
            val row = parsedRows[i]
            if (row.balance != null) {
                if (prevBalance != null) {
                    val expectedBal = prevBalance - row.debitAmount + row.creditAmount
                    val diff = Math.abs(row.balance - expectedBal)
                    if (diff > 0.05) {
                        discrepancies.add(
                            AuditDiscrepancy(
                                rowIndex = i + 1,
                                field = "Running Balance",
                                expected = String.format(Locale.ENGLISH, "₹%.2f", expectedBal),
                                actual = String.format(Locale.ENGLISH, "₹%.2f", row.balance),
                                reason = "Balance step discrepancy: previous (₹${String.format(Locale.ENGLISH, "%.2f", prevBalance)}) - debit (${row.debitAmount}) + credit (${row.creditAmount}) does not match recorded balance (₹${row.balance})"
                            )
                        )
                    }
                }
                prevBalance = row.balance
            }
        }

        // 3. Opening to Closing balance reconciliation
        var calculatedClosing: Double? = null
        var balanceReconciliationPassed: Boolean? = null

        if (openingBalance != null && closingBalance != null) {
            val calc = openingBalance - totalDebits + totalCredits
            calculatedClosing = calc
            val diff = Math.abs(calc - closingBalance)
            if (diff > 0.05) {
                balanceReconciliationPassed = false
                discrepancies.add(
                    AuditDiscrepancy(
                        rowIndex = 0,
                        field = "Closing Balance",
                        expected = String.format(Locale.ENGLISH, "₹%.2f", closingBalance),
                        actual = String.format(Locale.ENGLISH, "₹%.2f", calc),
                        reason = "Opening balance (₹${String.format(Locale.ENGLISH, "%.2f", openingBalance)}) - total debits (₹${String.format(Locale.ENGLISH, "%.2f", totalDebits)}) + total credits (₹${String.format(Locale.ENGLISH, "%.2f", totalCredits)}) = ₹${String.format(Locale.ENGLISH, "%.2f", calc)}, mismatch with statement closing balance ₹${String.format(Locale.ENGLISH, "%.2f", closingBalance)}"
                    )
                )
            } else {
                balanceReconciliationPassed = true
            }
        } else if (parsedRows.any { it.balance != null }) {
            balanceReconciliationPassed = discrepancies.none { it.field == "Running Balance" }
        }

        // 4. Duplicate checks
        val duplicates = parsedRows.count { it.isDuplicate }
        if (duplicates > 0) {
            discrepancies.add(
                AuditDiscrepancy(
                    rowIndex = 0,
                    field = "Duplicates",
                    expected = "0 duplicates",
                    actual = "$duplicates duplicate rows",
                    reason = "Possible duplicate transactions matching existing account records"
                )
            )
        }

        // 5. Determine audit status
        val status = when {
            discrepancies.isNotEmpty() -> StatementAuditStatus.REVIEW_REQUIRED
            openingBalance == null && closingBalance == null && parsedRows.none { it.balance != null } -> StatementAuditStatus.INCOMPLETE
            else -> StatementAuditStatus.PASS
        }

        return StatementAuditReport(
            status = status,
            detectedBank = detectedBank,
            totalSourceRowsDetected = if (sourceRowsCount > 0) sourceRowsCount else parsedRows.size,
            totalParsed = parsedRows.size,
            validCount = parsedRows.size,
            duplicateCount = duplicates,
            openingBalance = openingBalance,
            closingBalance = closingBalance,
            calculatedClosingBalance = calculatedClosing,
            totalDebits = totalDebits,
            totalCredits = totalCredits,
            balanceReconciliationPassed = balanceReconciliationPassed,
            discrepancies = discrepancies
        )
    }

    fun parseStatementText(
        content: String,
        rules: List<CategorizationRuleEntity> = emptyList(),
        existingTransactions: List<TransactionEntity> = emptyList(),
        fileName: String = "statement.csv",
        bankOverride: SupportedBank? = null
    ): ImportPreviewResult {
        if (content.isBlank()) {
            return ImportPreviewResult(0, emptyList(), 0, emptyList(), fileName)
        }

        val detectedBank = bankOverride ?: detectBank(content, fileName)

        val rawLines = content.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (rawLines.isEmpty()) {
            return ImportPreviewResult(0, emptyList(), 0, emptyList(), fileName, detectedBank)
        }

        // Find the first line that matches the header criteria
        var headerIndex = -1
        for (i in rawLines.indices) {
            if (isHeaderLine(rawLines[i])) {
                headerIndex = i
                break
            }
        }

        val isPdfFile = fileName.lowercase().endsWith(".pdf")
        val rawDelimiter = if (headerIndex != -1) detectDelimiter(rawLines[headerIndex]) else detectDelimiter(rawLines.first())
        val delimiter = if (isPdfFile) "" else rawDelimiter

        val detectedColumns = if (headerIndex != -1 && delimiter.isNotEmpty()) {
            detectColumns(rawLines[headerIndex], delimiter)
        } else if (delimiter.isNotEmpty()) {
            detectColumns(rawLines.first(), delimiter)
        } else {
            emptyList()
        }

        val dataLines = if (headerIndex != -1) {
            rawLines.drop(headerIndex + 1)
        } else {
            rawLines
        }

        val consolidatedRows = mutableListOf<String>()

        if (delimiter.isNotEmpty()) {
            // Structured delimiter (e.g. TSV from Excel or CSV)
            for (line in dataLines) {
                if (!isHeaderLine(line) && !isIgnoredOrTotalLine(line)) {
                    consolidatedRows.add(line)
                }
            }
        } else {
            // Unstructured / PDF text extraction
            val dateRegex = Regex("(\\d{1,2}[/-](?:[A-Za-z]{3,9}|\\d{1,2})[/-]\\d{2,4}|\\d{4}[/-]\\d{1,2}[/-]\\d{1,2})")
            val amountRegex = Regex("([0-9]{1,3}(?:,[0-9]{3})*\\.[0-9]{2})")

            val cleanLines = dataLines.filter { !isHeaderLine(it) && !isIgnoredOrTotalLine(it) }
            val currentBlock = mutableListOf<String>()

            for (line in cleanLines) {
                val datesInLine = dateRegex.findAll(line).toList()
                val startsWithDate = line.matches(Regex("^\\s*\\d{1,2}[/-](?:[A-Za-z]{3,9}|\\d{1,2})[/-]\\d{2,4}.*", RegexOption.DOT_MATCHES_ALL)) ||
                        line.matches(Regex("^\\s*\\d{4}[/-]\\d{1,2}[/-]\\d{1,2}.*", RegexOption.DOT_MATCHES_ALL))
                val hasDateInCurrent = currentBlock.any { dateRegex.containsMatchIn(it) }
                val hasAmountInCurrent = currentBlock.any { amountRegex.containsMatchIn(it) }

                var isNewTxnStart = false
                if (currentBlock.isNotEmpty()) {
                    if (hasDateInCurrent && hasAmountInCurrent) {
                        if (startsWithDate || datesInLine.isNotEmpty() || line.startsWith("UPI/") || line.startsWith("NEFT") ||
                            line.startsWith("IMPS") || line.startsWith("RTGS") || line.contains("Cr-") || line.contains("OUTUPI")) {
                            isNewTxnStart = true
                        }
                    }
                }

                if (isNewTxnStart) {
                    consolidatedRows.add(currentBlock.joinToString(" "))
                    currentBlock.clear()
                }
                currentBlock.add(line)
            }
            if (currentBlock.isNotEmpty()) {
                consolidatedRows.add(currentBlock.joinToString(" "))
            }
        }

        val parsedRows = mutableListOf<ParsedImportRow>()
        for (line in consolidatedRows) {
            val row = parseLine(line, delimiter, detectedColumns, rules, existingTransactions)
            if (row != null && row.description.isNotBlank() && row.amount > 0.0) {
                parsedRows.add(row)
            }
        }

        // Opening & Closing Balance Extraction
        var openingBal: Double? = null
        val openingBalMatch = Regex("(?:Opening|Brought Forward|B/F)\\s*(?:Balance)?\\s*[:\\-]?\\s*(?:INR|Rs\\.?|₹)?\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{2})|[0-9]+\\.[0-9]{2})", RegexOption.IGNORE_CASE).find(content)
        if (openingBalMatch != null) {
            openingBal = parseAmount(openingBalMatch.groupValues[1])
        }

        var closingBal: Double? = null
        val closingBalMatch = Regex("(?:Closing|Carried Forward|C/F)\\s*(?:Balance)?\\s*[:\\-]?\\s*(?:INR|Rs\\.?|₹)?\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{2})|[0-9]+\\.[0-9]{2})", RegexOption.IGNORE_CASE).find(content)
        if (closingBalMatch != null) {
            closingBal = parseAmount(closingBalMatch.groupValues[1])
        }

        // 2-Pass Balance Delta Mathematical Verification:
        var runningBal: Double? = openingBal

        val finalizedRows = mutableListOf<ParsedImportRow>()
        var duplicates = 0

        for (row in parsedRows) {
            var finalDebit = row.debitAmount
            var finalCredit = row.creditAmount
            var finalType = row.suggestedType

            if (row.balance != null && runningBal != null) {
                val diff = row.balance - runningBal
                if (diff > 0.009) {
                    // Balance increased -> Definitive Income / Credit!
                    finalCredit = if (finalCredit > 0.0) finalCredit else diff
                    finalDebit = 0.0
                    finalType = TransactionType.INCOME
                } else if (diff < -0.009) {
                    // Balance decreased -> Definitive Expense / Debit!
                    finalDebit = if (finalDebit > 0.0) finalDebit else Math.abs(diff)
                    finalCredit = 0.0
                    finalType = TransactionType.EXPENSE
                }
            }
            if (row.balance != null) {
                runningBal = row.balance
            }

            val updatedCatResult = if (rules.isNotEmpty()) {
                CategorizationEngine.categorize(row.description, rules, finalType == TransactionType.INCOME)
            } else null

            val finalCategory = updatedCatResult?.categoryName ?: row.suggestedCategory
            val finalCategoryId = updatedCatResult?.categoryId ?: row.suggestedCategoryId
            val finalConfidence = updatedCatResult?.confidence ?: row.confidence

            val finalAmount = if (finalDebit > 0.0) finalDebit else finalCredit
            val isDup = existingTransactions.any {
                it.transactionDate == row.date &&
                        it.description.equals(row.description, ignoreCase = true) &&
                        Math.abs(it.debitAmount - finalDebit) < 0.01 &&
                        Math.abs(it.creditAmount - finalCredit) < 0.01
            }
            if (isDup) duplicates++

            finalizedRows.add(row.copy(
                debitAmount = finalDebit,
                creditAmount = finalCredit,
                amount = finalAmount,
                suggestedType = finalType,
                suggestedCategory = finalCategory,
                suggestedCategoryId = finalCategoryId,
                confidence = finalConfidence,
                isDuplicate = isDup
            ))
        }

        val auditReport = auditParsedStatement(
            detectedBank = detectedBank,
            sourceRowsCount = consolidatedRows.size,
            parsedRows = finalizedRows,
            openingBalance = openingBal,
            closingBalance = closingBal
        )

        val flaggedRowIndices = auditReport.discrepancies.map { it.rowIndex }.filter { it > 0 }.toSet()
        val auditedRows = if (flaggedRowIndices.isNotEmpty()) {
            finalizedRows.mapIndexed { idx, row ->
                if (flaggedRowIndices.contains(idx + 1)) row.copy(hasDiscrepancy = true) else row
            }
        } else {
            finalizedRows
        }

        if (auditedRows.isEmpty()) {
            val fallback = parseFallbackUnstructured(content, rules, existingTransactions, fileName)
            if (fallback.validRows.isNotEmpty()) {
                return fallback
            }
        }

        return ImportPreviewResult(
            totalRows = auditedRows.size,
            validRows = auditedRows,
            duplicateCount = duplicates,
            detectedColumns = detectedColumns,
            fileName = fileName,
            detectedBank = detectedBank,
            auditReport = auditReport
        )
    }

    fun parseFallbackUnstructured(
        content: String,
        rules: List<CategorizationRuleEntity> = emptyList(),
        existingTransactions: List<TransactionEntity> = emptyList(),
        fileName: String = "statement.csv"
    ): ImportPreviewResult {
        if (content.isBlank()) return ImportPreviewResult(0, emptyList(), 0, emptyList(), fileName)

        val dateRegex = Regex("(\\d{1,2}[/-](?:[A-Za-z]{3,9}|\\d{1,2})[/-]\\d{2,4}|\\d{4}[/-]\\d{1,2}[/-]\\d{1,2})")
        val amountRegex = Regex("([0-9]{1,3}(?:,[0-9]{3})*\\.[0-9]{2}|[0-9]+\\.[0-9]{2})")

        val rawLines = content.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !isHeaderLine(it) && !isIgnoredOrTotalLine(it) }

        val parsedRows = mutableListOf<ParsedImportRow>()
        var duplicates = 0

        for (line in rawLines) {
            val dateMatch = dateRegex.find(line) ?: continue
            val rawDate = dateMatch.value
            val normDate = normalizeDate(rawDate)
            if (!isValidNormalizedDate(normDate)) continue

            val amounts = amountRegex.findAll(line).map { it.value }.toList()
            if (amounts.isEmpty()) continue

            val hasCredit = line.contains("Cr", ignoreCase = true) || line.contains("Credit", ignoreCase = true) ||
                    line.contains("Deposit", ignoreCase = true) || line.contains("Refund", ignoreCase = true) ||
                    line.contains("Salary", ignoreCase = true) || line.contains("Received", ignoreCase = true)

            val hasDebit = line.contains("Dr", ignoreCase = true) || line.contains("Debit", ignoreCase = true) ||
                    line.contains("Withdrawal", ignoreCase = true) || line.contains("Paid", ignoreCase = true) ||
                    line.contains("Transfer to", ignoreCase = true) || line.contains("POS", ignoreCase = true)

            val (debit, credit, balance) = if (amounts.size >= 2) {
                val bal = parseAmount(amounts.last())
                val amt = parseAmount(amounts[amounts.size - 2])
                if (hasCredit && !hasDebit) {
                    Triple(0.0, amt, bal)
                } else {
                    Triple(amt, 0.0, bal)
                }
            } else {
                val amt = parseAmount(amounts.first())
                if (hasCredit && !hasDebit) {
                    Triple(0.0, amt, null)
                } else {
                    Triple(amt, 0.0, null)
                }
            }

            val finalAmount = if (debit > 0.0) debit else credit
            if (finalAmount <= 0.0) continue

            // Clean description by stripping the date and amounts out of the line
            var desc = line
            desc = desc.replace(rawDate, " ")
            for (amtStr in amounts) {
                desc = desc.replace(amtStr, " ")
            }
            desc = sanitizeText(desc)
            if (desc.isBlank()) {
                desc = "Bank Transaction"
            }

            val txType = if (credit > 0.0) TransactionType.INCOME else TransactionType.EXPENSE
            val catResult = CategorizationEngine.categorize(desc, rules, txType == TransactionType.INCOME)
            val finalCategory = if (!catResult.categoryName.equals("Uncategorized", ignoreCase = true)) {
                catResult.categoryName
            } else {
                "General"
            }

            val isDup = existingTransactions.any {
                it.transactionDate == normDate &&
                        it.description.equals(desc, ignoreCase = true) &&
                        Math.abs(it.amount - finalAmount) < 0.01
            }
            if (isDup) duplicates++

            parsedRows.add(
                ParsedImportRow(
                    rawLine = line,
                    date = normDate,
                    description = desc,
                    debitAmount = debit,
                    creditAmount = credit,
                    amount = finalAmount,
                    balance = balance,
                    referenceNumber = "",
                    suggestedCategory = finalCategory,
                    suggestedCategoryId = catResult.categoryId,
                    suggestedType = txType,
                    confidence = 0.85f,
                    isDuplicate = isDup
                )
            )
        }

        return ImportPreviewResult(
            totalRows = parsedRows.size,
            validRows = parsedRows,
            duplicateCount = duplicates,
            detectedColumns = listOf("Date", "Description", "Debit", "Credit", "Balance"),
            fileName = "$fileName (Smart Fallback)",
            detectedBank = SupportedBank.UNKNOWN
        )
    }

    private fun detectDelimiter(header: String): String {
        return when {
            header.contains("\t") -> "\t"
            header.contains("|") -> "|"
            header.contains(";") -> ";"
            header.contains(",") -> ","
            else -> ""
        }
    }

    private fun detectColumns(header: String, delimiter: String): List<String> {
        val cols = if (delimiter.isNotEmpty()) {
            header.split(delimiter).map { sanitizeText(it) }
        } else {
            header.split("\\s{2,}".toRegex()).map { sanitizeText(it) }.filter { it.isNotBlank() }
        }
        if (cols.size > 1) return cols
        return listOf("Transaction Date", "Value Date", "Reference No", "Description", "Withdrawals", "Deposits", "Running Balance")
    }

    private fun isHeaderLine(line: String): Boolean {
        val lower = line.lowercase().trim()
        val hasDate = lower.contains("date") || lower.contains("txn dt") || lower.contains("value dt") || lower.contains("txn date")
        val hasDesc = lower.contains("description") || lower.contains("particular") || lower.contains("narration") || lower.contains("remark") || lower.contains("transaction details")
        val hasAmount = lower.contains("debit") || lower.contains("credit") || lower.contains("withdrawal") || lower.contains("deposit") || lower.contains("paid out") || lower.contains("paid in") || lower.contains("dr") || lower.contains("cr")
        val hasBalance = lower.contains("balance") || lower.contains("running") || lower.contains("closing")
        return (hasDate && hasDesc && (hasAmount || hasBalance)) || (hasDesc && hasAmount && hasBalance)
    }

    private fun isIgnoredOrTotalLine(line: String, description: String = ""): Boolean {
        val lowerLine = line.lowercase().trim()
        val lowerDesc = description.lowercase().trim()

        val ignoredKeywords = listOf(
            "opening balance", "closing balance", "running balance", "total withdrawals", "total deposits",
            "total debit", "total credit", "total debits", "total credits", "total amount",
            "total value", "total sum", "grand total", "sub total", "subtotal", "running total",
            "brought forward", "carried forward", "b/f", "c/f", "balance b/f", "balance c/f",
            "statement of account", "statement period", "transaction details for", "statement summary",
            "account status", "account variant", "smart salary", "primary holder", "nominee details",
            "joint holder", "od limit", "uncleared amount", "sweep in", "mandatory disclaimer",
            "transaction codes in your account", "page 1 of", "page 2 of", "page 3 of", "page of",
            "reward points", "to redeem your rewardz", "yes touch", "phonebanking number",
            "cin -", "branch details", "ifsc code", "micr code", "customer id", "cust id",
            "registered email", "mobile no", "end of statement", "computer generated",
            "authorized signatory", "disclaimer", "total inflow", "total outflow", "net balance",
            "summary for the period", "account statement", "e-statement", "benefits of nomination",
            "financial security", "hassle-free fund transfer", "time saving & cost effective",
            "please ignore if nominee", "under goods and services tax", "for any assistance required",
            "please contact a yes bank", "unless the discrepancy",
            "adikesavalu naidu complex", "greams rd", "thousand light", "door no. 225",
            "indian bank", "indbank", "corporate office: 254-260", "avvai shanmugam salai", "royapettah",
            "chennai - 600014", "scheme description", "cif no", "branch ifsc", "yes bank limited",
            "yes bank ltd", "yes first", "neft / rtgs ifsc : yesb", "cin: l65190mh2003plc143249",
            "atm withdrawal obd", "mobile funds transfer", "atm funds transfer", "returned rtgs", "rtgs transaction"
        )

        for (kw in ignoredKeywords) {
            if (lowerLine.contains(kw) || lowerDesc.contains(kw)) {
                return true
            }
        }

        // Header / meta description matching
        if (lowerDesc in listOf("description", "particulars", "narration", "remarks", "details", "transaction details", "txn details", "date", "amount", "total", "totals", "balance", "running", "withdrawals", "deposits")) {
            return true
        }

        if (lowerLine in listOf("rooparaj b", "platinum", "savings", "currency - inr", "active", "na", "balance", "running", "transaction date", "date", "description", "withdrawals", "deposits")) {
            return true
        }

        if (lowerDesc.matches(Regex("^total\\s*[:\\-]?.*$", RegexOption.IGNORE_CASE)) && !lowerDesc.contains("total energies") && !lowerDesc.contains("total gas")) {
            return true
        }

        if (lowerLine.startsWith("neft: national electronic") || lowerLine.startsWith("neft: national") || lowerLine.startsWith("upi: unified payment")) {
            return true
        }

        return false
    }

    private fun parseLine(
        line: String,
        delimiter: String,
        detectedCols: List<String>,
        rules: List<CategorizationRuleEntity>,
        existingTxs: List<TransactionEntity>
    ): ParsedImportRow? {
        if (isHeaderLine(line) || isIgnoredOrTotalLine(line)) {
            return null
        }

        val tokens = if (delimiter.isNotEmpty()) {
            line.split(delimiter).map { sanitizeText(it.trim().trim('"')) }
        } else {
            line.split("\\s{2,}".toRegex()).map { sanitizeText(it) }.filter { it.isNotBlank() }
        }

        if (tokens.isEmpty()) return null

        var date = ""
        var description = ""
        var debit = 0.0
        var credit = 0.0
        var balance: Double? = null
        var ref = ""
        var explicitCategory: String? = null

        if (delimiter.isNotEmpty() && tokens.size >= 2) {
            var dateIdx = -1
            var descIdx = -1
            var debitIdx = -1
            var creditIdx = -1
            var balanceIdx = -1
            var refIdx = -1
            var categoryIdx = -1

            for (i in detectedCols.indices) {
                val col = detectedCols[i].lowercase().trim()
                when {
                    (col.contains("txn") && col.contains("date")) || (col.contains("trans") && col.contains("date")) || (col == "date") -> dateIdx = i
                    dateIdx == -1 && (col.contains("date") || col.contains("txn dt")) && !col.contains("value") -> dateIdx = i
                    descIdx == -1 && (col.contains("desc") || col.contains("particular") || col.contains("narration") || col.contains("remark") || col.contains("detail")) -> descIdx = i
                    debitIdx == -1 && (col.contains("withdrawal") || col.contains("debit") || col.contains("paid out") || col.contains("dr")) && !col.contains("credit") -> debitIdx = i
                    creditIdx == -1 && (col.contains("deposit") || col.contains("credit") || col.contains("paid in") || col.contains("cr")) && !col.contains("debit") -> creditIdx = i
                    balanceIdx == -1 && (col.contains("balance") || col.contains("closing") || col.contains("bal") || col.contains("running")) -> balanceIdx = i
                    refIdx == -1 && (col.contains("ref") || col.contains("utr") || col.contains("chq") || col.contains("cheque") || col.contains("trans id") || col.contains("txn id") || col.contains("cheque no")) -> refIdx = i
                    categoryIdx == -1 && (col.contains("category") || col.contains("cat")) -> categoryIdx = i
                }
            }

            if (dateIdx == -1) dateIdx = 0
            if (descIdx == -1) descIdx = if (tokens.size >= 4) 3 else 1
            if (debitIdx == -1 && tokens.size >= 5) debitIdx = 4
            if (creditIdx == -1 && tokens.size >= 6) creditIdx = 5
            if (balanceIdx == -1 && tokens.size >= 7) balanceIdx = 6

            date = if (dateIdx in tokens.indices) normalizeDate(tokens[dateIdx]) else ""
            description = if (descIdx in tokens.indices) sanitizeText(tokens[descIdx]) else ""
            debit = if (debitIdx in tokens.indices) parseAmount(tokens[debitIdx]) else 0.0
            credit = if (creditIdx in tokens.indices) parseAmount(tokens[creditIdx]) else 0.0
            balance = if (balanceIdx in tokens.indices) parseAmountOrNull(tokens[balanceIdx]) else null
            if (refIdx != -1 && refIdx in tokens.indices) ref = sanitizeText(tokens[refIdx])
            if (categoryIdx != -1 && categoryIdx in tokens.indices) explicitCategory = tokens[categoryIdx].takeIf { it.isNotBlank() }
        }

        // Positional fallback for space-separated lines (e.g. Yes Bank, SBI, HDFC statements)
        if (!isValidNormalizedDate(date) || (debit == 0.0 && credit == 0.0)) {
            val dateRegex = Regex("(\\d{1,2}[/-](?:[A-Za-z]{3,9}|\\d{1,2})[/-]\\d{2,4}|\\d{4}[/-]\\d{1,2}[/-]\\d{1,2})")
            val allDates = dateRegex.findAll(line).map { it.value }.toList()
            if (allDates.isNotEmpty()) {
                date = normalizeDate(allDates.first())
            }

            val numberRegex = Regex("([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{2})|[0-9]+\\.[0-9]{2})")
            var allNumberMatches = numberRegex.findAll(line).map { it.value }.toList()

            // Handle concatenated amounts like 15,000.0015,000.42
            if (allNumberMatches.size < 2) {
                val concatMatch = Regex("([0-9]{1,3}(?:,[0-9]{3})*\\.[0-9]{2})([0-9]{1,3}(?:,[0-9]{3})*\\.[0-9]{2})").find(line)
                if (concatMatch != null) {
                    allNumberMatches = listOf(concatMatch.groupValues[1], concatMatch.groupValues[2])
                }
            }

            if (allNumberMatches.isNotEmpty()) {
                val hasExplicitCredit = line.contains("NEFT Cr-", ignoreCase = true) ||
                        line.contains("IMPS Cr-", ignoreCase = true) ||
                        line.contains("RTGS Cr-", ignoreCase = true) ||
                        line.contains("UPI Cr-", ignoreCase = true) ||
                        line.contains("Cr/", ignoreCase = true) ||
                        line.contains(" Cr-", ignoreCase = true) ||
                        line.contains("Cr.", ignoreCase = true) ||
                        line.contains("INW", ignoreCase = false) ||
                        line.contains("Refund", ignoreCase = true) ||
                        line.contains("Reversal", ignoreCase = true) ||
                        line.contains("Salary", ignoreCase = true) ||
                        line.contains("Deposit", ignoreCase = true) ||
                        line.contains("Interest Paid", ignoreCase = true) ||
                        line.contains("mani bro sent money", ignoreCase = true) ||
                        Regex("\\b(?:Cr|Credit|Credited|Deposits?)\\b", RegexOption.IGNORE_CASE).containsMatchIn(line)

                val hasExplicitDebit = line.contains("NEFT Dr-", ignoreCase = true) ||
                        line.contains("IMPS Dr-", ignoreCase = true) ||
                        line.contains("RTGS Dr-", ignoreCase = true) ||
                        line.contains("UPI Dr-", ignoreCase = true) ||
                        line.contains("Dr/", ignoreCase = true) ||
                        line.contains(" Dr-", ignoreCase = true) ||
                        line.contains("Dr.", ignoreCase = true) ||
                        line.contains("Withdrawal", ignoreCase = true) ||
                        line.contains("Debit", ignoreCase = true) ||
                        line.contains("Debited", ignoreCase = true) ||
                        line.contains("Paid to", ignoreCase = true) ||
                        line.contains("Payment to", ignoreCase = true) ||
                        line.contains("OUT", ignoreCase = false) ||
                        Regex("\\b(?:Dr|Debit|Debited|Withdrawals?)\\b", RegexOption.IGNORE_CASE).containsMatchIn(line)

                if (allNumberMatches.size >= 2) {
                    val lastNum = parseAmount(allNumberMatches.last())
                    val secondLastNum = parseAmount(allNumberMatches[allNumberMatches.size - 2])
                    balance = lastNum

                    if (hasExplicitCredit && !hasExplicitDebit) {
                        credit = secondLastNum
                        debit = 0.0
                    } else if (hasExplicitDebit && !hasExplicitCredit) {
                        debit = secondLastNum
                        credit = 0.0
                    } else if (hasExplicitCredit) {
                        credit = secondLastNum
                        debit = 0.0
                    } else {
                        debit = secondLastNum
                        credit = 0.0
                    }
                } else {
                    val singleNum = parseAmount(allNumberMatches.last())
                    if (hasExplicitCredit) {
                        credit = singleNum
                        debit = 0.0
                    } else {
                        debit = singleNum
                        credit = 0.0
                    }
                }
            }

            // Extract references
            val upiRefMatch = Regex("(?:UPI|IMPS|NEFT|RTGS|TRANSFER)/\\s*([0-9]{10,12})").find(line)
            if (upiRefMatch != null) {
                ref = upiRefMatch.groupValues[1]
            }

            val neftMatch = Regex("NEFT\\s+Cr-[A-Za-z0-9]+-(?:[A-Za-z0-9\\s]+-)+([A-Za-z0-9]+)").find(line)
            if (neftMatch != null && (ref.isBlank() || ref.length < 6 || ref == "2026")) {
                ref = neftMatch.groupValues[1]
            }

            val chqRefMatch = Regex("\\b(IN[0-9]{10,14})\\b").find(line)
            if (chqRefMatch != null && (ref.isBlank() || ref.length < 6 || ref == "2026")) {
                ref = chqRefMatch.groupValues[1]
            }

            // Clean description: remove dates and all identified amounts
            var remainder = line.trim()
            for (d in allDates) {
                remainder = remainder.replace(d, " ")
            }
            for (numStr in allNumberMatches) {
                remainder = remainder.replace(numStr, " ")
            }

            // Remove duplicated cheque number if repeated before description name suffix
            if (ref.isNotBlank() && ref.startsWith("IN")) {
                remainder = remainder.replace(Regex("\\b${Regex.escape(ref)}\\s+(?=[A-Za-z])"), "")
            }

            var cleanDesc = sanitizeText(remainder.replace("(", " ").replace(")", " ")).trim()
            if (cleanDesc.startsWith("/ ") || cleanDesc.startsWith("/\t")) {
                cleanDesc = "UPI$cleanDesc"
            }
            cleanDesc = cleanDesc.replace(Regex("^UPI/\\s*/?\\s*"), "UPI/ ")
            description = cleanDesc.trim()
        }

        if (!isValidNormalizedDate(date)) {
            return null
        }

        if (description.isBlank() || isIgnoredOrTotalLine(line, description)) {
            return null
        }

        val isCredit = credit > 0.0
        val amount = if (debit > 0.0) debit else credit

        if (amount <= 0.0) {
            return null
        }

        if (ref.isBlank()) {
            val upiMatch = Regex("(?:UPI|IMPS|NEFT|RTGS|TRANSFER)/\\s*([A-Za-z0-9]+)").find(description)
            if (upiMatch != null) {
                ref = upiMatch.groupValues[1]
            }
        }

        val finalCategory = explicitCategory ?: "Uncategorized"
        val finalType = if (isCredit) TransactionType.INCOME else TransactionType.EXPENSE

        val isDuplicate = existingTxs.any {
            it.transactionDate == date &&
                    it.description.equals(description, ignoreCase = true) &&
                    Math.abs(it.debitAmount - debit) < 0.01 &&
                    Math.abs(it.creditAmount - credit) < 0.01
        }

        return ParsedImportRow(
            rawLine = line,
            date = date,
            description = description,
            debitAmount = debit,
            creditAmount = credit,
            amount = amount,
            balance = balance,
            referenceNumber = ref,
            suggestedCategory = finalCategory,
            suggestedCategoryId = null,
            suggestedType = finalType,
            confidence = 0f,
            isDuplicate = isDuplicate
        )
    }

    private fun parseAmount(str: String): Double {
        return parseAmountOrNull(str) ?: 0.0
    }

    private fun parseAmountOrNull(str: String): Double? {
        val clean = str.replace("₹", "").replace(",", "").replace("CR", "", true).replace("DR", "", true).trim()
        return clean.toDoubleOrNull()
    }

    private fun isValidNormalizedDate(dateStr: String): Boolean {
        if (!dateStr.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) return false
        val parts = dateStr.split("-")
        if (parts.size != 3) return false
        val y = parts[0].toIntOrNull() ?: return false
        val m = parts[1].toIntOrNull() ?: return false
        val d = parts[2].toIntOrNull() ?: return false
        return y in 1990..2099 && m in 1..12 && d in 1..31
    }

    private fun normalizeDate(raw: String): String {
        val trimmed = raw.trim()
        if (isValidNormalizedDate(trimmed)) return trimmed

        // Handle Excel serial numeric date
        val doubleVal = trimmed.toDoubleOrNull()
        if (doubleVal != null && doubleVal > 10000.0 && doubleVal < 100000.0) {
            val days = doubleVal.toLong()
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            calendar.set(1899, Calendar.DECEMBER, 30, 0, 0, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.add(Calendar.DAY_OF_YEAR, days.toInt())
            val y = calendar.get(Calendar.YEAR)
            val m = calendar.get(Calendar.MONTH) + 1
            val d = calendar.get(Calendar.DAY_OF_MONTH)
            val formatted = String.format(Locale.ENGLISH, "%04d-%02d-%02d", y, m, d)
            if (isValidNormalizedDate(formatted)) return formatted
        }

        // Formats: DD/MM/YYYY or DD-MM-YYYY
        val dmyMatch = Regex("^(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})$").find(trimmed)
        if (dmyMatch != null) {
            val (d, m, y) = dmyMatch.destructured
            val formatted = String.format(Locale.ENGLISH, "%04d-%02d-%02d", y.toInt(), m.toInt(), d.toInt())
            if (isValidNormalizedDate(formatted)) return formatted
        }

        // Formats: YYYY/MM/DD or YYYY-MM-DD
        val ymdMatch = Regex("^(\\d{4})[/-](\\d{1,2})[/-](\\d{1,2})$").find(trimmed)
        if (ymdMatch != null) {
            val (y, m, d) = ymdMatch.destructured
            val formatted = String.format(Locale.ENGLISH, "%04d-%02d-%02d", y.toInt(), m.toInt(), d.toInt())
            if (isValidNormalizedDate(formatted)) return formatted
        }

        // Formats: DD-MMM-YYYY (e.g. 01-Aug-2026 or 01 Aug 2026)
        val dMmmYMatch = Regex("^(\\d{1,2})[ -]([A-Za-z]{3,9})[ -](\\d{4})$").find(trimmed)
        if (dMmmYMatch != null) {
            val (d, mStr, y) = dMmmYMatch.destructured
            val m = monthNameToNumber(mStr)
            val formatted = String.format(Locale.ENGLISH, "%04d-%02d-%02d", y.toInt(), m, d.toInt())
            if (isValidNormalizedDate(formatted)) return formatted
        }

        // Formats: MMM-DD-YYYY or MMM DD YYYY (e.g. Aug 01, 2026)
        val mmmDYMatch = Regex("^([A-Za-z]{3,9})[ -](\\d{1,2}),?[ -](\\d{4})$").find(trimmed)
        if (mmmDYMatch != null) {
            val (mStr, d, y) = mmmDYMatch.destructured
            val m = monthNameToNumber(mStr)
            val formatted = String.format(Locale.ENGLISH, "%04d-%02d-%02d", y.toInt(), m, d.toInt())
            if (isValidNormalizedDate(formatted)) return formatted
        }

        return ""
    }

    private fun monthNameToNumber(name: String): Int {
        return when (name.lowercase().take(3)) {
            "jan" -> 1
            "feb" -> 2
            "mar" -> 3
            "apr" -> 4
            "may" -> 5
            "jun" -> 6
            "jul" -> 7
            "aug" -> 8
            "sep" -> 9
            "oct" -> 10
            "nov" -> 11
            "dec" -> 12
            else -> 1
        }
    }
}
