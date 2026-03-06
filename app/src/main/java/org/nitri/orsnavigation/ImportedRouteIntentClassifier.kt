package org.nitri.orsnavigation

import android.content.Intent

sealed class IncomingIntentType {
    data class Geo(val intent: Intent) : IncomingIntentType()
    data class JsonRoute(val intent: Intent) : IncomingIntentType()
    data object Unsupported : IncomingIntentType()
}

object ImportedRouteIntentClassifier {
    fun classify(intent: Intent?): IncomingIntentType {
        if (intent == null) return IncomingIntentType.Unsupported

        val uri = intent.data ?: return IncomingIntentType.Unsupported
        val scheme = uri.scheme?.lowercase()
        if (scheme == "geo") {
            return IncomingIntentType.Geo(intent)
        }

        if (intent.action == Intent.ACTION_VIEW && intent.type == "application/json") {
            return IncomingIntentType.JsonRoute(intent)
        }

        if (intent.action == Intent.ACTION_VIEW && (scheme == "content" || scheme == "file")) {
            return IncomingIntentType.JsonRoute(intent)
        }

        return IncomingIntentType.Unsupported
    }
}
