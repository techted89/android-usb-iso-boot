package com.techtedapps.bootmaster.ui

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.techtedapps.bootmaster.R
import com.techtedapps.bootmaster.data.IsoRepository

class DownloadActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download)

        val rv = findViewById<RecyclerView>(R.id.rvIsos)
        rv.layoutManager = LinearLayoutManager(this)

        val adapter = DownloadAdapter(IsoRepository.isoList) { isoItem ->
            startDownload(isoItem.url, isoItem.name)
        }
        rv.adapter = adapter
    }

    private fun startDownload(url: String, title: String) {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Downloading $title")
            .setDescription("Downloading ISO image...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "bootmaster_${System.currentTimeMillis()}.iso")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)

        Toast.makeText(this, "Download started. Check notification bar.", Toast.LENGTH_LONG).show()
    }
}
