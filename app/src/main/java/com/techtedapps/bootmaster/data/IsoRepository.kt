package com.techtedapps.bootmaster.data

data class IsoItem(
    val name: String,
    val description: String,
    val url: String,
    val recommendedScheme: String = "GPT" // For future auto-select
)

object IsoRepository {
    val isoList = listOf(
        IsoItem(
            "Ubuntu 22.04 LTS",
            "Stable, user-friendly Linux. Good for beginners.",
            "https://releases.ubuntu.com/22.04/ubuntu-22.04.3-desktop-amd64.iso"
        ),
        IsoItem(
            "Fedora Workstation 39",
            "Bleeding edge features, reliable. Uses GNOME.",
            "https://download.fedoraproject.org/pub/fedora/linux/releases/39/Workstation/x86_64/iso/Fedora-Workstation-Live-x86_64-39-1.5.iso"
        ),
        IsoItem(
            "Debian 12 'Bookworm'",
            "Rock solid stability. The base for many other distros.",
            "https://cdimage.debian.org/debian-cd/current/amd64/iso-cd/debian-12.5.0-amd64-netinst.iso"
        ),
        IsoItem(
            "Linux Mint 21.3",
            "Familiar layout, based on Ubuntu. Great for Windows users.",
            "https://mirrors.layeronline.com/linuxmint/stable/21.3/linuxmint-21.3-cinnamon-64bit.iso"
        ),
        IsoItem(
            "SystemRescue 11.00",
            "Essential tool for repairing systems and recovering data.",
            "https://osdn.net/dl/systemrescuecd/systemrescue-11.00-amd64.iso"
        )
    )
}
