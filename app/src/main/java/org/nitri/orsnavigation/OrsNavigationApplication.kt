package org.nitri.orsnavigation

import android.app.Application
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer

class OrsNavigationApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this, getString(R.string.ors_api_key), WellKnownTileServer.MapLibre)
    }
}