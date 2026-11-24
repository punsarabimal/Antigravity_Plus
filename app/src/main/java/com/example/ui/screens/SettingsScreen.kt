package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.model.ApiKeyEntry
import com.example.model.IdeUiState
import com.example.ui.components.GlassCard
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
import com.example.ui.theme.TerminalGreenDim
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningCoral
import com.example.ui.theme.WarningCoralDim
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: IdeUiState,
    onAddOrUpdateKey: (existingId: String?, label: String, keyValue: String) -> Unit,
    onSelectKey: (String) -> Unit,
    onDeleteKey: (String) -> Unit,
    onTestActiveKey: () -> Unit,
    onSelectModel: (String) -> Unit,
    onSaveConfig: (systemInstruction: String, termuxWorkdir: String) -> Unit,
    onDismissNotice: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingKey by remember { mutableStateOf<ApiKeyEntry?>(null) }
    var showKeyDialog by rememberSaveable { mutableStateOf(false) }
    val revealedKeyIds = remember { mutableStateMapOf<String, Boolean>() }

    var modelDropdownExpanded by remember { mutableStateOf(false) }
    var instructionDraft by remember(uiState.systemInstruction) {
        mutableStateOf(uiState.systemInstruction)
    }
    var workdirDraft by remember(uiState.termuxWorkdir) {
        mutableStateOf(uiState.termuxWorkdir)
    }

    LaunchedEffect(uiState.settingsNotice) {
        if (uiState.settingsNotice != null) {
            delay(3000)
            onDismissNotice()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .imePadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianSurface.copy(alpha = 0.9f))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Text(
                    text = "Antigravity Studio v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }

        HorizontalDivider(color = ObsidianBorder, thickness = 1.dp)

        if (uiState.settingsNotice != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ElectricCyanDim)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = uiState.settingsNotice,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = ElectricCyan
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("settings_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Multi-Key Gemini API Manager
            item(key = "api_manager_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Key,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.settings_api_section),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }

                    Button(
                        onClick = {
                            editingKey = null
                            showKeyDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = ObsidianBackground
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("settings_add_key_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.settings_add_key),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            if (uiState.apiKeys.isEmpty()) {
                item(key = "api_keys_empty") {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.settings_no_keys),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(uiState.apiKeys, key = { it.id }) { entry ->
                    val isSelected = entry.id == uiState.activeApiKey?.id
                    val isRevealed = revealedKeyIds[entry.id] == true
                    ApiKeyCard(
                        entry = entry,
                        isSelected = isSelected,
                        isRevealed = isRevealed,
                        onSelect = { onSelectKey(entry.id) },
                        onToggleReveal = { revealedKeyIds[entry.id] = !isRevealed },
                        onEdit = {
                            editingKey = entry
                            showKeyDialog = true
                        },
                        onDelete = { onDeleteKey(entry.id) }
                    )
                }
            }

            // Test Active API Key CTA & Result
            item(key = "test_active_key_section") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onTestActiveKey,
                        enabled = !uiState.apiTestState.isTesting && uiState.activeApiKey != null,
                        border = BorderStroke(
                            1.dp,
                            if (uiState.activeApiKey != null) ElectricCyan.copy(alpha = 0.6f) else ObsidianBorder
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("settings_test_key_button")
                    ) {
                        if (uiState.apiTestState.isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = ElectricCyan
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_testing_key))
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.settings_test_key),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    val testResult = uiState.apiTestState
                    if (testResult.success != null && !testResult.message.isNullOrBlank()) {
                        val tint = if (testResult.success) TerminalGreen else WarningCoral
                        val bg = if (testResult.success) TerminalGreenDim else WarningCoralDim
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(bg)
                                .border(1.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (testResult.success) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = testResult.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary
                                )
                                if (testResult.latencyMs != null) {
                                    Text(
                                        text = "Latency: ${testResult.latencyMs} ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tint
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: Model & Agent Configuration
            item(key = "model_agent_config_card") {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_model_section),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )

                        // Model Dropdown
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(R.string.settings_model_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                            ExposedDropdownMenuBox(
                                expanded = modelDropdownExpanded,
                                onExpandedChange = { modelDropdownExpanded = !modelDropdownExpanded }
                            ) {
                                OutlinedTextField(
                                    value = uiState.activeModel,
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded)
                                    },
                                    textStyle = CodeTextStyle.copy(color = ElectricCyan),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ElectricCyan,
                                        unfocusedBorderColor = ObsidianBorder,
                                        focusedContainerColor = ObsidianElevated,
                                        unfocusedContainerColor = ObsidianElevated
                                    ),
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth()
                                        .testTag("settings_model_dropdown")
                                )

                                ExposedDropdownMenu(
                                    expanded = modelDropdownExpanded,
                                    onDismissRequest = { modelDropdownExpanded = false },
                                    modifier = Modifier.background(ObsidianSurface)
                                ) {
                                    IdeUiState.AVAILABLE_MODELS.forEach { option ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = option.label,
                                                        style = CodeTextStyle,
                                                        color = if (option.id == uiState.activeModel) {
                                                            ElectricCyan
                                                        } else {
                                                            TextPrimary
                                                        }
                                                    )
                                                    Text(
                                                        text = option.badge,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = SoftIndigo
                                                    )
                                                }
                                            },
                                            onClick = {
                                                onSelectModel(option.id)
                                                modelDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // System Instruction
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(R.string.settings_system_instruction_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                            OutlinedTextField(
                                value = instructionDraft,
                                onValueChange = { instructionDraft = it },
                                minLines = 3,
                                maxLines = 6,
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElectricCyan,
                                    unfocusedBorderColor = ObsidianBorder,
                                    focusedContainerColor = ObsidianElevated,
                                    unfocusedContainerColor = ObsidianElevated,
                                    cursorColor = ElectricCyan
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_system_instruction_input")
                            )
                        }

                        // Default Termux Workdir
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(R.string.settings_workdir_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                            OutlinedTextField(
                                value = workdirDraft,
                                onValueChange = { workdirDraft = it },
                                singleLine = true,
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
                                    .fillMaxWidth()
                                    .testTag("settings_termux_workdir_input")
                            )
                        }

                        Button(
                            onClick = { onSaveConfig(instructionDraft, workdirDraft) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SoftIndigo,
                                contentColor = ObsidianBackground
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("settings_save_config_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Save,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.settings_save_config),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }

    if (showKeyDialog) {
        ApiKeyEditorDialog(
            initialEntry = editingKey,
            onDismiss = { showKeyDialog = false },
            onSave = { label, keyValue ->
                onAddOrUpdateKey(editingKey?.id, label, keyValue)
                showKeyDialog = false
            }
        )
    }
}

@Composable
private fun ApiKeyCard(
    entry: ApiKeyEntry,
    isSelected: Boolean,
    isRevealed: Boolean,
    onSelect: () -> Unit,
    onToggleReveal: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onSelect)
            .testTag("api_key_card_${entry.id}"),
        borderColor = if (isSelected) ElectricCyan.copy(alpha = 0.65f) else ObsidianBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(
                    selectedColor = ElectricCyan,
                    unselectedColor = TextMuted
                )
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            ) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isRevealed) entry.key else entry.maskedKey(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) ElectricCyan else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onToggleReveal) {
                Icon(
                    imageVector = if (isRevealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = stringResource(R.string.settings_toggle_visibility_desc),
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.settings_edit_desc),
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = stringResource(R.string.settings_delete_desc),
                    tint = WarningCoral,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ApiKeyEditorDialog(
    initialEntry: ApiKeyEntry?,
    onDismiss: () -> Unit,
    onSave: (label: String, keyValue: String) -> Unit
) {
    var label by remember(initialEntry) { mutableStateOf(initialEntry?.label ?: "") }
    var keyValue by remember(initialEntry) { mutableStateOf(initialEntry?.key ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Text(
                text = if (initialEntry == null) "Add Gemini API Key" else "Edit Gemini API Key",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.settings_key_label_hint),
                            color = TextMuted
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = ObsidianBorder,
                        focusedContainerColor = ObsidianElevated,
                        unfocusedContainerColor = ObsidianElevated,
                        cursorColor = ElectricCyan
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_key_label_input")
                )

                OutlinedTextField(
                    value = keyValue,
                    onValueChange = { keyValue = it },
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.settings_key_value_hint),
                            fontFamily = JetBrainsMonoFontFamily,
                            color = TextMuted
                        )
                    },
                    textStyle = CodeTextStyle.copy(color = ElectricCyan),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = ObsidianBorder,
                        focusedContainerColor = ObsidianElevated,
                        unfocusedContainerColor = ObsidianElevated,
                        cursorColor = ElectricCyan
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_key_value_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(label, keyValue) },
                enabled = keyValue.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = ObsidianBackground
                ),
                modifier = Modifier.testTag("dialog_save_key_button")
            ) {
                Text(stringResource(R.string.settings_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.workspace_cancel), color = TextSecondary)
            }
        }
    )
}
