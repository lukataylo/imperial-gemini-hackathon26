package com.crusty.llm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long, val progressPercent: Int) : DownloadState
    data object Verifying : DownloadState
    data class Completed(val file: File) : DownloadState
    data class Error(val message: String) : DownloadState
}

class ModelDownloader(
    private val context: Context,
    private val modelProvider: ModelProvider
) {

    fun downloadModel(urlStr: String = DefaultModelProvider.HF_MODEL_URL): Flow<DownloadState> = flow {
        val targetFile = modelProvider.getTargetModelFile()
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.download")

        if (targetFile.exists() && targetFile.length() > 0) {
            emit(DownloadState.Completed(targetFile))
            return@flow
        }

        emit(DownloadState.Downloading(0L, DefaultModelProvider.MODEL_SIZE_BYTES, 0))

        try {
            val url = URL(urlStr)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
            }

            var downloadedBytes = 0L
            if (tempFile.exists()) {
                downloadedBytes = tempFile.length()
                connection.setRequestProperty("Range", "bytes=$downloadedBytes-")
            }

            val responseCode = connection.responseCode
            val isPartial = responseCode == HttpURLConnection.HTTP_PARTIAL
            val isOk = responseCode == HttpURLConnection.HTTP_OK

            if (!isOk && !isPartial) {
                emit(DownloadState.Error("Server returned HTTP $responseCode"))
                return@flow
            }

            val totalBytes = if (isPartial) {
                downloadedBytes + connection.contentLengthLong
            } else {
                downloadedBytes = 0L
                connection.contentLengthLong.takeIf { it > 0 } ?: DefaultModelProvider.MODEL_SIZE_BYTES
            }

            val append = isPartial && downloadedBytes > 0
            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(tempFile, append)

            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            var lastEmittedPercent = -1

            outputStream.use { out ->
                inputStream.use { input ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val percent = (if (totalBytes > 0) (downloadedBytes * 100) / totalBytes else 0L).toInt().coerceIn(0, 100)
                        if (percent != lastEmittedPercent) {
                            lastEmittedPercent = percent
                            emit(DownloadState.Downloading(downloadedBytes, totalBytes, percent))
                        }
                    }
                }
            }

            emit(DownloadState.Verifying)

            if (tempFile.renameTo(targetFile)) {
                emit(DownloadState.Completed(targetFile))
            } else {
                emit(DownloadState.Error("Failed to rename temporary download file"))
            }

        } catch (e: Exception) {
            emit(DownloadState.Error(e.message ?: "Download failed"))
        }
    }.flowOn(Dispatchers.IO)
}
