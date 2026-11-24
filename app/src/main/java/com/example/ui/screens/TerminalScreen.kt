package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.IdeUiState
import com.example.model.TerminalLogEntry
import com.example.ui.components.CodeBlockCard
import com.example.ui.components.GlassCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.CodeTextStyle
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TerminalGreenDim
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningCoral
import com.example.ui.theme.WarningCoralDim

private val QUICK_COMMANDS = listOf(
    "git status",
    "python3 --version",
    "ls -la",
    "pkg list-installed"
)

private const val SETUP_COMMANDS_SNIPPET =
    "mkdir -p ~/.termux && echo \"allow-external-apps=true\" >> ~/.termux/termux.properties && termux-reload-settings"

@Composable
fun TerminalScreen(
    uiState: IdeUiState,
    onRefreshStatus: () -> Unit,
    onOpenTermux: () -> Unit,
    onRunCommand: (String) -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var commandInput by rememberSaveable { mutableStateOf("") }
    var setupExpanded by rememberSaveable { mutableStateOf(!uiState.isTermuxInstalled) }

    LaunchedEffect(Unit) {
        onRefreshStatus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .imePadding()
    ) {
        // Header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianSurface.copy(alpha = 0.9f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.terminal_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Text(
                    text = uiState.termuxWorkdir,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onRefreshStatus,
                    modifier = Modifier.testTag("terminal_refresh_status_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh Termux status",
                        tint = TextSecondary
                    )
                }
                if (uiState.terminalLogs.isNotEmpty()) {
                    IconButton(
                        onClick = onClearLogs,
                        modifier = Modifier.testTag("terminal_clear_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = stringResource(R.string.terminal_clear_logs_desc),
                            tint = TextSecondary
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = ObsidianBorder, thickness = 1.dp)

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("terminal_log_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Termux Status Card
            item(key = "termux_status_card") {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = if (uiState.isTermuxInstalled) {
                        TerminalGreen.copy(alpha = 0.45f)
                    } else {
                        WarningCoral.copy(alpha = 0.45f)
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StatusPill(
                                label = if (uiState.isTermuxInstalled) {
                                    stringResource(R.string.terminal_status_installed)
                                } else {
                                    stringResource(R.string.terminal_status_missing)
                                },
                                dotColor = if (uiState.isTermuxInstalled) TerminalGreen else WarningCoral,
                                backgroundColor = if (uiState.isTermuxInstalled) TerminalGreenDim else WarningCoralDim,
                                borderColor = if (uiState.isTermuxInstalled) {
                                    TerminalGreen.copy(alpha = 0.35f)
                                } else {
                                    WarningCoral.copy(alpha = 0.35f)
                                },
                                textColor = if (uiState.isTermuxInstalled) TerminalGreen else WarningCoral
                            )
                            Text(
                                text = "Package: com.termux",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }

                        Button(
                            onClick = onOpenTermux,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = ObsidianBackground
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("open_termux_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.terminal_open_termux),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            // Collapsible Setup Guide Card
            item(key = "termux_setup_guide") {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { setupExpanded = !setupExpanded }
                                .padding(horizontal = 14.dp, vertical = 14.dp)
                                .testTag("terminal_setup_guide_toggle"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = stringResource(R.string.terminal_setup_guide),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                            }
                            Icon(
                                imageVector = if (setupExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = "Toggle setup guide",
                                tint = TextSecondary
                            )
                        }

                        AnimatedVisibility(visible = setupExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "1. Install Termux from F-Droid or GitHub Releases.\n" +
                                        "2. Enable external app execution by adding allow-external-apps=true inside ~/.termux/termux.properties.\n" +
                                        "3. Reload Termux settings using termux-reload-settings:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                CodeBlockCard(
                                    code = SETUP_COMMANDS_SNIPPET,
                                    language = "bash"
                                )
                            }
                        }
                    }
                }
            }

            // Execution History Header
            item(key = "terminal_logs_header") {
                Text(
                    text = "EXECUTION LOG (${uiState.terminalLogs.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (uiState.terminalLogs.isEmpty()) {
                item(key = "terminal_logs_empty") {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = ObsidianElevated
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Terminal,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = stringResource(R.string.terminal_empty_logs),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            } else {
                items(uiState.terminalLogs, key = { it.id }) { entry ->
                    TerminalLogCard(entry = entry)
                }
            }
        }

        // Bottom Quick Commands + Interactive Input Bar
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
                QUICK_COMMANDS.forEach { cmd ->
                    Surface(
                        onClick = { onRunCommand(cmd) },
                        shape = RoundedCornerShape(10.dp),
                        color = ObsidianElevated,
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.testTag("terminal_chip_${cmd.replace(" ", "_")}")
                    ) {
                        Text(
                            text = cmd,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = TerminalGreen,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = commandInput,
                    onValueChange = { commandInput = it },
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.terminal_input_placeholder),
                            style = CodeTextStyle,
                            color = TextMuted
                        )
                    },
                    textStyle = CodeTextStyle.copy(color = TerminalGreen),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TerminalGreen,
                        unfocusedBorderColor = ObsidianBorder,
                        focusedContainerColor = ObsidianElevated,
                        unfocusedContainerColor = ObsidianElevated,
                        cursorColor = TerminalGreen
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("terminal_command_input")
                )

                Surface(
                    onClick = {
                        val trimmed = commandInput.trim()
                        if (trimmed.isNotEmpty()) {
                            onRunCommand(trimmed)
                            commandInput = ""
                        }
                    },
                    enabled = commandInput.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (commandInput.isNotBlank()) TerminalGreen else ObsidianSurfaceVariant,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("terminal_run_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.terminal_run_desc),
                            tint = if (commandInput.isNotBlank()) ObsidianBackground else TextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalLogCard(entry: TerminalLogEntry) {
    val statusColor = if (entry.isError) WarningCoral else TerminalGreen
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = statusColor.copy(alpha = 0.35f),
        containerColor = ObsidianElevated
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                        imageVector = if (entry.isError) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = entry.timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
                Text(
                    text = entry.workdir,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Text(
                text = "$ ${entry.command}",
                style = CodeTextStyle,
                color = ElectricCyan
            )

            Text(
                text = entry.status,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor
            )
        }
    }
}
