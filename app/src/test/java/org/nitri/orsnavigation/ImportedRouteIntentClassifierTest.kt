package org.nitri.orsnavigation

import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedRouteIntentClassifierTest {

    @Test
    fun `classify geo intent`() {
        val result = ImportedRouteIntentClassifier.classify(
            ImportedRouteIntentInput(
                action = "android.intent.action.VIEW",
                mimeType = null,
                scheme = "geo",
                hasDataUri = true,
            )
        )

        assertTrue(result is IncomingIntentType.Geo)
    }

    @Test
    fun `classify json content intent`() {
        val result = ImportedRouteIntentClassifier.classify(
            ImportedRouteIntentInput(
                action = "android.intent.action.VIEW",
                mimeType = "application/json",
                scheme = "content",
                hasDataUri = true,
            )
        )

        assertTrue(result is IncomingIntentType.JsonRoute)
    }

    @Test
    fun `classify unsupported intent`() {
        val result = ImportedRouteIntentClassifier.classify(
            ImportedRouteIntentInput(
                action = "android.intent.action.SEND",
                mimeType = "application/json",
                scheme = null,
                hasDataUri = false,
            )
        )

        assertTrue(result is IncomingIntentType.Unsupported)
    }
}
