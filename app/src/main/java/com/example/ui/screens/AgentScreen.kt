package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.GeminiApiClient
import com.example.model.ActionStatus
import com.example.model.AgentToolCall
import com.example.model.ChatMessage
import com.example.model.ChatRole
import com.example.model.IdeUiState
import com.example.ui.components.CodeBlockCard
import com.example.ui.components.GlassCard
import com.example.ui.components.MarkdownMessageContent
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberNotice
import com.example.ui.theme.CodeTextStyle
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricCyanDim
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.ui.theme.SoftIndigo
import com.example.ui.theme.SoftIndigoDim
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TerminalGreenDim
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningCoral
import com.example.ui.theme.WarningCoralDim

@Composable
fun AgentScreen(
    uiState: IdeUiState,
    onSendPrompt: (String) -> Unit,
    onApproveTool: (String) -> Unit,
    onRejectTool: (String) -> Unit,
    onClearChat: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var promptText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size, uiState.isAgentBusy) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .imePadding()
    ) {
        AgentHeaderBar(
            activeModel = uiState.activeModel,
            activeKeyLabel = uiState.activeApiKey?.label,
            hasMessages = uiState.messages.isNotEmpty(),
            onSwitchSettings = onNavigateToSettings,
            onClearChat = onClearChat
        )

        HorizontalDivider(color = ObsidianBorder, thickness = 1.dp)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (uiState.messages.isEmpty()) {
                AgentEmptyState(
                    hasApiKey = uiState.activeApiKey != null,
                    workspacePath = uiState.workspaceDisplayPath,
                    fileCount = uiState.files.size,
                    onOpenSettings = onNavigateToSettings,
                    onQuickPrompt = onSendPrompt
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("agent_chat_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.messages, key = { it.id }) { message ->
                        ChatMessageItem(
                            message = message,
                            onApproveTool = onApproveTool,
                            onRejectTool = onRejectTool
                        )
                    }

                    if (uiState.isAgentBusy) {
                        item(key = "agent_busy_indicator") {
                            AgentBusyIndicator(
                                statusText = uiState.agentStatusMessage
                                    ?: stringResource(R.string.agent_thinking)
                            )
                        }
                    }
                }
            }
        }

        AgentComposer(
            promptText = promptText,
            onPromptChange = { promptText = it },
            isBusy = uiState.isAgentBusy,
            onSend = {
                val trimmed = promptText.trim()
                if (trimmed.isNotEmpty()) {
                    onSendPrompt(trimmed)
                    promptText = ""
                }
            },
            onQuickChipClick = { prompt ->
                onSendPrompt(prompt)
            }
        )
    }
}

@Composable
private fun AgentHeaderBar(
    activeModel: String,
    activeKeyLabel: String?,
    hasMessages: Boolean,
    onSwitchSettings: () -> Unit,
    onClearChat: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianSurface.copy(alpha = 0.85f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    label = activeModel,
                    dotColor = ElectricCyan,
                    backgroundColor = ElectricCyanDim,
                    borderColor = ElectricCyan.copy(alpha = 0.35f),
                    textColor = ElectricCyan
                )
                StatusPill(
                    label = activeKeyLabel ?: stringResource(R.string.agent_no_key),
                    dotColor = if (activeKeyLabel != null) TerminalGreen else WarningCoral,
                    backgroundColor = if (activeKeyLabel != null) TerminalGreenDim else WarningCoralDim,
                    borderColor = if (activeKeyLabel != null) {
                        TerminalGreen.copy(alpha = 0.35f)
                    } else {
                        WarningCoral.copy(alpha = 0.35f)
                    },
                    textColor = if (activeKeyLabel != null) TerminalGreen else WarningCoral,
                    modifier = Modifier
                        .clickable { onSwitchSettings() }
                        .testTag("agent_key_badge")
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AnimatedVisibility(visible = hasMessages) {
                IconButton(
                    onClick = onClearChat,
                    modifier = Modifier.testTag("agent_clear_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = stringResource(R.string.agent_clear_chat_desc),
                        tint = TextSecondary
                    )
                }
            }
            IconButton(
                onClick = onSwitchSettings,
                modifier = Modifier.testTag("agent_quick_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = stringResource(R.string.agent_switch_key_desc),
                    tint = ElectricCyan
                )
            }
        }
    }
}

