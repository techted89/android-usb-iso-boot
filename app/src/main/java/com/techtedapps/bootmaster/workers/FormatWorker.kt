package com.techtedapps.bootmaster.workers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.techtedapps.bootmaster.utils.ShellUtils

class FormatWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val usbDevice = inputData.getString("usb_device")
        val fsType = inputData.getString("fs_type") ?: "vfat" // vfat, exfat, ntfs

        if (usbDevice == null) return Result.failure()

        try {
            // Unmount
            ShellUtils.executeRootCommand("umount ${usbDevice}*")

            // Wipe partition table
            ShellUtils.executeRootCommand("wipefs -a $usbDevice")

            // Create single partition MBR
            val fdiskScript = "o\nn\np\n1\n\n\n\nw\n"
            // echo -e ... | fdisk
             val actualCommand = "echo -e \"${fdiskScript.replace("\n", "\\n")}\" | fdisk $usbDevice"
             ShellUtils.executeRootCommand(actualCommand)

             ShellUtils.executeRootCommand("sync")
             Thread.sleep(1000)
             ShellUtils.executeRootCommand("mdev -s")

             // Format
             val part = if (usbDevice.contains("mmcblk") || usbDevice.contains("nvme")) "${usbDevice}p1" else "${usbDevice}1"

             val mkfsCmd = when(fsType) {
                 "ntfs" -> "mkfs.ntfs -f $part" // or mkfs.exfat
                 "exfat" -> "mkfs.exfat $part"
                 else -> "mkfs.vfat -F 32 $part"
             }

             val res = ShellUtils.executeRootCommand(mkfsCmd)
             if (res.exitCode != 0) {
                 return Result.failure(workDataOf("error" to "Format failed: ${res.error}"))
             }

             return Result.success()

        } catch (e: Exception) {
            return Result.failure()
        }
    }
}
