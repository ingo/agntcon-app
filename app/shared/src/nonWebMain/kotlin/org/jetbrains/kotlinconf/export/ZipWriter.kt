package org.jetbrains.kotlinconf.export

import okio.BufferedSink
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

/**
 * Writes a zip file without compression, which suits a report of photos, videos and PDFs (they are
 * compressed already) and any phone can open. Files are read twice, once for the checksum and once to
 * copy, so nothing large is held in memory. Without the ZIP64 extensions an archive stays under 4 GB.
 */
class ZipWriter(
    private val sink: BufferedSink,
    private val dosDate: Int,
    private val dosTime: Int,
) : AutoCloseable {
    private class Entry(val name: ByteArray, val crc: Long, val size: Long, val offset: Long)

    private val entries = mutableListOf<Entry>()
    private var offset = 0L

    fun addText(name: String, text: String) {
        val bytes = text.encodeToByteArray()
        addEntry(name, Crc32().apply { update(bytes, 0, bytes.size) }.value, bytes.size.toLong()) {
            sink.write(bytes)
        }
    }

    fun addFile(name: String, fileSystem: FileSystem, path: Path) {
        val crc = Crc32()
        var size = 0L
        fileSystem.source(path).buffer().use { source ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = source.read(buffer, 0, buffer.size)
                if (read == -1) break
                crc.update(buffer, 0, read)
                size += read
            }
        }
        addEntry(name, crc.value, size) {
            fileSystem.source(path).use { sink.writeAll(it) }
        }
    }

    private inline fun addEntry(name: String, crc: Long, size: Long, writeData: () -> Unit) {
        val nameBytes = name.encodeToByteArray()
        if (offset + size + LOCAL_HEADER + nameBytes.size >= LIMIT) throw ZipTooLargeException()
        val header = Buffer()
            .writeIntLe(0x04034b50)
            .writeShortLe(VERSION)
            .writeShortLe(UTF8_NAMES)
            .writeShortLe(0) // stored, not compressed
            .writeShortLe(dosTime)
            .writeShortLe(dosDate)
            .writeIntLe(crc.toInt())
            .writeIntLe(size.toInt())
            .writeIntLe(size.toInt())
            .writeShortLe(nameBytes.size)
            .writeShortLe(0)
            .write(nameBytes)
        val headerSize = header.size
        sink.writeAll(header)
        writeData()
        entries += Entry(nameBytes, crc, size, offset)
        offset += headerSize + size
    }

    /** Writes the central directory; the zip is complete after this. */
    fun finish() {
        if (entries.size >= 0xFFFF) throw ZipTooLargeException()
        val directoryStart = offset
        var directorySize = 0L
        for (entry in entries) {
            val record = Buffer()
                .writeIntLe(0x02014b50)
                .writeShortLe(VERSION)
                .writeShortLe(VERSION)
                .writeShortLe(UTF8_NAMES)
                .writeShortLe(0)
                .writeShortLe(dosTime)
                .writeShortLe(dosDate)
                .writeIntLe(entry.crc.toInt())
                .writeIntLe(entry.size.toInt())
                .writeIntLe(entry.size.toInt())
                .writeShortLe(entry.name.size)
                .writeShortLe(0) // extra field
                .writeShortLe(0) // comment
                .writeShortLe(0) // disk
                .writeShortLe(0) // internal attributes
                .writeIntLe(0) // external attributes
                .writeIntLe(entry.offset.toInt())
                .write(entry.name)
            directorySize += record.size
            sink.writeAll(record)
        }
        if (directoryStart + directorySize >= LIMIT) throw ZipTooLargeException()
        sink.writeIntLe(0x06054b50)
            .writeShortLe(0)
            .writeShortLe(0)
            .writeShortLe(entries.size)
            .writeShortLe(entries.size)
            .writeIntLe(directorySize.toInt())
            .writeIntLe(directoryStart.toInt())
            .writeShortLe(0)
        sink.flush()
    }

    override fun close() = sink.close()

    companion object {
        private const val VERSION = 20
        private const val UTF8_NAMES = 0x0800
        private const val LOCAL_HEADER = 30
        private const val BUFFER_SIZE = 64 * 1024

        /** Offsets and sizes are 32-bit without ZIP64; keep some room for the directory. */
        const val LIMIT = 0xFFFF_FFFFL - 16L * 1024 * 1024
    }
}

/** The files do not fit in a zip without ZIP64 (4 GB, 65535 files). */
class ZipTooLargeException : RuntimeException("Too large for a zip file")

/** CRC-32 as zip uses it (the IEEE 802.3 polynomial). */
class Crc32 {
    private var crc = 0xFFFFFFFFL

    val value: Long get() = crc xor 0xFFFFFFFFL

    fun update(bytes: ByteArray, offset: Int, length: Int) {
        var c = crc
        for (i in offset until offset + length) {
            c = TABLE[((c xor bytes[i].toLong()) and 0xFF).toInt()] xor (c ushr 8)
        }
        crc = c
    }

    private companion object {
        val TABLE = LongArray(256) { n ->
            var c = n.toLong()
            repeat(8) { c = if (c and 1L != 0L) 0xEDB88320L xor (c ushr 1) else c ushr 1 }
            c
        }
    }
}
