package com.crusty.llm

import android.content.Context
import com.crusty.data.SettingsRepository
import com.crusty.model.ModelVariant
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
        // Verified Aug 2026: this repo exists and is NOT gated (no HF token needed).
        // The old URL (litert-community/gemma-4-E2B-it) 404/401s.
        const val HF_MODEL_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
        const val MODEL_SIZE_BYTES = 2_590_000_000L // ~2.59 GB. NB: the -gpu variant does
        // NOT load — it lacks TF_LITE_PREFILL_DECODE. Use this standard build.
    }

    private fun modelsDir(): File =
        File(context.filesDir, "models").apply { if (!exists()) mkdirs() }

    /** The file for a specific variant, downloaded or not. */
    fun fileFor(variant: ModelVariant): File = File(modelsDir(), variant.fileName)

    fun isDownloaded(variant: ModelVariant): Boolean =
        fileFor(variant).let { it.exists() && it.length() > 0 }

    override fun getTargetModelFile(): File = fileFor(settingsRepository.getModelVariant())

    override suspend fun getModelFile(): File? {
        // 1. Check custom path if set
        val customPath = settingsRepository.getCustomModelPath()
        if (!customPath.isNullOrBlank()) {
            val customFile = File(customPath)
            if (customFile.exists() && customFile.length() > 0) {
                return customFile
            }
        }

        // 2. The chosen variant, then any other variant that is present, so the app
        //    still works if the user picked one they have not downloaded yet.
        val chosen = settingsRepository.getModelVariant()
        fileFor(chosen).let { if (it.exists() && it.length() > 0) return it }
        ModelVariant.entries.forEach { v ->
            fileFor(v).let { if (it.exists() && it.length() > 0) return it }
        }

        // 3. Check common adb push paths for fast development
        // The app-specific external dir is the ONLY sideload target that works on modern
        // Android: adb (shell) can write it, and we can read it with no storage permission.
        // /data/local/tmp is SELinux-denied to us and /sdcard/Download needs a permission we
        // deliberately don't hold — both of those probes silently always failed.
        val external = context.getExternalFilesDir(null)
        val adbPaths = listOfNotNull(
            external?.let { File(it, "models/$MODEL_FILENAME").absolutePath },
            external?.let { File(it, MODEL_FILENAME).absolutePath },
            "/sdcard/Download/$MODEL_FILENAME",
            "/data/local/tmp/$MODEL_FILENAME"
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
