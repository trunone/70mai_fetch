package com.mai70.downloader.engine

import android.content.Context
import com.github.trunone.mai70protocol.Mai70Client
import com.github.trunone.mai70protocol.android.AndroidNetworkTransport
import com.github.trunone.mai70protocol.models.Category
import com.github.trunone.mai70protocol.models.FileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class DownloadProgress(
    val currentFile: String = "",
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val isRunning: Boolean = false,
    val statusMessage: String = ""
)

sealed class SyncResult {
    data class Success(val downloadedFiles: List<String>, val deletedFiles: List<String>) : SyncResult()
    data class Error(val message: String, val throwable: Throwable? = null) : SyncResult()
}

class Mai70DownloaderEngine(
    private val context: Context,
    private val host: String = "192.168.0.1",
    private val port: Int = 80
) {
    private val transport = AndroidNetworkTransport(context, defaultCameraIp = host)
    private val client = Mai70Client(transport = transport, host = host, port = port)

    private val _progressFlow = MutableStateFlow(DownloadProgress())
    val progressFlow: StateFlow<DownloadProgress> = _progressFlow.asStateFlow()

    suspend fun pair(onWaitingForButton: suspend () -> Unit): String = withContext(Dispatchers.IO) {
        client.pair(onWaitingForButton = onWaitingForButton)
    }

    suspend fun syncFiles(
        outputDir: File,
        categories: List<Category> = Category.entries,
        deleteAfterDownload: Boolean = false
    ): SyncResult = withContext(Dispatchers.IO) {
        _progressFlow.value = DownloadProgress(isRunning = true, statusMessage = "Connecting to 70mai dashcam...")

        try {
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            client.prepareCameraAccess()

            val allEntries = mutableListOf<FileEntry>()
            for (category in categories) {
                _progressFlow.value = _progressFlow.value.copy(
                    statusMessage = "Fetching file list for category ${category.categoryName}..."
                )
                val categoryFiles = try {
                    client.listFiles(category)
                } catch (e: Exception) {
                    emptyList()
                }
                allEntries.addAll(categoryFiles)
            }

            if (allEntries.isEmpty()) {
                _progressFlow.value = DownloadProgress(isRunning = false, statusMessage = "No files found on camera.")
                return@withContext SyncResult.Success(emptyList(), emptyList())
            }

            val downloadedFiles = mutableListOf<String>()
            val deletedFiles = mutableListOf<String>()
            val totalCount = allEntries.size

            for ((index, entry) in allEntries.withIndex()) {
                val categoryDir = File(outputDir, entry.category.categoryName)
                if (!categoryDir.exists()) {
                    categoryDir.mkdirs()
                }

                val targetFile = File(categoryDir, entry.name)

                _progressFlow.value = DownloadProgress(
                    currentFile = entry.name,
                    completedCount = index,
                    totalCount = totalCount,
                    bytesDownloaded = 0L,
                    totalBytes = entry.size,
                    isRunning = true,
                    statusMessage = "Downloading ${entry.name} (${index + 1}/$totalCount)..."
                )

                var downloadSuccess = false
                if (targetFile.exists() && targetFile.length() == entry.size && entry.size > 0) {
                    downloadSuccess = true
                    downloadedFiles.add(targetFile.absolutePath)
                } else {
                    val tempFile = File(categoryDir, "${entry.name}.tmp")
                    try {
                        client.openFile(entry).use { inputStream ->
                            FileOutputStream(tempFile).use { outputStream ->
                                val buffer = ByteArray(32 * 1024)
                                var bytesRead: Int
                                var totalDownloaded = 0L

                                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                    outputStream.write(buffer, 0, bytesRead)
                                    totalDownloaded += bytesRead

                                    _progressFlow.value = _progressFlow.value.copy(
                                        bytesDownloaded = totalDownloaded
                                    )
                                }
                            }
                        }

                        if (tempFile.renameTo(targetFile) || (tempFile.exists() && tempFile.length() > 0)) {
                            if (!targetFile.exists() && tempFile.exists()) {
                                tempFile.renameTo(targetFile)
                            }
                            downloadSuccess = true
                            downloadedFiles.add(targetFile.absolutePath)
                        }
                    } catch (e: Exception) {
                        if (tempFile.exists()) {
                            tempFile.delete()
                        }
                    }
                }

                if (downloadSuccess && deleteAfterDownload) {
                    _progressFlow.value = _progressFlow.value.copy(
                        statusMessage = "Deleting ${entry.name} from camera after download..."
                    )
                    try {
                        client.delete(entry)
                        deletedFiles.add(entry.name)
                    } catch (_: Exception) {
                    }
                }
            }

            _progressFlow.value = DownloadProgress(
                completedCount = totalCount,
                totalCount = totalCount,
                isRunning = false,
                statusMessage = "Sync completed! Downloaded ${downloadedFiles.size} files, deleted ${deletedFiles.size} files."
            )

            SyncResult.Success(downloadedFiles, deletedFiles)
        } catch (e: Exception) {
            _progressFlow.value = DownloadProgress(
                isRunning = false,
                statusMessage = "Sync failed: ${e.message}"
            )
            SyncResult.Error(e.message ?: "Unknown error", e)
        }
    }
}
