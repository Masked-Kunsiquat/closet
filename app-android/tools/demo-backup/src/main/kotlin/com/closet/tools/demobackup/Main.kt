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

fun main(args: Array<String>) {
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
