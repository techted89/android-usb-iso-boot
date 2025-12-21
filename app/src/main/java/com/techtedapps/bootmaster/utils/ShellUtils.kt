package com.techtedapps.bootmaster.utils

import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

/**
 * Utility class to execute shell commands with Root privileges.
 */
object ShellUtils {

    data class CommandResult(
        val exitCode: Int,
        val output: List<String>,
        val error: List<String>
    )

    fun isRootAvailable(): Boolean {
        return try {
            val p = Runtime.getRuntime().exec("su -c ls")
            p.waitFor()
            p.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Executes a command as root.
     * Redirects stderr to stdout to avoid deadlocks on large output.
     */
    fun executeRootCommand(command: String): CommandResult {
        var process: Process? = null
        var os: DataOutputStream? = null
        val output = mutableListOf<String>()
        val error = mutableListOf<String>()

        try {
            process = Runtime.getRuntime().exec("su")
            os = DataOutputStream(process.outputStream)
            // Redirect stderr to stdout using 2>&1
            // Note: This merges stdout and stderr. If distinct error parsing is needed,
            // proper threaded stream reading is required.
            // For this app, preventing deadlock is priority.
            // We append 2>&1 to the command execution line.
            // However, since we are feeding into 'su' shell, we wrap the command.
            os.writeBytes("$command 2>&1\n")
            os.writeBytes("exit\n")
            os.flush()

            val stdInput = BufferedReader(InputStreamReader(process.inputStream))
            // Only read stdout (which now contains stderr)

            var s: String?
            while (stdInput.readLine().also { s = it } != null) {
                if (s != null) output.add(s!!)
            }

            process.waitFor()
        } catch (e: Exception) {
            output.add("Exception: ${e.message}")
        } finally {
            try {
                os?.close()
                process?.destroy()
            } catch (e: Exception) {
                // Ignore
            }
        }

        return CommandResult(
            exitCode = process?.exitValue() ?: -1,
            output = output,
            error = output // Map full output to error as well for debugging if exitCode != 0
        )
    }
}
