package com.techtedapps.bootmaster.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.techtedapps.bootmaster.R
import com.techtedapps.bootmaster.data.UsbDrive
import com.techtedapps.bootmaster.data.UsbRepository
import com.techtedapps.bootmaster.workers.FormatWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FormatActivity : AppCompatActivity() {

    private lateinit var usbRepo: UsbRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_format)

        usbRepo = UsbRepository(this)
        val spinner = findViewById<Spinner>(R.id.spinnerDrives)

        CoroutineScope(Dispatchers.IO).launch {
            val drives = usbRepo.getUsbDrives()
            withContext(Dispatchers.Main) {
                val adapter = ArrayAdapter(this@FormatActivity, android.R.layout.simple_spinner_item, drives)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinner.adapter = adapter
            }
        }

        findViewById<Button>(R.id.btnFormat).setOnClickListener {
            val drive = spinner.selectedItem as? UsbDrive
            if (drive == null) return@setOnClickListener

            val fs = when(findViewById<RadioGroup>(R.id.rgFs).checkedRadioButtonId) {
                R.id.rbExFat -> "exfat"
                R.id.rbNtfs -> "ntfs"
                else -> "vfat"
            }

            val req = OneTimeWorkRequestBuilder<FormatWorker>()
                .setInputData(workDataOf("usb_device" to drive.path, "fs_type" to fs))
                .build()

            WorkManager.getInstance(this).enqueue(req)
            Toast.makeText(this, "Format started...", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
