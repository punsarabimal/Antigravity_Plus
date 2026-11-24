package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.example.model.WorkspaceFile
import com.example.model.WorkspaceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class WorkspaceRepository(private val context: Context) {

    val internalWorkspaceDir: File
        get() = File(context.filesDir, "workspace")

    suspend fun ensureDefaultWorkspacePopulated() = withContext(Dispatchers.IO) {
        val dir = internalWorkspaceDir
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val existing = dir.listFiles()
        if (existing == null || existing.isEmpty()) {
            File(dir, "main.py").writeText(DEFAULT_MAIN_PY)
            File(dir, "README.md").writeText(DEFAULT_README_MD)
        }
    }

    fun persistSafPermission(treeUri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        } catch (_: SecurityException) {
            // Permission may already be persisted or temporary; proceed gracefully.
        }
    }

    fun formatSafDisplayPath(treeUriString: String): String {
        return try {
            val uri = Uri.parse(treeUriString)
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val cleaned = docId.substringAfter(":", docId)
            if (cleaned.isBlank()) "External Folder" else "SAF:/$cleaned"
        } catch (_: Exception) {
            "SAF Workspace"
        }
    }

    suspend fun listFiles(mode: WorkspaceMode, safTreeUri: String?): List<WorkspaceFile> =
        withContext(Dispatchers.IO) {
            if (mode == WorkspaceMode.SAF_TREE && !safTreeUri.isNullOrBlank()) {
                listSafFiles(Uri.parse(safTreeUri))
            } else {
                ensureDefaultWorkspacePopulated()
                listInternalFiles()
            }
        }

    private fun listInternalFiles(): List<WorkspaceFile> {
        val dir = internalWorkspaceDir
        val children = dir.listFiles() ?: return emptyList()
        return children
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            .map { file ->
                WorkspaceFile(
                    name = file.name,
                    pathOrUri = file.absolutePath,
                    sizeBytes = if (file.isFile) file.length() else 0L,
                    isDirectory = file.isDirectory,
                    isSaf = false
                )
            }
    }

    private fun listSafFiles(treeUri: Uri): List<WorkspaceFile> {
        val results = mutableListOf<WorkspaceFile>()
        try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                while (cursor.moveToNext()) {
                    val childId = if (idIdx >= 0) cursor.getString(idIdx) else continue
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: continue else continue
                    val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) ?: "" else ""
                    val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                    val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                    results.add(
                        WorkspaceFile(
                            name = name,
                            pathOrUri = childUri.toString(),
                            sizeBytes = size,
                            isDirectory = isDir,
                            isSaf = true
                        )
                    )
                }
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return results.sortedWith(compareByDescending<WorkspaceFile> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    suspend fun readFile(file: WorkspaceFile): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (file.isDirectory) {
                return@withContext Result.failure(IllegalArgumentException("Cannot open directory in text editor."))
            }
            val text = if (file.isSaf) {
                val uri = Uri.parse(file.pathOrUri)
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: return@withContext Result.failure(IllegalStateException("Unable to open SAF input stream."))
            } else {
                File(file.pathOrUri).readText()
            }
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readFileByName(
        fileName: String,
        mode: WorkspaceMode,
        safTreeUri: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        val sanitized = sanitizeFileName(fileName)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid file name: $fileName"))
        val files = listFiles(mode, safTreeUri)
        val match = files.firstOrNull { !it.isDirectory && it.name == sanitized }
            ?: return@withContext Result.failure(IllegalArgumentException("File not found in workspace: $sanitized"))
        readFile(match)
    }

    suspend fun writeFile(file: WorkspaceFile, content: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                if (file.isSaf) {
                    val uri = Uri.parse(file.pathOrUri)
                    context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use {
                        it.write(content)
                    } ?: return@withContext Result.failure(IllegalStateException("Unable to open SAF output stream."))
                } else {
                    File(file.pathOrUri).writeText(content)
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun writeOrCreateFile(
        fileName: String,
        content: String,
        mode: WorkspaceMode,
        safTreeUri: String?
    ): Result<WorkspaceFile> = withContext(Dispatchers.IO) {
        val sanitized = sanitizeFileName(fileName)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid file name: $fileName"))
        try {
            if (mode == WorkspaceMode.SAF_TREE && !safTreeUri.isNullOrBlank()) {
                val treeUri = Uri.parse(safTreeUri)
                val existing = listSafFiles(treeUri).firstOrNull { !it.isDirectory && it.name == sanitized }
                if (existing != null) {
                    writeFile(existing, content).getOrThrow()
                    Result.success(existing.copy(sizeBytes = content.toByteArray().size.toLong()))
                } else {
                    val parentDocId = DocumentsContract.getTreeDocumentId(treeUri)
                    val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
                    val mime = guessMimeType(sanitized)
                    val createdUri = DocumentsContract.createDocument(
                        context.contentResolver,
                        parentUri,
                        mime,
                        sanitized
                    ) ?: return@withContext Result.failure(IllegalStateException("Failed to create SAF file: $sanitized"))
                    context.contentResolver.openOutputStream(createdUri, "wt")?.bufferedWriter()?.use {
                        it.write(content)
                    }
                    Result.success(
                        WorkspaceFile(
                            name = sanitized,
                            pathOrUri = createdUri.toString(),
                            sizeBytes = content.toByteArray().size.toLong(),
                            isDirectory = false,
                            isSaf = true
                        )
                    )
                }
            } else {
                ensureDefaultWorkspacePopulated()
                val target = File(internalWorkspaceDir, sanitized)
                target.writeText(content)
                Result.success(
                    WorkspaceFile(
                        name = target.name,
                        pathOrUri = target.absolutePath,
                        sizeBytes = target.length(),
                        isDirectory = false,
                        isSaf = false
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun sanitizeFileName(raw: String): String? {
        val trimmed = raw.trim().removePrefix("./").removePrefix("/")
        if (trimmed.isBlank() || trimmed.contains("..") || trimmed.contains("/") || trimmed.contains("\\")) {
            return null
        }
        return trimmed
    }

    private fun guessMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "py", "sh", "kt", "java", "js", "ts", "md", "txt", "yaml", "yml", "xml" -> "text/plain"
            "json" -> "application/json"
            "html" -> "text/html"
            else -> "text/plain"
        }
    }

    companion object {
        private val DEFAULT_MAIN_PY = """
            #!/usr/bin/env python3
            \"\"\"Antigravity Studio starter workspace script.\"\"\"

            import platform
            import sys


            def greet(target: str = "Antigravity Studio") -> str:
                return f"Hello from {target} on {platform.machine()}!"


            def fibonacci(count: int) -> list[int]:
                seq = [0, 1]
                while len(seq) < count:
                    seq.append(seq[-1] + seq[-2])
                return seq[:count]


            if __name__ == "__main__":
                print(greet())
                print("Python:", sys.version.split()[0])
                print("Fibonacci(8):", fibonacci(8))
        """.trimIndent() + "\n"

        private val DEFAULT_README_MD = """
            # Antigravity Studio Workspace

            Welcome to your local ARM64 Android coding sandbox.

            ## Included Files
            - `main.py`: Starter Python 3 script ready to edit or run via Termux.
            - `README.md`: Project overview and quick tips.

            ## Quick Workflow
            1. Open **Settings** to add a Gemini API key and choose your preferred model.
            2. Switch to **Agent** and tap **Inspect Project** to let Gemini read these files.
            3. Use **Pick Folder** in **Workspace** to bind any external project directory via Android SAF.
            4. Configure `~/.termux/termux.properties` with `allow-external-apps=true` for direct Termux command execution.
        """.trimIndent() + "\n"
    }
}
