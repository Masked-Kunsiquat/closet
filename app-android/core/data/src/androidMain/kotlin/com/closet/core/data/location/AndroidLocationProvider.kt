package com.closet.core.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import co.touchlab.kermit.Logger
import kotlin.coroutines.resume

/**
 * [LocationProvider] for Android: a single passive location reading via [LocationManager]. No Play
 * Services dependency — the app has none.
 *
 * Strategy:
 * 1. Return [LocationManager.NETWORK_PROVIDER] last-known (instant, no battery).
 * 2. Fall back to [LocationManager.GPS_PROVIDER] last-known.
 * 3. If both caches are empty, request a single active [NETWORK_PROVIDER] update
 *    with a [timeoutMs] deadline. Returns `null` on timeout or provider error.
 *
 * Callers must hold [android.Manifest.permission.ACCESS_COARSE_LOCATION] before
 * invoking [getLocation] — this is guaranteed by the Settings toggle flow.
 */
class AndroidLocationProvider(
    private val context: Context,
) : LocationProvider {
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @SuppressLint("MissingPermission")
    override suspend fun getLocation(timeoutMs: Long): Pair<Double, Double>? {
        try {
            lastKnownLocation()?.let { return it }
        } catch (e: SecurityException) {
            // Permission revoked since the toggle was enabled: let the caller fall back to cached coords.
            Logger.withTag("LocationProvider").w(e) { "LocationProvider: location permission missing" }
            return null
        }
        return withTimeoutOrNull(timeoutMs) { requestSingleUpdate() }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(): Pair<Double, Double>? {
        for (provider in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)) {
            if (!locationManager.isProviderEnabled(provider)) continue
            val loc = locationManager.getLastKnownLocation(provider) ?: continue
            Logger.withTag("LocationProvider").d { "LocationProvider: cached fix from $provider" }
            return Pair(loc.latitude, loc.longitude)
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSingleUpdate(): Pair<Double, Double>? =
        suspendCancellableCoroutine { cont ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    locationManager.removeUpdates(this)
                    Logger.withTag("LocationProvider").d { "LocationProvider: active fix received" }
                    if (cont.isActive) cont.resume(Pair(location.latitude, location.longitude))
                }

                override fun onProviderDisabled(provider: String) {
                    locationManager.removeUpdates(this)
                    if (cont.isActive) cont.resume(null)
                }
            }
            try {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    0L, 0f,
                    listener,
                    Looper.getMainLooper(),
                )
                cont.invokeOnCancellation { locationManager.removeUpdates(listener) }
            } catch (e: Exception) {
                Logger.withTag("LocationProvider").e(e) { "LocationProvider: requestLocationUpdates failed" }
                if (cont.isActive) cont.resume(null)
            }
        }
}
