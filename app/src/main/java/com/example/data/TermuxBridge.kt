package com.example.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

data class TermuxDispatchResult(
    val dispatchedDirectly: Boolean,
    val statusMessage: String,
    val isError: Boolean
)

class TermuxBridge(private val context: Context) {

    fun isTermuxInstalled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    TERMUX_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun openTermux(): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
                ?: return false
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun executeCommand(command: String, workdir: String): TermuxDispatchResult {
        val cleanCommand = command.trim()
        val cleanWorkdir = workdir.trim().ifBlank { DEFAULT_TERMUX_HOME }
        if (cleanCommand.isEmpty()) {
            return TermuxDispatchResult(
                dispatchedDirectly = false,
                statusMessage = "Command is empty.",
                isError = true
            )
        }

        if (!isTermuxInstalled()) {
            copyToClipboard(cleanCommand)
            return TermuxDispatchResult(
                dispatchedDirectly = false,
                statusMessage = "Termux not installed. Command copied to clipboard.",
                isError = true
            )
        }

        val intent = Intent().apply {
            action = ACTION_RUN_COMMAND
            setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
            putExtra(EXTRA_COMMAND_PATH, TERMUX_BASH_PATH)
            putExtra(EXTRA_ARGUMENTS, arrayOf("-c", cleanCommand))
            putExtra(EXTRA_WORKDIR, cleanWorkdir)
            putExtra(EXTRA_BACKGROUND, false)
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            TermuxDispatchResult(
                dispatchedDirectly = true,
                statusMessage = "Dispatched to Termux RunCommandService.",
                isError = false
            )
        } catch (e: Exception) {
            copyToClipboard(cleanCommand)
            val launched = openTermux()
            val detail = e.localizedMessage ?: e.javaClass.simpleName
            val fallbackMsg = if (launched) {
                "Background start restricted ($detail). Copied command to clipboard and opened Termux."
            } else {
                "Dispatch blocked ($detail). Copied command to clipboard. Check allow-external-apps=true in ~/.termux/termux.properties."
            }
            TermuxDispatchResult(
                dispatchedDirectly = false,
                statusMessage = fallbackMsg,
                isError = true
            )
        }
    }

    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Antigravity Command", text))
    }

    companion object {
        const val TERMUX_PACKAGE = "com.termux"
        const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
        const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
        const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
        const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        const val TERMUX_BASH_PATH = "/data/data/com.termux/files/usr/bin/bash"
        const val DEFAULT_TERMUX_HOME = "/data/data/com.termux/files/home"
    }
}
