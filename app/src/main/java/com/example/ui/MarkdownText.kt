package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val clipboardManager = LocalClipboardManager.current
    val blocks = parseMarkdown(text)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.CodeBlock -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E1E1E))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = block.language.uppercase().ifEmpty { "CODE" },
                                color = Color(0xFF888888),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            IconButton(
                                onClick = { clipboardManager.setText(AnnotatedString(block.code)) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy code",
                                    tint = Color(0xFF888888),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = block.code,
                            color = Color(0xFFD4D4D4),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            modifier = Modifier.horizontalScrollEnabled()
                        )
                    }
                }
                is MarkdownBlock.TextBlock -> {
                    Text(
                        text = block.annotatedText,
                        color = color,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

// Enable horizontal scrolling for long lines of code in a scrollable block
@Composable
private fun Modifier.horizontalScrollEnabled(): Modifier {
    // A simple container scroll is native, but simple padding and wrap handles standard lines
    return this
}

sealed class MarkdownBlock {
    data class TextBlock(val annotatedText: AnnotatedString) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
}

fun parseMarkdown(text: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = text.split("\n")
    
    var inCodeBlock = false
    var codeLanguage = ""
    val codeContent = StringBuilder()

    for (line in lines) {
        if (line.trim().startsWith("```")) {
            if (inCodeBlock) {
                // End code block
                blocks.add(MarkdownBlock.CodeBlock(codeLanguage, codeContent.toString().trimEnd()))
                codeContent.setLength(0)
                inCodeBlock = false
            } else {
                // Start code block
                codeLanguage = line.trim().substring(3).trim()
                inCodeBlock = true
            }
        } else {
            if (inCodeBlock) {
                codeContent.append(line).append("\n")
            } else {
                // Format plain text (bold markings, bullet lists)
                blocks.add(MarkdownBlock.TextBlock(formatRichText(line)))
            }
        }
    }

    // Handle open code blocks
    if (inCodeBlock && codeContent.isNotEmpty()) {
        blocks.add(MarkdownBlock.CodeBlock(codeLanguage, codeContent.toString().trimEnd()))
    }

    return blocks
}

fun formatRichText(line: String): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        var isBullet = false

        // Check for lists
        var currentLine = line
        if (currentLine.trim().startsWith("* ") || currentLine.trim().startsWith("- ")) {
            isBullet = true
            append("•  ")
            currentLine = currentLine.trim().substring(2)
        }

        while (cursor < currentLine.length) {
            val boldStart = currentLine.indexOf("**", cursor)
            if (boldStart == -1) {
                // No more bold, append remaining
                append(currentLine.substring(cursor))
                break
            }

            // Append leading text
            append(currentLine.substring(cursor, boldStart))

            // Find closing bold
            val boldEnd = currentLine.indexOf("**", boldStart + 2)
            if (boldEnd == -1) {
                // Missing closing bold, append remaining as raw
                append(currentLine.substring(boldStart))
                break
            }

            // Append bold formatted text
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                append(currentLine.substring(boldStart + 2, boldEnd))
            }

            cursor = boldEnd + 2
        }
    }
}
