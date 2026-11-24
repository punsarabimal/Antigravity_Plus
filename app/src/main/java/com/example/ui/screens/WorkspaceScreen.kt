package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.IdeUiState
import com.example.model.WorkspaceFile
import com.example.model.WorkspaceMode
import com.example.ui.components.GlassCard
import com.example.ui.components.formatFileSize
import com.example.ui.theme.AmberNotice
import com.example.ui.theme.CodeTextStyle
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricCyanDim
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.SoftIndigo
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun WorkspaceScreen(
    uiState: IdeUiState,
    onPickSafFolder: (Uri) -> Unit,
    onUseInternalWorkspace: () -> Unit,
    onRefreshFiles: () -> Unit,
    onOpenFile: (WorkspaceFile) -> Unit,
    onCloseEditor: () -> Unit,
    onEditorContentChange: (String) -> Unit,
    onSaveFile: () -> Unit,
    onCreateFile: (String) -> Unit,
    onRunInTermux: () -> Unit,
    onDismissNotice: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            onPickSafFolder(uri)
        }
    }

    LaunchedEffect(uiState.workspaceNotice) {
        if (uiState.workspaceNotice != null) {
            delay(3200)
            onDismissNotice()
        }
    }

    if (uiState.selectedFile != null) {
        BackHandler {
            onCloseEditor()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
    ) {
        if (uiState.selectedFile == null) {
            WorkspaceSelectorHeader(
                displayPath = uiState.workspaceDisplayPath,
                workspaceMode = uiState.workspaceMode,
                onPickFolder = { folderPickerLauncher.launch(null) },
                onUseInternal = onUseInternalWorkspace,
                onRefresh = onRefreshFiles,
                onAddFile = { showCreateDialog = true }
            )

            HorizontalDivider(color = ObsidianBorder, thickness = 1.dp)

            if (uiState.workspaceNotice != null) {
                WorkspaceNoticeBanner(notice = uiState.workspaceNotice)
            }

            if (uiState.files.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FolderOpen,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = stringResource(R.string.workspace_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { showCreateDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricCyan,
                                    contentColor = ObsidianBackground
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.workspace_new_file))
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("workspace_file_list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.files, key = { it.pathOrUri }) { file ->
                        WorkspaceFileRow(
                            file = file,
                            onClick = { onOpenFile(file) }
                        )
                    }
                }
            }
        } else {
            InlineCodeEditorView(
                file = uiState.selectedFile,
                content = uiState.editorContent,
                isDirty = uiState.isEditorDirty,
                notice = uiState.workspaceNotice,
                onBack = onCloseEditor,
                onContentChange = onEditorContentChange,
                onSave = onSaveFile,
                onRunInTermux = onRunInTermux
            )
        }
    }

    if (showCreateDialog) {
        CreateFileDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { fileName ->
                onCreateFile(fileName)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun WorkspaceSelectorHeader(
    displayPath: String,
    workspaceMode: WorkspaceMode,
    onPickFolder: () -> Unit,
    onUseInternal: () -> Unit,
    onRefresh: () -> Unit,
    onAddFile: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianSurface.copy(alpha = 0.9f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.workspace_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Text(
                    text = displayPath,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = ElectricCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("workspace_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.workspace_refresh_desc),
                        tint = TextSecondary
                    )
                }
                Surface(
                    onClick = onAddFile,
                    shape = RoundedCornerShape(12.dp),
                    color = ElectricCyan,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("workspace_new_file_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.workspace_new_file),
                            tint = ObsidianBackground
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onPickFolder,
                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("workspace_pick_folder_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.workspace_pick_folder),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            if (workspaceMode == WorkspaceMode.SAF_TREE) {
                OutlinedButton(
                    onClick = onUseInternal,
                    border = BorderStroke(1.dp, ObsidianBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("workspace_internal_button")
                ) {
                    Text(
                        text = stringResource(R.string.workspace_use_internal),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceNoticeBanner(notice: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ElectricCyanDim)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = notice,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = JetBrainsMonoFontFamily,
            color = ElectricCyan,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WorkspaceFileRow(
    file: WorkspaceFile,
    onClick: () -> Unit
) {
    val ext = file.name.substringAfterLast('.', "").lowercase()
    val iconTint = when {
        file.isDirectory -> AmberNotice
        ext == "py" -> ElectricCyan
        ext == "sh" -> TerminalGreen
        ext == "kt" || ext == "java" -> SoftIndigo
        else -> TextSecondary
    }
    val iconVector = when {
        file.isDirectory -> Icons.Filled.Folder
        ext == "sh" -> Icons.Filled.Terminal
        ext in listOf("py", "kt", "java", "js", "ts", "json", "xml") -> Icons.Filled.Code
        else -> Icons.Filled.Description
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("workspace_file_${file.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconTint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (file.isDirectory) "Directory" else formatFileSize(file.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            if (!file.isDirectory) {
                Text(
                    text = ext.ifBlank { "txt" }.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = iconTint,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(iconTint.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun InlineCodeEditorView(
    file: WorkspaceFile,
    content: String,
    isDirty: Boolean,
    notice: String?,
    onBack: () -> Unit,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onRunInTermux: () -> Unit
) {
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val lineCount = remember(content) { maxOf(1, content.count { it == '\n' } + 1) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianSurface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("editor_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.workspace_back_files_desc),
                    tint = TextPrimary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isDirty) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(AmberNotice)
                        )
                    }
                }
                Text(
                    text = if (isDirty) {
                        stringResource(R.string.workspace_unsaved)
                    } else {
                        "${lineCount} lines"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDirty) AmberNotice else TextSecondary
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianSurface)
                .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = ObsidianBackground
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("editor_save_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Save,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.workspace_save_file),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Button(
                onClick = onRunInTermux,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TerminalGreen,
                    contentColor = ObsidianBackground
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("editor_run_termux_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.workspace_run_termux),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        HorizontalDivider(color = ObsidianBorder, thickness = 1.dp)

        if (notice != null) {
            WorkspaceNoticeBanner(notice = notice)
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(ObsidianElevated)
                .verticalScroll(verticalScrollState)
        ) {
            // Line numbers gutter
            Column(
                modifier = Modifier
                    .background(ObsidianSurface.copy(alpha = 0.65f))
                    .border(width = 1.dp, color = ObsidianBorder.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.End
            ) {
                val lineNumbersText = remember(lineCount) {
                    (1..lineCount).joinToString("\n")
                }
                Text(
                    text = lineNumbersText,
                    style = CodeTextStyle,
                    color = TextMuted,
                    textAlign = TextAlign.End
                )
            }

            // Horizontally scrollable monospace code surface
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(horizontalScrollState)
                    .padding(12.dp)
            ) {
                BasicTextField(
                    value = content,
                    onValueChange = onContentChange,
                    textStyle = CodeTextStyle.copy(color = TextPrimary),
                    cursorBrush = SolidColor(ElectricCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("code_editor_text_field")
                )
            }
        }
    }
}

@Composable
private fun CreateFileDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Text(
                text = stringResource(R.string.workspace_create_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(R.string.workspace_filename_hint),
                        fontFamily = JetBrainsMonoFontFamily,
                        color = TextMuted
                    )
                },
                textStyle = CodeTextStyle.copy(color = TextPrimary),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = ObsidianBorder,
                    focusedContainerColor = ObsidianElevated,
                    unfocusedContainerColor = ObsidianElevated,
                    cursorColor = ElectricCyan
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("new_file_name_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = ObsidianBackground
                ),
                modifier = Modifier.testTag("confirm_create_file_button")
            ) {
                Text(stringResource(R.string.workspace_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.workspace_cancel), color = TextSecondary)
            }
        }
    )
}