@Composable
private fun AgentEmptyState(
    hasApiKey: Boolean,
    workspacePath: String,
    fileCount: Int,
    onOpenSettings: () -> Unit,
    onQuickPrompt: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = ObsidianBorder
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(156.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_agent_hero_1795487671919),
                        contentDescription = "Antigravity Studio orbital graphic",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        ObsidianSurface.copy(alpha = 0.72f),
                                        ObsidianSurface
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.agent_welcome_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$workspacePath ($fileCount files)",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = ElectricCyan
                        )
                    }
                }

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.agent_welcome_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    if (!hasApiKey) {
                        Button(
                            onClick = onOpenSettings,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = ObsidianBackground
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("agent_configure_key_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Key,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.agent_open_settings),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onQuickPrompt(
                                        "Inspect the current project workspace using list_files and read_file, then summarize what the code does."
                                    )
                                },
                                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text(
                                    text = "Inspect Workspace",
                                    color = ElectricCyan,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "AVAILABLE AGENT TOOLS",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    ToolCapabilityRow(
                        icon = Icons.Filled.Description,
                        name = "list_files & read_file",
                        detail = "Inspect workspace structure and source files automatically",
                        tint = ElectricCyan
                    )
                    ToolCapabilityRow(
                        icon = Icons.Filled.Code,
                        name = "write_file",
                        detail = "Propose file creation or edits with interactive approval",
                        tint = SoftIndigo
                    )
                    ToolCapabilityRow(
                        icon = Icons.Filled.Terminal,
                        name = "run_termux_command",
                        detail = "Dispatch bash, python3, and git commands to Termux",
                        tint = TerminalGreen
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolCapabilityRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    name: String,
    detail: String,
    tint: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = JetBrainsMonoFontFamily,
                color = TextPrimary
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun ChatMessageItem(
    message: ChatMessage,
    onApproveTool: (String) -> Unit,
    onRejectTool: (String) -> Unit
) {
    when {
        message.role == ChatRole.USER -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .border(
                            1.dp,
                            SoftIndigo.copy(alpha = 0.4f),
                            RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
                        ),
                    shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                    color = SoftIndigoDim
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = message.timestamp,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }
        }

        message.role == ChatRole.TOOL_LOG -> {
            val toolName = message.toolCall?.name ?: "tool"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianElevated)
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = TerminalGreen,
                    modifier = Modifier.size(16.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = toolName,
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = TextSecondary
                    )
                }
                Text(
                    text = message.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }

        message.role == ChatRole.ERROR -> {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = WarningCoral.copy(alpha = 0.5f),
                containerColor = WarningCoralDim
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = WarningCoral,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Agent Error",
                            style = MaterialTheme.typography.labelLarge,
                            color = WarningCoral
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        message.toolCall != null -> {
            ActionApprovalCard(
                toolCall = message.toolCall,
                timestamp = message.timestamp,
                onApprove = { onApproveTool(message.toolCall.id) },
                onReject = { onRejectTool(message.toolCall.id) }
            )
        }

        else -> {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = ObsidianBorder,
                containerColor = ObsidianSurface
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "ANTIGRAVITY",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                        }
                        Text(
                            text = message.timestamp,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                    MarkdownMessageContent(text = message.text)
                }
            }
        }
    }
}

