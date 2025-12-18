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
     */
    fun executeRootCommand(command: String): CommandResult {
        var process: Process? = null
        var os: DataOutputStream? = null
        val output = mutableListOf<String>()
        val error = mutableListOf<String>()

        try {
            process = Runtime.getRuntime().exec("su")
            os = DataOutputStream(process.outputStream)
            os.writeBytes(command + "\n")
            os.writeBytes("exit\n")
            os.flush()

            val stdInput = BufferedReader(InputStreamReader(process.inputStream))
            val stdError = BufferedReader(InputStreamReader(process.errorStream))

            var s: String?
            while (stdInput.readLine().also { s = it } != null) {
                if (s != null) output.add(s!!)
            }
            while (stdError.readLine().also { s = it } != null) {
                if (s != null) error.add(s!!)
            }

            process.waitFor()
        } catch (e: Exception) {
            error.add(e.message ?: "Unknown error")
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
            error = error
        )
    }
}
