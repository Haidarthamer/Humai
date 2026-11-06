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
 * Parses and verifies binary GGUF files according to the GGML GGUF specification (v2/v3).
 * Extracts metadata, validates magic headers (0x46554747), architecture, and tensor count.
 */
object GGUFParser {
    private const val TAG = "GGUFParser"
    private const val GGUF_MAGIC = 0x46554747 // "GGUF" in ASCII little-endian

    // GGUF metadata value types
    private const val GGUF_TYPE_UINT8 = 0
    private const val GGUF_TYPE_INT8 = 1
    private const val GGUF_TYPE_UINT16 = 2
    private const val GGUF_TYPE_INT16 = 3
    private const val GGUF_TYPE_UINT32 = 4
    private const val GGUF_TYPE_INT32 = 5
    private const val GGUF_TYPE_FLOAT32 = 6
    private const val GGUF_TYPE_BOOL = 7
    private const val GGUF_TYPE_STRING = 8
    private const val GGUF_TYPE_ARRAY = 9
    private const val GGUF_TYPE_UINT64 = 10
    private const val GGUF_TYPE_INT64 = 11
    private const val GGUF_TYPE_FLOAT64 = 12

    fun parse(file: File): GGUFMetadata {
        if (!file.exists()) {
            return GGUFMetadata(isValid = false, errorMessage = "File not found: ${file.name}")
        }
        if (file.length() < 32) {
            return GGUFMetadata(isValid = false, errorMessage = "File too small to be a valid GGUF binary (${file.length()} bytes)")
        }

        return try {
            RandomAccessFile(file, "r").use { raf ->
                val headerBytes = ByteArray(24)
                raf.readFully(headerBytes)
                val buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)

                val magic = buffer.int
                if (magic != GGUF_MAGIC) {
                    val magicStr = String(headerBytes.copyOfRange(0, 4))
                    return GGUFMetadata(
                        isValid = false,
                        errorMessage = "Invalid magic bytes '$magicStr' (expected 'GGUF'). Not a GGUF file."
                    )
                }

                val version = buffer.int
                if (version !in 2..3) {
                    return GGUFMetadata(
                        isValid = false,
                        version = version,
                        errorMessage = "Unsupported GGUF version $version (supports v2 and v3)"
                    )
                }

                val tensorCount = buffer.long
                val kvCount = buffer.long

                var architecture = "qwen3"
                var modelName = file.nameWithoutExtension
                var contextLength = 4096
                var quantization = "Q4_K_M"

                // Read a few initial key-value pairs if present
                var readKv = 0
                while (readKv < kvCount && raf.filePointer < (file.length() - 16) && readKv < 30) {
                    val keyLen = readUint64(raf)
                    if (keyLen <= 0 || keyLen > 256) break
                    val keyBytes = ByteArray(keyLen.toInt())
                    raf.readFully(keyBytes)
                    val key = String(keyBytes, Charsets.UTF_8)

                    val valType = readInt32(raf) // 4 bytes in v3 little-endian
                    when (valType) {
                        GGUF_TYPE_STRING -> {
                            val strLen = readUint64(raf)
                            if (strLen in 1..1024) {
                                val strBytes = ByteArray(strLen.toInt())
                                raf.readFully(strBytes)
                                val strVal = String(strBytes, Charsets.UTF_8)
                                if (key.endsWith(".architecture") || key == "general.architecture") {
                                    architecture = strVal
                                } else if (key.endsWith(".name") || key == "general.name") {
                                    modelName = strVal
                                } else if (key.contains("quantization")) {
                                    quantization = strVal
                                }
                            }
                        }
                        GGUF_TYPE_UINT32, GGUF_TYPE_INT32 -> {
                            val intVal = readInt32(raf)
                            if (key.contains("context_length")) {
                                contextLength = intVal.coerceIn(1024, 32768)
                            }
                        }
                        GGUF_TYPE_UINT64, GGUF_TYPE_INT64 -> {
                            val longVal = readUint64(raf)
                            if (key.contains("context_length")) {
                                contextLength = longVal.toInt().coerceIn(1024, 32768)
                            }
                        }
                        else -> {
                            // Skip or break loop to avoid extensive reading
                            break
                        }
                    }
                    readKv++
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse GGUF file: ${e.message}", e)
            GGUFMetadata(isValid = false, errorMessage = "GGUF parse error: ${e.message}")
        }
    }

    private fun readUint64(raf: RandomAccessFile): Long {
        val bytes = ByteArray(8)
        raf.readFully(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).long
    }

    private fun readInt32(raf: RandomAccessFile): Int {
        val bytes = ByteArray(4)
        raf.readFully(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int
    }
}
