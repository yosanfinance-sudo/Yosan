package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class MarkdownElement {
    data class Header(val level: Int, val text: String) : MarkdownElement()
    data class Paragraph(val text: String) : MarkdownElement()
    data class BulletPoint(val text: String) : MarkdownElement()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownElement()
}

/**
 * Parses raw markdown text into a list of structured [MarkdownElement] objects.
 */
fun parseMarkdownText(rawText: String): List<MarkdownElement> {
    val lines = rawText.lines()
    val elements = mutableListOf<MarkdownElement>()
    var i = 0

    while (i < lines.size) {
        val line = lines[i].trim()

        if (line.isEmpty()) {
            i++
            continue
        }

        // 1. Table Detection
        if (line.startsWith("|") && line.endsWith("|") && i + 1 < lines.size && isTableSeparator(lines[i + 1].trim())) {
            val headers = parseTableRow(line)
            i += 2 // skip header and separator row
            val rows = mutableListOf<List<String>>()

            while (i < lines.size) {
                val tableLine = lines[i].trim()
                if (tableLine.startsWith("|") && tableLine.endsWith("|")) {
                    val rowCells = parseTableRow(tableLine)
                    // Pad or truncate to match header size
                    val paddedRow = headers.indices.map { idx ->
                        if (idx < rowCells.size) rowCells[idx] else ""
                    }
                    rows.add(paddedRow)
                    i++
                } else {
                    break
                }
            }

            elements.add(MarkdownElement.Table(headers, rows))
            continue
        }

        // 2. Headers (# Header, ## Header, ### Header)
        if (line.startsWith("#")) {
            val level = line.takeWhile { it == '#' }.length
            val headerText = line.drop(level).trim()
            elements.add(MarkdownElement.Header(level, headerText))
            i++
            continue
        }

        // 3. Bullet points (* item, - item, • item)
        if (line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ")) {
            elements.add(MarkdownElement.BulletPoint(line.drop(2).trim()))
            i++
            continue
        }

        // 4. Regular Paragraph
        elements.add(MarkdownElement.Paragraph(line))
        i++
    }

    return elements
}

private fun isTableSeparator(line: String): Boolean {
    if (!line.startsWith("|") || !line.endsWith("|")) return false
    val inner = line.trim('|')
    val parts = inner.split("|")
    return parts.isNotEmpty() && parts.all { cell ->
        val trimmed = cell.trim()
        trimmed.matches(Regex("^:?-+:?$"))
    }
}

private fun parseTableRow(line: String): List<String> {
    return line.trim()
        .removePrefix("|")
        .removeSuffix("|")
        .split("|")
        .map { it.trim() }
}

/**
 * Builds an AnnotatedString parsing simple bold `**bold**` markdown tags.
 */
@Composable
fun buildFormattedMarkdownString(text: String): AnnotatedString {
    return remember(text) {
        val parts = text.split("**")
        buildAnnotatedString {
            parts.forEachIndexed { index, part ->
                if (index % 2 == 1) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(part)
                    pop()
                } else {
                    append(part)
                }
            }
        }
    }
}

/**
 * Material3 Compose Markdown Renderer that renders formatted text, headers,
 * bullet lists, and horizontal-scrollable Material3 data tables.
 */
@Composable
fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val elements = remember(content) { parseMarkdownText(content) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        elements.forEach { element ->
            when (element) {
                is MarkdownElement.Header -> {
                    val (style, topPadding) = when (element.level) {
                        1 -> MaterialTheme.typography.titleLarge to 10.dp
                        2 -> MaterialTheme.typography.titleMedium to 8.dp
                        else -> MaterialTheme.typography.titleSmall to 6.dp
                    }
                    Text(
                        text = buildFormattedMarkdownString(element.text),
                        style = style,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = topPadding)
                    )
                }

                is MarkdownElement.Paragraph -> {
                    Text(
                        text = buildFormattedMarkdownString(element.text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                        lineHeight = 20.sp
                    )
                }

                is MarkdownElement.BulletPoint -> {
                    Row(
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = buildFormattedMarkdownString(element.text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                            lineHeight = 20.sp
                        )
                    }
                }

                is MarkdownElement.Table -> {
                    MarkdownTable(
                        headers = element.headers,
                        rows = element.rows
                    )
                }
            }
        }
    }
}

/**
 * Renders a clean Material3 Data Table with:
 * - Rounded corners and outline border
 * - Smooth horizontal scrolling
 * - Distinct header row with primary container styling
 * - Zebra-striped alternating rows
 * - Smart numeric/currency right-alignment
 */
@Composable
fun MarkdownTable(
    headers: List<String>,
    rows: List<List<String>>,
    modifier: Modifier = Modifier
) {
    if (headers.isEmpty()) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                headers.forEachIndexed { index, header ->
                    val isNumberOrCurrency = isNumericCell(header) || (rows.isNotEmpty() && isNumericCell(rows[0].getOrElse(index) { "" }))
                    Text(
                        text = header,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = if (isNumberOrCurrency) TextAlign.End else TextAlign.Start,
                        modifier = Modifier
                            .widthIn(min = 96.dp)
                            .padding(horizontal = 6.dp)
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                thickness = 1.dp
            )

            // Data Rows with Zebra Striping
            rows.forEachIndexed { rowIndex, row ->
                val rowBackground = if (rowIndex % 2 == 0) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                }

                Row(
                    modifier = Modifier
                        .background(rowBackground)
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEachIndexed { colIndex, cell ->
                        val isNumberOrCurrency = isNumericCell(cell)
                        Text(
                            text = buildFormattedMarkdownString(cell),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = if (isNumberOrCurrency) TextAlign.End else TextAlign.Start,
                            modifier = Modifier
                                .widthIn(min = 96.dp)
                                .padding(horizontal = 6.dp)
                        )
                    }
                }

                if (rowIndex < rows.size - 1) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

private fun isNumericCell(cell: String): Boolean {
    val clean = cell.trim()
    if (clean.isEmpty()) return false
    // Checks if starts with currency (₹, $, €, £), contains digits, percentages, or decimals
    return clean.matches(Regex("^[₹$€£]?\\s*[-+]?[0-9,]+(\\.[0-9]+)?%?$"))
}
