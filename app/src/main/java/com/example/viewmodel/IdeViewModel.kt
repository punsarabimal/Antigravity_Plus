package com.example.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.GeminiApiClient
import com.example.data.ParsedFunctionCall
import com.example.data.SettingsRepository
import com.example.data.TermuxBridge
import com.example.data.WorkspaceRepository
import com.example.model.ActionStatus
import com.example.model.AgentToolCall
import com.example.model.ApiKeyEntry
import com.example.model.ApiTestState
import com.example.model.ChatMessage
import com.example.model.ChatRole
import com.example.model.IdeUiState
import com.example.model.TerminalLogEntry
import com.example.model.WorkspaceFile
import com.example.model.WorkspaceMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class IdeViewModel(
    private val settingsRepo: SettingsRepository,
    private val workspaceRepo: WorkspaceRepository,
    private val termuxBridge: TermuxBridge,
    private val geminiClient: GeminiApiClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(IdeUiState())
    val uiState: StateFlow<IdeUiState> = _uiState.asStateFlow()

    private val conversationContents = JSONArray()

    // Tracks a single Gemini model turn's tool calls so every functionCall gets a matching functionResponse
    private val currentBatchToolOrder = mutableListOf<Pair<String, String>>() // callId to toolName
    private val currentBatchResponses = mutableMapOf<String, JSONObject>()
    private val pendingApprovalCallIds = mutableSetOf<String>()

    init {
        initializeAppState()
    }

    private fun initializeAppState() {
        val savedKeys = settingsRepo.loadApiKeys()
        val savedActiveId = settingsRepo.loadActiveApiKeyId()
            ?: savedKeys.firstOrNull()?.id
        val savedModel = settingsRepo.loadActiveModel()
        val savedInstruction = settingsRepo.loadSystemInstruction()
        val savedWorkdir = settingsRepo.loadTermuxWorkdir()
        val savedSafUri = settingsRepo.loadSafTreeUri()
        val termuxInstalled = termuxBridge.isTermuxInstalled()

        val initialMode = if (!savedSafUri.isNullOrBlank()) WorkspaceMode.SAF_TREE else WorkspaceMode.INTERNAL
        val initialDisplayPath = if (initialMode == WorkspaceMode.SAF_TREE && savedSafUri != null) {
            workspaceRepo.formatSafDisplayPath(savedSafUri)
        } else {
            "filesDir/workspace"
        }

        _uiState.update {
            it.copy(
                apiKeys = savedKeys,
                activeApiKeyId = savedActiveId,
                activeModel = savedModel,
                systemInstruction = savedInstruction,
                termuxWorkdir = savedWorkdir,
                workspaceMode = initialMode,
                safTreeUri = savedSafUri,
                workspaceDisplayPath = initialDisplayPath,
                isTermuxInstalled = termuxInstalled
            )
        }

        refreshWorkspaceFiles()
    }

    // -------------------------------------------------------------------------
    // AGENT CHAT & FUNCTION CALLING
    // -------------------------------------------------------------------------

    fun sendUserPrompt(rawPrompt: String) {
        val prompt = rawPrompt.trim()
        if (prompt.isEmpty()) return

        val state = _uiState.value
        val activeKey = state.activeApiKey
        if (activeKey == null) {
            appendErrorMessage("No active Gemini API key configured. Open Settings to add a key.")
            return
        }

        if (state.isAgentBusy) return

        // If user starts a fresh prompt while old approvals are hanging, resolve them as rejected
        if (pendingApprovalCallIds.isNotEmpty()) {
            flushUnresolvedApprovalsAsRejected()
        }

        appendMessage(
            ChatMessage(
                id = newId(),
                role = ChatRole.USER,
                text = prompt,
                timestamp = nowTimestamp()
            )
        )

        conversationContents.put(
            JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            }
        )

        viewModelScope.launch {
            runGeminiTurnLoop(depth = 0)
        }
    }

    fun clearChatHistory() {
        while (conversationContents.length() > 0) {
            conversationContents.remove(conversationContents.length() - 1)
        }
        currentBatchToolOrder.clear()
        currentBatchResponses.clear()
        pendingApprovalCallIds.clear()
        _uiState.update {
            it.copy(
                messages = emptyList(),
                isAgentBusy = false,
                agentStatusMessage = null
            )
        }
    }

    fun approveToolCall(callId: String) {
        val targetMsg = _uiState.value.messages.firstOrNull { it.toolCall?.id == callId } ?: return
        val toolCall = targetMsg.toolCall ?: return
        if (toolCall.status != ActionStatus.PENDING) return

        viewModelScope.launch {
            _uiState.update { it.copy(isAgentBusy = true, agentStatusMessage = "Executing ${toolCall.name}...") }

            val resultPayload = when (toolCall.name) {
                GeminiApiClient.TOOL_WRITE_FILE -> executeWriteFileTool(toolCall)
                GeminiApiClient.TOOL_RUN_TERMUX -> executeRunTermuxTool(toolCall)
                else -> JSONObject().put("status", "error").put("message", "Unknown tool: ${toolCall.name}")
            }

            val outputSummary = resultPayload.optString("message", resultPayload.toString())
            updateToolCallCard(callId, ActionStatus.APPROVED, outputSummary)

            currentBatchResponses[callId] = resultPayload
            pendingApprovalCallIds.remove(callId)

            if (pendingApprovalCallIds.isEmpty() && currentBatchToolOrder.isNotEmpty()) {
                submitBatchFunctionResponsesAndContinue()
            } else {
                _uiState.update {
                    it.copy(
                        isAgentBusy = false,
                        agentStatusMessage = if (pendingApprovalCallIds.isNotEmpty()) {
                            "Waiting for remaining action approvals"
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }

    fun rejectToolCall(callId: String) {
        val targetMsg = _uiState.value.messages.firstOrNull { it.toolCall?.id == callId } ?: return
        val toolCall = targetMsg.toolCall ?: return
        if (toolCall.status != ActionStatus.PENDING) return

        val rejectPayload = JSONObject()
            .put("status", "rejected_by_user")
            .put("message", "User rejected execution of ${toolCall.name}.")

        updateToolCallCard(callId, ActionStatus.REJECTED, "Action rejected by user.")
        currentBatchResponses[callId] = rejectPayload
        pendingApprovalCallIds.remove(callId)

        if (pendingApprovalCallIds.isEmpty() && currentBatchToolOrder.isNotEmpty()) {
            viewModelScope.launch {
                submitBatchFunctionResponsesAndContinue()
            }
        }
    }

    private suspend fun runGeminiTurnLoop(depth: Int) {
        if (depth >= MAX_AGENT_TOOL_ROUNDS) {
            appendErrorMessage("Agent paused after reaching maximum consecutive tool calls.")
            _uiState.update { it.copy(isAgentBusy = false, agentStatusMessage = null) }
            return
        }

        val state = _uiState.value
        val activeKey = state.activeApiKey
        if (activeKey == null) {
            appendErrorMessage("Missing active Gemini API key.")
            _uiState.update { it.copy(isAgentBusy = false, agentStatusMessage = null) }
            return
        }

        _uiState.update {
            it.copy(
                isAgentBusy = true,
                agentStatusMessage = if (depth == 0) "Contacting ${state.activeModel}..." else "Analyzing tool output..."
            )
        }

        val result = geminiClient.generateAgentResponse(
            apiKey = activeKey.key,
            model = state.activeModel,
            systemInstruction = state.systemInstruction,
            contents = conversationContents
        )

        result.onFailure { err ->
            appendErrorMessage(err.localizedMessage ?: "Failed to reach Gemini API.")
            _uiState.update { it.copy(isAgentBusy = false, agentStatusMessage = null) }
            return
        }

        val turn = result.getOrThrow()

        // Append model turn into conversation history
        if (turn.rawModelParts.length() > 0) {
            conversationContents.put(
                JSONObject().apply {
                    put("role", "model")
                    put("parts", turn.rawModelParts)
                }
            )
        }

        if (turn.text.isNotBlank()) {
            appendMessage(
                ChatMessage(
                    id = newId(),
                    role = ChatRole.MODEL,
                    text = turn.text,
                    timestamp = nowTimestamp()
                )
            )
        }

        if (turn.functionCalls.isEmpty()) {
            _uiState.update { it.copy(isAgentBusy = false, agentStatusMessage = null) }
            return
        }

        // Prepare batch tracking for all function calls in this model turn
        currentBatchToolOrder.clear()
        currentBatchResponses.clear()
        pendingApprovalCallIds.clear()

        for (call in turn.functionCalls) {
            val callId = newId()
            currentBatchToolOrder.add(callId to call.name)

            when (call.name) {
                GeminiApiClient.TOOL_LIST_FILES -> {
                    val responseJson = executeListFilesTool()
                    currentBatchResponses[callId] = responseJson
                    appendToolLogMessage(callId, call, responseJson.optString("summary", "Listed workspace files"))
                }

                GeminiApiClient.TOOL_READ_FILE -> {
                    val path = call.args.optString("path")
                    val responseJson = executeReadFileTool(path)
                    currentBatchResponses[callId] = responseJson
                    val logSummary = if (responseJson.optString("status") == "ok") {
                        "Read $path (${responseJson.optInt("length")} chars)"
                    } else {
                        responseJson.optString("message", "Failed to read $path")
                    }
                    appendToolLogMessage(callId, call, logSummary, path = path)
                }

                GeminiApiClient.TOOL_WRITE_FILE -> {
                    val path = call.args.optString("path")
                    val content = call.args.optString("content")
                    pendingApprovalCallIds.add(callId)
                    appendMessage(
                        ChatMessage(
                            id = newId(),
                            role = ChatRole.MODEL,
                            text = "",
                            timestamp = nowTimestamp(),
                            toolCall = AgentToolCall(
                                id = callId,
                                name = call.name,
                                argsJson = call.args.toString(),
                                path = path,
                                content = content,
                                status = ActionStatus.PENDING
                            )
                        )
                    )
                }

                GeminiApiClient.TOOL_RUN_TERMUX -> {
                    val command = call.args.optString("command")
                    val workdir = call.args.optString("workdir")
                        .ifBlank { _uiState.value.termuxWorkdir }
                    pendingApprovalCallIds.add(callId)
                    appendMessage(
                        ChatMessage(
                            id = newId(),
                            role = ChatRole.MODEL,
                            text = "",
                            timestamp = nowTimestamp(),
                            toolCall = AgentToolCall(
                                id = callId,
                                name = call.name,
                                argsJson = call.args.toString(),
                                command = command,
                                workdir = workdir,
                                status = ActionStatus.PENDING
                            )
                        )
                    )
                }

                else -> {
                    val errJson = JSONObject()
                        .put("status", "error")
                        .put("message", "Unsupported tool requested: ${call.name}")
                    currentBatchResponses[callId] = errJson
                }
            }
        }

        if (pendingApprovalCallIds.isEmpty()) {
            submitBatchFunctionResponsesAndContinue(nextDepth = depth + 1)
        } else {
            _uiState.update {
                it.copy(
                    isAgentBusy = false,
                    agentStatusMessage = "Action approval required"
                )
            }
        }
    }

    private suspend fun submitBatchFunctionResponsesAndContinue(nextDepth: Int = 1) {
        val parts = JSONArray()
        for ((callId, toolName) in currentBatchToolOrder) {
            val responseObj = currentBatchResponses[callId]
                ?: JSONObject().put("status", "error").put("message", "Missing tool result")
            parts.put(
                JSONObject().put(
                    "functionResponse",
                    JSONObject()
                        .put("name", toolName)
                        .put("response", responseObj)
                )
            )
        }

        currentBatchToolOrder.clear()
        currentBatchResponses.clear()
        pendingApprovalCallIds.clear()

        if (parts.length() > 0) {
            conversationContents.put(
                JSONObject().apply {
                    put("role", "user")
                    put("parts", parts)
                }
            )
            runGeminiTurnLoop(depth = nextDepth)
        }
    }

    private fun flushUnresolvedApprovalsAsRejected() {
        val parts = JSONArray()
        for ((callId, toolName) in currentBatchToolOrder) {
            val responseObj = if (pendingApprovalCallIds.contains(callId)) {
                updateToolCallCard(callId, ActionStatus.REJECTED, "Skipped due to new user prompt.")
                JSONObject().put("status", "rejected_by_user").put("message", "Skipped by user.")
            } else {
                currentBatchResponses[callId] ?: JSONObject().put("status", "ok")
            }
            parts.put(
                JSONObject().put(
                    "functionResponse",
                    JSONObject().put("name", toolName).put("response", responseObj)
                )
            )
        }
        if (parts.length() > 0) {
            conversationContents.put(
                JSONObject().apply {
                    put("role", "user")
                    put("parts", parts)
                }
            )
        }
        currentBatchToolOrder.clear()
        currentBatchResponses.clear()
        pendingApprovalCallIds.clear()
    }

    private suspend fun executeListFilesTool(): JSONObject {
        val state = _uiState.value
        val files = workspaceRepo.listFiles(state.workspaceMode, state.safTreeUri)
        _uiState.update { it.copy(files = files) }
        val fileArray = JSONArray()
        files.forEach { f ->
            fileArray.put(
                JSONObject()
                    .put("name", f.name)
                    .put("sizeBytes", f.sizeBytes)
                    .put("isDirectory", f.isDirectory)
            )
        }
        return JSONObject()
            .put("status", "ok")
            .put("workspace", state.workspaceDisplayPath)
            .put("files", fileArray)
            .put("summary", "Found ${files.size} item(s) in ${state.workspaceDisplayPath}")
    }

    private suspend fun executeReadFileTool(path: String): JSONObject {
        val state = _uiState.value
        val readResult = workspaceRepo.readFileByName(path, state.workspaceMode, state.safTreeUri)
        return readResult.fold(
            onSuccess = { content ->
                JSONObject()
                    .put("status", "ok")
                    .put("path", path)
                    .put("length", content.length)
                    .put("content", content)
            },
            onFailure = { err ->
                JSONObject()
                    .put("status", "error")
                    .put("path", path)
                    .put("message", err.localizedMessage ?: "Unable to read file.")
            }
        )
    }

    private suspend fun executeWriteFileTool(toolCall: AgentToolCall): JSONObject {
        val state = _uiState.value
        val targetPath = toolCall.path.orEmpty()
        val content = toolCall.content.orEmpty()
        val writeResult = workspaceRepo.writeOrCreateFile(
            fileName = targetPath,
            content = content,
            mode = state.workspaceMode,
            safTreeUri = state.safTreeUri
        )
        return writeResult.fold(
            onSuccess = { savedFile ->
                refreshWorkspaceFiles()
                if (_uiState.value.selectedFile?.name == savedFile.name) {
                    _uiState.update {
                        it.copy(
                            selectedFile = savedFile,
                            editorContent = content,
                            isEditorDirty = false
                        )
                    }
                }
                JSONObject()
                    .put("status", "ok")
                    .put("path", savedFile.name)
                    .put("sizeBytes", savedFile.sizeBytes)
                    .put("message", "Wrote ${savedFile.name} (${savedFile.sizeBytes} bytes) successfully.")
            },
            onFailure = { err ->
                JSONObject()
                    .put("status", "error")
                    .put("path", targetPath)
                    .put("message", err.localizedMessage ?: "Failed to write file.")
            }
        )
    }

    private fun executeRunTermuxTool(toolCall: AgentToolCall): JSONObject {
        val command = toolCall.command.orEmpty()
        val workdir = toolCall.workdir.orEmpty().ifBlank { _uiState.value.termuxWorkdir }
        val dispatchResult = dispatchTermuxCommandInternal(command, workdir)
        return JSONObject()
            .put("status", if (dispatchResult.dispatchedDirectly) "dispatched" else "fallback")
            .put("command", command)
            .put("workdir", workdir)
            .put("message", dispatchResult.statusMessage)
    }

    private fun appendToolLogMessage(
        callId: String,
        call: ParsedFunctionCall,
        summary: String,
        path: String? = null
    ) {
        appendMessage(
            ChatMessage(
                id = newId(),
                role = ChatRole.TOOL_LOG,
                text = summary,
                timestamp = nowTimestamp(),
                toolCall = AgentToolCall(
                    id = callId,
                    name = call.name,
                    argsJson = call.args.toString(),
                    path = path,
                    status = ActionStatus.APPROVED,
                    resultOutput = summary
                )
            )
        )
    }

    private fun updateToolCallCard(callId: String, status: ActionStatus, resultOutput: String) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { msg ->
                    if (msg.toolCall?.id == callId) {
                        msg.copy(
                            toolCall = msg.toolCall.copy(
                                status = status,
                                resultOutput = resultOutput
                            )
                        )
                    } else {
                        msg
                    }
                }
            )
        }
    }

    private fun appendMessage(message: ChatMessage) {
        _uiState.update { it.copy(messages = it.messages + message) }
    }

    private fun appendErrorMessage(text: String) {
        appendMessage(
            ChatMessage(
                id = newId(),
                role = ChatRole.ERROR,
                text = text,
                timestamp = nowTimestamp()
            )
        )
    }

    // -------------------------------------------------------------------------
    // WORKSPACE & CODE EDITOR
    // -------------------------------------------------------------------------

    fun onSafFolderPicked(uri: Uri) {
        workspaceRepo.persistSafPermission(uri)
        val uriString = uri.toString()
        settingsRepo.saveSafTreeUri(uriString)
        val displayPath = workspaceRepo.formatSafDisplayPath(uriString)
        _uiState.update {
            it.copy(
                workspaceMode = WorkspaceMode.SAF_TREE,
                safTreeUri = uriString,
                workspaceDisplayPath = displayPath,
                selectedFile = null,
                editorContent = "",
                isEditorDirty = false,
                workspaceNotice = "Workspace switched to $displayPath"
            )
        }
        refreshWorkspaceFiles()
    }

    fun switchToInternalWorkspace() {
        settingsRepo.saveSafTreeUri(null)
        _uiState.update {
            it.copy(
                workspaceMode = WorkspaceMode.INTERNAL,
                safTreeUri = null,
                workspaceDisplayPath = "filesDir/workspace",
                selectedFile = null,
                editorContent = "",
                isEditorDirty = false,
                workspaceNotice = "Switched to internal sandbox workspace"
            )
        }
        refreshWorkspaceFiles()
    }

    fun refreshWorkspaceFiles() {
        viewModelScope.launch {
            val state = _uiState.value
            val files = workspaceRepo.listFiles(state.workspaceMode, state.safTreeUri)
            _uiState.update { it.copy(files = files) }
        }
    }

    fun openFileInEditor(file: WorkspaceFile) {
        if (file.isDirectory) {
            _uiState.update { it.copy(workspaceNotice = "Directory preview is not editable.") }
            return
        }
        viewModelScope.launch {
            val result = workspaceRepo.readFile(file)
            result.onSuccess { text ->
                _uiState.update {
                    it.copy(
                        selectedFile = file,
                        editorContent = text,
                        isEditorDirty = false,
                        workspaceNotice = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(workspaceNotice = err.localizedMessage ?: "Unable to open file.")
                }
            }
        }
    }

    fun closeEditor() {
        _uiState.update {
            it.copy(
                selectedFile = null,
                editorContent = "",
                isEditorDirty = false
            )
        }
    }

    fun updateEditorContent(newContent: String) {
        _uiState.update {
            it.copy(
                editorContent = newContent,
                isEditorDirty = true
            )
        }
    }

    fun saveCurrentFile() {
        val file = _uiState.value.selectedFile ?: return
        val content = _uiState.value.editorContent
        viewModelScope.launch {
            val result = workspaceRepo.writeFile(file, content)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isEditorDirty = false,
                        workspaceNotice = "Saved ${file.name}"
                    )
                }
                refreshWorkspaceFiles()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(workspaceNotice = "Save failed: ${err.localizedMessage}")
                }
            }
        }
    }

    fun createNewFile(fileName: String) {
        val cleanName = fileName.trim()
        if (cleanName.isEmpty()) return
        val starter = when {
            cleanName.endsWith(".py") -> "#!/usr/bin/env python3\n\nprint(\"Hello from $cleanName\")\n"
            cleanName.endsWith(".sh") -> "#!/usr/bin/env bash\n\necho \"Running $cleanName\"\n"
            cleanName.endsWith(".md") -> "# ${cleanName.removeSuffix(".md")}\n\n"
            else -> ""
        }
        viewModelScope.launch {
            val state = _uiState.value
            val result = workspaceRepo.writeOrCreateFile(
                fileName = cleanName,
                content = starter,
                mode = state.workspaceMode,
                safTreeUri = state.safTreeUri
            )
            result.onSuccess { created ->
                refreshWorkspaceFiles()
                _uiState.update {
                    it.copy(
                        selectedFile = created,
                        editorContent = starter,
                        isEditorDirty = false,
                        workspaceNotice = "Created ${created.name}"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(workspaceNotice = err.localizedMessage ?: "Failed to create file.")
                }
            }
        }
    }

    fun runSelectedFileInTermux() {
        val state = _uiState.value
        val file = state.selectedFile ?: return
        val content = state.editorContent
        val workdir = state.termuxWorkdir.ifBlank { IdeUiState.DEFAULT_TERMUX_WORKDIR }

        viewModelScope.launch {
            if (state.isEditorDirty) {
                workspaceRepo.writeFile(file, content)
                _uiState.update { it.copy(isEditorDirty = false) }
                refreshWorkspaceFiles()
            }

            val safeName = file.name.replace("'", "")
            val runner = when {
                safeName.endsWith(".py", ignoreCase = true) -> "python3 '$safeName'"
                safeName.endsWith(".sh", ignoreCase = true) -> "bash '$safeName'"
                safeName.endsWith(".js", ignoreCase = true) -> "node '$safeName'"
                else -> "cat '$safeName'"
            }

            // Sync file content into Termux working directory before executing so sandbox files run seamlessly
            val command = buildString {
                append("cat << 'ANTIGRAVITY_EOF' > '")
                append(safeName)
                append("'\n")
                append(content)
                if (!content.endsWith("\n")) append("\n")
                append("ANTIGRAVITY_EOF\n")
                append(runner)
            }

            val dispatchResult = dispatchTermuxCommandInternal(command, workdir)
            _uiState.update {
                it.copy(workspaceNotice = "${file.name}: ${dispatchResult.statusMessage}")
            }
        }
    }

    fun clearWorkspaceNotice() {
        _uiState.update { it.copy(workspaceNotice = null) }
    }

    // -------------------------------------------------------------------------
    // TERMUX TERMINAL BRIDGE
    // -------------------------------------------------------------------------

    fun refreshTermuxStatus() {
        val installed = termuxBridge.isTermuxInstalled()
        _uiState.update { it.copy(isTermuxInstalled = installed) }
    }

    fun openTermuxApp() {
        val launched = termuxBridge.openTermux()
        if (!launched) {
            appendTerminalLog(
                command = "open com.termux",
                workdir = _uiState.value.termuxWorkdir,
                status = "Termux package not found on this device.",
                isError = true
            )
        }
    }

    fun runTermuxCommand(command: String) {
        val clean = command.trim()
        if (clean.isEmpty()) return
        val workdir = _uiState.value.termuxWorkdir
        dispatchTermuxCommandInternal(clean, workdir)
    }

    private fun dispatchTermuxCommandInternal(
        command: String,
        workdir: String
    ): com.example.data.TermuxDispatchResult {
        val installed = termuxBridge.isTermuxInstalled()
        val result = termuxBridge.executeCommand(command, workdir)
        val displayCmd = if (command.contains("ANTIGRAVITY_EOF")) {
            val runnerLine = command.lines().lastOrNull().orEmpty()
            "sync & $runnerLine"
        } else {
            command
        }
        _uiState.update { it.copy(isTermuxInstalled = installed) }
        appendTerminalLog(
            command = displayCmd,
            workdir = workdir,
            status = result.statusMessage,
            isError = result.isError
        )
        return result
    }

    private fun appendTerminalLog(command: String, workdir: String, status: String, isError: Boolean) {
        val entry = TerminalLogEntry(
            id = newId(),
            timestamp = nowTimestamp(),
            command = command,
            workdir = workdir,
            status = status,
            isError = isError
        )
        _uiState.update { it.copy(terminalLogs = listOf(entry) + it.terminalLogs) }
    }

    fun clearTerminalLogs() {
        _uiState.update { it.copy(terminalLogs = emptyList()) }
    }

    // -------------------------------------------------------------------------
    // SETTINGS & MULTI-KEY GEMINI API MANAGER
    // -------------------------------------------------------------------------

    fun addOrUpdateApiKey(existingId: String?, label: String, keyValue: String) {
        val cleanLabel = label.trim().ifBlank { "Gemini Key" }
        val cleanKey = keyValue.trim()
        if (cleanKey.isEmpty()) return

        val currentKeys = _uiState.value.apiKeys
        val updatedKeys: List<ApiKeyEntry>
        val newActiveId: String

        if (existingId != null && currentKeys.any { it.id == existingId }) {
            updatedKeys = currentKeys.map { entry ->
                if (entry.id == existingId) entry.copy(label = cleanLabel, key = cleanKey) else entry
            }
            newActiveId = _uiState.value.activeApiKeyId ?: existingId
        } else {
            val created = ApiKeyEntry(id = newId(), label = cleanLabel, key = cleanKey)
            updatedKeys = currentKeys + created
            newActiveId = _uiState.value.activeApiKeyId ?: created.id
        }

        settingsRepo.saveApiKeys(updatedKeys)
        settingsRepo.saveActiveApiKeyId(newActiveId)
        _uiState.update {
            it.copy(
                apiKeys = updatedKeys,
                activeApiKeyId = newActiveId,
                apiTestState = ApiTestState(),
                settingsNotice = if (existingId != null) "Updated API key '$cleanLabel'" else "Saved API key '$cleanLabel'"
            )
        }
    }

    fun selectActiveApiKey(id: String) {
        settingsRepo.saveActiveApiKeyId(id)
        _uiState.update {
            it.copy(
                activeApiKeyId = id,
                apiTestState = ApiTestState(),
                settingsNotice = null
            )
        }
    }

    fun deleteApiKey(id: String) {
        val updated = _uiState.value.apiKeys.filterNot { it.id == id }
        val nextActive = if (_uiState.value.activeApiKeyId == id) {
            updated.firstOrNull()?.id
        } else {
            _uiState.value.activeApiKeyId
        }
        settingsRepo.saveApiKeys(updated)
        settingsRepo.saveActiveApiKeyId(nextActive)
        _uiState.update {
            it.copy(
                apiKeys = updated,
                activeApiKeyId = nextActive,
                apiTestState = ApiTestState(),
                settingsNotice = "API key removed"
            )
        }
    }

    fun testActiveApiKey() {
        val state = _uiState.value
        val active = state.activeApiKey
        if (active == null) {
            _uiState.update {
                it.copy(
                    apiTestState = ApiTestState(
                        isTesting = false,
                        success = false,
                        message = "Add and select an API key first."
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(apiTestState = ApiTestState(isTesting = true))
            }
            val ping = geminiClient.testApiKey(active.key, state.activeModel)
            _uiState.update {
                it.copy(
                    apiTestState = ApiTestState(
                        isTesting = false,
                        success = ping.success,
                        message = ping.message,
                        latencyMs = ping.latencyMs
                    )
                )
            }
        }
    }

    fun updateActiveModel(modelId: String) {
        settingsRepo.saveActiveModel(modelId)
        _uiState.update {
            it.copy(
                activeModel = modelId,
                apiTestState = ApiTestState(),
                settingsNotice = "Active model switched to $modelId"
            )
        }
    }

    fun saveAgentConfiguration(systemInstruction: String, termuxWorkdir: String) {
        val cleanInstruction = systemInstruction.trim().ifBlank { IdeUiState.DEFAULT_SYSTEM_INSTRUCTION }
        val cleanWorkdir = termuxWorkdir.trim().ifBlank { IdeUiState.DEFAULT_TERMUX_WORKDIR }
        settingsRepo.saveSystemInstruction(cleanInstruction)
        settingsRepo.saveTermuxWorkdir(cleanWorkdir)
        _uiState.update {
            it.copy(
                systemInstruction = cleanInstruction,
                termuxWorkdir = cleanWorkdir,
                settingsNotice = "Configuration saved"
            )
        }
    }

    fun clearSettingsNotice() {
        _uiState.update { it.copy(settingsNotice = null) }
    }

    private fun newId(): String = UUID.randomUUID().toString()

    private fun nowTimestamp(): String {
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.US)
        return formatter.format(Date())
    }

    class Factory(private val appContext: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val ctx = appContext.applicationContext
            return IdeViewModel(
                settingsRepo = SettingsRepository(ctx),
                workspaceRepo = WorkspaceRepository(ctx),
                termuxBridge = TermuxBridge(ctx),
                geminiClient = GeminiApiClient()
            ) as T
        }
    }

    companion object {
        private const val MAX_AGENT_TOOL_ROUNDS = 6
    }
}
