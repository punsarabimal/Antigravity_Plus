package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.CodeTextStyle
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = ObsidianBorder,
    containerColor: Color = ObsidianSurface,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier
            .border(BorderStroke(1.dp, borderColor), shape),
        shape = shape,
        color = containerColor,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.03f),
                        Color.Transparent
                    )
                )
            )
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun StatusPill(
    label: String,
    dotColor: Color,
    modifier: Modifier = Modifier,
    backgroundColor: Color = ObsidianElevated,
    borderColor: Color = ObsidianBorder,
    textColor: Color = TextPrimary
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = JetBrainsMonoFontFamily,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CodeBlockCard(
    code: String,
    language: String = "",
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(code) { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800)
            copied = false
        }
    }

    val copyColor by animateColorAsState(
        targetValue = if (copied) TerminalGreen else ElectricCyan,
        label = "copy_tint"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianElevated)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.03f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = language.ifBlank { "code" },
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("copy_code_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (copied) Icons.Filled.Check else Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(R.string.agent_copy_code),
                    tint = copyColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = if (copied) {
                        stringResource(R.string.agent_copied)
                    } else {
                        stringResource(R.string.agent_copy_code)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = copyColor
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(
                text = code.trimEnd(),
                style = CodeTextStyle,
                color = TextPrimary
            )
        }
    }
}

private sealed interface MessageSegment {
    data class Prose(val text: String) : MessageSegment
    data class Code(val language: String, val code: String) : MessageSegment
}

private fun parseMessageSegments(raw: String): List<MessageSegment> {
    if (!raw.contains("```")) {
        return listOf(MessageSegment.Prose(raw))
    }
    val segments = mutableListOf<MessageSegment>()
    val regex = Regex("```([a-zA-Z0-9_+-]*)\\n?([\\s\\S]*?)```")
    var lastIndex = 0
    for (match in regex.findAll(raw)) {
        if (match.range.first > lastIndex) {
            val prose = raw.substring(lastIndex, match.range.first).trim()
            if (prose.isNotEmpty()) {
                segments.add(MessageSegment.Prose(prose))
            }
        }
        val lang = match.groupValues[1].trim()
        val code = match.groupValues[2].trim('\n')
        segments.add(MessageSegment.Code(lang, code))
        lastIndex = match.range.last + 1
    }
    if (lastIndex < raw.length) {
        val trailing = raw.substring(lastIndex).trim()
        if (trailing.isNotEmpty()) {
            segments.add(MessageSegment.Prose(trailing))
        }
    }
    return segments
}

@Composable
fun MarkdownMessageContent(
    text: String,
    modifier: Modifier = Modifier
) {
    val segments = remember(text) { parseMessageSegments(text) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        segments.forEach { segment ->
            when (segment) {
                is MessageSegment.Prose -> {
                    Text(
                        text = segment.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }
                is MessageSegment.Code -> {
                    CodeBlockCard(
                        code = segment.code,
                        language = segment.language
                    )
                }
            }
        }
    }
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format("%.1f KB", bytes / 1024.0)
        else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
    }
}
