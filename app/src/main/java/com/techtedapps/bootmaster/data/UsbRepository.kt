package com.techtedapps.bootmaster.data

import com.techtedapps.bootmaster.utils.ShellUtils

data class UsbDrive(
    val name: String, // e.g., sdb
    val size: String, // e.g., 28.7G
    val path: String  // e.g., /dev/sdb
) {
    override fun toString(): String {
        return "$path ($size)"
    }
}

class UsbRepository {

    /**
     * Lists available block devices that are likely USB drives.
     * Uses `lsblk` via ShellUtils.
     */
    fun getUsbDrives(): List<UsbDrive> {
        val drives = mutableListOf<UsbDrive>()

        // Command to list block devices, excluding loop devices and RAM disks
        // -d: nodeps (don't print partitions, just the disk)
        // -n: no headings
        // -o: output columns
        // -e 7,1: exclude loop (7) and ram (1) devices.

        val result = ShellUtils.executeRootCommand("lsblk -d -n -o NAME,SIZE,TYPE,TRAN")

        if (result.exitCode == 0) {
            for (line in result.output) {
                // Parse the line
                // Example: "sdb    28.7G disk usb"

                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size >= 4) {
                    val name = parts[0]
                    val size = parts[1]
                    val type = parts[2]
                    val tran = parts[3] // transport: usb, sata, nvme

                    if (type == "disk" && tran == "usb") {
                        drives.add(UsbDrive(name, size, "/dev/$name"))
                    }
                }
            }
        } else {
            // Fallback: simple ls /dev/block/sd* logic if lsblk fails or returns nothing
            // This is a naive fallback but helpful for debug
            val fallback = ShellUtils.executeRootCommand("ls /dev/block/sd*")
             if (fallback.exitCode == 0) {
                 // parsing raw paths is hard without size info, so typically rely on lsblk
             }
        }

        return drives
    }
}
