package com.mai70.downloader

import com.github.trunone.mai70protocol.Mai70Client
import com.github.trunone.mai70protocol.http.HttpTransport
import com.github.trunone.mai70protocol.models.Category
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

class MockTransport : HttpTransport {
    val getRequests = mutableListOf<String>()
    val openStreamRequests = mutableListOf<String>()
    val responses = mutableMapOf<String, String>()
    val streams = mutableMapOf<String, InputStream>()

    override suspend fun get(url: String): String? {
        getRequests.add(url)
        for ((key, value) in responses) {
            if (url.contains(key)) return value
        }
        return "{\"ResultCode\":0}"
    }

    override suspend fun openStream(url: String): InputStream? {
        openStreamRequests.add(url)
        for ((key, value) in streams) {
            if (url.contains(key)) return value
        }
        return ByteArrayInputStream("dummy stream content".toByteArray())
    }
}

class DownloaderEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testSyncFilesDownloadAndAutoDelete() = runBlocking {
        val transport = MockTransport()
        transport.responses["getfilelist.cgi"] = """
            {
                "ResultCode": 0,
                "Result": [
                    {"path": "Normal", "name": "VIDEO001.MP4", "size": "100"}
                ]
            }
        """.trimIndent()
        transport.streams["VIDEO001.MP4"] = ByteArrayInputStream("test video data".toByteArray())

        val client = Mai70Client(transport = transport)
        val downloadDir = tempFolder.newFolder("downloads")

        val category = Category.NORMAL
        val files = client.listFiles(category)
        assertEquals(1, files.size)

        val entry = files.first()
        val catDir = File(downloadDir, entry.category.categoryName).apply { mkdirs() }
        val targetFile = File(catDir, entry.name)

        client.openFile(entry).use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        assertTrue(targetFile.exists())
        assertEquals("test video data", targetFile.readText())

        client.delete(entry)
        assertTrue(transport.getRequests.any { it.contains("delete.cgi") })
    }
}
