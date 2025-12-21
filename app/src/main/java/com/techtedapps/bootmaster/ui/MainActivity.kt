package com.techtedapps.bootmaster.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.app.AlertDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.work.WorkInfo
import com.techtedapps.bootmaster.R
import com.techtedapps.bootmaster.data.UsbDrive
import com.techtedapps.bootmaster.databinding.ActivityMainBinding
import com.techtedapps.bootmaster.utils.ShellUtils

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private val selectIsoLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val filename = uri.path?.substringAfterLast('/')
            viewModel.selectIso(it, filename)
            // updateFormatInfo(it) -> Moved to Observer
        }
    }

    private val selectFilesLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.addFiles(uris)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkRootAccess()

        setupObservers()
        setupListeners()

        // Initial refresh
        viewModel.refreshDrives()
    }

    private fun checkRootAccess() {
        if (!ShellUtils.isRootAvailable()) {
            AlertDialog.Builder(this)
                .setTitle("Root Access Required")
                .setMessage("BootMaster requires Root access to perform low-level disk operations (partitioning, formatting, writing). Please root your device or grant permission.")
                .setPositiveButton("Exit") { _, _ -> finish() }
                .setCancelable(false)
                .show()
        }
    }

    private fun setupObservers() {
        viewModel.usbDrives.observe(this) { drives ->
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, drives)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerUsbDrives.adapter = adapter
        }

        viewModel.selectedIsoUri.observe(this) { uri ->
            binding.tvSelectedIso.text = uri?.path ?: "No ISO selected"
        }

        viewModel.addedFiles.observe(this) { files ->
            binding.tvAddedFiles.text = "${files.size} additional files added"
        }

        viewModel.isoSuggestion.observe(this) { suggestion ->
            // Update UI based on Smart Detection
            binding.tvBootInfo.text = "Detected: ${suggestion.detectedName}\n${suggestion.description}"

            if (suggestion.isDdMode) {
                // DD Mode
                binding.rgTargetSystem.visibility = android.view.View.GONE
                binding.rgPartitionScheme.visibility = android.view.View.GONE
                binding.cbPersistence.visibility = android.view.View.GONE
            } else {
                // ISO Mode
                binding.rgTargetSystem.visibility = android.view.View.VISIBLE
                binding.rgPartitionScheme.visibility = android.view.View.VISIBLE
                binding.cbPersistence.visibility = android.view.View.VISIBLE

                // Auto-Select Buttons (User can override)
                when(suggestion.targetSystem) {
                    com.techtedapps.bootmaster.utils.TargetSystem.UEFI -> binding.rbTargetUefi.isChecked = true
                    com.techtedapps.bootmaster.utils.TargetSystem.LEGACY -> binding.rbTargetLegacy.isChecked = true
                    else -> {}
                }

                when(suggestion.partitionScheme) {
                    com.techtedapps.bootmaster.utils.PartitionScheme.GPT -> binding.rbSchemeGpt.isChecked = true
                    com.techtedapps.bootmaster.utils.PartitionScheme.MBR -> binding.rbSchemeMbr.isChecked = true
                    else -> {}
                }

                // Persistence
                if (suggestion.supportsPersistence) {
                    binding.cbPersistence.isEnabled = true
                    binding.cbPersistence.text = "Enable Persistence (Recommended for this Linux)"
                } else {
                    binding.cbPersistence.isChecked = false
                    binding.cbPersistence.isEnabled = false // Or just leave enabled but warned
                    binding.cbPersistence.text = "Enable Persistence (Not supported/Unknown)"
                }
            }

            Toast.makeText(this, "Optimized settings for ${suggestion.detectedName}", Toast.LENGTH_SHORT).show()
        }

        viewModel.outputWorkInfos.observe(this) { workInfos ->
            if (workInfos.isNullOrEmpty()) return@observe

            val workInfo = workInfos[0]
            val progress = workInfo.progress.getInt("progress", 0)
            val status = workInfo.progress.getString("status") ?: "Working..."
            val logs = workInfo.progress.getString("logs") ?: ""

            when (workInfo.state) {
                WorkInfo.State.RUNNING -> {
                    binding.layoutProgress.visibility = android.view.View.VISIBLE
                    binding.progressBar.isIndeterminate = progress == 0
                    binding.progressBar.progress = progress
                    binding.tvStatus.text = status
                    binding.tvLogs.text = logs
                    binding.btnCreate.isEnabled = false
                }
                WorkInfo.State.SUCCEEDED -> {
                    binding.layoutProgress.visibility = android.view.View.VISIBLE
                    binding.progressBar.progress = 100
                    binding.tvStatus.text = "Success!"
                    binding.tvLogs.text = logs + "\nDone."
                    binding.btnCreate.isEnabled = true
                    Toast.makeText(this, "Bootable USB Created Successfully!", Toast.LENGTH_LONG).show()
                }
                WorkInfo.State.FAILED -> {
                    binding.layoutProgress.visibility = android.view.View.VISIBLE
                    binding.tvStatus.text = "Failed"
                    val error = workInfo.outputData.getString("error") ?: "Unknown error"
                    binding.tvLogs.text = logs + "\nError: $error"
                    binding.btnCreate.isEnabled = true
                    Toast.makeText(this, "Failed: $error", Toast.LENGTH_LONG).show()
                }
                else -> {
                    // IDLE etc
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnSelectIso.setOnClickListener {
            // Allow .iso, .img, .bin, .zip, .gz, .xz
            selectIsoLauncher.launch(arrayOf(
                "application/x-iso9660-image",
                "application/octet-stream",
                "application/zip",
                "application/x-gzip",
                "application/x-xz",
                "*/*"
            ))
        }

        binding.btnDownloadIso.setOnClickListener {
            startActivity(Intent(this, DownloadActivity::class.java))
        }

        binding.btnFormatTool.setOnClickListener {
            startActivity(Intent(this, FormatActivity::class.java))
        }

        binding.btnRefreshDrives.setOnClickListener {
            viewModel.refreshDrives()
        }

        binding.btnAddFiles.setOnClickListener {
            selectFilesLauncher.launch(arrayOf("*/*"))
        }

        binding.btnFileBrowser.setOnClickListener {
            val selectedDrive = binding.spinnerUsbDrives.selectedItem as? UsbDrive
            if (selectedDrive == null) {
                Toast.makeText(this, "Select a drive first", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(this, FileBrowserActivity::class.java)
                intent.putExtra("drive_path", selectedDrive.path)
                startActivity(intent)
            }
        }

        binding.btnCreate.setOnClickListener {
            val selectedDrive = binding.spinnerUsbDrives.selectedItem as? UsbDrive
            if (selectedDrive == null) {
                Toast.makeText(this, "Please select a USB drive", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (viewModel.selectedIsoUri.value == null) {
                Toast.makeText(this, "Please select an ISO file", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val isTargetUefi = binding.rbTargetUefi.isChecked
            val isGpt = binding.rbSchemeGpt.isChecked

            // Validate Logic
            if (isTargetUefi && !isGpt) {
                 Toast.makeText(this, "Recommendation: Use GPT for UEFI", Toast.LENGTH_LONG).show()
            }
            if (!isTargetUefi && isGpt) {
                 Toast.makeText(this, "Warning: GPT may not boot on Legacy BIOS", Toast.LENGTH_LONG).show()
            }

            val persistenceSizeGb = if (binding.cbPersistence.isChecked) {
                if (binding.seekBarPersistence.progress < 1) 1 else binding.seekBarPersistence.progress
            } else {
                0
            }

            viewModel.createBootableUsb(selectedDrive, isTargetUefi, isGpt, persistenceSizeGb)
        }

        binding.rgTargetSystem.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbTargetUefi) {
                binding.rbSchemeGpt.isChecked = true
                binding.tvBootInfo.text = "Info: GPT is standard for UEFI systems (Windows 8+, Modern Linux)."
            } else {
                binding.rbSchemeMbr.isChecked = true
                binding.tvBootInfo.text = "Info: MBR is standard for Legacy BIOS (Old Windows, Old PCs)."
            }
        }

        binding.cbPersistence.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutPersistence.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
        }

        binding.seekBarPersistence.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                // Minimum 1GB
                val size = if (progress < 1) 1 else progress
                binding.tvPersistenceSize.text = "Size: $size GB"
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
    }

    // updateFormatInfo removed - replaced by SmartDetector logic in ViewModel Observer
}
