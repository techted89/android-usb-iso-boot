package com.techtedapps.bootmaster.data

import com.techtedapps.bootmaster.utils.ShellUtils

data class FileItem(
    val name: String,
    val size: String,
    val isDirectory: Boolean,
    val path: String
)

class FileRepository {

    private val baseMountPath = "/data/local/tmp/bootmaster_mnt/usb_data"

    fun listFiles(relativePath: String = ""): List<FileItem> {
        val targetPath = if (relativePath.isEmpty()) baseMountPath else "$baseMountPath/$relativePath"
        val items = mutableListOf<FileItem>()

        // ls -l to get details
        val res = ShellUtils.executeRootCommand("ls -l \"$targetPath\"")
        if (res.exitCode == 0) {
            for (line in res.output) {
                // Parse ls -l output (simplified)
                // drwxrwxrwx 1 root root 4096 ... name
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size >= 8) {
                    val perms = parts[0]
                    val size = parts[4] // rough guess on standard busybox ls -l col
                    // Name is the rest
                    // Date/Time usually takes 3 cols (Nov 11 12:00)
                    // So name starts at index 8
                    val name = parts.subList(8, parts.size).joinToString(" ")

                    if (name == "." || name == "..") continue

                    val isDir = perms.startsWith("d")
                    items.add(FileItem(name, size, isDir, "$relativePath/$name".trimStart('/')))
                }
            }
        }
        return items
    }

    fun deleteFile(relativePath: String): Boolean {
        val targetPath = "$baseMountPath/$relativePath"
        val res = ShellUtils.executeRootCommand("rm -rf \"$targetPath\"")
        return res.exitCode == 0
    }

    fun copyFileToUsb(sourcePath: String, relativeDestPath: String): Boolean {
        val dest = "$baseMountPath/$relativeDestPath"
        // Ensure dir exists
        ShellUtils.executeRootCommand("mkdir -p \"$(dirname \"$dest\")\"")
        val res = ShellUtils.executeRootCommand("cp \"$sourcePath\" \"$dest\"")
        return res.exitCode == 0
    }
}
