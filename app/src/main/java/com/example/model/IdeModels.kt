package com.example.model

data class ApiKeyEntry(
    val id: String,
    val label: String,
    val key: String
) {
    fun maskedKey(): String {
        val trimmed = key.trim()
        if (trimmed.length <= 10) return "********"
        val prefix = trimmed.take(4)
        val suffix = trimmed.takeLast(4)
        return "$prefix...$suffix"
    }
}

data class AgentModelOption(
    val id: String,
    val label: String,
    val badge: String
)

enum class WorkspaceMode {
    INTERNAL,
    SAF_TREE
}

data class WorkspaceFile(
    val name: String,
    val pathOrUri: String,
    val sizeBytes: Long,
    val isDirectory: Boolean = false,
    val isSaf: Boolean = false
)

enum class ActionStatus {
    PENDING,
    APPROVED,
    REJECTED
}

enum class ChatRole {
    USER,
    MODEL,
    TOOL_LOG,
    ERROR
}

data class AgentToolCall(
    val id: String,
    val name: String,
    val argsJson: String,
    val path: String? = null,
    val content: String? = null,
    val command: String? = null,
    val workdir: String? = null,
    val status: ActionStatus = ActionStatus.PENDING,
    val resultOutput: String? = null
)

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
    val timestamp: String,
    val toolCall: AgentToolCall? = null
)

data class TerminalLogEntry(
    val id: String,
    val timestamp: String,
    val command: String,
    val workdir: String,
    val status: String,
    val isError: Boolean = false
)

data class ApiTestState(
    val isTesting: Boolean = false,
    val success: Boolean? = null,
    val message: String? = null,
    val latencyMs: Long? = null
)

data class IdeUiState(
    // Agent state
    val messages: List<ChatMessage> = emptyList(),
    val isAgentBusy: Boolean = false,
    val agentStatusMessage: String? = null,

    // Workspace state
    val workspaceMode: WorkspaceMode = WorkspaceMode.INTERNAL,
    val workspaceDisplayPath: String = "filesDir/workspace",
    val safTreeUri: String? = null,
    val files: List<WorkspaceFile> = emptyList(),
    val selectedFile: WorkspaceFile? = null,
    val editorContent: String = "",
    val isEditorDirty: Boolean = false,
    val workspaceNotice: String? = null,

    // Terminal state
    val isTermuxInstalled: Boolean = false,
    val terminalLogs: List<TerminalLogEntry> = emptyList(),

    // Settings state
    val apiKeys: List<ApiKeyEntry> = emptyList(),
    val activeApiKeyId: String? = null,
    val activeModel: String = DEFAULT_MODEL,
    val systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    val termuxWorkdir: String = DEFAULT_TERMUX_WORKDIR,
    val apiTestState: ApiTestState = ApiTestState(),
    val settingsNotice: String? = null
) {
    val activeApiKey: ApiKeyEntry?
        get() = apiKeys.firstOrNull { it.id == activeApiKeyId } ?: apiKeys.firstOrNull()

    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash"
        const val DEFAULT_TERMUX_WORKDIR = "/data/data/com.termux/files/home"
        val AVAILABLE_MODELS = listOf(
            AgentModelOption("gemini-2.5-flash", "gemini-2.5-flash", "Fast & Agentic"),
            AgentModelOption("gemini-2.5-pro", "gemini-2.5-pro", "Deep Reasoning"),
            AgentModelOption("gemini-2.0-flash", "gemini-2.0-flash", "Lightweight")
        )
        const val DEFAULT_SYSTEM_INSTRUCTION =
            "You are Antigravity Studio, an autonomous mobile-first coding agent running on ARM64 Android. " +
                "You can inspect files with list_files and read_file, propose file edits with write_file, and propose Termux Linux shell commands with run_termux_command. " +
                "Write clean, concise, production-quality code and always explain your actions clearly."
    }
}
