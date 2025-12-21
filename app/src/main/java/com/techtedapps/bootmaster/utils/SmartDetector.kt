package com.techtedapps.bootmaster.utils

import android.net.Uri

data class IsoSuggestion(
    val detectedName: String,
    val targetSystem: TargetSystem,
    val partitionScheme: PartitionScheme,
    val supportsPersistence: Boolean,
    val description: String,
    val isDdMode: Boolean = false
)

enum class TargetSystem {
    UEFI, LEGACY, AUTO
}

enum class PartitionScheme {
    GPT, MBR, AUTO
}

object SmartDetector {

    fun detect(uri: Uri, filename: String?): IsoSuggestion {
        val name = filename?.lowercase() ?: uri.path?.substringAfterLast('/')?.lowercase() ?: "unknown"

        // 1. Check Extension for Mode (DD vs ISO)
        if (name.endsWith(".img") || name.endsWith(".bin") || name.endsWith(".sdcard")) {
            return IsoSuggestion(
                detectedName = "Raw Disk Image",
                targetSystem = TargetSystem.AUTO, // Irrelevant for DD
                partitionScheme = PartitionScheme.AUTO, // Irrelevant for DD
                supportsPersistence = false,
                description = "Raw image detected. Will assume Direct Write (DD) mode. Partition table is embedded in the image.",
                isDdMode = true
            )
        }

        if (name.endsWith(".gz") || name.endsWith(".xz")) {
             return IsoSuggestion(
                detectedName = "Compressed Image",
                targetSystem = TargetSystem.AUTO,
                partitionScheme = PartitionScheme.AUTO,
                supportsPersistence = false,
                description = "Compressed image detected. Will decompress and write directly.",
                isDdMode = true
            )
        }

        // 2. Check for Windows
        if (name.contains("win") && (name.contains("10") || name.contains("11") || name.contains("server"))) {
            return IsoSuggestion(
                detectedName = "Windows 10/11/Server",
                targetSystem = TargetSystem.UEFI,
                partitionScheme = PartitionScheme.GPT,
                supportsPersistence = false,
                description = "Detected modern Windows. Recommended: UEFI + GPT. Persistence disabled."
            )
        }

        if (name.contains("win") && (name.contains("7") || name.contains("xp") || name.contains("8"))) {
             return IsoSuggestion(
                detectedName = "Legacy Windows",
                targetSystem = TargetSystem.LEGACY, // Win7 often needs CSM/Legacy logic on USB
                partitionScheme = PartitionScheme.MBR,
                supportsPersistence = false,
                description = "Detected older Windows. Recommended: Legacy BIOS + MBR."
            )
        }

        // 3. Check for popular Linux Distros (Persistence Friendly)
        // Debian/Ubuntu based often work with casper-rw
        val persistenceFriendly = listOf("ubuntu", "mint", "debian", "kali", "pop", "elementary", "zorin", "parrot")
        if (persistenceFriendly.any { name.contains(it) }) {
            return IsoSuggestion(
                detectedName = "Linux (Debian/Ubuntu-based)",
                targetSystem = TargetSystem.UEFI,
                partitionScheme = PartitionScheme.GPT,
                supportsPersistence = true,
                description = "Detected Linux. Recommended: UEFI + GPT. Persistence is supported."
            )
        }

        // 4. Other Linux
        val otherLinux = listOf("fedora", "arch", "manjaro", "centos", "opensuse", "rhel")
        if (otherLinux.any { name.contains(it) }) {
             return IsoSuggestion(
                detectedName = "Linux (RPM/Arch-based)",
                targetSystem = TargetSystem.UEFI,
                partitionScheme = PartitionScheme.GPT,
                supportsPersistence = false, // Often requires specific labels other than casper-rw
                description = "Detected Linux. Recommended: UEFI + GPT."
            )
        }

        // 5. Default / Unknown ISO
        return IsoSuggestion(
            detectedName = "Unknown ISO",
            targetSystem = TargetSystem.UEFI,
            partitionScheme = PartitionScheme.GPT,
            supportsPersistence = false,
            description = "Standard ISO detected. Defaulting to Modern UEFI settings."
        )
    }
}
