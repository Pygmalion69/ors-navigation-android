package org.nitri.orsnavigation

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.Locale

sealed class ImportedRouteResult {
    data class Success(val route: org.maplibre.navigation.core.models.DirectionsRoute) : ImportedRouteResult()
    data class Error(val message: String, val cause: Throwable? = null) : ImportedRouteResult()
}

class ImportedRouteLoader(
    private val contentResolver: ContentResolver,
    language: String = Locale.getDefault().language,
) {
    private val parser = ImportedRouteParser(language = language)

    suspend fun load(uri: Uri): ImportedRouteResult = withContext(Dispatchers.IO) {
        Timber.d("ImportedRouteLoader.load uri=%s", uri)
        val jsonText = try {
            readUri(uri)
        } catch (e: Exception) {
            Timber.e(e, "Route import read failed for uri=%s", uri)
            val message = if (uri.scheme == ContentResolver.SCHEME_FILE) {
                "Unable to read file URI. On modern Android, use an app-accessible file path (for example app internal storage) or a content URI granted by a document picker."
            } else {
                "Unable to read route JSON from URI."
            }
            return@withContext ImportedRouteResult.Error(
                message = message,
                cause = e,
            )
        }

        parser.parseAndNormalize(jsonText)
    }

    private fun readUri(uri: Uri): String {
        return when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> {
                val stream = contentResolver.openInputStream(uri)
                    ?: throw IOException("Input stream is null for $uri")
                stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }

            ContentResolver.SCHEME_FILE -> {
                val path = uri.path ?: throw IOException("Missing file path in URI: $uri")
                File(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }

            else -> {
                throw IOException("Unsupported URI scheme: ${uri.scheme}")
            }
        }
    }
}
