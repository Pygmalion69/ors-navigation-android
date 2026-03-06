package org.nitri.orsnavigation

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedRouteIntentClassifierTest {

    @Test
    fun `classify geo intent`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:52.1,13.2"))
        val result = ImportedRouteIntentClassifier.classify(intent)

        assertTrue(result is IncomingIntentType.Geo)
    }

    @Test
    fun `classify json content intent`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("content://test/route.json")).apply {
            type = "application/json"
        }
        val result = ImportedRouteIntentClassifier.classify(intent)

        assertTrue(result is IncomingIntentType.JsonRoute)
    }

    @Test
    fun `classify unsupported intent`() {
        val intent = Intent(Intent.ACTION_SEND)
        val result = ImportedRouteIntentClassifier.classify(intent)

        assertTrue(result is IncomingIntentType.Unsupported)
    }
}
