package com.techtedapps.bootmaster.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.techtedapps.bootmaster.R
import com.techtedapps.bootmaster.data.FileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class FileBrowserActivity : AppCompatActivity() {

    private lateinit var repo: FileRepository
    private lateinit var adapter: FileAdapter
    private var currentPath = "" // Relative to mount point

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { uploadFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_browser)

        repo = FileRepository()

        val rv = findViewById<RecyclerView>(R.id.rvFiles)
        rv.layoutManager = LinearLayoutManager(this)

        adapter = FileAdapter(emptyList(),
            onItemClick = { item ->
                if (item.isDirectory) {
                    currentPath = if (currentPath.isEmpty()) item.name else "$currentPath/${item.name}"
                    loadFiles()
                }
            },
            onDeleteClick = { item ->
                deleteFile(item)
            }
        )
        rv.adapter = adapter

        findViewById<Button>(R.id.btnUpload).setOnClickListener {
            pickFileLauncher.launch(arrayOf("*/*"))
        }

        // Handle Back for navigation
        // (Simplified: real app would override onBackPressed properly)

        loadFiles()
    }

    override fun onBackPressed() {
        if (currentPath.isNotEmpty()) {
            // Go up one level
            val lastSlash = currentPath.lastIndexOf('/')
            currentPath = if (lastSlash > 0) currentPath.substring(0, lastSlash) else ""
            loadFiles()
        } else {
            super.onBackPressed()
        }
    }

    private fun loadFiles() {
        findViewById<TextView>(R.id.tvCurrentPath).text = "/$currentPath"
        CoroutineScope(Dispatchers.IO).launch {
            val items = repo.listFiles(currentPath)
            withContext(Dispatchers.Main) {
                adapter.updateData(items)
            }
        }
    }

    private fun deleteFile(item: com.techtedapps.bootmaster.data.FileItem) {
        CoroutineScope(Dispatchers.IO).launch {
            val path = if (currentPath.isEmpty()) item.name else "$currentPath/${item.name}"
            val success = repo.deleteFile(path)
            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(this@FileBrowserActivity, "Deleted", Toast.LENGTH_SHORT).show()
                    loadFiles()
                } else {
                    Toast.makeText(this@FileBrowserActivity, "Delete failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun uploadFile(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            // 1. Copy to cache first
            val fileName = getFileName(uri) ?: "uploaded_file"
            val tempFile = File(cacheDir, fileName)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            // 2. Root copy to USB
            val destPath = if (currentPath.isEmpty()) fileName else "$currentPath/$fileName"
            val success = repo.copyFileToUsb(tempFile.absolutePath, destPath)

            // 3. Cleanup
            tempFile.delete()

            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(this@FileBrowserActivity, "Uploaded", Toast.LENGTH_SHORT).show()
                    loadFiles()
                } else {
                     Toast.makeText(this@FileBrowserActivity, "Upload failed. Check permissions/mount.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        // Simplified query for name
        return uri.path?.substringAfterLast('/')
    }
}
