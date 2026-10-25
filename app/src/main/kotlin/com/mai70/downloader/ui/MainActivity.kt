package com.mai70.downloader.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mai70.downloader.data.PreferencesManager
import com.mai70.downloader.databinding.ActivityMainBinding
import com.mai70.downloader.engine.Mai70DownloaderEngine
import com.mai70.downloader.worker.PeriodicSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)

        setupUIFromPrefs()
        setupListeners()
    }

    private fun setupUIFromPrefs() {
        binding.etHost.setText(prefs.host)
        binding.etPort.setText(prefs.port.toString())
        binding.switchAutoDelete.isChecked = prefs.autoDeleteAfterDownload
        binding.switchPeriodicCheck.isChecked = prefs.periodicCheckEnabled
        binding.etInterval.setText(prefs.pollIntervalMinutes.toString())
        binding.layoutInterval.visibility = if (prefs.periodicCheckEnabled) View.VISIBLE else View.GONE
    }

    private fun setupListeners() {
        binding.btnPair.setOnClickListener {
            savePrefs()
            performPairing()
        }

        binding.btnDownloadAll.setOnClickListener {
            savePrefs()
            performDownloadAll()
        }

        binding.switchAutoDelete.setOnCheckedChangeListener { _, isChecked ->
            prefs.autoDeleteAfterDownload = isChecked
        }

        binding.switchPeriodicCheck.setOnCheckedChangeListener { _, isChecked ->
            prefs.periodicCheckEnabled = isChecked
            binding.layoutInterval.visibility = if (isChecked) View.VISIBLE else View.GONE
            savePrefs()

            if (isChecked) {
                PeriodicSyncWorker.schedulePeriodicSync(this, prefs.pollIntervalMinutes)
                Toast.makeText(this, "Periodic check scheduled every ${prefs.pollIntervalMinutes} mins", Toast.LENGTH_SHORT).show()
            } else {
                PeriodicSyncWorker.cancelPeriodicSync(this)
                Toast.makeText(this, "Periodic check canceled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun savePrefs() {
        prefs.host = binding.etHost.text.toString().trim()
        prefs.port = binding.etPort.text.toString().trim().toIntOrNull() ?: 80
        prefs.pollIntervalMinutes = binding.etInterval.text.toString().trim().toLongOrNull() ?: 15L
    }

    private fun performPairing() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.text = "Initiating pairing... Please wait."

        lifecycleScope.launch {
            val engine = Mai70DownloaderEngine(this@MainActivity, prefs.host, prefs.port)
            try {
                val token = engine.pair(
                    onWaitingForButton = {
                        withContext(Dispatchers.Main) {
                            binding.tvStatus.text = "PAIRING REQUIRED: Please press the physical power button on your 70mai dashcam!"
                        }
                    }
                )
                binding.tvStatus.text = "Pairing successful! Active Token: $token"
                Toast.makeText(this@MainActivity, "Pairing successful!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                binding.tvStatus.text = "Pairing failed: ${e.message}"
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun performDownloadAll() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnDownloadAll.isEnabled = false

        val downloadDir = if (prefs.downloadDirPath.isNotEmpty()) {
            File(prefs.downloadDirPath)
        } else {
            File(getExternalFilesDir(null), "70mai_downloads")
        }

        lifecycleScope.launch {
            val engine = Mai70DownloaderEngine(this@MainActivity, prefs.host, prefs.port)

            val job = launch {
                engine.progressFlow.collect { progress ->
                    binding.tvStatus.text = progress.statusMessage
                    if (progress.totalCount > 0) {
                        binding.progressBar.isIndeterminate = false
                        binding.progressBar.max = progress.totalCount
                        binding.progressBar.progress = progress.completedCount
                    } else {
                        binding.progressBar.isIndeterminate = true
                    }
                }
            }

            engine.syncFiles(
                outputDir = downloadDir,
                deleteAfterDownload = prefs.autoDeleteAfterDownload
            )

            job.cancel()
            binding.progressBar.visibility = View.GONE
            binding.btnDownloadAll.isEnabled = true
        }
    }
}
