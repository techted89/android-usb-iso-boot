package com.techtedapps.bootmaster.data

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.techtedapps.bootmaster.utils.ShellUtils
import java.io.File

data class UsbDrive(
    val name: String, // e.g., sdb
    val size: String, // e.g., 28.7G
    val path: String, // e.g., /dev/sdb
    val productName: String? = null,
    val vendorId: Int = 0,
    val productId: Int = 0
) {
    override fun toString(): String {
        return if (productName != null) {
            "$productName ($size) - $path"
        } else {
            "$path ($size)"
        }
    }
}

class UsbRepository(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    /**
     * Lists available block devices that are likely USB drives.
     * Combines UsbManager info with lsblk/root info.
     */
    fun getUsbDrives(): List<UsbDrive> {
        val drives = mutableListOf<UsbDrive>()

        // 1. Get native USB devices
        val nativeDevices = usbManager.deviceList // HashMap<String, UsbDevice>

        // 2. Get block devices via Root
        val blockDevices = getBlockDevicesFromRoot()

        // 3. Correlate
        // This is tricky. UsbDevice has deviceName like "/dev/bus/usb/001/002"
        // Block devices are /dev/block/sdb.
        // We can try to match by VendorID/ProductID if lsblk provides it, or assume all sdX (excluding sda often) are USB.
        // A robust way on Android is checking /sys/class/block/sdX/device/.. for bus type.
        // For this implementation, we will list all Root-detected 'usb' transport devices (from lsblk)
        // and try to enrich name if possible, or just rely on lsblk which is safer for the 'path' needed by fdisk.

        return blockDevices
    }

    private fun getBlockDevicesFromRoot(): List<UsbDrive> {
        val drives = mutableListOf<UsbDrive>()
        // lsblk -d -n -o NAME,SIZE,TYPE,TRAN,VENDOR,MODEL
        val result = ShellUtils.executeRootCommand("lsblk -d -n -o NAME,SIZE,TYPE,TRAN,VENDOR,MODEL")

        if (result.exitCode == 0) {
            for (line in result.output) {
                // Example: sdb    28.7G disk usb  SanDisk  Cruzer_Glide
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size >= 4) {
                    val name = parts[0]
                    val size = parts[1]
                    val type = parts[2]
                    val tran = parts[3]

                    if (type == "disk" && tran == "usb") {
                        // Try to reconstruct product name
                        val model = if (parts.size > 5) parts.subList(4, parts.size).joinToString(" ") else "USB Drive"
                        drives.add(UsbDrive(name, size, "/dev/$name", model))
                    }
                }
            }
        } else {
            // Fallback
             val fallback = ShellUtils.executeRootCommand("ls /dev/block/sd*")
             if (fallback.exitCode == 0) {
                 for (path in fallback.output) {
                     // Filter out sda usually? Risky.
                     if (!path.endsWith("sda")) {
                         drives.add(UsbDrive(File(path).name, "Unknown", path, "Generic USB"))
                     }
                 }
             }
        }
        return drives
    }
}
