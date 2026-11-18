package com.closet.core.data.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The source of "now" and "today" for the app. Production code reads time through this so tests
 * can pin it with [FixedAppClock]. Both the instant and the zone are read per call, so a device
 * time-zone change is picked up without a restart.
 */
interface AppClock {
    fun now(): Instant
    fun timeZone(): TimeZone
    fun today(): LocalDate = now().toLocalDateTime(timeZone()).date
}

/** The real clock: the system time and the device's current time zone. */
object SystemAppClock : AppClock {
    override fun now(): Instant = Clock.System.now()
    override fun timeZone(): TimeZone = TimeZone.currentSystemDefault()
}

/** A clock frozen at [instant] in [zone], for tests. */
class FixedAppClock(
    private val instant: Instant,
    private val zone: TimeZone = TimeZone.UTC,
) : AppClock {
    override fun now(): Instant = instant
    override fun timeZone(): TimeZone = zone
}
