package com.example.core.ai

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class GGUFMetadata(
    val isValid: Boolean,
    val version: Int = 0,
    val tensorCount: Long = 0L,
    val metadataKvCount: Long = 0L,
    val architecture: String = "unknown",
    val modelName: String = "unknown",
    val contextLength: Int = 4096,
    val quantization: String = "unknown",
    val errorMessage: String? = null
)

/**
 * Bounded GGUF v2/v3 metadata reader.
 *
 * It skips primitive and array metadata values according to the GGUF value
 * type instead of stopping at the first array (tokenizer metadata commonly
 * contains large arrays). It validates file bounds and does not assume that
 * every GGUF is a Qwen3 Q4 model.
 */
object GGUFParser {
    private const val TAG = "GGUFParser"
    private const val GGUF_MAGIC = 0x46554747
    private const val MAX_METADATA_ENTRIES = 100_000L
    private const val MAX_KEY_BYTES = 16L * 1024L
    private const val MAX_STRING_BYTES = 16L * 1024L * 1024L

    private const val UINT8 = 0
    private const val INT8 = 1
    private const val UINT16 = 2
    private const val INT16 = 3
    private const val UINT32 = 4
    private const val INT32 = 5
    private const val FLOAT32 = 6
    private const val BOOL = 7
    private const val STRING = 8
    private const val ARRAY = 9
    private const val UINT64 = 10
    private const val INT64 = 11
    private const val FLOAT64 = 12

