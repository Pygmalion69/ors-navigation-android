package org.nitri.orsnavigation

import android.content.Intent

sealed class IncomingIntentType {
    data object Geo : IncomingIntentType()
    data object JsonRoute : IncomingIntentType()
    data object Unsupported : IncomingIntentType()
}

data class ImportedRouteIntentInput(
    val action: String?,
    val mimeType: String?,
    val scheme: String?,
    val hasDataUri: Boolean,
)

object ImportedRouteIntentClassifier {
    fun classify(intent: Intent?): IncomingIntentType {
        if (intent == null) return IncomingIntentType.Unsupported

        val uri = intent.data
        return classify(
            ImportedRouteIntentInput(
                action = intent.action,
                mimeType = intent.type,
                scheme = uri?.scheme?.lowercase(),
                hasDataUri = uri != null,
            )
        )
    }

    fun classify(input: ImportedRouteIntentInput): IncomingIntentType {
        if (!input.hasDataUri) {
            return IncomingIntentType.Unsupported
        }

        if (input.scheme == "geo") {
            return IncomingIntentType.Geo
        }

        if (input.action == Intent.ACTION_VIEW && input.mimeType == "application/json") {
            return IncomingIntentType.JsonRoute
        }

        if (input.action == Intent.ACTION_VIEW && (input.scheme == "content" || input.scheme == "file")) {
            return IncomingIntentType.JsonRoute
        }

        return IncomingIntentType.Unsupported
    }
}
