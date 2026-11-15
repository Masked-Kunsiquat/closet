package com.closet.core.data.location

/**
 * A single coarse location reading for weather lookups.
 *
 * Each platform binds its own implementation in its data module: Android binds
 * `AndroidLocationProvider`; JVM and iOS bind [NoLocationProvider] for now.
 */
interface LocationProvider {
    /**
     * Returns `(latitude, longitude)`, or `null` when no location is available within
     * [timeoutMs] (no permission, no fix, provider error, or no location support).
     */
    suspend fun getLocation(timeoutMs: Long = 10_000L): Pair<Double, Double>?
}

/**
 * A [LocationProvider] for platforms with no location support yet. Always returns `null`,
 * so [com.closet.core.data.repository.WeatherRepository] falls back to the last cached
 * coordinates, or reports that no location is available.
 */
object NoLocationProvider : LocationProvider {
    override suspend fun getLocation(timeoutMs: Long): Pair<Double, Double>? = null
}