    fun parse(file: File): GGUFMetadata {
        if (!file.isFile || !file.canRead()) {
            return GGUFMetadata(false, errorMessage = "File is missing or unreadable: " + file.name)
        }
        if (file.length() < 24L) {
            return GGUFMetadata(false, errorMessage = "File is too small to contain a GGUF header.")
        }

        return try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = readInt32(raf)
                if (magic != GGUF_MAGIC) {
                    return GGUFMetadata(false, errorMessage = "Invalid GGUF magic header.")
                }

                val version = readInt32(raf)
                if (version !in 2..3) {
                    return GGUFMetadata(false, version = version, errorMessage = "Unsupported GGUF version: " + version)
                }

                val tensorCount = readUInt64(raf)
                val kvCount = readUInt64(raf)
                if (tensorCount <= 0L || tensorCount > 10_000_000L) {
                    return GGUFMetadata(false, version, tensorCount, kvCount, errorMessage = "Invalid GGUF tensor count.")
                }
                if (kvCount < 0L || kvCount > MAX_METADATA_ENTRIES) {
                    return GGUFMetadata(false, version, tensorCount, kvCount, errorMessage = "Invalid GGUF metadata entry count.")
                }

                var architecture = "unknown"
                var modelName = "unknown"
                var contextLength = 4096
                var quantization = "unknown"

                var entry = 0L
                while (entry < kvCount) {
                    ensureRemaining(raf, 12L)
                    val keyLength = readUInt64(raf)
                    if (keyLength <= 0L || keyLength > MAX_KEY_BYTES) {
                        throw IllegalArgumentException("Invalid GGUF metadata key length.")
                    }
                    ensureRemaining(raf, keyLength + 4L)
                    val keyBytes = ByteArray(keyLength.toInt())
                    raf.readFully(keyBytes)
                    val key = String(keyBytes, Charsets.UTF_8)
                    val valueType = readInt32(raf)

                    when (valueType) {
                        STRING -> {
                            val value = readString(raf)
                            when {
                                key == "general.architecture" -> architecture = value
                                key == "general.name" -> modelName = value
                                key.contains("quantization", ignoreCase = true) -> quantization = value
                            }
                        }
                        UINT32, INT32 -> {
                            val value = readInt32(raf)
                            if (key.endsWith(".context_length") || key == "context_length") {
                                contextLength = value.coerceIn(512, 1_000_000)
                            }
                        }
                        UINT64, INT64 -> {
                            val value = readUInt64(raf)
                            if ((key.endsWith(".context_length") || key == "context_length") &&
                                value in 512L..1_000_000L
                            ) {
                                contextLength = value.toInt()
                            }
                        }
                        else -> skipValue(raf, valueType, 0)
                    }
                    entry++
                }

                if (raf.filePointer > raf.length()) {
                    throw IllegalArgumentException("GGUF metadata extends beyond the end of the file.")
                }

                GGUFMetadata(
                    isValid = true,
                    version = version,
                    tensorCount = tensorCount,
                    metadataKvCount = kvCount,
                    architecture = architecture,
                    modelName = modelName,
                    contextLength = contextLength,
                    quantization = quantization
                )
            }
        } catch (error: Exception) {
            Log.e(TAG, "GGUF parse failed for " + file.name + ": " + error.message, error)
            GGUFMetadata(false, errorMessage = "GGUF parse error: " + (error.message ?: "unknown error"))
        }
    }

    private fun skipValue(raf: RandomAccessFile, type: Int, depth: Int) {
        if (depth > 2) throw IllegalArgumentException("Nested GGUF metadata arrays are unsupported.")
        when (type) {
            UINT8, INT8, BOOL -> skipExactly(raf, 1L)
            UINT16, INT16 -> skipExactly(raf, 2L)
            UINT32, INT32, FLOAT32 -> skipExactly(raf, 4L)
            UINT64, INT64, FLOAT64 -> skipExactly(raf, 8L)
            STRING -> {
                val length = readUInt64(raf)
                if (length < 0L || length > MAX_STRING_BYTES) {
                    throw IllegalArgumentException("GGUF metadata string is too large.")
                }
                skipExactly(raf, length)
            }
            ARRAY -> {
                val elementType = readInt32(raf)
                val count = readUInt64(raf)
                if (count < 0L || count > 100_000_000L) {
                    throw IllegalArgumentException("Invalid GGUF metadata array length.")
                }
                val primitiveWidth = when (elementType) {
                    UINT8, INT8, BOOL -> 1L
                    UINT16, INT16 -> 2L
                    UINT32, INT32, FLOAT32 -> 4L
                    UINT64, INT64, FLOAT64 -> 8L
                    else -> 0L
                }
                if (primitiveWidth > 0L) {
                    if (count > Long.MAX_VALUE / primitiveWidth) {
                        throw IllegalArgumentException("GGUF metadata array size overflow.")
                    }
                    skipExactly(raf, count * primitiveWidth)
                } else if (elementType == STRING) {
                    repeat(count.toInt()) {
                        val length = readUInt64(raf)
                        if (length < 0L || length > MAX_STRING_BYTES) {
                            throw IllegalArgumentException("Invalid string length in GGUF array.")
                        }
                        skipExactly(raf, length)
                    }
                } else {
                    throw IllegalArgumentException("Unsupported GGUF array element type: " + elementType)
                }
            }
            else -> throw IllegalArgumentException("Unsupported GGUF metadata value type: " + type)
        }
    }

    private fun readString(raf: RandomAccessFile): String {
        val length = readUInt64(raf)
        if (length < 0L || length > MAX_STRING_BYTES) {
            throw IllegalArgumentException("GGUF metadata string is too large.")
        }
        ensureRemaining(raf, length)
        val bytes = ByteArray(length.toInt())
        raf.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun readUInt64(raf: RandomAccessFile): Long {
        val bytes = ByteArray(8)
        raf.readFully(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).long
    }

    private fun readInt32(raf: RandomAccessFile): Int {
        val bytes = ByteArray(4)
        raf.readFully(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private fun ensureRemaining(raf: RandomAccessFile, count: Long) {
        if (count < 0L || count > raf.length() - raf.filePointer) {
            throw IllegalArgumentException("Unexpected end of GGUF file.")
        }
    }

    private fun skipExactly(raf: RandomAccessFile, count: Long) {
        ensureRemaining(raf, count)
        raf.seek(raf.filePointer + count)
    }
}
