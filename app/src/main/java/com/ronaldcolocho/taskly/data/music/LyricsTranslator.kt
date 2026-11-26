package com.ronaldcolocho.taskly.data.music

import android.content.Context
import android.util.AtomicFile
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsTranslator @Inject constructor(@ApplicationContext context: Context) {
    private val cacheDirectory = context.cacheDir

    suspend fun sourceLanguage(text: String): String? {
        val identifier = LanguageIdentification.getClient()
        return try {
            TranslateLanguage.fromLanguageTag(identifier.identifyLanguage(text).await())
                ?.takeUnless { it == TranslateLanguage.SPANISH }
        } finally {
            identifier.close()
        }
    }

    suspend fun translate(sourceLanguage: String, lines: List<String>): List<String> = withContext(Dispatchers.IO) {
        val english = sourceLanguage == TranslateLanguage.ENGLISH
        val directory = File(cacheDirectory, if (english) "lyrics_en_es_v1" else "lyrics_to_es_v1")
        val text = JSONArray(lines).toString()
        val cacheKey = if (english) text else "$sourceLanguage|$text"
        val key = MessageDigest.getInstance("SHA-256").digest(cacheKey.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val file = AtomicFile(File(directory, "$key.json"))
        readCache(file, lines.size)?.let { return@withContext it }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLanguage)
            .setTargetLanguage(TranslateLanguage.SPANISH)
            .build()
        val translator = Translation.getClient(options)
        val translated = try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().requireWifi().build()).await()
            val unique = mutableMapOf<String, String>()
            lines.map { line ->
                ensureActive()
                if (line.none(Char::isLetter)) ""
                else unique.getOrPut(line) { translator.translate(line).await().trim() }
            }
        } finally {
            translator.close()
        }
        ensureActive()
        check(translated.any(String::isNotBlank))
        writeCache(file, translated)
        translated
    }

    @Synchronized
    private fun readCache(file: AtomicFile, count: Int): List<String>? = runCatching {
        val data = JSONArray(file.readFully().toString(Charsets.UTF_8))
        if (data.length() == count) List(count, data::getString) else null
    }.getOrNull()

    @Synchronized
    private fun writeCache(file: AtomicFile, lines: List<String>) {
        runCatching {
            val stream = file.startWrite()
            try {
                stream.write(JSONArray(lines).toString().toByteArray())
                file.finishWrite(stream)
            } catch (error: Exception) {
                file.failWrite(stream)
                throw error
            }
            file.baseFile.parentFile?.listFiles { entry -> entry.extension == "json" }
                ?.sortedByDescending(File::lastModified)?.drop(128)?.forEach(File::delete)
        }
    }
}
