package com.techtedapps.bootmaster.workers

import android.content.Context
import android.net.Uri
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.techtedapps.bootmaster.utils.ShellUtils
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class BootableUsbWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    private val logBuffer = StringBuilder()

    override fun doWork(): Result {
        val isoUriString = inputData.getString("iso_uri")
        val usbDevice = inputData.getString("usb_device") // /dev/sdX
        val isTargetUefi = inputData.getBoolean("is_uefi", true) // Originally named is_uefi in ViewModel
        val isGpt = inputData.getBoolean("is_gpt", true)
        val persistenceGb = inputData.getInt("persistence_gb", 0)
        val addedFiles = inputData.getStringArray("added_files")

        if (isoUriString == null || usbDevice == null) {
            return Result.failure(workDataOf("error" to "Missing input data"))
        }

        // Safety: ensure usbDevice looks like a device path to prevent catastrophic commands
        if (!usbDevice.startsWith("/dev/")) {
             return Result.failure(workDataOf("error" to "Invalid device path"))
        }

        var isoFile: File? = null

        try {
            updateProgress(0, "Initializing...")

            // 1. Prepare Source File (Copy to local cache)
            updateProgress(5, "Preparing source file...")
            val uri = Uri.parse(isoUriString)
            // Determine extension from input URI if possible, or assume based on content?
            // Since we copied to "source.iso" hardcoded before, let's try to preserve extension
            // or pass extension as input data. For now, we'll try to guess or use generic.
            // Actually, we can just copy to a temp file and inspect the original URI string for ext.
            val originalPath = uri.path ?: ""
            val ext = if (originalPath.endsWith(".img") || originalPath.endsWith(".bin")) "img"
                      else if (originalPath.endsWith(".zip")) "zip"
                      else if (originalPath.endsWith(".gz")) "gz"
                      else if (originalPath.endsWith(".xz")) "xz"
                      else "iso"

            isoFile = File(applicationContext.cacheDir, "source.$ext")
            copyUriToFile(uri, isoFile!!)

            // 2. Unmount Drive
            updateProgress(10, "Unmounting drive...")
            exec("umount ${usbDevice}*") // Unmount all partitions (best effort)

            if (isoFile!!.name.endsWith(".img") || isoFile!!.name.endsWith(".bin") || isoFile!!.name.endsWith(".gz") || isoFile!!.name.endsWith(".xz")) {
                // === MODE: DD (Direct Write) ===
                updateProgress(20, "Writing Image (Direct Write Mode)...")
                logBuffer.append("Mode: DD (Raw Image)\n")

                val sourcePath = isoFile!!.absolutePath
                // Check if compressed
                val cmd = if (sourcePath.endsWith(".gz")) {
                    "zcat $sourcePath | dd of=$usbDevice bs=4M conv=fsync status=progress"
                } else if (sourcePath.endsWith(".xz")) {
                    // Use busybox unxz -c which is more standard on Android
                    "busybox unxz -c $sourcePath | dd of=$usbDevice bs=4M conv=fsync status=progress"
                } else {
                    "dd if=$sourcePath of=$usbDevice bs=4M conv=fsync status=progress"
                }

                // This might take a long time and output might be stderr
                checkSuccess(exec(cmd), "DD Write Failed")

                // DD mode ends here. No extra files or persistence possible (unless manually added later)
                updateProgress(100, "Write Complete")

            } else if (isoFile!!.name.endsWith(".zip")) {
                // === MODE: ZIP (Partition -> Extract) ===
                 updateProgress(20, "Partitioning for Archive...")
                 // For ZIPs, we usually just need a FAT32 partition (common for manual Windows installs)
                 // Or stick to the User Selection (MBR/GPT)

                 // Reuse Partition Logic?
                 // Yes, duplicate partitioning logic from ISO mode but skip loop mount
                 // COPY-PASTE of Partitioning Logic Below (Simplified call or Refactor would be better, but inline for now)

                 // [Partitioning Logic for ZIP/ISO]
                 val fdiskScript = StringBuilder()
                 if (isGpt) {
                    fdiskScript.append("g\n")
                    fdiskScript.append("n\n").append("1\n").append("\n").append("\n") // One big partition usually enough for ZIP extraction unless UEFI split needed
                    // For simplicity in ZIP mode, just use standard Data partition
                    fdiskScript.append("w\n")
                 } else {
                    fdiskScript.append("o\n")
                    fdiskScript.append("n\n").append("p\n").append("1\n").append("\n").append("\n")
                    fdiskScript.append("a\n").append("1\n")
                    fdiskScript.append("w\n")
                 }
                 exec("fdisk $usbDevice", input = fdiskScript.toString())
                 exec("sync"); Thread.sleep(2000); exec("mdev -s")

                 val part1 = if (usbDevice.contains("mmcblk") || usbDevice.contains("nvme")) "${usbDevice}p1" else "${usbDevice}1"
                 updateProgress(40, "Formatting (FAT32)...")
                 checkSuccess(exec("mkfs.vfat -F 32 $part1"), "Format Failed")

                 updateProgress(50, "Mounting...")
                 val baseMount = "/data/local/tmp/bootmaster_mnt"
                 exec("mkdir -p $baseMount/usb_data")
                 checkSuccess(exec("mount $part1 $baseMount/usb_data"), "Mount Failed")

                 updateProgress(60, "Extracting Archive...")
                 // unzip
                 checkSuccess(exec("unzip -o ${isoFile!!.absolutePath} -d $baseMount/usb_data"), "Unzip Failed")

                 // Cleanup happens in finally

            } else {
                // === MODE: ISO (Partition -> Mount -> Copy) ===
                updateProgress(20, "Partitioning drive ($usbDevice)...")

                // Fdisk script builder
                val fdiskScript = StringBuilder()

                if (isGpt) {
                    // GPT Scheme
                    // g: create a new empty GPT partition table
                    fdiskScript.append("g\n")

                    if (isTargetUefi) {
                        // UEFI Standard: ESP + Data (+ Persistence)
                        // Part 1: ESP (100MB)
                        fdiskScript.append("n\n").append("1\n").append("\n").append("+100M\n")
                        fdiskScript.append("t\n").append("1\n").append("1\n")

                        // Part 2: Data (If persistence, size limited; else all)
                        fdiskScript.append("n\n").append("2\n").append("\n")
                        if (persistenceGb > 0) {
                            fdiskScript.append("-${persistenceGb}G\n")
                        } else {
                            fdiskScript.append("\n")
                        }

                        // Part 3: Persistence (If enabled)
                        if (persistenceGb > 0) {
                             fdiskScript.append("n\n").append("3\n").append("\n").append("\n")
                             // Type? Linux Filesystem (default)
                        }

                        fdiskScript.append("w\n")
                    } else {
                         // GPT for Legacy
                         fdiskScript.append("n\n").append("1\n").append("\n")
                         if (persistenceGb > 0) {
                            fdiskScript.append("-${persistenceGb}G\n")
                            fdiskScript.append("n\n").append("2\n").append("\n").append("\n")
                         } else {
                            fdiskScript.append("\n")
                         }
                         fdiskScript.append("w\n")
                    }
                } else {
                    // MBR Scheme (Legacy or Hybrid)
                    fdiskScript.append("o\n") // DOS Label

                    if (isTargetUefi) {
                        // Hybrid MBR for UEFI (The prompt guide's specific method)
                        // Part 1: ESP (100MB)
                        fdiskScript.append("n\n").append("p\n").append("1\n").append("\n").append("+100M\n")
                        fdiskScript.append("t\n").append("1\n").append("ef\n") // ef = EFI

                        // Part 2: Data
                        fdiskScript.append("n\n").append("p\n").append("2\n").append("\n")
                         if (persistenceGb > 0) {
                            fdiskScript.append("-${persistenceGb}G\n")
                        } else {
                            fdiskScript.append("\n")
                        }
                        fdiskScript.append("a\n").append("2\n") // Bootable

                        // Part 3: Persistence
                        if (persistenceGb > 0) {
                            fdiskScript.append("n\n").append("p\n").append("3\n").append("\n").append("\n")
                        }

                        fdiskScript.append("w\n")
                    } else {
                        // Standard Legacy MBR
                        fdiskScript.append("n\n").append("p\n").append("1\n").append("\n")
                         if (persistenceGb > 0) {
                            fdiskScript.append("-${persistenceGb}G\n")
                            fdiskScript.append("a\n").append("1\n")
                            fdiskScript.append("n\n").append("p\n").append("2\n").append("\n").append("\n")
                         } else {
                            fdiskScript.append("\n")
                            fdiskScript.append("a\n").append("1\n")
                         }
                        fdiskScript.append("w\n")
                    }
                }

                val fdiskRes = exec("fdisk $usbDevice", input = fdiskScript.toString())
                if (fdiskRes.exitCode != 0) {
                     // Fdisk sometimes returns non-zero even on success if kernel re-read fails, but we should log warning
                     logBuffer.append("Warning: fdisk exit code ${fdiskRes.exitCode}. Continuing if sync works.\n")
                }

                // Sync to ensure kernel knows about new partitions
                exec("sync")
                Thread.sleep(2000) // Wait for kernel to settle
                exec("mdev -s") // Trigger device scan if available

                // 4. Formatting
                updateProgress(40, "Formatting partitions...")

                // Handle partition naming (mmcblk0 -> mmcblk0p1 vs sdb -> sdb1)
                val part1 = if (usbDevice.contains("mmcblk") || usbDevice.contains("nvme")) "${usbDevice}p1" else "${usbDevice}1"
                val part2 = if (usbDevice.contains("mmcblk") || usbDevice.contains("nvme")) "${usbDevice}p2" else "${usbDevice}2"
                val part3 = if (usbDevice.contains("mmcblk") || usbDevice.contains("nvme")) "${usbDevice}p3" else "${usbDevice}3"

                // Logic: If Dual Partition (Target UEFI + GPT OR Target UEFI + Hybrid MBR)
                val isDualPartition = (isTargetUefi && isGpt) || (isTargetUefi && !isGpt)

                // Format Persistence if needed
                // Determine which partition index is persistence
                var persistencePart: String? = null

                if (isDualPartition) {
                    // Format ESP
                    checkSuccess(exec("mkfs.vfat -F 32 $part1"), "Failed to format ESP ($part1)")

                    // Format Data
                    // Use FAT32 for broad compatibility (Windows + Linux)
                    checkSuccess(exec("mkfs.vfat -F 32 $part2"), "Failed to format Data partition ($part2)")

                    if (persistenceGb > 0) persistencePart = part3
                } else {
                    // Single Partition (Legacy MBR or GPT-Data-Only)
                    checkSuccess(exec("mkfs.vfat -F 32 $part1"), "Failed to format partition ($part1)")

                    if (persistenceGb > 0) persistencePart = part2
                }

                if (persistencePart != null) {
                    updateProgress(45, "Formatting Persistence Partition...")
                    // mkfs.ext4 -L casper-rw
                    checkSuccess(exec("mkfs.ext4 -L casper-rw $persistencePart"), "Failed to format Persistence ($persistencePart)")
                }

                // 5. Mounting
                updateProgress(50, "Mounting...")

                // Use /data/local/tmp for mount points to avoid Read-only file system errors on Android root
                val baseMount = "/data/local/tmp/bootmaster_mnt"
                exec("mkdir -p $baseMount/usb_esp")
                exec("mkdir -p $baseMount/usb_data")
                exec("mkdir -p $baseMount/iso_mount")

                val mntEsp = "$baseMount/usb_esp"
                val mntData = "$baseMount/usb_data"
                val mntIso = "$baseMount/iso_mount"

                if (isDualPartition) {
                    checkSuccess(exec("mount $part1 $mntEsp"), "Failed to mount ESP ($part1)")
                    checkSuccess(exec("mount $part2 $mntData"), "Failed to mount Data partition ($part2)")
                } else {
                    checkSuccess(exec("mount $part1 $mntData"), "Failed to mount partition ($part1)")
                }

                // Mount ISO (loop)
                checkSuccess(exec("mount -o loop ${isoFile!!.absolutePath} $mntIso"), "Failed to mount ISO")

                // 6. Copying Files
                updateProgress(60, "Copying ISO files (this may take a while)...")

                // CP
                checkSuccess(exec("cp -r $mntIso/. $mntData/"), "Failed to copy ISO files")

                // 7. Extra Files
                if (!addedFiles.isNullOrEmpty()) {
                    updateProgress(80, "Adding extra files...")
                    exec("mkdir -p $mntData/drivers")

                    addedFiles.forEach { uriStr ->
                        val fileUri = Uri.parse(uriStr)
                        val tempFile = File(applicationContext.cacheDir, "temp_driver")
                        copyUriToFile(fileUri, tempFile)
                        checkSuccess(exec("cp ${tempFile.absolutePath} $mntData/drivers/"), "Failed to copy extra file")
                        tempFile.delete()
                    }
                }

                // UEFI cleanup/setup
                if (isTargetUefi) {
                    updateProgress(90, "Configuring Bootloader...")
                    // Copy EFI folder to ESP
                    val checkEfi = exec("test -d $mntData/EFI")
                    if (checkEfi.exitCode == 0) {
                        checkSuccess(exec("cp -r $mntData/EFI $mntEsp/"), "Failed to copy EFI folder to ESP")
                    }

                    val checkBoot = exec("test -d $mntData/boot")
                    if (checkBoot.exitCode == 0) {
                         exec("cp -r $mntData/boot $mntEsp/") // Best effort
                    }
                }
            }

            return Result.success(workDataOf("logs" to logBuffer.toString()))

        } catch (e: Exception) {
            return Result.failure(workDataOf("error" to e.message, "logs" to logBuffer.toString()))
        } finally {
             // 8. Cleanup (Always runs)
            updateProgress(99, "Cleaning up...")
            exec("sync")

            // Use /data/local/tmp for mount points to avoid Read-only file system errors on Android root
            val baseMount = "/data/local/tmp/bootmaster_mnt"
            val mntEsp = "$baseMount/usb_esp"
            val mntData = "$baseMount/usb_data"
            val mntIso = "$baseMount/iso_mount"

            // Unmount everything (ignore errors here as they might not be mounted)
            exec("umount $mntIso")
            exec("umount $mntEsp")
            exec("umount $mntData")

            // Remove mount dirs
            exec("rm -rf $baseMount")

            // Clean temp ISO
            try {
                isoFile?.delete()
            } catch (e: Exception) {}
        }
    }

    private fun checkSuccess(result: ShellUtils.CommandResult, errorMessage: String) {
        if (result.exitCode != 0) {
            val errorDetails = result.error.joinToString("\n")
            throw Exception("$errorMessage: $errorDetails")
        }
    }

    private fun updateProgress(progress: Int, status: String) {
        logBuffer.append("[$progress%] $status\n")
        setProgressAsync(workDataOf(
            "progress" to progress,
            "status" to status,
            "logs" to logBuffer.toString()
        ))
    }

    private fun exec(command: String, input: String? = null): ShellUtils.CommandResult {
        logBuffer.append("> $command\n")

        val actualCommand = if (input != null) {
            // Escape newlines for echo
            val inputs = input.replace("\n", "\\n")
            "echo -e \"$inputs\" | $command"
        } else {
            command
        }

        val res = ShellUtils.executeRootCommand(actualCommand)
        if (res.exitCode != 0) {
            logBuffer.append("Error: ${res.error.joinToString()}\n")
        }
        return res
    }

    private fun copyUriToFile(uri: Uri, destFile: File) {
        applicationContext.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
    }
}
