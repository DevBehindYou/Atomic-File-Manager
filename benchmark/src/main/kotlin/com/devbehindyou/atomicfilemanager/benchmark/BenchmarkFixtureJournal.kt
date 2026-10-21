package com.devbehindyou.atomicfilemanager.benchmark

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Append-only, synced recovery evidence; no automatic deletion/recovery trusts this file. */
internal class BenchmarkFixtureJournal(context: Context, runId: String, entries: Int) {
    val file = File(
        File(context.filesDir, "benchmark-fixture-recovery").apply { check(isDirectory || mkdirs()) },
        "$runId.jsonl",
    )

    init {
        check(file.createNewFile()) { "Recovery run ID already exists" }
        append(
            JSONObject().put("event", "run").put("schema", 1).put("runId", runId)
                .put("owner", context.packageName).put("entries", entries),
        )
    }

    fun record(event: String, row: BenchmarkOwnedRow) {
        val record = JSONObject().put("event", event).put("uri", row.uri.toString())
            .put("requestedPath", row.relativePath).put("requestedName", row.name).put("requestedMime", row.mime)
        row.identity?.let { identity ->
            record.put("actualOwner", identity.owner ?: JSONObject.NULL)
                .put("actualPath", identity.path ?: JSONObject.NULL)
                .put("actualName", identity.name ?: JSONObject.NULL)
                .put("actualMime", identity.mime ?: JSONObject.NULL)
        }
        append(record)
    }

    fun intent(path: String, name: String, mime: String) {
        append(JSONObject().put("event", "intent").put("path", path).put("name", name).put("mime", mime))
    }

    fun complete() = append(JSONObject().put("event", "cleanupComplete"))

    private fun append(record: JSONObject) {
        FileOutputStream(file, true).use { output ->
            output.write((record.toString() + "\n").toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
    }
}
