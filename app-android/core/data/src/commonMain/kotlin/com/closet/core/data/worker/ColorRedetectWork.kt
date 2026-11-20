package com.closet.core.data.worker

import kotlinx.coroutines.flow.Flow

/**
 * Constants shared between `ColorRedetectWorker` (features/wardrobe) and `SettingsViewModel`
 * (features/settings) so neither module needs to depend on the other (#128).
 */
object ColorRedetectWork {
    const val NAME = "color_redetect"

    /** Input key: when true, only items whose colours the user hasn't edited (`colors_curated = 0`). */
    const val KEY_ONLY_UNCURATED = "only_uncurated"

    /** Progress / output key: items whose colours were re-detected and written. */
    const val KEY_DONE = WorkStatus.KEY_DONE

    /** Progress / output key: total items queued for this run. */
    const val KEY_TOTAL = WorkStatus.KEY_TOTAL

    /** Progress / output key: items whose re-detection or write failed. */
    const val KEY_FAILED = WorkStatus.KEY_FAILED

    /**
     * Progress / output key: items left as they were because nothing was detected.
     * Shares [WorkStatus.KEY_SKIPPED], so it reads back as [WorkStatus.skipped].
     */
    const val KEY_UNCHANGED = WorkStatus.KEY_SKIPPED
}

/**
 * Lets `SettingsViewModel` start a bulk colour re-detect without a direct dependency on
 * `features/wardrobe`. On Android it is bound by `wardrobeModule` (both flavors: colour
 * extraction needs no Play Services); JVM and iOS bind [NoOpColorRedetectScheduler].
 */
interface ColorRedetectScheduler {
    /** `true` on Android (both flavors); `false` on JVM/iOS, which have no background runner. */
    val isSupported: Boolean

    /**
     * Starts the unique [ColorRedetectWork.NAME] job unless one is already pending or running.
     * @param onlyUncurated skip items whose colours the user has edited.
     */
    fun schedule(onlyUncurated: Boolean)

    /**
     * Live status of the unique colour re-detect job; null when none. `done`, `failed` and
     * `skipped` (unchanged) together count the items processed so far.
     */
    val workStatus: Flow<WorkStatus?>
}
