package com.closet.features.recommendations.scoreboard

import kotlinx.coroutines.runBlocking

/**
 * The curated personas' production (harmony on) scoreboard run, made once per test JVM and shared
 * by [BaselineTest] and [CuratedReportTest], so the five simulated years run only once.
 */
object CuratedRun {
    val results: List<PersonaResult> by lazy {
        runBlocking { AllPersonas.list.map { Scoreboard.run(it) } }
    }
}