@Composable
private fun ActionApprovalCard(
    toolCall: AgentToolCall,
    timestamp: String,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val isTermux = toolCall.name == GeminiApiClient.TOOL_RUN_TERMUX
    val accentColor = when (toolCall.status) {
        ActionStatus.PENDING -> if (isTermux) ElectricCyan else SoftIndigo
        ActionStatus.APPROVED -> TerminalGreen
        ActionStatus.REJECTED -> WarningCoral
    }
    val statusText = when (toolCall.status) {
        ActionStatus.PENDING -> stringResource(R.string.agent_pending_badge)
        ActionStatus.APPROVED -> stringResource(R.string.agent_approved_badge)
        ActionStatus.REJECTED -> stringResource(R.string.agent_rejected_badge)
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("agent_action_card_${toolCall.name}"),
        borderColor = accentColor.copy(alpha = 0.55f),
        containerColor = ObsidianSurface
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isTermux) Icons.Filled.Terminal else Icons.Filled.Code,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = toolCall.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = TextPrimary
                    )
                }
                StatusPill(
                    label = statusText,
                    dotColor = accentColor,
                    backgroundColor = accentColor.copy(alpha = 0.14f),
                    borderColor = accentColor.copy(alpha = 0.4f),
                    textColor = accentColor
                )
            }

            if (isTermux) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!toolCall.workdir.isNullOrBlank()) {
                        Text(
                            text = "cwd: ${toolCall.workdir}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    CodeBlockCard(
                        code = "$ ${toolCall.command.orEmpty()}",
                        language = "termux"
                    )
                }
            } else {
                val targetFile = toolCall.path.orEmpty()
                val previewContent = toolCall.content.orEmpty()
                val ext = targetFile.substringAfterLast('.', "text")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "target: $targetFile (${previewContent.lines().size} lines)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    CodeBlockCard(
                        code = previewContent.take(1600),
                        language = ext
                    )
                }
            }

            if (!toolCall.resultOutput.isNullOrBlank()) {
                Text(
                    text = toolCall.resultOutput,
                    style = CodeTextStyle,
                    color = if (toolCall.status == ActionStatus.REJECTED) WarningCoral else TerminalGreen
                )
            }

            if (toolCall.status == ActionStatus.PENDING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onReject,
                        border = BorderStroke(1.dp, WarningCoral.copy(alpha = 0.55f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = WarningCoral),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("agent_reject_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.agent_reject),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Button(
                        onClick = onApprove,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TerminalGreen,
                            contentColor = ObsidianBackground
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("agent_approve_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.agent_approve_run),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun AgentBusyIndicator(statusText: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianSurface)
            .border(1.dp, ElectricCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = ElectricCyan
        )
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = JetBrainsMonoFontFamily,
            color = ElectricCyan
        )
    }
}

@Composable
private fun AgentComposer(
    promptText: String,
    onPromptChange: (String) -> Unit,
    isBusy: Boolean,
    onSend: () -> Unit,
    onQuickChipClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianSurface)
            .border(1.dp, ObsidianBorder)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickActionChip(
                label = stringResource(R.string.agent_chip_inspect),
                enabled = !isBusy,
                tag = "chip_inspect_project",
                onClick = {
                    onQuickChipClick(
                        "Inspect the workspace files using list_files and read_file, then summarize the codebase and suggest one improvement."
                    )
                }
            )
            QuickActionChip(
                label = stringResource(R.string.agent_chip_git),
                enabled = !isBusy,
                tag = "chip_git_status",
                onClick = {
                    onQuickChipClick(
                        "Propose running git status in Termux using run_termux_command."
                    )
                }
            )
            QuickActionChip(
                label = stringResource(R.string.agent_chip_python),
                enabled = !isBusy,
                tag = "chip_run_python",
                onClick = {
                    onQuickChipClick(
                        "Read main.py in the workspace and propose running it via python3 in Termux."
                    )
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = promptText,
                onValueChange = onPromptChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.agent_prompt_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                },
                maxLines = 4,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = ObsidianBorder,
                    focusedContainerColor = ObsidianElevated,
                    unfocusedContainerColor = ObsidianElevated,
                    cursorColor = ElectricCyan
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("agent_prompt_input")
            )

            Surface(
                onClick = onSend,
                enabled = !isBusy && promptText.isNotBlank(),
                shape = CircleShape,
                color = if (!isBusy && promptText.isNotBlank()) ElectricCyan else ObsidianSurfaceVariant,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("agent_send_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.agent_send_desc),
                        tint = if (!isBusy && promptText.isNotBlank()) ObsidianBackground else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionChip(
    label: String,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = ObsidianElevated,
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.testTag(tag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) ElectricCyan else TextMuted,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
