package org.nitri.orsnavigation

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
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
            return@withContext ImportedRouteResult.Error(
                message = "Unable to read route JSON from URI.",
                cause = e,
            )
        }

        parser.parseAndNormalize(jsonText)
    }

    private fun readUri(uri: Uri): String {
        val stream = contentResolver.openInputStream(uri)
            ?: throw IOException("Input stream is null for $uri")

        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
