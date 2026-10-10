package et.windows.db

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Which copy of the database this server is serving (Test/Sync/corner-cases.md,
 * S20). Phones remember the id; when it changes they start over — re-read
 * everything and re-send everything — so a database restored from an old
 * backup is rebuilt from the phones instead of silently missing what was
 * written after the backup.
 *
 * The id can't live inside the database, since restoring the database
 * would restore the old id with it. It's kept in [file] beside it, with the
 * highest write sequence number this id has handed out. On start, a
 * database whose sequence is behind that (it went back in time) or a
 * missing file gets a new id.
 */
class DatabaseIdentity private constructor(private val file: File, val dbId: String, private var highSeq: Long) {
    /** Called for every write sequence number handed out (see [Stamper]). */
    @Synchronized
    fun recordSeq(seq: Long) {
        if (seq <= highSeq) return
        highSeq = seq
        save(file, dbId, highSeq)
    }

    companion object {
        fun load(file: File, databaseMaxSeq: Long): DatabaseIdentity {
            val saved = runCatching { file.readLines() }.getOrNull()
            val savedId = saved?.getOrNull(0)?.trim().orEmpty()
            val savedSeq = saved?.getOrNull(1)?.trim()?.toLongOrNull()
            if (savedId.isNotEmpty() && savedSeq != null && databaseMaxSeq >= savedSeq) {
                return DatabaseIdentity(file, savedId, databaseMaxSeq)
            }
            val fresh = UUID.randomUUID().toString()
            save(file, fresh, databaseMaxSeq)
            return DatabaseIdentity(file, fresh, databaseMaxSeq)
        }

        private fun save(file: File, dbId: String, seq: Long) {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText("$dbId\n$seq\n")
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
    }
}
