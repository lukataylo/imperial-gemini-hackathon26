package com.gatekeeper.llm

import android.content.Context
import com.gatekeeper.data.SettingsRepository
import java.io.File

interface ModelProvider {
    suspend fun getModelFile(): File?
    suspend fun isModelAvailable(): Boolean
    fun getTargetModelFile(): File
}

class DefaultModelProvider(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) : ModelProvider {

    companion object {
        const val MODEL_FILENAME = "gemma-4-E2B-it.litertlm"
        const val HF_MODEL_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it/resolve/main/gemma-4-E2B-it.litertlm"
        const val MODEL_SIZE_BYTES = 1_250_000_000L // ~1.25 GB
    }

    override fun getTargetModelFile(): File {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        return File(modelsDir, MODEL_FILENAME)
    }

    override suspend fun getModelFile(): File? {
        // 1. Check custom path if set
        val customPath = settingsRepository.getCustomModelPath()
        if (!customPath.isNullOrBlank()) {
            val customFile = File(customPath)
            if (customFile.exists() && customFile.length() > 0) {
                return customFile
            }
        }

        // 2. Check standard app files directory
        val targetFile = getTargetModelFile()
        if (targetFile.exists() && targetFile.length() > 0) {
            return targetFile
        }

        // 3. Check common adb push paths for fast development
        val adbPaths = listOf(
            "/data/local/tmp/$MODEL_FILENAME",
            "/sdcard/Download/$MODEL_FILENAME",
            "/data/local/tmp/gemma-2b-it.litertlm"
        )
        for (path in adbPaths) {
            val file = File(path)
            if (file.exists() && file.length() > 0) {
                return file
            }
        }

        return null
    }

    override suspend fun isModelAvailable(): Boolean {
        return getModelFile() != null
    }
}
