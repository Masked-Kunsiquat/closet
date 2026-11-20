package com.closet.tools.demobackup

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.exitProcess

private const val USAGE = """Usage: --dataset <dir> --out <file.hangr> [--count 300] [--seed 42] [--no-history]

  --dataset     directory holding styles.csv and images/<id>.jpg (Fashion Product Images (Small))
  --out         the .hangr file to write
  --count       number of closet items (default 300)
  --seed        seed for sampling and history (default 42)
  --no-history  skip the demo outfits and wear logs"""

private const val COLOR_CHECK_USAGE = """Usage: color-check <datasetDir> [--limit 300] [--seed 42] [--merge <deltaE>] [--min-share <share>]

  Compares the shared colour extractor with the dataset's baseColour labels, and with the
  legacy HSL matcher as a baseline. --merge and --min-share override the extractor constants
  (for calibration only)."""

private class UsageError(message: String) : Exception(message)

private fun parseArgs(args: Array<String>): Triple<File, File, BackupOptions> {
    var dataset: File? = null
    var out: File? = null
    var options = BackupOptions()
    var i = 0
    fun value(flag: String): String = args.getOrNull(++i) ?: throw UsageError("$flag needs a value")
    while (i < args.size) {
        when (val flag = args[i]) {
            "--dataset" -> dataset = File(value(flag))
            "--out" -> out = File(value(flag))
            "--count" -> options = options.copy(
                count = value(flag).toIntOrNull()?.takeIf { it > 0 } ?: throw UsageError("--count must be a positive integer"),
            )
            "--seed" -> options = options.copy(seed = value(flag).toLongOrNull() ?: throw UsageError("--seed must be an integer"))
            "--no-history" -> options = options.copy(history = false)
            "--history" -> options = options.copy(history = true)
            else -> throw UsageError("Unknown argument: $flag")
        }
        i++
    }
    return Triple(
        dataset ?: throw UsageError("--dataset is required"),
        out ?: throw UsageError("--out is required"),
        options,
    )
}

private fun colorCheck(args: List<String>) {
    var dir: File? = null
    var limit = 300
    var seed = 42L
    var merge: Double? = null
    var minShare: Double? = null
    var i = 0
    fun value(flag: String): String = args.getOrNull(++i) ?: throw UsageError("$flag needs a value")
    while (i < args.size) {
        when (val a = args[i]) {
            "--limit" -> limit = value(a).toIntOrNull()?.takeIf { it > 0 } ?: throw UsageError("--limit must be a positive integer")
            "--seed" -> seed = value(a).toLongOrNull() ?: throw UsageError("--seed must be an integer")
            "--merge" -> merge = value(a).toDoubleOrNull()?.takeIf { it > 0 } ?: throw UsageError("--merge must be a positive number")
            "--min-share" -> minShare = value(a).toDoubleOrNull()?.takeIf { it in 0.0..1.0 } ?: throw UsageError("--min-share must be between 0 and 1")
            else -> if (a.startsWith("--") || dir != null) throw UsageError("Unknown argument: $a") else dir = File(a)
        }
        i++
    }
    println(ColorCheck.run(dir ?: throw UsageError("datasetDir is required"), limit, seed, merge, minShare).render())
}

fun main(args: Array<String>) {
    if (args.firstOrNull() == "color-check") {
        try {
            colorCheck(args.drop(1))
        } catch (e: UsageError) {
            System.err.println("${e.message}\n\n$COLOR_CHECK_USAGE")
            exitProcess(2)
        }
        exitProcess(0)
    }
    val (dataset, out, options) = try {
        parseArgs(args)
    } catch (e: UsageError) {
        System.err.println("${e.message}\n\n$USAGE")
        exitProcess(2)
    }
    // core/data logs every vectorized item at debug level; keep the console to the summary.
    Logger.setMinSeverity(Severity.Warn)
    val summary = runBlocking { DemoBackup.build(dataset, out, options) }
    println(summary.render())
    println("Wrote ${out.absolutePath} (${out.length() / 1024} KiB)")
    // Room and the Ktor/OkHttp classes on the classpath can leave non-daemon threads behind.
    exitProcess(0)
}
