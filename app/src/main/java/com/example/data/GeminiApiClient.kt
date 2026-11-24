package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class ParsedFunctionCall(
    val name: String,
    val args: JSONObject
)

data class GeminiTurnResponse(
    val text: String,
    val functionCalls: List<ParsedFunctionCall>,
    val rawModelParts: JSONArray
)

data class ApiPingResult(
    val success: Boolean,
    val message: String,
    val latencyMs: Long
)

class GeminiApiClient {

    suspend fun testApiKey(apiKey: String, model: String): ApiPingResult =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            var connection: HttpURLConnection? = null
            try {
                val encodedKey = URLEncoder.encode(apiKey.trim(), "UTF-8")
                val encodedModel = URLEncoder.encode(model.trim(), "UTF-8")
                val endpoint =
                    "https://generativelanguage.googleapis.com/v1beta/models/$encodedModel?key=$encodedKey"
                connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    setRequestProperty("Accept", "application/json")
                }

                val code = connection.responseCode
                val latency = System.currentTimeMillis() - startTime
                val body = readStream(if (code in 200..299) connection.inputStream else connection.errorStream)

                if (code in 200..299) {
                    val json = JSONObject(body)
                    val displayName = json.optString("displayName", model)
                    ApiPingResult(
                        success = true,
                        message = "Connected to $displayName (HTTP $code)",
                        latencyMs = latency
                    )
                } else {
                    val errMsg = extractErrorMessage(body, code)
                    ApiPingResult(
                        success = false,
                        message = errMsg,
                        latencyMs = latency
                    )
                }
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - startTime
                ApiPingResult(
                    success = false,
                    message = "Network error: ${e.localizedMessage ?: e.javaClass.simpleName}",
                    latencyMs = latency
                )
            } finally {
                connection?.disconnect()
            }
        }

    suspend fun generateAgentResponse(
        apiKey: String,
        model: String,
        systemInstruction: String,
        contents: JSONArray
    ): Result<GeminiTurnResponse> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val encodedKey = URLEncoder.encode(apiKey.trim(), "UTF-8")
            val encodedModel = URLEncoder.encode(model.trim(), "UTF-8")
            val endpoint =
                "https://generativelanguage.googleapis.com/v1beta/models/$encodedModel:generateContent?key=$encodedKey"

            val payload = JSONObject().apply {
                if (systemInstruction.isNotBlank()) {
                    put(
                        "systemInstruction",
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", systemInstruction))
                        )
                    )
                }
                put("contents", contents)
                put("tools", buildAgentToolsDeclaration())
            }

            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 20_000
                readTimeout = 60_000
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val code = connection.responseCode
            val responseText = readStream(
                if (code in 200..299) connection.inputStream else connection.errorStream
            )

            if (code !in 200..299) {
                return@withContext Result.failure(IllegalStateException(extractErrorMessage(responseText, code)))
            }

            val root = JSONObject(responseText)
            val candidates = root.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                val blockReason = root.optJSONObject("promptFeedback")?.optString("blockReason")
                val msg = if (!blockReason.isNullOrBlank()) {
                    "Response blocked by safety filter: $blockReason"
                } else {
                    "Gemini returned an empty response."
                }
                return@withContext Result.failure(IllegalStateException(msg))
            }

            val firstCandidate = candidates.getJSONObject(0)
            val contentObj = firstCandidate.optJSONObject("content")
            val parts = contentObj?.optJSONArray("parts") ?: JSONArray()

            val textBuilder = StringBuilder()
            val calls = mutableListOf<ParsedFunctionCall>()

            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                if (part.has("text")) {
                    val chunk = part.optString("text")
                    if (chunk.isNotEmpty()) {
                        if (textBuilder.isNotEmpty()) textBuilder.append("\n")
                        textBuilder.append(chunk)
                    }
                }
                val fc = part.optJSONObject("functionCall")
                if (fc != null) {
                    val name = fc.optString("name")
                    val args = fc.optJSONObject("args") ?: JSONObject()
                    if (name.isNotBlank()) {
                        calls.add(ParsedFunctionCall(name = name, args = args))
                    }
                }
            }

            Result.success(
                GeminiTurnResponse(
                    text = textBuilder.toString().trim(),
                    functionCalls = calls,
                    rawModelParts = parts
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    private fun buildAgentToolsDeclaration(): JSONArray {
        val declarations = JSONArray()

        // 1. list_files
        declarations.put(
            JSONObject().apply {
                put("name", TOOL_LIST_FILES)
                put("description", "Lists all files and folders in the active project workspace.")
                put(
                    "parameters",
                    JSONObject().apply {
                        put("type", "OBJECT")
                        put("properties", JSONObject())
                    }
                )
            }
        )

        // 2. read_file
        declarations.put(
            JSONObject().apply {
                put("name", TOOL_READ_FILE)
                put("description", "Reads the full UTF-8 text content of a file in the active project workspace.")
                put(
                    "parameters",
                    JSONObject().apply {
                        put("type", "OBJECT")
                        put(
                            "properties",
                            JSONObject().put(
                                "path",
                                JSONObject()
                                    .put("type", "STRING")
                                    .put("description", "The filename inside the active workspace, e.g. main.py")
                            )
                        )
                        put("required", JSONArray().put("path"))
                    }
                )
            }
        )

        // 3. write_file
        declarations.put(
            JSONObject().apply {
                put("name", TOOL_WRITE_FILE)
                put("description", "Proposes creating or updating a file in the active project workspace. Requires user approval.")
                put(
                    "parameters",
                    JSONObject().apply {
                        put("type", "OBJECT")
                        put(
                            "properties",
                            JSONObject()
                                .put(
                                    "path",
                                    JSONObject()
                                        .put("type", "STRING")
                                        .put("description", "The target filename in the workspace, e.g. utils.py")
                                )
                                .put(
                                    "content",
                                    JSONObject()
                                        .put("type", "STRING")
                                        .put("description", "The complete text content to write to the file.")
                                )
                        )
                        put("required", JSONArray().put("path").put("content"))
                    }
                )
            }
        )

        // 4. run_termux_command
        declarations.put(
            JSONObject().apply {
                put("name", TOOL_RUN_TERMUX)
                put("description", "Proposes executing a Linux shell command (bash, python3, git, ls, etc.) in Termux. Requires user approval.")
                put(
                    "parameters",
                    JSONObject().apply {
                        put("type", "OBJECT")
                        put(
                            "properties",
                            JSONObject()
                                .put(
                                    "command",
                                    JSONObject()
                                        .put("type", "STRING")
                                        .put("description", "Shell command to run inside bash -c in Termux.")
                                )
                                .put(
                                    "workdir",
                                    JSONObject()
                                        .put("type", "STRING")
                                        .put("description", "Optional working directory path in Termux.")
                                )
                        )
                        put("required", JSONArray().put("command"))
                    }
                )
            }
        )

        return JSONArray().put(
            JSONObject().put("functionDeclarations", declarations)
        )
    }

    private fun readStream(stream: java.io.InputStream?): String {
        if (stream == null) return ""
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            reader.readText()
        }
    }

    private fun extractErrorMessage(body: String, code: Int): String {
        return try {
            val json = JSONObject(body)
            val errObj = json.optJSONObject("error")
            val message = errObj?.optString("message").orEmpty()
            if (message.isNotBlank()) "HTTP $code: $message" else "HTTP $code error"
        } catch (_: Exception) {
            if (body.isNotBlank()) "HTTP $code: ${body.take(180)}" else "HTTP $code error"
        }
    }

    companion object {
        const val TOOL_LIST_FILES = "list_files"
        const val TOOL_READ_FILE = "read_file"
        const val TOOL_WRITE_FILE = "write_file"
        const val TOOL_RUN_TERMUX = "run_termux_command"
    }
}
