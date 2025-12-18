package com.techtedapps.bootmaster.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.techtedapps.bootmaster.data.UsbDrive
import com.techtedapps.bootmaster.data.UsbRepository
import com.techtedapps.bootmaster.workers.BootableUsbWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val usbRepository = UsbRepository()
    private val workManager = WorkManager.getInstance(application)

    private val _usbDrives = MutableLiveData<List<UsbDrive>>()
    val usbDrives: LiveData<List<UsbDrive>> = _usbDrives

    private val _selectedIsoUri = MutableLiveData<Uri?>()
    val selectedIsoUri: LiveData<Uri?> = _selectedIsoUri

    private val _addedFiles = MutableLiveData<List<Uri>>()
    val addedFiles: LiveData<List<Uri>> = _addedFiles

    // WorkInfo for observing progress
    val outputWorkInfos: LiveData<List<WorkInfo>> = workManager.getWorkInfosByTagLiveData("bootable_usb_work")

    fun refreshDrives() {
        viewModelScope.launch(Dispatchers.IO) {
            val drives = usbRepository.getUsbDrives()
            _usbDrives.postValue(drives)
        }
    }

    fun selectIso(uri: Uri) {
        _selectedIsoUri.value = uri
    }

    fun addFiles(uris: List<Uri>) {
        val current = _addedFiles.value.orEmpty().toMutableList()
        current.addAll(uris)
        _addedFiles.value = current
    }

    fun createBootableUsb(drive: UsbDrive, isUefi: Boolean) {
        val isoUri = _selectedIsoUri.value ?: return

        // Pass URI string. The Worker will need to handle file access.
        val addedFileUris = _addedFiles.value.orEmpty().map { it.toString() }.toTypedArray()

        val inputData = Data.Builder()
            .putString("iso_uri", isoUri.toString())
            .putString("usb_device", drive.path) // /dev/sdb
            .putBoolean("is_uefi", isUefi)
            .putStringArray("added_files", addedFileUris)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<BootableUsbWorker>()
            .setInputData(inputData)
            .addTag("bootable_usb_work")
            .build()

        workManager.enqueue(workRequest)
    }
}
